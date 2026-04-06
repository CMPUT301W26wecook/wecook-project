package com.example.wecookproject;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;
import android.widget.Button;

import androidx.appcompat.widget.SearchView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;


import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity for organizers to review an event's waitlist, search entrants, and run a lottery draw
 * after registration closes. Within the app it acts as the UI controller for the organizer
 * entrant-management flow, coordinating list presentation, filtering, and Firestore updates from a
 * single screen.
 *
 * Outstanding issues:
 * - Several organizer actions are still placeholders, including invited-list viewing,
 *   redraw, and bulk deletion and will be implemented in part 4.
 * - Firestore reads and lottery-write logic are handled directly in the Activity, which puts
 *   UI and data logic together instead of separating them through a repository or ViewModel-style
 *   layer.
 */
public class OrganizerEntrantListActivity extends AppCompatActivity {
    private static final String SELECTED_NOTIFICATION_MESSAGE =
            "Congratulations on being selected to this event! Hope you have fun!!";
    private final List<OrganizerWaitlistItem> waitlistEntrants = new ArrayList<>();
    private final List<OrganizerWaitlistItem> searchableEntrants = new ArrayList<>();

    private FirebaseFirestore db;
    private OrganizerWaitlistAdapter adapter;
    private SearchView searchView;
    private RecyclerView entrantsRecyclerView;
    private TextView emptyStateView;
    private LinearLayout actionButtons;
    private String eventId;
    private android.widget.EditText lotteryCountInput;
    private final List<String> waitlistEntrantIds = new ArrayList<>();
    private final List<String> selectedEntrantIds = new ArrayList<>();
    private final List<String> replacementEntrantIds = new ArrayList<>();
    private final List<String> declinedEntrantIds = new ArrayList<>();
    private int lotteryCount = 0;
    private Date registrationEndDate;
    private boolean waitlistLoaded = false;
    private boolean isPrivateEvent = false;
    private Button inviteSelectedButton;
    private Button sendNotificationButton;
    private String organizerId;


    /**
     * Initializes organizer entrant management screen and loads waitlist data.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_entrant_list);

        db = FirebaseFirestore.getInstance();
        eventId = getIntent().getStringExtra("eventId");
        organizerId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        searchView = findViewById(R.id.sv_search_entrant);
        entrantsRecyclerView = findViewById(R.id.rv_entrants);
        emptyStateView = findViewById(R.id.tv_empty_state);
        actionButtons = findViewById(R.id.ll_action_buttons);
        lotteryCountInput = findViewById(R.id.et_lottery_count);
        inviteSelectedButton = findViewById(R.id.btn_invite_selected_waitlist);
        ImageButton backButton = findViewById(R.id.btn_back);

        entrantsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OrganizerWaitlistAdapter(this::deleteEntrant);
        adapter.setOnSelectionChangedListener(selectedCount -> onWaitlistSelectionChanged());
        entrantsRecyclerView.setAdapter(adapter);
        backButton.setOnClickListener(v -> finish());

        setupBottomNav();
        setupSearch();
        setupActionButtons();

        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadWaitlist();
    }

    /**
     * Configures organizer bottom navigation actions.
     */
    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_events);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_events) {
                Intent intent = new Intent(this, OrganizerHomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
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
    }

    /**
     * Configures waitlist search behavior.
     */
    private void setupSearch() {
        searchView.setIconifiedByDefault(false);
        searchView.setIconified(false);
        searchView.clearFocus();
        searchView.setQueryHint("Search all users by name, phone, or email");

        EditText searchText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        if (searchText != null) {
            searchText.setHint("Search all users by name, phone, or email");
            searchText.setHintTextColor(Color.parseColor("#7A7A7A"));
            searchText.setTextColor(Color.parseColor("#1F1F1F"));
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            /**
             * Handles submitted search query.
             *
             * @param query submitted query text
             * @return true when handled
             */
            @Override
            public boolean onQueryTextSubmit(String query) {
                applyFilter(query);
                return true;
            }

            /**
             * Handles live search text changes.
             *
             * @param newText updated query text
             * @return true when handled
             */
            @Override
            public boolean onQueryTextChange(String newText) {
                applyFilter(newText);
                return true;
            }
        });
    }

    /**
     * Wires action buttons on the entrant-management panel.
     */
    private void setupActionButtons() {
        actionButtons.setVisibility(View.VISIBLE);

        sendNotificationButton = findViewById(R.id.btn_send_notification_to_all);
        sendNotificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrganizerNotificationActivity.class);
            intent.putExtra("eventId", eventId);
            List<String> checked = adapter.getSelectedEntrantIds();
            if (!checked.isEmpty()) {
                intent.putStringArrayListExtra(
                        OrganizerNotificationActivity.EXTRA_EXPLICIT_RECIPIENT_IDS,
                        new ArrayList<>(checked)
                );
            }
            startActivity(intent);
        });
        findViewById(R.id.btn_view_lottery_winners).setOnClickListener(v -> {
            Intent intent = new Intent(this, OrganizerEntrantInvitedListActivity.class);
            intent.putExtra("eventId", eventId);
            startActivity(intent);
        });
        inviteSelectedButton.setOnClickListener(v -> sendInvitationToSelectedWaitlistEntrants());
        /**
         findViewById(R.id.btn_lottery_draw).setOnClickListener(v -> {
            performLotteryDraw();
        });
        findViewById(R.id.btn_redraw_entrants).setOnClickListener(v -> {
            performReplacementDraw();
        });
        **/

        View lotteryButton = findViewById(R.id.btn_lottery_draw);
        View redrawButton = findViewById(R.id.btn_redraw_entrants);

        lotteryButton.setEnabled(false);
        redrawButton.setEnabled(false);

        lotteryButton.setOnClickListener(v -> performLotteryDraw());
        redrawButton.setOnClickListener(v -> performReplacementDraw());

        findViewById(R.id.btn_delete_all_selected).setOnClickListener(v -> {
            Toast.makeText(this, "Bulk delete is not available yet", Toast.LENGTH_SHORT).show();
        });
    }



    /**
     * Deletes one entrant from the event waitlist.
     *
     * @param item entrant row data
     */
    private void deleteEntrant(OrganizerWaitlistItem item) {
        String entrantId = item.getEntrantId();
        List<String> updatedWaitlist = new ArrayList<>(waitlistEntrantIds);
        if (!updatedWaitlist.remove(entrantId)) {
            Toast.makeText(this, "Entrant is no longer in the waitlist", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> updatedSelected = new ArrayList<>(selectedEntrantIds);
        updatedSelected.remove(entrantId);
        List<String> updatedReplacement = new ArrayList<>(replacementEntrantIds);
        updatedReplacement.remove(entrantId);

        db.collection("events").document(eventId)
                .update(
                        "waitlistEntrantIds", updatedWaitlist,
                        "currentWaitlistCount", updatedWaitlist.size(),
                        "selectedEntrantIds", updatedSelected,
                        "replacementEntrantIds", updatedReplacement
                )
                .addOnSuccessListener(unused -> {
                    waitlistEntrantIds.clear();
                    waitlistEntrantIds.addAll(updatedWaitlist);
                    selectedEntrantIds.clear();
                    selectedEntrantIds.addAll(updatedSelected);
                    replacementEntrantIds.clear();
                    replacementEntrantIds.addAll(updatedReplacement);

                    for (int i = 0; i < waitlistEntrants.size(); i++) {
                        if (entrantId.equals(waitlistEntrants.get(i).getEntrantId())) {
                            waitlistEntrants.remove(i);
                            break;
                        }
                    }

                    applyFilter(searchView.getQuery() != null ? searchView.getQuery().toString() : "");
                    Toast.makeText(this, "Entrant deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to delete entrant", Toast.LENGTH_SHORT).show());
    }

    /**
     * Loads event waitlist metadata and entrant profiles.
     */
    private void loadWaitlist() {
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    String eventName = documentSnapshot.getString("eventName");
                    if (eventName != null && !eventName.trim().isEmpty()) {
                        setTitle(eventName + " Waitlist");
                    } else {
                        setTitle("Event Waitlist");
                    }

                    // Extract and store the registration end date
                    registrationEndDate = documentSnapshot.getDate("registrationEndDate");
                    isPrivateEvent = "private".equalsIgnoreCase(documentSnapshot.getString("visibilityTag"));

                    List<String> entrantIds = readEntrantIds(documentSnapshot);
                    syncWaitlistCountIfNeeded(documentSnapshot, entrantIds.size());

                    waitlistEntrantIds.clear();
                    waitlistEntrantIds.addAll(entrantIds);

                    selectedEntrantIds.clear();
                    Object rawSelected = documentSnapshot.get("selectedEntrantIds");
                    if (rawSelected instanceof List<?>) {
                        for (Object item : (List<?>) rawSelected) {
                            if (item instanceof String) {
                                selectedEntrantIds.add((String) item);
                            }
                        }
                    }

                    replacementEntrantIds.clear();
                    Object rawReplacement = documentSnapshot.get("replacementEntrantIds");
                    if (rawReplacement instanceof List<?>) {
                        for (Object item : (List<?>) rawReplacement) {
                            if (item instanceof String) {
                                replacementEntrantIds.add((String) item);
                            }
                        }
                    }

                    declinedEntrantIds.clear();
                    Object rawDeclined = documentSnapshot.get("declinedEntrantIds");
                    if (rawDeclined instanceof List<?>) {
                        for (Object item : (List<?>) rawDeclined) {
                            if (item instanceof String) {
                                declinedEntrantIds.add((String) item);
                            }
                        }
                    }

                    Object rawLotteryCount = documentSnapshot.get("lotteryCount");
                    if (rawLotteryCount instanceof Number) {
                        lotteryCount = ((Number) rawLotteryCount).intValue();
                    } else if (rawLotteryCount instanceof String) {
                        try {
                            lotteryCount = Integer.parseInt(((String) rawLotteryCount).trim());
                        } catch (NumberFormatException ignored) {
                            lotteryCount = 0;
                        }
                    } else {
                        lotteryCount = 0;
                    }

                    waitlistLoaded = true;
                    findViewById(R.id.btn_lottery_draw).setEnabled(true);
                    findViewById(R.id.btn_redraw_entrants).setEnabled(true);
                    onWaitlistSelectionChanged();
                    loadEntrantProfiles(entrantIds);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load waitlist", Toast.LENGTH_SHORT).show();
                    showEmptyState(true);
                });
    }

    private void syncWaitlistCountIfNeeded(DocumentSnapshot eventSnapshot, int actualWaitlistSize) {
        Long currentWaitlistCount = eventSnapshot.getLong("currentWaitlistCount");
        if (currentWaitlistCount == null || currentWaitlistCount.intValue() != actualWaitlistSize) {
            db.collection("events").document(eventId)
                    .update("currentWaitlistCount", actualWaitlistSize);
        }
    }

    /**
     * Reads waitlist entrant IDs from an event document.
     *
     * @param documentSnapshot source event snapshot
     * @return extracted entrant IDs
     */
    private List<String> readEntrantIds(DocumentSnapshot documentSnapshot) {
        List<String> entrantIds = new ArrayList<>();
        Object rawWaitlist = documentSnapshot.get("waitlistEntrantIds");
        if (rawWaitlist instanceof List<?>) {
            for (Object item : (List<?>) rawWaitlist) {
                if (item instanceof String) {
                    entrantIds.add((String) item);
                }
            }
        }
        return entrantIds;
    }

    /**
     * Loads entrant profile documents for given entrant IDs.
     *
     * @param entrantIds entrant identifiers
     */
    private void loadEntrantProfiles(List<String> entrantIds) {
        db.collection("users")
                .get()
                .addOnSuccessListener(userSnapshots -> {
                    searchableEntrants.clear();
                    Map<String, OrganizerWaitlistItem> byEntrantId = new HashMap<>();
                    for (DocumentSnapshot userSnapshot : userSnapshots.getDocuments()) {
                        String userId = userSnapshot.getId();
                        OrganizerWaitlistItem item = OrganizerWaitlistItem.fromSnapshot(userId, userSnapshot);
                        searchableEntrants.add(item);
                        byEntrantId.put(userId, item);
                    }

                    waitlistEntrants.clear();
                    for (String entrantId : entrantIds) {
                        OrganizerWaitlistItem item = byEntrantId.get(entrantId);
                        waitlistEntrants.add(item != null ? item : OrganizerWaitlistItem.fallback(entrantId));
                    }

                    applyFilter(searchView.getQuery() != null ? searchView.getQuery().toString() : "");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load entrant profiles", Toast.LENGTH_SHORT).show();
                    showEmptyState(true);
                });
    }

    /**
     * Applies text filtering to the waitlist dataset.
     *
     * @param query filter query
     */
    private void applyFilter(String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        List<OrganizerWaitlistItem> filteredEntrants = new ArrayList<>();
        List<OrganizerWaitlistItem> source = normalizedQuery.isEmpty()
                ? waitlistEntrants
                : searchableEntrants;

        for (OrganizerWaitlistItem entrant : source) {
            if (normalizedQuery.isEmpty() || entrant.matches(normalizedQuery)) {
                filteredEntrants.add(entrant);
            }
        }

        adapter.submitList(filteredEntrants);
        onWaitlistSelectionChanged();
        showEmptyState(filteredEntrants.isEmpty());
    }

    private void onWaitlistSelectionChanged() {
        updateInviteButtonVisibility();
        updateSendNotificationButtonLabel();
    }

    private void updateSendNotificationButtonLabel() {
        if (sendNotificationButton == null) {
            return;
        }
        int n = adapter.getSelectedEntrantIds().size();
        if (n == 0) {
            sendNotificationButton.setText(R.string.organizer_send_notification_all);
        } else {
            sendNotificationButton.setText(getResources().getQuantityString(
                    R.plurals.organizer_send_notification_selected, n, n));
        }
    }

    private void updateInviteButtonVisibility() {
        if (inviteSelectedButton == null) {
            return;
        }
        boolean hasSelection = !adapter.getSelectedEntrantIds().isEmpty();
        inviteSelectedButton.setVisibility(isPrivateEvent && hasSelection ? View.VISIBLE : View.GONE);
    }

    private void sendInvitationToSelectedWaitlistEntrants() {
        if (!isPrivateEvent) {
            Toast.makeText(this, "Invitations are available only for private events", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> selected = adapter.getSelectedEntrantIds();
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select at least one entrant", Toast.LENGTH_SHORT).show();
            onWaitlistSelectionChanged();
            return;
        }

        List<String> updatedWaitlist = new ArrayList<>(waitlistEntrantIds);
        updatedWaitlist.removeAll(selected);

        List<String> updatedSelected = new ArrayList<>(selectedEntrantIds);
        for (String entrantId : selected) {
            if (!updatedSelected.contains(entrantId)) {
                updatedSelected.add(entrantId);
            }
        }

        Map<String, Object> updates = buildWaitlistRemovalUpdate(updatedWaitlist, selected);
        updates.put("selectedEntrantIds", updatedSelected);
        updates.put("declinedEntrantIds", FieldValue.arrayRemove(selected.toArray()));

        db.collection("events").document(eventId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    waitlistEntrantIds.clear();
                    waitlistEntrantIds.addAll(updatedWaitlist);
                    selectedEntrantIds.clear();
                    selectedEntrantIds.addAll(updatedSelected);
                    removeEntrantsFromVisibleWaitlist(selected);
                    persistInvitedHistory(selected);
                    Toast.makeText(this, "Invitation sent to selected entrants", Toast.LENGTH_SHORT).show();
                    onWaitlistSelectionChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to send invitations", Toast.LENGTH_SHORT).show());
    }

    private void persistInvitedHistory(List<String> invitedEntrants) {
        db.collection("events").document(eventId).get().addOnSuccessListener(eventSnapshot -> {
            if (!eventSnapshot.exists()) {
                return;
            }
            String eventName = eventSnapshot.getString("eventName");
            String eventLocation = eventSnapshot.getString("location");
            for (String entrantId : invitedEntrants) {
                Map<String, Object> historyData = new HashMap<>();
                historyData.put("eventId", eventId);
                historyData.put("eventName", eventName);
                historyData.put("location", eventLocation);
                historyData.put("organizerId", eventSnapshot.getString("organizerId"));
                historyData.put("posterUrl", eventSnapshot.getString("posterPath"));
                historyData.put("registrationStartDate", eventSnapshot.getTimestamp("registrationStartDate"));
                historyData.put("registrationEndDate", eventSnapshot.getTimestamp("registrationEndDate"));
                historyData.put("description", eventSnapshot.getString("description"));
                historyData.put("status", "invited");
                historyData.put("updatedAt", FieldValue.serverTimestamp());

                db.collection("users").document(entrantId)
                        .collection("eventHistory").document(eventId)
                        .set(historyData, SetOptions.merge());
            }
            sendNotifications(
                    invitedEntrants,
                    eventName == null || eventName.trim().isEmpty() ? "Event Invitation" : eventName,
                    eventLocation == null ? "" : eventLocation,
                    SELECTED_NOTIFICATION_MESSAGE,
                    NotificationHelper.TYPE_PRIVATE_INVITE
            );
        });
    }

    /**
     * Toggles empty-state views.
     *
     * @param show true to show empty state
     */
    private void showEmptyState(boolean show) {
        emptyStateView.setVisibility(show ? View.VISIBLE : View.GONE);
        entrantsRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    /**
     * Runs lottery draw and persists selected entrants.
     */
    private void performLotteryDraw() {
        if (!waitlistLoaded) {
            Toast.makeText(this, "Waitlist is still loading", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if lottery is available (only after registration ends)
        if (registrationEndDate == null) {
            Toast.makeText(this, "Registration end date not found", Toast.LENGTH_SHORT).show();
            return;
        }

        Date currentDate = new Date();
        if (!currentDate.after(registrationEndDate)) {
            Toast.makeText(this, "Lottery is available only after registration ends", Toast.LENGTH_SHORT).show();
            return;
        }

        String input = lotteryCountInput.getText().toString().trim();

        if (input.isEmpty()) {
            Toast.makeText(this, "Please enter the number of attendees to draw", Toast.LENGTH_SHORT).show();
            return;
        }

        int requestedDrawCount;
        try {
            requestedDrawCount = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid lottery number", Toast.LENGTH_SHORT).show();
            return;
        }

        if (requestedDrawCount <= 0) {
            Toast.makeText(this, "Lottery number must be greater than 0", Toast.LENGTH_SHORT).show();
            return;
        }

        int configuredMaxLotteryCount = lotteryCount;
        if (configuredMaxLotteryCount <= 0) {
            // Backward-compatible behavior: if event max is not configured yet,
            // use this draw request as the initial max and persist it.
            configuredMaxLotteryCount = requestedDrawCount;
        }

        if (requestedDrawCount > configuredMaxLotteryCount) {
            Toast.makeText(this, "Lottery number cannot exceed the event maximum draw count", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> eligiblePool = new ArrayList<>(waitlistEntrantIds);
        eligiblePool.removeAll(declinedEntrantIds);

        if (eligiblePool.isEmpty()) {
            Toast.makeText(this, "No entrants in the waitlist", Toast.LENGTH_SHORT).show();
            return;
        }

        if (requestedDrawCount > eligiblePool.size()) {
            Toast.makeText(this, "Lottery number exceeds eligible waitlist size", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> shuffledEntrants = new ArrayList<>(eligiblePool);
        java.util.Collections.shuffle(shuffledEntrants);

        List<String> selected = new ArrayList<>(shuffledEntrants.subList(0, requestedDrawCount));
        List<String> notSelectedRecipients = new ArrayList<>(eligiblePool);
        notSelectedRecipients.removeAll(selected);
        List<String> updatedWaitlist = new ArrayList<>(waitlistEntrantIds);
        updatedWaitlist.removeAll(selected);
        final int persistedMaxLotteryCount = configuredMaxLotteryCount;
        Map<String, Object> updates = buildWaitlistRemovalUpdate(updatedWaitlist, selected);
        updates.put("lotteryCount", persistedMaxLotteryCount);
        updates.put("selectedEntrantIds", selected);
        updates.put("declinedEntrantIds", FieldValue.arrayRemove(selected.toArray()));

        db.collection("events").document(eventId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    waitlistEntrantIds.clear();
                    waitlistEntrantIds.addAll(updatedWaitlist);
                    removeEntrantsFromVisibleWaitlist(selected);
                    selectedEntrantIds.clear();
                    selectedEntrantIds.addAll(selected);
                    Toast.makeText(this, "Lottery draw completed", Toast.LENGTH_SHORT).show();

                    db.collection("events").document(eventId).get().addOnSuccessListener(eventSnapshot -> {
                        if (eventSnapshot.exists()) {
                            List<String> lotteryRecipients = new ArrayList<>();
                            for (String winnerId : selected) {
                                java.util.Map<String, Object> historyData = new java.util.HashMap<>();
                                historyData.put("eventId", eventId);
                                historyData.put("eventName", eventSnapshot.getString("eventName"));
                                historyData.put("location", eventSnapshot.getString("location"));
                                historyData.put("organizerId", eventSnapshot.getString("organizerId"));
                                historyData.put("posterUrl", eventSnapshot.getString("posterPath"));
                                historyData.put("registrationStartDate", eventSnapshot.getTimestamp("registrationStartDate"));
                                historyData.put("registrationEndDate", eventSnapshot.getTimestamp("registrationEndDate"));
                                historyData.put("description", eventSnapshot.getString("description"));
                                historyData.put("status", "invited");
                                historyData.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

                                db.collection("users").document(winnerId)
                                        .collection("eventHistory").document(eventId)
                                        .set(historyData, com.google.firebase.firestore.SetOptions.merge());
                                lotteryRecipients.add(winnerId);
                            }

                            sendNotifications(
                                    lotteryRecipients,
                                    eventSnapshot.getString("eventName"),
                                    eventSnapshot.getString("location"),
                                    SELECTED_NOTIFICATION_MESSAGE,
                                    NotificationHelper.TYPE_LOTTERY_SELECTED
                            );

                            if (requestedDrawCount == persistedMaxLotteryCount && !notSelectedRecipients.isEmpty()) {
                                sendNotifications(
                                        notSelectedRecipients,
                                        eventSnapshot.getString("eventName"),
                                        eventSnapshot.getString("location"),
                                        "You were not selected in this lottery round. If any selected entrant declines, another draw will be conducted from the remaining waitlist.",
                                        NotificationHelper.TYPE_LOTTERY_NOT_SELECTED
                                );
                            }
                        }
                    });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to save lottery result", Toast.LENGTH_SHORT).show());
    }

    private void performReplacementDraw() {
        if (!waitlistLoaded) {
            Toast.makeText(this, "Waitlist is still loading", Toast.LENGTH_SHORT).show();
            return;
        }

        if (registrationEndDate == null) {
            Toast.makeText(this, "Registration end date not found", Toast.LENGTH_SHORT).show();
            return;
        }
        Date currentDate = new Date();
        if (!currentDate.after(registrationEndDate)) {
            Toast.makeText(this, "Replacement draw is available only after registration ends", Toast.LENGTH_SHORT).show();
            return;
        }
        if (lotteryCount <= 0) {
            Toast.makeText(this, "Run lottery draw before selecting replacements", Toast.LENGTH_SHORT).show();
            return;
        }
        int vacancies = lotteryCount - selectedEntrantIds.size();
        if (vacancies <= 0) {
            Toast.makeText(this, "No replacement needed at this time", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> pool = new ArrayList<>(waitlistEntrantIds);
        pool.removeAll(declinedEntrantIds);
        pool.removeAll(selectedEntrantIds);
        pool.removeAll(replacementEntrantIds);
        if (pool.isEmpty()) {
            Toast.makeText(this, "No remaining applicants for replacement", Toast.LENGTH_SHORT).show();
            return;
        }
        java.util.Collections.shuffle(pool);
        int drawCount = Math.min(vacancies, pool.size());
        List<String> drawn = new ArrayList<>(pool.subList(0, drawCount));
        List<String> newReplacements = new ArrayList<>(replacementEntrantIds);
        newReplacements.addAll(drawn);
        List<String> newSelected = new ArrayList<>(selectedEntrantIds);
        newSelected.addAll(drawn);
        List<String> updatedWaitlist = new ArrayList<>(waitlistEntrantIds);
        updatedWaitlist.removeAll(drawn);
        Map<String, Object> updates = buildWaitlistRemovalUpdate(updatedWaitlist, drawn);
        updates.put("replacementEntrantIds", newReplacements);
        updates.put("selectedEntrantIds", newSelected);
        updates.put("declinedEntrantIds", FieldValue.arrayRemove(drawn.toArray()));
        db.collection("events").document(eventId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    waitlistEntrantIds.clear();
                    waitlistEntrantIds.addAll(updatedWaitlist);
                    removeEntrantsFromVisibleWaitlist(drawn);
                    replacementEntrantIds.clear();
                    replacementEntrantIds.addAll(newReplacements);
                    selectedEntrantIds.clear();
                    selectedEntrantIds.addAll(newSelected);
                    Toast.makeText(this, "Replacement selected", Toast.LENGTH_SHORT).show();
                    sendReplacementNotifications(drawn);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to save replacement", Toast.LENGTH_SHORT).show());
    }

    private Map<String, Object> buildWaitlistRemovalUpdate(List<String> updatedWaitlist, List<String> removedEntrantIds) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("waitlistEntrantIds", updatedWaitlist);
        updates.put("currentWaitlistCount", updatedWaitlist.size());
        // Keep stored entrant coordinates so organizer registration map can show where
        // registrations came from even after entrants are moved out of waitlist.
        return updates;
    }

    private void removeEntrantsFromVisibleWaitlist(List<String> removedEntrantIds) {
        if (removedEntrantIds == null || removedEntrantIds.isEmpty()) {
            return;
        }
        waitlistEntrants.removeIf(item -> removedEntrantIds.contains(item.getEntrantId()));
        applyFilter(searchView.getQuery() != null ? searchView.getQuery().toString() : "");
    }

    private void sendReplacementNotifications(List<String> entrantIds) {
        if (entrantIds == null || entrantIds.isEmpty()) {
            return;
        }

        db.collection("events").document(eventId).get()
                .addOnSuccessListener(eventSnapshot -> {
                    if (!eventSnapshot.exists()) {
                        return;
                    }

                    sendNotifications(
                            entrantIds,
                            eventSnapshot.getString("eventName"),
                            eventSnapshot.getString("location"),
                            SELECTED_NOTIFICATION_MESSAGE,
                            NotificationHelper.TYPE_REPLACEMENT_SELECTED
                    );
                });
    }

    private void sendNotifications(List<String> recipientIds,
                                   String eventName,
                                   String eventLocation,
                                   String message,
                                   String type) {
        if (recipientIds == null || recipientIds.isEmpty()) {
            return;
        }

        List<com.google.android.gms.tasks.Task<Boolean>> tasks = new ArrayList<>();
        for (String recipientId : recipientIds) {
            tasks.add(NotificationHelper.sendEventNotification(
                    db,
                    recipientId,
                    organizerId,
                    eventId,
                    eventName,
                    eventLocation,
                    message,
                    type,
                    eventId
            ));
        }

        com.google.android.gms.tasks.Tasks.whenAllComplete(tasks);
    }
}
