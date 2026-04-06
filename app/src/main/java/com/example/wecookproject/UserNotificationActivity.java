package com.example.wecookproject;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
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
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private UserNotificationItem pendingJoinNotification;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_notification);

        entrantId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                            || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (granted && pendingJoinNotification != null) {
                        fetchLocationAndJoinWaitlist(pendingJoinNotification);
                    } else if (!granted) {
                        Toast.makeText(this, "Location permission is required to join the waitlist", Toast.LENGTH_SHORT).show();
                    }
                }
        );
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
                if (item != null && item.isPrivateWaitlistInvite()) {
                    joinWaitlistFromNotification(item);
                } else if (item != null && item.requiresConfirmation()) {
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
        if (item != null && item.isPrivateWaitlistInvite() && item.isDeclined()) {
            Toast.makeText(this, "This private waitlist invite was declined", Toast.LENGTH_SHORT).show();
            return;
        }
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

    private void joinWaitlistFromNotification(UserNotificationItem item) {
        if (item == null || item.isConfirmed() || item.isDeclined()) {
            return;
        }
        pendingJoinNotification = item;
        attemptNotificationJoin(item, null, false);
    }

    private void attemptNotificationJoin(UserNotificationItem item,
                                         Location entrantLocation,
                                         boolean locationAttempted) {
        String eventId = item.getActionTarget().isEmpty() ? item.getEventId() : item.getActionTarget();
        EntrantWaitlistManager.joinWaitlist(db, entrantId, eventId, entrantLocation)
                .addOnSuccessListener(result ->
                        NotificationHelper.markMatchingNotificationsAsConfirmed(
                                        db,
                                        entrantId,
                                        item.getEventId(),
                                        NotificationHelper.TYPE_PRIVATE_WAITLIST_INVITE
                                )
                                .addOnCompleteListener(task -> {
                                    pendingJoinNotification = null;
                                    loadNotifications();
                                    Toast.makeText(this, "Joined waiting list successfully", Toast.LENGTH_SHORT).show();
                                }))
                .addOnFailureListener(e -> {
                    String message = e.getMessage();
                    if (!locationAttempted
                            && "Location is required to join this waitlist".equals(message)) {
                        requestLocationAndJoinWaitlist(item);
                        return;
                    }
                    pendingJoinNotification = null;
                    Toast.makeText(
                            this,
                            message == null || message.trim().isEmpty()
                                    ? "Unable to update event status"
                                    : message,
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    private void requestLocationAndJoinWaitlist(UserNotificationItem item) {
        pendingJoinNotification = item;
        if (hasLocationPermission()) {
            fetchLocationAndJoinWaitlist(item);
            return;
        }
        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void fetchLocationAndJoinWaitlist(UserNotificationItem item) {
        if (!hasLocationPermission()) {
            Toast.makeText(this, "Location permission is required to join the waitlist", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            attemptNotificationJoin(item, TestingLocationPool.createRandomCountryLocation(this), true);
                            return;
                        }

                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this, "Location permission is required to join the waitlist", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        CancellationTokenSource tokenSource = new CancellationTokenSource();
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.getToken())
                                .addOnSuccessListener(currentLocation -> {
                                    if (currentLocation == null) {
                                        Toast.makeText(this, "Unable to read location. Please enable location and try again.", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    attemptNotificationJoin(item, TestingLocationPool.createRandomCountryLocation(this), true);
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Unable to read location. Please try again.", Toast.LENGTH_SHORT).show());
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Unable to read location. Please try again.", Toast.LENGTH_SHORT).show());
        } catch (SecurityException e) {
            Toast.makeText(this, "Location permission is required to join the waitlist", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
