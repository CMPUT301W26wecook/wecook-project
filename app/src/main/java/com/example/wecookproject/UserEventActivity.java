package com.example.wecookproject;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wecookproject.model.Event;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserEventActivity extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<UserEventRecord> eventList = new ArrayList<>();

    private RecyclerView rvEvents;
    private TextView tvEmptyState;
    private UserEventAdapter eventAdapter;
    private String entrantId;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_event_list);

        entrantId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        rvEvents = findViewById(R.id.rv_events);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        bottomNav = findViewById(R.id.bottom_nav);

        rvEvents.setLayoutManager(new LinearLayoutManager(this));
        eventAdapter = new UserEventAdapter(eventList, this::showEventDetailsDialog);
        rvEvents.setAdapter(eventAdapter);

        setupBottomNav();
        loadEventsAndHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEventsAndHistory();
    }

    private void setupBottomNav() {
        bottomNav.setSelectedItemId(R.id.nav_events);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_events) {
                return true;
            } else if (itemId == R.id.nav_scan) {
                Toast.makeText(this, "Scan (coming soon)", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.nav_history) {
                Intent intent = new Intent(UserEventActivity.this, UserHistoryActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (itemId == R.id.nav_profile) {
                Intent intent = new Intent(UserEventActivity.this, UserProfileActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
                return true;
            }

            return false;
        });
    }


    private void loadEventsAndHistory() {
        db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .get()
                .addOnSuccessListener(historySnapshots -> {
                    Map<String, String> historyStatuses = new HashMap<>();
                    for (QueryDocumentSnapshot historyDocument : historySnapshots) {
                        String eventId = historyDocument.getString("eventId");
                        String status = historyDocument.getString("status");
                        if (eventId != null && status != null) {
                            historyStatuses.put(eventId, status);
                        }
                    }
                    loadEvents(historyStatuses);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show();
                    loadEvents(new HashMap<>());
                });
    }

    private void loadEvents(Map<String, String> historyStatuses) {
        db.collection("events")
                .get()
                .addOnSuccessListener(eventSnapshots -> {
                    eventList.clear();
                    for (QueryDocumentSnapshot document : eventSnapshots) {
                        if (!document.contains("waitlistEntrantIds")) {
                            initializeWaitingList(document.getReference());
                        }

                        UserEventRecord eventRecord = UserEventRecord.fromEventSnapshot(
                                document,
                                entrantId,
                                historyStatuses.get(document.getId())
                        );

                        if (eventRecord.isEntrantOnWaitlist() && historyStatuses.get(document.getId()) == null) {
                            upsertHistoryDocument(eventRecord, UserEventRecord.STATUS_WAITLISTED);
                        }

                        eventList.add(eventRecord);
                    }

                    eventAdapter.notifyDataSetChanged();
                    updateEmptyState();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load events", Toast.LENGTH_SHORT).show());
    }

    private void initializeWaitingList(DocumentReference eventReference) {
        eventReference.update(
                "waitlistEntrantIds", new ArrayList<String>(),
                "currentWaitlistCount", 0
        );
    }

    private void updateEmptyState() {
        if (eventList.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            rvEvents.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            rvEvents.setVisibility(View.VISIBLE);
        }
    }

    private void showEventDetailsDialog(UserEventRecord eventRecord) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_user_event_details, null, false);

        TextView tvAvatar = dialogView.findViewById(R.id.tv_dialog_avatar);
        TextView tvHeaderName = dialogView.findViewById(R.id.tv_dialog_event_name);
        TextView tvHeaderLocation = dialogView.findViewById(R.id.tv_dialog_location);
        Button btnShowQr = dialogView.findViewById(R.id.btn_dialog_show_qr);
        ImageView ivPoster = dialogView.findViewById(R.id.iv_dialog_poster);
        TextView tvDetailName = dialogView.findViewById(R.id.tv_dialog_name_detail);
        TextView tvDateRange = dialogView.findViewById(R.id.tv_dialog_date_range);
        TextView tvWaitlist = dialogView.findViewById(R.id.tv_dialog_waitlist);
        TextView tvStatusChip = dialogView.findViewById(R.id.tv_dialog_status_chip);
        TextView tvDescription = dialogView.findViewById(R.id.tv_dialog_description);
        Button btnSecondary = dialogView.findViewById(R.id.btn_dialog_secondary);
        Button btnJoinWaitlist = dialogView.findViewById(R.id.btn_join_waitlist);

        tvAvatar.setText(UserEventUiUtils.getAvatarLetter(eventRecord.getEventName()));
        tvHeaderName.setText(eventRecord.getEventName());
        tvHeaderLocation.setText(eventRecord.getLocation());
        tvDetailName.setText(eventRecord.getEventName());
        tvDateRange.setText(UserEventUiUtils.formatDateRange(eventRecord.getRegistrationStartDate(), eventRecord.getRegistrationEndDate()));
        tvWaitlist.setText(UserEventUiUtils.formatWaitlistSummary(eventRecord));
        tvDescription.setText(UserEventUiUtils.buildDescription(eventRecord));
        PosterLoader.loadInto(ivPoster, eventRecord.getPosterPath());

        String status = eventRecord.getEffectiveStatus();
        if (status.isEmpty()) {
            tvStatusChip.setVisibility(View.GONE);
        } else {
            tvStatusChip.setVisibility(View.VISIBLE);
            UserEventUiUtils.applyStatusChip(tvStatusChip, status, true);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialog.setCanceledOnTouchOutside(true);

        btnShowQr.setOnClickListener(v ->
                Toast.makeText(this, "QR code preview coming soon", Toast.LENGTH_SHORT).show());

        configureDialogActions(dialog, eventRecord, btnJoinWaitlist, btnSecondary);
        dialog.show();
    }

    private void configureDialogActions(AlertDialog dialog,
                                        UserEventRecord eventRecord,
                                        Button btnJoinWaitlist,
                                        Button btnSecondary) {
        String status = eventRecord.getEffectiveStatus();
        btnSecondary.setVisibility(View.GONE);
        btnJoinWaitlist.setEnabled(true);

        if (UserEventRecord.STATUS_INVITED.equals(status)) {
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText("Decline");
            btnJoinWaitlist.setText("Accept");
            btnSecondary.setOnClickListener(v -> declineInvitation(eventRecord, dialog));
            btnJoinWaitlist.setOnClickListener(v -> acceptInvitation(eventRecord, dialog));
            return;
        }

        if (UserEventRecord.STATUS_ACCEPTED.equals(status)) {
            btnJoinWaitlist.setText("Accepted");
            btnJoinWaitlist.setEnabled(false);
            return;
        }

        if (UserEventRecord.STATUS_REJECTED.equals(status)) {
            btnJoinWaitlist.setText("Rejected");
            btnJoinWaitlist.setEnabled(false);
            return;
        }

        if (UserEventRecord.STATUS_WAITLISTED.equals(status)) {
            btnJoinWaitlist.setText("Leave Waitlist");
            btnJoinWaitlist.setOnClickListener(v -> leaveWaitlist(eventRecord, dialog));
            return;
        }

        if (eventRecord.isWaitlistFull()) {
            btnJoinWaitlist.setText("Waitlist Full");
            btnJoinWaitlist.setEnabled(false);
            return;
        }

        btnJoinWaitlist.setText("Join the Waitlist");
        btnJoinWaitlist.setOnClickListener(v -> joinWaitingList(eventRecord, dialog));
    }

    private void joinWaitingList(UserEventRecord eventRecord, AlertDialog dialog) {
        updateWaitlistMembership(
                eventRecord,
                true,
                UserEventRecord.STATUS_WAITLISTED,
                false,
                "Joined waiting list successfully",
                dialog
        );
    }

    private void leaveWaitlist(UserEventRecord eventRecord, AlertDialog dialog) {
        updateWaitlistMembership(
                eventRecord,
                false,
                null,
                true,
                "Left waiting list",
                dialog
        );
    }

    private void acceptInvitation(UserEventRecord eventRecord, AlertDialog dialog) {
        updateWaitlistMembership(
                eventRecord,
                false,
                UserEventRecord.STATUS_ACCEPTED,
                false,
                "Invitation accepted",
                dialog
        );
    }

    private void declineInvitation(UserEventRecord eventRecord, AlertDialog dialog) {
        updateWaitlistMembership(
                eventRecord,
                false,
                UserEventRecord.STATUS_REJECTED,
                false,
                "Invitation declined",
                dialog
        );
    }

    private void updateWaitlistMembership(UserEventRecord eventRecord,
                                          boolean addEntrant,
                                          String newStatus,
                                          boolean deleteHistory,
                                          String successMessage,
                                          AlertDialog dialog) {
        DocumentReference eventReference = db.collection("events").document(eventRecord.getEventId());

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
            eventRecord.setWaitlistEntrantIds(new ArrayList<>(updatedWaitlist));
            if (deleteHistory) {
                deleteHistoryDocument(eventRecord.getEventId());
                eventRecord.setHistoryStatus("");
            } else if (newStatus != null) {
                eventRecord.setHistoryStatus(newStatus);
                upsertHistoryDocument(eventRecord, newStatus);
            }

            eventAdapter.notifyDataSetChanged();
            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            loadEventsAndHistory();
        }).addOnFailureListener(e -> {
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = "Unable to update event status";
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private void upsertHistoryDocument(UserEventRecord eventRecord, String status) {
        Map<String, Object> historyData = new HashMap<>();
        historyData.put("eventId", eventRecord.getEventId());
        historyData.put("eventName", eventRecord.getEventName());
        historyData.put("location", eventRecord.getLocation());
        historyData.put("organizerId", eventRecord.getOrganizerId());
        historyData.put("posterPath", eventRecord.getPosterPath());
        historyData.put("registrationStartDate", eventRecord.getRegistrationStartDate());
        historyData.put("registrationEndDate", eventRecord.getRegistrationEndDate());
        historyData.put("description", eventRecord.getDescription());
        historyData.put("enrollmentCriteria", eventRecord.getEnrollmentCriteria());
        historyData.put("lotteryMethodology", eventRecord.getLotteryMethodology());
        historyData.put("status", status);
        historyData.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(eventRecord.getEventId())
                .set(historyData);
    }

    private void deleteHistoryDocument(String eventId) {
        db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(eventId)
                .delete();
    }

    private static class UserEventAdapter extends RecyclerView.Adapter<UserEventAdapter.UserEventViewHolder> {
        private final List<UserEventRecord> eventItems;
        private final OnEventClickListener listener;

        private UserEventAdapter(List<UserEventRecord> eventItems, OnEventClickListener listener) {
            this.eventItems = eventItems;
            this.listener = listener;
        }

        @NonNull
        @Override
        public UserEventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
            return new UserEventViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull UserEventViewHolder holder, int position) {
            UserEventRecord eventItem = eventItems.get(position);
            holder.tvEventName.setText(eventItem.getEventName());

            if (eventItem.getEffectiveStatus().isEmpty()) {
                UserEventUiUtils.applyStatusChip(
                        holder.tvEventStatus,
                        eventItem.isWaitlistFull() ? UserEventUiUtils.STATUS_FULL : UserEventUiUtils.STATUS_OPEN,
                        false
                );
            } else {
                UserEventUiUtils.applyStatusChip(holder.tvEventStatus, eventItem.getEffectiveStatus(), false);
            }

            holder.itemView.setOnClickListener(v -> listener.onEventClick(eventItem));
        }

        @Override
        public int getItemCount() {
            return eventItems.size();
        }

        private static class UserEventViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvEventName;
            private final TextView tvEventStatus;

            private UserEventViewHolder(@NonNull View itemView) {
                super(itemView);
                tvEventName = itemView.findViewById(R.id.tv_event_name);
                tvEventStatus = itemView.findViewById(R.id.tv_event_status);
            }
        }
    }

    private interface OnEventClickListener {
        void onEventClick(UserEventRecord eventRecord);
    }
}
