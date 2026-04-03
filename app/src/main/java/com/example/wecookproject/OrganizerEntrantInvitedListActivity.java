package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SearchView.SearchAutoComplete;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class OrganizerEntrantInvitedListActivity extends AppCompatActivity {
    private final List<OrganizerInvitedEntrantItem> allEntrants = new ArrayList<>();
    private FirebaseFirestore db;
    private OrganizerInvitedEntrantAdapter adapter;
    private SearchView searchView;
    private RecyclerView recyclerView;
    private TextView emptyState;
    private String eventId;
    private String filterMode = "all";
    private String currentQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_entrant_invited_list);

        db = FirebaseFirestore.getInstance();
        eventId = getIntent().getStringExtra("eventId");

        searchView = findViewById(R.id.sv_search_entrant);
        recyclerView = findViewById(R.id.rv_entrants);
        emptyState = findViewById(R.id.tv_empty_state);
        configureSearchInput();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OrganizerInvitedEntrantAdapter(item ->
                Toast.makeText(this, "Delete action is not available yet", Toast.LENGTH_SHORT).show());
        recyclerView.setAdapter(adapter);

        setupBottomNav();
        setupSearch();
        setupButtons();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadInvitedEntrants();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (eventId != null && !eventId.trim().isEmpty()) {
            loadInvitedEntrants();
        }
    }

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

    private void setupSearch() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                applyFilter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                applyFilter(newText);
                return true;
            }
        });
    }

    private void configureSearchInput() {
        searchView.setIconifiedByDefault(false);
        searchView.setIconified(false);
        searchView.clearFocus();

        SearchAutoComplete searchText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        searchView.setQueryHint("Search by entrant name, phone, or email");
        if (searchText != null) {
            searchText.setHint("Search by entrant name, phone, or email");
            searchText.setFocusable(true);
            searchText.setFocusableInTouchMode(true);
            searchText.setCursorVisible(true);
        }

        searchView.setOnClickListener(v -> {
            searchView.setIconified(false);
            if (searchText != null) {
                searchText.requestFocus();
            }
        });
    }

    private void setupButtons() {
        findViewById(R.id.view_accepted_button).setOnClickListener(v -> {
            filterMode = "accepted";
            applyFilter(searchView.getQuery() != null ? searchView.getQuery().toString() : "");
        });
        findViewById(R.id.view_declined_button).setOnClickListener(v -> {
            filterMode = "cancelled";
            applyFilter(searchView.getQuery() != null ? searchView.getQuery().toString() : "");
        });
        findViewById(R.id.btn_revoke_selected).setOnClickListener(v ->
                Toast.makeText(this, "Revoke selected entrants is not available yet", Toast.LENGTH_SHORT).show());
    }

    private void loadInvitedEntrants() {
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(eventDoc -> {
                    if (!eventDoc.exists()) {
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                        showEmptyState(true);
                        return;
                    }

                    List<String> invitedIds = readStringList(eventDoc, "selectedEntrantIds");
                    List<String> acceptedIds = readStringList(eventDoc, "acceptedEntrantIds");
                    List<String> cancelledIds = readStringList(eventDoc, "declinedEntrantIds");
                    List<String> confirmedIds = readStringList(eventDoc, "confirmedEntrantIds");
                    List<String> waitlistIds = readStringList(eventDoc, "waitlistEntrantIds");

                    Set<String> allInvitedIds = new LinkedHashSet<>();
                    allInvitedIds.addAll(invitedIds);
                    allInvitedIds.addAll(acceptedIds);
                    for (String cancelledId : cancelledIds) {
                        // If a cancelled entrant is back on the waitlist, they are not a current
                        // lottery winner and should not appear in the winners list.
                        if (!waitlistIds.contains(cancelledId)) {
                            allInvitedIds.add(cancelledId);
                        }
                    }

                    if (allInvitedIds.isEmpty()) {
                        allEntrants.clear();
                        adapter.submitList(new ArrayList<>());
                        showEmptyState(true);
                        return;
                    }

                    List<OrganizerInvitedEntrantItem> loaded = new ArrayList<>();
                    final int[] pending = {allInvitedIds.size()};

                    for (String entrantId : allInvitedIds) {
                        db.collection("users").document(entrantId).get()
                                .addOnSuccessListener(userDoc ->
                                        db.collection("users")
                                                .document(entrantId)
                                                .collection("eventHistory")
                                                .document(eventId)
                                                .get()
                                                .addOnSuccessListener(historyDoc -> {
                                                    loaded.add(toInvitedItem(entrantId, userDoc, historyDoc, invitedIds, acceptedIds, cancelledIds, confirmedIds));
                                                    pending[0]--;
                                                    if (pending[0] == 0) {
                                                        onInvitedLoaded(loaded);
                                                    }
                                                })
                                                .addOnFailureListener(e -> {
                                                    loaded.add(toInvitedItem(entrantId, userDoc, null, invitedIds, acceptedIds, cancelledIds, confirmedIds));
                                                    pending[0]--;
                                                    if (pending[0] == 0) {
                                                        onInvitedLoaded(loaded);
                                                    }
                                                })
                                )
                                .addOnFailureListener(e -> {
                                    loaded.add(new OrganizerInvitedEntrantItem(entrantId, entrantId, OrganizerInvitedEntrantAdapter.STATUS_PENDING));
                                    pending[0]--;
                                    if (pending[0] == 0) {
                                        onInvitedLoaded(loaded);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load invited entrants", Toast.LENGTH_SHORT).show();
                    showEmptyState(true);
                });
    }

    private OrganizerInvitedEntrantItem toInvitedItem(String entrantId, DocumentSnapshot userDoc,
                                                      DocumentSnapshot historyDoc,
                                                      List<String> invitedIds,
                                                      List<String> acceptedIds,
                                                      List<String> cancelledIds,
                                                      List<String> confirmedIds) {
        String firstName = safe(userDoc.getString("firstName"));
        String lastName = safe(userDoc.getString("lastName"));
        String phoneNumber = safe(userDoc.getString("phoneNumber"));
        String email = safe(userDoc.getString("email"));
        String displayName = (firstName + " " + lastName).trim();
        if (displayName.isEmpty()) {
            displayName = entrantId;
        }

        String historyStatus = historyDoc == null ? "" : safe(historyDoc.getString("status")).toLowerCase(Locale.ROOT);
        String status = OrganizerInvitedEntrantAdapter.STATUS_PENDING;
        if ("accepted".equals(historyStatus) || acceptedIds.contains(entrantId)) {
            status = OrganizerInvitedEntrantAdapter.STATUS_ACCEPTED;
        } else if (confirmedIds.contains(entrantId)) {
            status = OrganizerInvitedEntrantAdapter.STATUS_CONFIRMED;
        } else if ("invited".equals(historyStatus) || invitedIds.contains(entrantId)) {
            status = OrganizerInvitedEntrantAdapter.STATUS_PENDING;
        } else if ("rejected".equals(historyStatus) || cancelledIds.contains(entrantId)) {
            status = OrganizerInvitedEntrantAdapter.STATUS_CANCELLED;
        }

        return new OrganizerInvitedEntrantItem(entrantId, displayName, phoneNumber, email, status);
    }

    private void onInvitedLoaded(List<OrganizerInvitedEntrantItem> loaded) {
        allEntrants.clear();
        allEntrants.addAll(loaded);
        applyFilter(searchView.getQuery() != null ? searchView.getQuery().toString() : "");
    }

    private void applyFilter(String query) {
        currentQuery = query == null ? "" : query.trim();
        String normalized = currentQuery.toLowerCase(Locale.ROOT);
        List<OrganizerInvitedEntrantItem> filtered = new ArrayList<>();
        for (OrganizerInvitedEntrantItem item : allEntrants) {
            boolean statusMatch = "all".equals(filterMode)
                    || ("accepted".equals(filterMode)
                        && (OrganizerInvitedEntrantAdapter.STATUS_ACCEPTED.equals(item.getStatus())
                        || OrganizerInvitedEntrantAdapter.STATUS_CONFIRMED.equals(item.getStatus())))
                    || ("cancelled".equals(filterMode) && OrganizerInvitedEntrantAdapter.STATUS_CANCELLED.equals(item.getStatus()));
            boolean textMatch = normalized.isEmpty()
                    || item.getDisplayName().toLowerCase(Locale.ROOT).contains(normalized)
                    || item.getPhoneNumber().toLowerCase(Locale.ROOT).contains(normalized)
                    || item.getEmail().toLowerCase(Locale.ROOT).contains(normalized);

            if (statusMatch && textMatch) {
                filtered.add(item);
            }
        }
        adapter.submitList(filtered);
        showEmptyState(filtered.isEmpty(), currentQuery);
    }

    private void showEmptyState(boolean show) {
        showEmptyState(show, "");
    }

    private void showEmptyState(boolean show, String query) {
        if (show) {
            if (allEntrants.isEmpty()) {
                emptyState.setText("No invited entrants found.");
            } else if (query != null && !query.trim().isEmpty()) {
                emptyState.setText("No invited entrants match \"" + query.trim() + "\".");
            } else if ("accepted".equals(filterMode)) {
                emptyState.setText("No accepted entrants found.");
            } else if ("cancelled".equals(filterMode)) {
                emptyState.setText("No cancelled entrants found.");
            } else {
                emptyState.setText("No invited entrants found.");
            }
        }
        emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private static List<String> readStringList(DocumentSnapshot snapshot, String field) {
        List<String> values = new ArrayList<>();
        Object raw = snapshot.get(field);
        if (raw instanceof List<?>) {
            for (Object item : (List<?>) raw) {
                if (item instanceof String) {
                    values.add((String) item);
                }
            }
        }
        return values;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
