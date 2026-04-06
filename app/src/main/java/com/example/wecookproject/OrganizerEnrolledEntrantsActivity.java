package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SearchView.SearchAutoComplete;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shows the final list of entrants confirmed for an event ({@code acceptedEntrantIds}), with live
 * Firestore updates when enrollment changes.
 */
public class OrganizerEnrolledEntrantsActivity extends AppCompatActivity {
    private final List<OrganizerEnrolledEntrantItem> allEnrolled = new ArrayList<>();

    private FirebaseFirestore db;
    private OrganizerEnrolledEntrantAdapter adapter;
    private SearchView searchView;
    private RecyclerView recyclerView;
    private TextView emptyState;
    private String eventId;
    private ListenerRegistration eventListener;
    private String lastAcceptedSignature = "";
    private String currentQuery = "";
    private int profileLoadGeneration = 0;
    private ActivityResultLauncher<String> createCsvDocumentLauncher;
    private String currentEventName = "event";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_enrolled_entrants);

        db = FirebaseFirestore.getInstance();
        eventId = getIntent().getStringExtra("eventId");

        searchView = findViewById(R.id.sv_search_entrant);
        recyclerView = findViewById(R.id.rv_entrants);
        emptyState = findViewById(R.id.tv_empty_state);
        configureSearchInput();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OrganizerEnrolledEntrantAdapter();
        recyclerView.setAdapter(adapter);

        createCsvDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/csv"),
                this::writeCsvToUri
        );

        setupBottomNav();
        setupSearch();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_export_csv).setOnClickListener(v -> exportEnrolledEntrants());

        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        attachEventListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (eventListener != null) {
            eventListener.remove();
            eventListener = null;
        }
    }

    private void attachEventListener() {
        if (eventListener != null) {
            eventListener.remove();
        }
        eventListener = db.collection("events").document(eventId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Failed to load enrolled entrants", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    String eventName = snapshot.getString("eventName");
                    if (eventName != null && !eventName.trim().isEmpty()) {
                        currentEventName = eventName.trim();
                        setTitle(currentEventName + " — Enrolled");
                    } else {
                        currentEventName = "event";
                        setTitle("Enrolled entrants");
                    }

                    List<String> acceptedIds = FirestoreFieldUtils.getStringList(snapshot, "acceptedEntrantIds");
                    List<String> signatureList = new ArrayList<>(acceptedIds);
                    Collections.sort(signatureList);
                    String signature = String.join(",", signatureList);
                    if (signature.equals(lastAcceptedSignature)) {
                        return;
                    }
                    lastAcceptedSignature = signature;

                    if (acceptedIds.isEmpty()) {
                        allEnrolled.clear();
                        adapter.submitList(new ArrayList<>());
                        applyFilter(currentQuery);
                        return;
                    }

                    profileLoadGeneration++;
                    final int generation = profileLoadGeneration;
                    loadProfilesForAccepted(new ArrayList<>(new LinkedHashSet<>(acceptedIds)), generation);
                });
    }

    private void loadProfilesForAccepted(List<String> acceptedIds, int generation) {
        List<OrganizerEnrolledEntrantItem> loaded = new ArrayList<>();
        final int[] pending = {acceptedIds.size()};

        if (acceptedIds.isEmpty()) {
            onProfilesLoaded(loaded, acceptedIds, generation);
            return;
        }

        for (String entrantId : acceptedIds) {
            db.collection("users").document(entrantId).get()
                    .addOnSuccessListener(userDoc -> {
                        loaded.add(toItem(entrantId, userDoc));
                        pending[0]--;
                        if (pending[0] == 0) {
                            onProfilesLoaded(loaded, acceptedIds, generation);
                        }
                    })
                    .addOnFailureListener(e -> {
                        loaded.add(new OrganizerEnrolledEntrantItem(entrantId, entrantId, "", ""));
                        pending[0]--;
                        if (pending[0] == 0) {
                            onProfilesLoaded(loaded, acceptedIds, generation);
                        }
                    });
        }
    }

    private void onProfilesLoaded(List<OrganizerEnrolledEntrantItem> loaded, List<String> order, int generation) {
        if (generation != profileLoadGeneration) {
            return;
        }
        List<OrganizerEnrolledEntrantItem> ordered = new ArrayList<>();
        for (String id : order) {
            for (OrganizerEnrolledEntrantItem item : loaded) {
                if (id.equals(item.getEntrantId())) {
                    ordered.add(item);
                    break;
                }
            }
        }
        allEnrolled.clear();
        allEnrolled.addAll(ordered);
        applyFilter(currentQuery);
    }

    private static OrganizerEnrolledEntrantItem toItem(String entrantId, DocumentSnapshot userDoc) {
        if (userDoc == null || !userDoc.exists()) {
            return new OrganizerEnrolledEntrantItem(entrantId, entrantId, "", "");
        }
        String firstName = safe(userDoc.getString("firstName"));
        String lastName = safe(userDoc.getString("lastName"));
        String displayName = (firstName + " " + lastName).trim();
        if (displayName.isEmpty()) {
            displayName = entrantId;
        }
        String phone = safe(userDoc.getString("phoneNumber"));
        String email = safe(userDoc.getString("email"));
        return new OrganizerEnrolledEntrantItem(entrantId, displayName, phone, email);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
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
        searchView.setQueryHint("Search by name, phone, or email");
        if (searchText != null) {
            searchText.setHint("Search by name, phone, or email");
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

    private void applyFilter(String query) {
        currentQuery = query == null ? "" : query.trim();
        String normalized = currentQuery.toLowerCase(Locale.ROOT);

        List<OrganizerEnrolledEntrantItem> filtered = new ArrayList<>();
        for (OrganizerEnrolledEntrantItem item : allEnrolled) {
            if (item.matchesQuery(normalized)) {
                filtered.add(item);
            }
        }
        adapter.submitList(filtered);
        showEmptyState(filtered.isEmpty(), allEnrolled.isEmpty());
    }

    private void showEmptyState(boolean showListEmpty, boolean noEnrolledAtAll) {
        if (!showListEmpty) {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            return;
        }
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        if (noEnrolledAtAll) {
            emptyState.setText("No enrolled entrants yet.");
        } else if (currentQuery != null && !currentQuery.trim().isEmpty()) {
            emptyState.setText("No enrolled entrants match \"" + currentQuery.trim() + "\".");
        } else {
            emptyState.setText("No enrolled entrants yet.");
        }
    }

    private void exportEnrolledEntrants() {
        if (allEnrolled.isEmpty()) {
            Toast.makeText(this, "No enrolled entrants to export", Toast.LENGTH_SHORT).show();
            return;
        }

        String safeEventName = currentEventName == null || currentEventName.trim().isEmpty()
                ? "event"
                : currentEventName.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_");

        String fileName = safeEventName + "_enrolled_entrants.csv";
        createCsvDocumentLauncher.launch(fileName);
    }

    private void writeCsvToUri(Uri uri) {
        if (uri == null) {
            Toast.makeText(this, "Export cancelled", Toast.LENGTH_SHORT).show();
            return;
        }

        String csvContent = CsvExportUtils.buildEnrolledEntrantsCsv(allEnrolled);

        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                Toast.makeText(this, "Failed to open file location", Toast.LENGTH_SHORT).show();
                return;
            }

            outputStream.write(csvContent.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();

            Toast.makeText(this, "CSV exported successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to export CSV", Toast.LENGTH_SHORT).show();
        }
    }
}
