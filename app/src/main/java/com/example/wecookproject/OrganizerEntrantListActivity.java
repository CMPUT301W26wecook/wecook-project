package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.List;

public class OrganizerEntrantListActivity extends AppCompatActivity {
    private final List<OrganizerWaitlistItem> allEntrants = new ArrayList<>();

    private FirebaseFirestore db;
    private OrganizerWaitlistAdapter adapter;
    private SearchView searchView;
    private RecyclerView entrantsRecyclerView;
    private TextView emptyStateView;
    private LinearLayout actionButtons;
    private String eventId;

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
        findViewById(R.id.btn_lottery_draw).setOnClickListener(v -> {
            Toast.makeText(this, "Lottery draw is not available yet", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btn_redraw_entrants).setOnClickListener(v -> {
            Toast.makeText(this, "Redraw is not available yet", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btn_delete_all_selected).setOnClickListener(v -> {
            Toast.makeText(this, "Bulk delete is not available yet", Toast.LENGTH_SHORT).show();
        });
    }

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

                    List<String> entrantIds = readEntrantIds(documentSnapshot);
                    loadEntrantProfiles(entrantIds);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load waitlist", Toast.LENGTH_SHORT).show();
                    showEmptyState(true);
                });
    }

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

    private void showEmptyState(boolean show) {
        emptyStateView.setVisibility(show ? View.VISIBLE : View.GONE);
        entrantsRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }
}
