package com.example.wecookproject;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class UserEventActivity extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<UserEventItem> eventList = new ArrayList<>();

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
        loadEvents();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEvents();
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
                Toast.makeText(this, "History (coming soon)", Toast.LENGTH_SHORT).show();
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

    private void loadEvents() {
        db.collection("events")
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(this, "Failed to load events", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    eventList.clear();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Event event = document.toObject(Event.class);
                        if (event == null) {
                            continue;
                        }

                        List<String> waitlistEntrants = getWaitlistEntrants(document);
                        if (!document.contains("waitlistEntrantIds")) {
                            initializeWaitingList(document.getReference());
                        }

                        eventList.add(new UserEventItem(
                                document.getId(),
                                valueOrDefault(event.getEventName(), "Unnamed Event"),
                                valueOrDefault(event.getLocation(), "Location TBD"),
                                valueOrDefault(event.getRegistrationPeriod(), "No registration period"),
                                valueOrDefault(event.getOrganizerId(), "Unknown Organizer"),
                                valueOrDefault(event.getEnrollmentCriteria(), "Open to all"),
                                valueOrDefault(event.getLotteryMethodology(), "System generates"),
                                valueOrDefault(event.getDescription(), "No event description available."),
                                event.getMaxWaitlist(),
                                waitlistEntrants
                        ));
                    }

                    eventAdapter.notifyDataSetChanged();
                    updateEmptyState();
                });
    }

    private void initializeWaitingList(DocumentReference eventReference) {
        eventReference.update(
                "waitlistEntrantIds", new ArrayList<String>(),
                "currentWaitlistCount", 0
        );
    }

    private List<String> getWaitlistEntrants(DocumentSnapshot snapshot) {
        @SuppressWarnings("unchecked")
        List<String> waitlistEntrants = (List<String>) snapshot.get("waitlistEntrantIds");
        if (waitlistEntrants == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(waitlistEntrants);
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

    private void showEventDetailsDialog(UserEventItem eventItem) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_user_event_details, null, false);

        TextView tvEventName = dialogView.findViewById(R.id.tv_dialog_event_name);
        TextView tvLocation = dialogView.findViewById(R.id.tv_dialog_location);
        TextView tvRegistration = dialogView.findViewById(R.id.tv_dialog_registration);
        TextView tvOrganizer = dialogView.findViewById(R.id.tv_dialog_organizer);
        TextView tvWaitlist = dialogView.findViewById(R.id.tv_dialog_waitlist);
        TextView tvDescription = dialogView.findViewById(R.id.tv_dialog_description);
        Button btnJoin = dialogView.findViewById(R.id.btn_join_waitlist);
        Button btnClose = dialogView.findViewById(R.id.btn_close_dialog);

        tvEventName.setText(eventItem.eventName);
        tvLocation.setText("Location: " + eventItem.location);
        tvRegistration.setText("Registration: " + eventItem.registrationPeriod);
        tvOrganizer.setText("Organizer: " + abbreviateOrganizer(eventItem.organizerId));
        tvWaitlist.setText(getWaitlistSummary(eventItem));
        tvDescription.setText(
                "Enrollment: " + eventItem.enrollmentCriteria + "\n"
                        + "Methodology: " + eventItem.lotteryMethodology + "\n"
                        + eventItem.description
        );

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        bindJoinButton(btnJoin, eventItem);

        btnJoin.setOnClickListener(v -> joinWaitingList(eventItem, btnJoin, tvWaitlist));
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void bindJoinButton(Button button, UserEventItem eventItem) {
        if (eventItem.waitlistEntrants.contains(entrantId)) {
            button.setEnabled(false);
            button.setText("Already Joined");
            return;
        }

        if (eventItem.maxWaitlist <= 0 || eventItem.waitlistEntrants.size() >= eventItem.maxWaitlist) {
            button.setEnabled(false);
            button.setText("Waitlist Full");
            return;
        }

        button.setEnabled(true);
        button.setText("Join the waiting list");
    }

    private void joinWaitingList(UserEventItem eventItem, Button btnJoin, TextView tvWaitlist) {
        DocumentReference eventReference = db.collection("events").document(eventItem.eventId);

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

            if (waitlistEntrants.contains(entrantId)) {
                throw new IllegalStateException("You have already joined this waiting list");
            }

            if (maxWaitlist <= 0 || waitlistEntrants.size() >= maxWaitlist) {
                throw new IllegalStateException("This waiting list is full");
            }

            waitlistEntrants.add(entrantId);
            transaction.update(eventReference,
                    "waitlistEntrantIds", waitlistEntrants,
                    "currentWaitlistCount", waitlistEntrants.size());
            return waitlistEntrants;
        }).addOnSuccessListener(updatedWaitlist -> {
            eventItem.waitlistEntrants = new ArrayList<>(updatedWaitlist);
            eventAdapter.notifyDataSetChanged();
            tvWaitlist.setText(getWaitlistSummary(eventItem));
            bindJoinButton(btnJoin, eventItem);
            Toast.makeText(this, "Joined waiting list successfully", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = "Unable to join the waiting list";
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private String getWaitlistSummary(UserEventItem eventItem) {
        return "Waitlist: " + eventItem.waitlistEntrants.size() + "/" + eventItem.maxWaitlist;
    }

    private String abbreviateOrganizer(String organizerId) {
        if (organizerId == null || organizerId.isEmpty()) {
            return "Unknown";
        }
        int endIndex = Math.min(organizerId.length(), 5);
        return organizerId.substring(0, endIndex) + "...";
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static class UserEventItem {
        private final String eventId;
        private final String eventName;
        private final String location;
        private final String registrationPeriod;
        private final String organizerId;
        private final String enrollmentCriteria;
        private final String lotteryMethodology;
        private final String description;
        private final int maxWaitlist;
        private List<String> waitlistEntrants;

        private UserEventItem(String eventId,
                              String eventName,
                              String location,
                              String registrationPeriod,
                              String organizerId,
                              String enrollmentCriteria,
                              String lotteryMethodology,
                              String description,
                              int maxWaitlist,
                              List<String> waitlistEntrants) {
            this.eventId = eventId;
            this.eventName = eventName;
            this.location = location;
            this.registrationPeriod = registrationPeriod;
            this.organizerId = organizerId;
            this.enrollmentCriteria = enrollmentCriteria;
            this.lotteryMethodology = lotteryMethodology;
            this.description = description;
            this.maxWaitlist = maxWaitlist;
            this.waitlistEntrants = waitlistEntrants;
        }
    }

    private static class UserEventAdapter extends RecyclerView.Adapter<UserEventAdapter.UserEventViewHolder> {
        private final List<UserEventItem> eventItems;
        private final OnEventClickListener listener;

        private UserEventAdapter(List<UserEventItem> eventItems, OnEventClickListener listener) {
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
            UserEventItem eventItem = eventItems.get(position);
            holder.tvEventName.setText(eventItem.eventName);
            holder.tvEventStatus.setText(
                    "Waitlist " + eventItem.waitlistEntrants.size() + "/" + eventItem.maxWaitlist
            );
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
        void onEventClick(UserEventItem eventItem);
    }
}