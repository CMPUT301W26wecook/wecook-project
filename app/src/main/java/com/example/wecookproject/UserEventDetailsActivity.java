package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserEventDetailsActivity extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String entrantId;
    private String eventId;
    private UserEventRecord currentEvent;

    private TextView tvAvatar;
    private TextView tvHeaderName;
    private TextView tvHeaderLocation;
    private ImageView ivPoster;
    private TextView tvEventName;
    private TextView tvDateRange;
    private TextView tvWaitlist;
    private TextView tvStatus;
    private TextView tvDescription;
    private Button btnSecondary;
    private Button btnPrimary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_event_details);

        entrantId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        eventId = getIntent().getStringExtra("eventId");

        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "Missing event details", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvAvatar = findViewById(R.id.tv_detail_avatar);
        tvHeaderName = findViewById(R.id.tv_detail_event_name_header);
        tvHeaderLocation = findViewById(R.id.tv_detail_location_header);
        ivPoster = findViewById(R.id.iv_detail_poster);
        tvEventName = findViewById(R.id.tv_detail_event_name);
        tvDateRange = findViewById(R.id.tv_detail_date_range);
        tvWaitlist = findViewById(R.id.tv_detail_waitlist);
        tvStatus = findViewById(R.id.tv_detail_status_chip);
        tvDescription = findViewById(R.id.tv_detail_description);
        btnSecondary = findViewById(R.id.btn_detail_secondary);
        btnPrimary = findViewById(R.id.btn_detail_primary);

        findViewById(R.id.iv_detail_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_detail_show_qr).setOnClickListener(v ->
                Toast.makeText(this, "QR code preview coming soon", Toast.LENGTH_SHORT).show());

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_events);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_events) {
                Intent intent = new Intent(this, UserEventActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
                return true;
            }
            if (itemId == R.id.nav_history) {
                startActivity(new Intent(this, UserHistoryActivity.class));
                return true;
            }
            if (itemId == R.id.nav_scan) {
                Toast.makeText(this, "Scan (coming soon)", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (itemId == R.id.nav_profile) {
                startActivity(new Intent(this, UserProfileActivity.class));
                return true;
            }
            return true;
        });

        loadEvent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEvent();
    }

    private void loadEvent() {
        db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(eventId)
                .get()
                .addOnSuccessListener(historySnapshot -> {
                    String historyStatus = historySnapshot.getString("status");
                    db.collection("events")
                            .document(eventId)
                            .get()
                            .addOnSuccessListener(eventSnapshot -> bindEvent(eventSnapshot, historyStatus))
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show());
    }

    private void bindEvent(DocumentSnapshot eventSnapshot, String historyStatus) {
        if (!eventSnapshot.exists()) {
            Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentEvent = UserEventRecord.fromEventSnapshot(eventSnapshot, entrantId, historyStatus);

        tvAvatar.setText(UserEventUiUtils.getAvatarLetter(currentEvent.getEventName()));
        tvHeaderName.setText(currentEvent.getEventName());
        tvHeaderLocation.setText(currentEvent.getLocation());
        tvEventName.setText(currentEvent.getEventName());
        tvDateRange.setText(UserEventUiUtils.formatDateRange(currentEvent.getRegistrationStartDate(), currentEvent.getRegistrationEndDate()));
        tvWaitlist.setText(UserEventUiUtils.formatWaitlistSummary(currentEvent));
        tvDescription.setText(UserEventUiUtils.buildDescription(currentEvent));
        PosterLoader.loadInto(ivPoster, currentEvent.getPosterPath());

        if (currentEvent.getEffectiveStatus().isEmpty()) {
            tvStatus.setVisibility(View.GONE);
        } else {
            tvStatus.setVisibility(View.VISIBLE);
            UserEventUiUtils.applyStatusChip(tvStatus, currentEvent.getEffectiveStatus(), true);
        }

        configureActionButtons();
    }

    private void configureActionButtons() {
        String status = currentEvent.getEffectiveStatus();
        btnSecondary.setVisibility(View.GONE);
        btnPrimary.setEnabled(true);

        if (UserEventRecord.STATUS_INVITED.equals(status)) {
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText("Decline");
            btnPrimary.setText("Accept");
            btnSecondary.setOnClickListener(v -> declineInvitation());
            btnPrimary.setOnClickListener(v -> acceptInvitation());
            return;
        }

        if (UserEventRecord.STATUS_ACCEPTED.equals(status)) {
            btnPrimary.setText("Accepted");
            btnPrimary.setEnabled(false);
            return;
        }

        if (UserEventRecord.STATUS_REJECTED.equals(status)) {
            btnPrimary.setText("Rejected");
            btnPrimary.setEnabled(false);
            return;
        }

        if (UserEventRecord.STATUS_WAITLISTED.equals(status)) {
            btnPrimary.setText("Leave Waitlist");
            btnPrimary.setOnClickListener(v -> leaveWaitlist());
            return;
        }

        if (currentEvent.isWaitlistFull()) {
            btnPrimary.setText("Waitlist Full");
            btnPrimary.setEnabled(false);
            return;
        }

        btnPrimary.setText("Join the Waitlist");
        btnPrimary.setOnClickListener(v -> joinWaitlist());
    }

    private void joinWaitlist() {
        updateWaitlistMembership(true, UserEventRecord.STATUS_WAITLISTED, false, "Joined waiting list successfully");
    }

    private void leaveWaitlist() {
        updateWaitlistMembership(false, null, true, "Left waiting list");
    }

    private void acceptInvitation() {
        updateWaitlistMembership(false, UserEventRecord.STATUS_ACCEPTED, false, "Invitation accepted");
    }

    private void declineInvitation() {
        updateWaitlistMembership(false, UserEventRecord.STATUS_REJECTED, false, "Invitation declined");
    }

    private void updateWaitlistMembership(boolean addEntrant,
                                          String newStatus,
                                          boolean deleteHistory,
                                          String successMessage) {
        DocumentReference eventReference = db.collection("events").document(eventId);

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(eventReference);
            if (!snapshot.exists()) {
                throw new IllegalStateException("Event not found");
            }

            @SuppressWarnings("unchecked")
            List<String> waitlistEntrants = (List<String>) snapshot.get("waitlistEntrantIds");
            if (waitlistEntrants == null) {
                waitlistEntrants = new ArrayList<>();
            } else {
                waitlistEntrants = new ArrayList<>(waitlistEntrants);
            }

            Long maxWaitlistValue = snapshot.getLong("maxWaitlist");
            int maxWaitlist = maxWaitlistValue == null ? 0 : maxWaitlistValue.intValue();

            if (addEntrant) {
                if (waitlistEntrants.contains(entrantId)) {
                    throw new IllegalStateException("You already joined this waiting list");
                }
                if (maxWaitlist > 0 && waitlistEntrants.size() >= maxWaitlist) {
                    throw new IllegalStateException("This waiting list is full");
                }
                waitlistEntrants.add(entrantId);
            } else {
                waitlistEntrants.remove(entrantId);
            }

            transaction.update(eventReference,
                    "waitlistEntrantIds", waitlistEntrants,
                    "currentWaitlistCount", waitlistEntrants.size());
            return waitlistEntrants;
        }).addOnSuccessListener(updatedWaitlist -> {
            currentEvent.setWaitlistEntrantIds(updatedWaitlist);
            if (deleteHistory) {
                deleteHistoryDocument();
                currentEvent.setHistoryStatus("");
            } else if (newStatus != null) {
                currentEvent.setHistoryStatus(newStatus);
                upsertHistoryDocument(newStatus);
            }

            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
            loadEvent();
        }).addOnFailureListener(e -> {
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = "Unable to update event status";
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private void upsertHistoryDocument(String status) {
        Map<String, Object> historyData = new HashMap<>();
        historyData.put("eventId", currentEvent.getEventId());
        historyData.put("eventName", currentEvent.getEventName());
        historyData.put("location", currentEvent.getLocation());
        historyData.put("organizerId", currentEvent.getOrganizerId());
        historyData.put("posterPath", currentEvent.getPosterPath());
        historyData.put("registrationStartDate", currentEvent.getRegistrationStartDate());
        historyData.put("registrationEndDate", currentEvent.getRegistrationEndDate());
        historyData.put("description", currentEvent.getDescription());
        historyData.put("enrollmentCriteria", currentEvent.getEnrollmentCriteria());
        historyData.put("lotteryMethodology", currentEvent.getLotteryMethodology());
        historyData.put("status", status);
        historyData.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(currentEvent.getEventId())
                .set(historyData);
    }

    private void deleteHistoryDocument() {
        db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(currentEvent.getEventId())
                .delete();
    }
}
