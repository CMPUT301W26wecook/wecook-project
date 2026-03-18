package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity for organizers to access the notification-sending screen from the organizer workflow.
 * Within the app it acts as the UI controller for notification composition/navigation, connected to
 * the organizer bottom-navigation structure.
 *
 * Outstanding issues:
 * - It is more of a placeholder than a fully implemented feature, as the actual notification-sending logic 
 *   is not yet implemented and the screen primarily serves as a navigation stub. The functionality will be 
 *   implemented in part 4.
 */
public class OrganizerNotificationActivity extends AppCompatActivity {
    private FirebaseFirestore db;
    private String eventId;
    private String eventName = "Event Notification";
    private String eventLocation = "";

    private TextView tvEventName;
    private TextView tvEventLocation;
    private TextInputEditText etMessage;
    private Button btnSendNotification;

    /**
     * Initializes organizer notification UI and navigation.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_notification);
        db = FirebaseFirestore.getInstance();
        eventId = getIntent().getStringExtra("eventId");

        tvEventName = findViewById(R.id.tv_notification_event_name);
        tvEventLocation = findViewById(R.id.tv_notification_location);
        etMessage = findViewById(R.id.et_notification_message);
        btnSendNotification = findViewById(R.id.btn_send_notification);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_events) {
                startActivity(new Intent(this, OrganizerHomeActivity.class));
                return true;
            } else if (id == R.id.nav_create_events) {
                startActivity(new Intent(this, OrganizerCreateEventActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, OrganizerProfileActivity.class));
                return true;
            }
            return true;
        });

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        btnSendNotification.setOnClickListener(v -> sendNotificationToWaitlist());

        loadEventHeaderAndTemplate();
    }

    private void loadEventHeaderAndTemplate() {
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "No event selected for notification", Toast.LENGTH_SHORT).show();
            btnSendNotification.setEnabled(false);
            return;
        }

        db.collection("events").document(eventId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                        btnSendNotification.setEnabled(false);
                        return;
                    }

                    String loadedName = snapshot.getString("eventName");
                    String loadedLocation = snapshot.getString("location");
                    if (loadedName != null && !loadedName.trim().isEmpty()) {
                        eventName = loadedName.trim();
                    }
                    if (loadedLocation != null) {
                        eventLocation = loadedLocation.trim();
                    }

                    tvEventName.setText(eventName + " Notification");
                    tvEventLocation.setText(eventLocation.isEmpty() ? "Location unavailable" : eventLocation);

                    if (etMessage.getText() == null || etMessage.getText().toString().trim().isEmpty()) {
                        etMessage.setText("Update for " + eventName + ": ");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load event details", Toast.LENGTH_SHORT).show();
                    btnSendNotification.setEnabled(false);
                });
    }

    private void sendNotificationToWaitlist() {
        String message = etMessage.getText() == null ? "" : etMessage.getText().toString().trim();
        if (message.isEmpty()) {
            Toast.makeText(this, "Please enter a notification message", Toast.LENGTH_SHORT).show();
            return;
        }
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "No event selected", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSendNotification.setEnabled(false);

        db.collection("events").document(eventId).get()
                .addOnSuccessListener(eventSnapshot -> {
                    if (!eventSnapshot.exists()) {
                        btnSendNotification.setEnabled(true);
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<String> waitlistEntrants = FirestoreFieldUtils.getStringList(eventSnapshot, "waitlistEntrantIds");
                    if (waitlistEntrants.isEmpty()) {
                        btnSendNotification.setEnabled(true);
                        Toast.makeText(this, "No users on the waitlist to notify", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    deliverToWaitlist(waitlistEntrants, message);
                })
                .addOnFailureListener(e -> {
                    btnSendNotification.setEnabled(true);
                    Toast.makeText(this, "Failed to load waitlist recipients", Toast.LENGTH_SHORT).show();
                });
    }

    private void deliverToWaitlist(List<String> waitlistEntrants, String message) {
        List<Task<Boolean>> deliveryTasks = new ArrayList<>();
        for (String entrantId : waitlistEntrants) {
            deliveryTasks.add(deliverSingleNotification(entrantId, message));
        }

        Tasks.whenAllComplete(deliveryTasks)
                .addOnSuccessListener(unused -> {
                    int successCount = 0;
                    List<String> failedEntrants = new ArrayList<>();

                    for (int i = 0; i < deliveryTasks.size(); i++) {
                        Task<Boolean> task = deliveryTasks.get(i);
                        String entrantId = waitlistEntrants.get(i);
                        if (task.isSuccessful() && Boolean.TRUE.equals(task.getResult())) {
                            successCount++;
                        } else {
                            failedEntrants.add(entrantId);
                        }
                    }

                    btnSendNotification.setEnabled(true);
                    if (failedEntrants.isEmpty()) {
                        Toast.makeText(
                                this,
                                "Notification sent successfully to " + successCount + " waitlisted users",
                                Toast.LENGTH_LONG
                        ).show();
                    } else {
                        Toast.makeText(
                                this,
                                "Sent to " + successCount + " users. Failed for " + failedEntrants.size() + " users",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .addOnFailureListener(e -> {
                    btnSendNotification.setEnabled(true);
                    Toast.makeText(this, "Failed to send notifications", Toast.LENGTH_SHORT).show();
                });
    }

    private Task<Boolean> deliverSingleNotification(String entrantId, String message) {
        return db.collection("users").document(entrantId).get()
                .continueWithTask(userTask -> {
                    if (!userTask.isSuccessful()) {
                        return Tasks.forResult(false);
                    }

                    DocumentSnapshot userSnapshot = userTask.getResult();
                    if (userSnapshot == null || !userSnapshot.exists()) {
                        return Tasks.forResult(false);
                    }

                    String organizerId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("eventId", eventId);
                    notification.put("eventName", eventName);
                    notification.put("location", eventLocation);
                    notification.put("message", message);
                    notification.put("recipientId", entrantId);
                    notification.put("senderId", organizerId);
                    notification.put("status", "unread");
                    notification.put("createdAt", Timestamp.now());

                    return db.collection("users")
                            .document(entrantId)
                            .collection("notifications")
                            .document()
                            .set(notification, SetOptions.merge())
                            .continueWith(writeTask -> writeTask.isSuccessful());
                });
    }
}
