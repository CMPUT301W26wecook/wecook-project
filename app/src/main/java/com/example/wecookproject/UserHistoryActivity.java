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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Displays entrant event history and supports history-item removal.
 */
public class UserHistoryActivity extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<UserHistoryItem> historyItems = new ArrayList<>();

    private RecyclerView rvHistory;
    private TextView tvEmptyState;
    private UserHistoryAdapter adapter;
    private String entrantId;
    private BottomNavigationView bottomNav;

    /**
     * Initializes history list, adapter, and bottom navigation.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_history);

        entrantId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        rvHistory = findViewById(R.id.rv_history);
        tvEmptyState = findViewById(R.id.tv_history_empty_state);
        bottomNav = findViewById(R.id.bottom_nav);

        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserHistoryAdapter(historyItems, new UserHistoryAdapter.Listener() {
            /**
             * Opens event details for a selected history item.
             *
             * @param item selected history item
             */
            @Override
            public void onHistoryClicked(UserHistoryItem item) {
                if (item.isDeleted()) {
                    Toast.makeText(UserHistoryActivity.this, "This event has been deleted", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(UserHistoryActivity.this, UserEventDetailsActivity.class);
                intent.putExtra("eventId", item.getEventId());
                startActivity(intent);
            }

            /**
             * Deletes the selected history item.
             *
             * @param item selected history item
             */
            @Override
            public void onDeleteClicked(UserHistoryItem item) {
                deleteHistoryItem(item);
            }
        });
        rvHistory.setAdapter(adapter);

        setupBottomNav();
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        loadHistory();
    }

    /**
     * Configures entrant bottom-navigation behavior.
     */
    private void setupBottomNav() {
        bottomNav.setSelectedItemId(R.id.nav_history);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_history) {
                return true;
            } else if (itemId == R.id.nav_events) {
                Intent intent = new Intent(UserHistoryActivity.this, UserEventActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
                return true;
            } else if (itemId == R.id.nav_scan) {
                Intent intent = new Intent(UserHistoryActivity.this, UserScanActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
                return true;
            } else if (itemId == R.id.nav_profile) {
                Intent intent = new Intent(UserHistoryActivity.this, UserProfileActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
                return true;
            }

            return false;
        });
    }

    /**
     * Reloads history whenever the activity returns to foreground.
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_history);
        }
    }

    /**
     * Loads history entries from Firestore ordered by update time.
     */
    private void loadHistory() {
        db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    historyItems.clear();
                    if (querySnapshot.isEmpty()) {
                        adapter.notifyDataSetChanged();
                        updateEmptyState();
                        return;
                    }

                    List<UserHistoryItem> resolvedItems = new ArrayList<>(
                            Collections.nCopies(querySnapshot.size(), null));
                    final int[] remaining = {querySnapshot.size()};
                    int index = 0;
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        final int resolvedIndex = index++;
                        enrichHistoryItem(UserHistoryItem.fromSnapshot(document), resolvedItem -> {
                            resolvedItems.set(resolvedIndex, resolvedItem);
                            remaining[0]--;
                            if (remaining[0] == 0) {
                                historyItems.clear();
                                for (UserHistoryItem resolved : resolvedItems) {
                                    if (resolved != null) {
                                        historyItems.add(resolved);
                                    }
                                }
                                adapter.notifyDataSetChanged();
                                updateEmptyState();
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show());
    }

    private void enrichHistoryItem(UserHistoryItem baseItem, HistoryItemCallback callback) {
        db.collection("users")
                .document(baseItem.getOrganizerId())
                .get()
                .addOnSuccessListener(userSnapshot -> {
                    String organizerName = UserDocumentUtils.buildDisplayName(userSnapshot, "Organizer");
                    db.collection("events")
                            .document(baseItem.getEventId())
                            .get()
                            .addOnSuccessListener(eventSnapshot -> {
                                boolean eventDeleted = !eventSnapshot.exists();
                                callback.onResolved(baseItem.withResolvedState(
                                        organizerName,
                                        eventSnapshot.getTimestamp("eventTime"),
                                        eventDeleted
                                ));
                            })
                            .addOnFailureListener(e ->
                                    callback.onResolved(baseItem.withResolvedState(
                                            organizerName,
                                            baseItem.getEventTime(),
                                            false
                                    )));
                })
                .addOnFailureListener(e -> db.collection("events")
                        .document(baseItem.getEventId())
                        .get()
                        .addOnSuccessListener(eventSnapshot ->
                                callback.onResolved(baseItem.withResolvedState(
                                        "Organizer",
                                        eventSnapshot.getTimestamp("eventTime"),
                                        !eventSnapshot.exists()
                                )))
                        .addOnFailureListener(error ->
                                callback.onResolved(baseItem.withResolvedState(
                                        "Organizer",
                                        baseItem.getEventTime(),
                                        false
                                ))));
    }

    /**
     * Deletes one history item and removes current entrant from event waitlist when present.
     *
     * @param item history item to remove
     */
    private void deleteHistoryItem(UserHistoryItem item) {
        DocumentReference eventRef = db.collection("events").document(item.getEventId());
        DocumentReference historyRef = db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(item.getEventId());

        db.runTransaction(transaction -> {
            DocumentSnapshot eventSnapshot = transaction.get(eventRef);
            if (eventSnapshot.exists()) {
                List<String> waitlistEntrants = FirestoreFieldUtils.getStringList(eventSnapshot, "waitlistEntrantIds");
                if (waitlistEntrants != null && waitlistEntrants.contains(entrantId)) {
                    waitlistEntrants = new ArrayList<>(waitlistEntrants);
                    waitlistEntrants.remove(entrantId);
                    transaction.update(eventRef,
                            "waitlistEntrantIds", waitlistEntrants,
                            "currentWaitlistCount", waitlistEntrants.size());
                }
                transaction.update(eventRef,
                        EntrantWaitlistManager.FIELD_PRIVATE_WAITLIST_INVITEE_IDS,
                        com.google.firebase.firestore.FieldValue.arrayRemove(entrantId));
            }
            transaction.delete(historyRef);
            return null;
        }).addOnSuccessListener(unused -> {
            Toast.makeText(this, "History deleted", Toast.LENGTH_SHORT).show();
            loadHistory();
        }).addOnFailureListener(e -> Toast.makeText(this, "Failed to delete history", Toast.LENGTH_SHORT).show());
    }

    /**
     * Toggles empty-state visibility based on current adapter data.
     */
    private void updateEmptyState() {
        if (historyItems.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            rvHistory.setVisibility(View.VISIBLE);
        }
    }

    private interface HistoryItemCallback {
        void onResolved(UserHistoryItem item);
    }
}
