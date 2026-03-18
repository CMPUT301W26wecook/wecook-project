package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.appcompat.widget.SearchView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;


import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Activity for organizers to review an event's waitlist, search entrants, and run a lottery draw
 * after registration closes. Within the app it acts as the UI controller for the organizer
 * entrant-management flow, coordinating list presentation, filtering, and Firestore updates from a
 * single screen.
 *
 * Outstanding issues:
 * - Several organizer actions are still placeholders, including invitations, invited-list viewing,
 *   redraw, and bulk deletion and will be implemented in part 4.
 * - Firestore reads and lottery-write logic are handled directly in the Activity, which puts
 *   UI and data logic together instead of separating them through a repository or ViewModel-style
 *   layer.
 */
public class OrganizerEntrantListActivity extends AppCompatActivity {
    private final List<OrganizerWaitlistItem> allEntrants = new ArrayList<>();

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
    private int lotteryCount = 0;
    private Date registrationEndDate;
    private boolean waitlistLoaded = false;


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

        searchView = findViewById(R.id.sv_search_entrant);
        entrantsRecyclerView = findViewById(R.id.rv_entrants);
        emptyStateView = findViewById(R.id.tv_empty_state);
        actionButtons = findViewById(R.id.ll_action_buttons);
        lotteryCountInput = findViewById(R.id.et_lottery_count);

        entrantsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OrganizerWaitlistAdapter();
        entrantsRecyclerView.setAdapter(adapter);

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

        findViewById(R.id.btn_send_invitation_to_selected).setOnClickListener(v -> {
            Toast.makeText(this, "Invitations are not available yet", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btn_send_notification_to_all).setOnClickListener(v ->
            startActivity(new Intent(this, OrganizerNotificationActivity.class)));
        findViewById(R.id.btn_view_invited).setOnClickListener(v -> {
            Toast.makeText(this, "Invited list is not available yet", Toast.LENGTH_SHORT).show();
        });
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

                    List<String> entrantIds = readEntrantIds(documentSnapshot);

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

                    Object rawLotteryCount = documentSnapshot.get("lotteryCount");
                    if (rawLotteryCount instanceof Number) {
                        lotteryCount = ((Number) rawLotteryCount).intValue();
                    } else {
                        lotteryCount = 0;
                    }

                    waitlistLoaded = true;
                    findViewById(R.id.btn_lottery_draw).setEnabled(true);
                    findViewById(R.id.btn_redraw_entrants).setEnabled(true);
                    loadEntrantProfiles(entrantIds);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load waitlist", Toast.LENGTH_SHORT).show();
                    showEmptyState(true);
                });
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
        if (entrantIds.isEmpty()) {
            allEntrants.clear();
            adapter.submitList(new ArrayList<>());
            showEmptyState(true);
            return;
        }

        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (String entrantId : entrantIds) {
            tasks.add(db.collection("users").document(entrantId).get());
        }

        Tasks.whenAllComplete(tasks)
                .addOnSuccessListener(unused -> {
                    List<OrganizerWaitlistItem> loadedEntrants = new ArrayList<>();
                    for (int index = 0; index < tasks.size(); index++) {
                        String entrantId = entrantIds.get(index);
                        Task<DocumentSnapshot> task = tasks.get(index);
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                            loadedEntrants.add(OrganizerWaitlistItem.fromSnapshot(entrantId, task.getResult()));
                        } else {
                            loadedEntrants.add(OrganizerWaitlistItem.fallback(entrantId));
                        }
                    }

                    allEntrants.clear();
                    allEntrants.addAll(loadedEntrants);
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

        for (OrganizerWaitlistItem entrant : allEntrants) {
            if (normalizedQuery.isEmpty() || entrant.matches(normalizedQuery)) {
                filteredEntrants.add(entrant);
            }
        }

        adapter.submitList(filteredEntrants);
        showEmptyState(filteredEntrants.isEmpty());
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

        int lotteryCount;
        try {
            lotteryCount = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid lottery number", Toast.LENGTH_SHORT).show();
            return;
        }

        if (lotteryCount <= 0) {
            Toast.makeText(this, "Lottery number must be greater than 0", Toast.LENGTH_SHORT).show();
            return;
        }

        if (waitlistEntrantIds.isEmpty()) {
            Toast.makeText(this, "No entrants in the waitlist", Toast.LENGTH_SHORT).show();
            return;
        }

        if (lotteryCount > waitlistEntrantIds.size()) {
            Toast.makeText(this, "Lottery number exceeds waitlist size", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> shuffledEntrants = new ArrayList<>(waitlistEntrantIds);
        java.util.Collections.shuffle(shuffledEntrants);

        List<String> selected = new ArrayList<>(shuffledEntrants.subList(0, lotteryCount));

        db.collection("events").document(eventId)
                .update(
                        "lotteryCount", lotteryCount,
                        "selectedEntrantIds", selected
                )
                .addOnSuccessListener(unused -> {
                    selectedEntrantIds.clear();
                    selectedEntrantIds.addAll(selected);
                    Toast.makeText(this, "Lottery draw completed", Toast.LENGTH_SHORT).show();
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
        db.collection("events").document(eventId)
                .update(
                        "replacementEntrantIds", newReplacements,
                        "selectedEntrantIds", newSelected
                )
                .addOnSuccessListener(unused -> {
                    replacementEntrantIds.clear();
                    replacementEntrantIds.addAll(newReplacements);
                    selectedEntrantIds.clear();
                    selectedEntrantIds.addAll(newSelected);
                    Toast.makeText(this, "Replacement selected", Toast.LENGTH_SHORT).show();
                    for (String entrant : drawn) {
                        sendReplacementNotification(entrant);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to save replacement", Toast.LENGTH_SHORT).show());
    }

    private void sendReplacementNotification(String entrantId) {
        // TODO: implement push notification logic or FCM integration
        // placeholder to satisfy optional acceptance criterion
        // Log.d("REPLACEMENT", "Would notify entrant " + entrantId);
    }
}
