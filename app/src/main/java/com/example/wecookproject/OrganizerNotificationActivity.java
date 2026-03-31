package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

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
    private Spinner recipientSpinner;
    private String organizerId;

    private static final String RECIPIENT_WAITLIST = "All waitlist";
    private static final String RECIPIENT_SELECTED = "Selected entrants only";
    private static final String RECIPIENT_ACCEPTED = "Accepted entrants";

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
        organizerId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        tvEventName = findViewById(R.id.tv_notification_event_name);
        tvEventLocation = findViewById(R.id.tv_notification_location);
        etMessage = findViewById(R.id.et_notification_message);
        btnSendNotification = findViewById(R.id.btn_send_notification);
        recipientSpinner = findViewById(R.id.spinner_notification_recipients);

        ArrayAdapter<String> recipientAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{RECIPIENT_WAITLIST, RECIPIENT_SELECTED, RECIPIENT_ACCEPTED}
        );
        recipientAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recipientSpinner.setAdapter(recipientAdapter);

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

                    List<String> recipients = getRecipientsForSelection(eventSnapshot);
                    if (recipients.isEmpty()) {
                        btnSendNotification.setEnabled(true);
                        Toast.makeText(this, "No matching recipients for this notification", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    deliverNotifications(recipients, message);
                })
                .addOnFailureListener(e -> {
                    btnSendNotification.setEnabled(true);
                    Toast.makeText(this, "Failed to load waitlist recipients", Toast.LENGTH_SHORT).show();
                });
    }

    private void deliverNotifications(List<String> recipientIds, String message) {
        List<Task<Boolean>> deliveryTasks = new ArrayList<>();
        for (String entrantId : recipientIds) {
            deliveryTasks.add(deliverSingleNotification(entrantId, message));
        }

        Tasks.whenAllComplete(deliveryTasks)
                .addOnSuccessListener(unused -> {
                    int successCount = 0;
                    List<String> failedEntrants = new ArrayList<>();

                    for (int i = 0; i < deliveryTasks.size(); i++) {
                        Task<Boolean> task = deliveryTasks.get(i);
                        String entrantId = recipientIds.get(i);
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
                                "Notification sent successfully to " + successCount + " users",
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
        return NotificationHelper.sendEventNotification(
                db,
                entrantId,
                organizerId,
                eventId,
                eventName,
                eventLocation,
                message,
                NotificationHelper.TYPE_MANUAL_WAITLIST_UPDATE,
                eventId
        );
    }

    private List<String> getRecipientsForSelection(DocumentSnapshot eventSnapshot) {
        List<String> recipients;
        String selectedOption = recipientSpinner.getSelectedItem() == null
                ? RECIPIENT_WAITLIST
                : recipientSpinner.getSelectedItem().toString();

        if (RECIPIENT_SELECTED.equals(selectedOption)) {
            recipients = FirestoreFieldUtils.getStringList(eventSnapshot, "selectedEntrantIds");
        } else if (RECIPIENT_ACCEPTED.equals(selectedOption)) {
            recipients = FirestoreFieldUtils.getStringList(eventSnapshot, "acceptedEntrantIds");
        } else {
            recipients = FirestoreFieldUtils.getStringList(eventSnapshot, "waitlistEntrantIds");
        }

        return new ArrayList<>(new java.util.LinkedHashSet<>(recipients));
    }
}
