package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entrant notifications inbox.
 */
public class UserNotificationActivity extends AppCompatActivity {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<UserNotificationItem> items = new ArrayList<>();

    private RecyclerView recyclerView;
    private TextView emptyState;
    private UserNotificationAdapter adapter;
    private BottomNavigationView bottomNav;
    private String entrantId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_notification);

        entrantId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        recyclerView = findViewById(R.id.rv_notifications);
        emptyState = findViewById(R.id.tv_notifications_empty_state);
        bottomNav = findViewById(R.id.bottom_nav);
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserNotificationAdapter(items, new UserNotificationAdapter.NotificationActionListener() {
            @Override
            public void onNotificationOpened(UserNotificationItem item) {
                openNotification(item);
            }

            @Override
            public void onMarkReadClicked(UserNotificationItem item) {
                if (item != null && item.requiresConfirmation()) {
                    confirmNotification(item, false);
                } else {
                    markNotificationRead(item, false);
                }
            }
        });
        recyclerView.setAdapter(adapter);

        setupBottomNav();
        loadNotifications();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotifications();
    }

    private void setupBottomNav() {
        bottomNav.setSelectedItemId(R.id.nav_profile);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_events) {
                Intent intent = new Intent(UserNotificationActivity.this, UserEventActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
                return true;
            } else if (itemId == R.id.nav_scan) {
                Toast.makeText(this, "Scan (coming soon)", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.nav_history) {
                Intent intent = new Intent(UserNotificationActivity.this, UserHistoryActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
                return true;
            } else if (itemId == R.id.nav_profile) {
                Intent intent = new Intent(UserNotificationActivity.this, UserProfileActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
                return true;
            }
            return false;
        });
    }

    private void loadNotifications() {
        db.collection("users")
                .document(entrantId)
                .collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    items.clear();
                    for (QueryDocumentSnapshot snapshot : querySnapshot) {
                        items.add(UserNotificationItem.fromSnapshot(snapshot));
                    }
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show());
    }

    private void updateEmptyState() {
        if (items.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void openNotification(UserNotificationItem item) {
        markNotificationRead(item, true);
    }

    private void markNotificationRead(UserNotificationItem item, boolean openAfterRead) {
        if (item == null) {
            return;
        }

        if (!item.isUnread()) {
            if (openAfterRead) {
                navigateToNotificationTarget(item);
            }
            return;
        }

        NotificationHelper.markAsRead(db, entrantId, item.getId())
                .addOnSuccessListener(unused -> {
                    loadNotifications();
                    if (openAfterRead) {
                        navigateToNotificationTarget(item);
                    }
                })
                .addOnFailureListener(e -> {
                    if (openAfterRead) {
                        navigateToNotificationTarget(item);
                    } else {
                        Toast.makeText(this, "Failed to mark notification as read", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void confirmNotification(UserNotificationItem item, boolean openAfterConfirm) {
        if (item == null) {
            return;
        }

        if (NotificationHelper.TYPE_CO_ORGANIZER_INVITE.equals(item.getType())) {
            acceptCoOrganizerInvitation(item, openAfterConfirm);
            return;
        }

        if (item.isConfirmed()) {
            if (openAfterConfirm) {
                navigateToNotificationTarget(item);
            }
            return;
        }

        NotificationHelper.markAsConfirmed(db, entrantId, item.getId())
                .addOnSuccessListener(unused -> {
                    loadNotifications();
                    if (openAfterConfirm) {
                        navigateToNotificationTarget(item);
                    }
                })
                .addOnFailureListener(e -> {
                    if (openAfterConfirm) {
                        navigateToNotificationTarget(item);
                    } else {
                        Toast.makeText(this, "Failed to confirm notification", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void acceptCoOrganizerInvitation(UserNotificationItem item, boolean openAfterConfirm) {
        String inviteEventId = item.getEventId();
        if (inviteEventId == null || inviteEventId.trim().isEmpty()) {
            Toast.makeText(this, "Event details unavailable for this invitation", Toast.LENGTH_SHORT).show();
            return;
        }

        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot eventSnapshot = transaction.get(
                    db.collection("events").document(inviteEventId)
            );
            if (!eventSnapshot.exists()) {
                throw new IllegalStateException("Event no longer exists");
            }

            List<String> pendingIds = FirestoreFieldUtils.getStringList(eventSnapshot, "pendingCoOrganizerIds");
            List<String> coOrganizerIds = FirestoreFieldUtils.getStringList(eventSnapshot, "coOrganizerIds");
            pendingIds.remove(entrantId);
            if (!coOrganizerIds.contains(entrantId)) {
                coOrganizerIds.add(entrantId);
            }

            transaction.update(
                    db.collection("events").document(inviteEventId),
                    "pendingCoOrganizerIds", pendingIds,
                    "coOrganizerIds", coOrganizerIds
            );
            return null;
        }).addOnSuccessListener(unused -> {
            Map<String, Object> updates = new HashMap<>();
            Map<String, Object> roles = new HashMap<>();
            roles.put(UserDocumentUtils.ROLE_ORGANIZER, true);
            updates.put("roles", roles);
            db.collection("users")
                    .document(entrantId)
                    .set(updates, SetOptions.merge())
                    .addOnSuccessListener(roleResult ->
                            NotificationHelper.markAsConfirmed(db, entrantId, item.getId())
                                    .addOnSuccessListener(notificationResult -> {
                                        loadNotifications();
                                        Toast.makeText(
                                                this,
                                                "Co-organizer access granted. Sign in as Organizer to manage this event.",
                                                Toast.LENGTH_LONG
                                        ).show();
                                        if (openAfterConfirm) {
                                            Intent intent = new Intent(this, LoginActivity.class);
                                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                            startActivity(intent);
                                        }
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(this, "Failed to confirm notification", Toast.LENGTH_SHORT).show()))
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to grant organizer access", Toast.LENGTH_SHORT).show());
        }).addOnFailureListener(e -> {
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = "Failed to accept co-organizer invitation";
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private void navigateToNotificationTarget(UserNotificationItem item) {
        if (NotificationHelper.TYPE_CO_ORGANIZER_INVITE.equals(item.getType())) {
            if (!item.isConfirmed()) {
                Toast.makeText(this, "Confirm this invitation, then sign in as Organizer to manage the event.", Toast.LENGTH_LONG).show();
                return;
            }

            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            return;
        }

        String eventId = item.getActionTarget().isEmpty() ? item.getEventId() : item.getActionTarget();
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "Event details unavailable for this notification", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, UserEventDetailsActivity.class);
        intent.putExtra("eventId", eventId);
        startActivity(intent);
    }
}
