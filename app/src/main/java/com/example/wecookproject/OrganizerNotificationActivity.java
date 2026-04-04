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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Activity for organizers to compose and send in-app notifications for an event. Recipients are
 * chosen via the spinner, or restricted to an explicit list when opened from the waitlist with
 * entrants selected.
 */
public class OrganizerNotificationActivity extends AppCompatActivity {
    /** When non-empty, send only to these user IDs (must still belong to the event). */
    public static final String EXTRA_EXPLICIT_RECIPIENT_IDS = "explicitRecipientIds";

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
    private ArrayList<String> explicitRecipientIds;

    private static final String RECIPIENT_WAITLIST = "All waitlist";
    private static final String RECIPIENT_SELECTED = "Selected entrants only";
    private static final String RECIPIENT_ACCEPTED = "Accepted entrants";
    private static final String RECIPIENT_CANCELLED = "Cancelled entrants";

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
        explicitRecipientIds = getIntent().getStringArrayListExtra(EXTRA_EXPLICIT_RECIPIENT_IDS);

        tvEventName = findViewById(R.id.tv_notification_event_name);
        tvEventLocation = findViewById(R.id.tv_notification_location);
        etMessage = findViewById(R.id.et_notification_message);
        btnSendNotification = findViewById(R.id.btn_send_notification);
        recipientSpinner = findViewById(R.id.spinner_notification_recipients);

        ArrayAdapter<String> recipientAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{RECIPIENT_WAITLIST, RECIPIENT_SELECTED, RECIPIENT_ACCEPTED, RECIPIENT_CANCELLED}
        );
        recipientAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recipientSpinner.setAdapter(recipientAdapter);
        if (explicitRecipientIds != null && !explicitRecipientIds.isEmpty()) {
            recipientSpinner.setEnabled(false);
        }

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

                    boolean cancelledMode = isCancelledRecipientMode();
                    String message;
                    String notificationType;
                    if (cancelledMode) {
                        String nameForTemplate = eventSnapshot.getString("eventName");
                        if (nameForTemplate == null || nameForTemplate.trim().isEmpty()) {
                            nameForTemplate = eventName;
                        } else {
                            nameForTemplate = nameForTemplate.trim();
                        }
                        message = getString(R.string.organizer_notification_cancelled_entrant_template, nameForTemplate);
                        notificationType = NotificationHelper.TYPE_CANCELLED_ENTRANT_OUTREACH;
                    } else {
                        message = etMessage.getText() == null ? "" : etMessage.getText().toString().trim();
                        if (message.isEmpty()) {
                            btnSendNotification.setEnabled(true);
                            Toast.makeText(this, "Please enter a notification message", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        notificationType = NotificationHelper.TYPE_MANUAL_WAITLIST_UPDATE;
                    }

                    List<String> recipients = resolveRecipients(eventSnapshot);
                    if (recipients.isEmpty()) {
                        btnSendNotification.setEnabled(true);
                        if (explicitRecipientIds != null && !explicitRecipientIds.isEmpty()) {
                            Toast.makeText(
                                    this,
                                    "No selected entrants are still eligible for this event",
                                    Toast.LENGTH_SHORT
                            ).show();
                        } else {
                            Toast.makeText(this, "No matching recipients for this notification", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }

                    deliverNotifications(recipients, message, notificationType);
                })
                .addOnFailureListener(e -> {
                    btnSendNotification.setEnabled(true);
                    Toast.makeText(this, "Failed to load event recipients", Toast.LENGTH_SHORT).show();
                });
    }

    private boolean isCancelledRecipientMode() {
        if (explicitRecipientIds != null && !explicitRecipientIds.isEmpty()) {
            return false;
        }
        String selectedOption = recipientSpinner.getSelectedItem() == null
                ? RECIPIENT_WAITLIST
                : recipientSpinner.getSelectedItem().toString();
        return RECIPIENT_CANCELLED.equals(selectedOption);
    }

    private void deliverNotifications(List<String> recipientIds, String message, String notificationType) {
        List<Task<Boolean>> deliveryTasks = new ArrayList<>();
        for (String entrantId : recipientIds) {
            deliveryTasks.add(deliverSingleNotification(entrantId, message, notificationType));
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

    private Task<Boolean> deliverSingleNotification(String entrantId, String message, String notificationType) {
        return NotificationHelper.sendEventNotification(
                db,
                entrantId,
                organizerId,
                eventId,
                eventName,
                eventLocation,
                message,
                notificationType,
                eventId
        );
    }

    private List<String> resolveRecipients(DocumentSnapshot eventSnapshot) {
        if (explicitRecipientIds != null && !explicitRecipientIds.isEmpty()) {
            Set<String> allowed = new LinkedHashSet<>();
            allowed.addAll(FirestoreFieldUtils.getStringList(eventSnapshot, "waitlistEntrantIds"));
            allowed.addAll(FirestoreFieldUtils.getStringList(eventSnapshot, "selectedEntrantIds"));
            allowed.addAll(FirestoreFieldUtils.getStringList(eventSnapshot, "acceptedEntrantIds"));

            List<String> ordered = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (String rawId : explicitRecipientIds) {
                if (rawId == null) {
                    continue;
                }
                String id = rawId.trim();
                if (id.isEmpty() || !allowed.contains(id) || !seen.add(id)) {
                    continue;
                }
                ordered.add(id);
            }
            return ordered;
        }
        return getRecipientsForSelection(eventSnapshot);
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
        } else if (RECIPIENT_CANCELLED.equals(selectedOption)) {
            recipients = FirestoreFieldUtils.getStringList(eventSnapshot, "declinedEntrantIds");
        } else {
            recipients = FirestoreFieldUtils.getStringList(eventSnapshot, "waitlistEntrantIds");
        }

        return new ArrayList<>(new LinkedHashSet<>(recipients));
    }
}
