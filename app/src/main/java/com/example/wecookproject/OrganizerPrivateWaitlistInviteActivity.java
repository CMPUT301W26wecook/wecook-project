package com.example.wecookproject;

import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wecookproject.model.Event;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Organizer flow for sending private-event waitlist invites.
 */
public class OrganizerPrivateWaitlistInviteActivity extends AppCompatActivity {
    private static final String DEFAULT_INVITE_MESSAGE =
            "You've been invited to join the waiting list for this private event.";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<OrganizerWaitlistItem> allEntrants = new ArrayList<>();

    private SearchView searchView;
    private RecyclerView recyclerView;
    private TextView emptyState;
    private Button sendInviteButton;
    private OrganizerPrivateInviteAdapter adapter;
    private String organizerId;
    private String eventId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_private_waitlist_invite);

        organizerId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        eventId = getIntent().getStringExtra("eventId");

        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        searchView = findViewById(R.id.sv_private_invite_search);
        recyclerView = findViewById(R.id.rv_private_invite_entrants);
        emptyState = findViewById(R.id.tv_private_invite_empty_state);
        sendInviteButton = findViewById(R.id.btn_send_private_waitlist_invites);
        findViewById(R.id.iv_private_invite_back).setOnClickListener(v -> finish());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OrganizerPrivateInviteAdapter();
        adapter.setOnSelectionChangedListener(this::updateSendButton);
        recyclerView.setAdapter(adapter);

        setupSearch();
        sendInviteButton.setOnClickListener(v -> sendPrivateWaitlistInvites());
        updateSendButton(0);
        loadEligibleEntrants();
    }

    private void setupSearch() {
        searchView.setIconifiedByDefault(false);
        searchView.setIconified(false);
        searchView.clearFocus();
        searchView.setQueryHint("Search entrant by name, phone, or email");
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

    private void loadEligibleEntrants() {
        db.collection("events").document(eventId)
                .get()
                .addOnSuccessListener(eventSnapshot -> {
                    if (!eventSnapshot.exists()) {
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    String visibilityTag = eventSnapshot.getString("visibilityTag");
                    if (!Event.VISIBILITY_PRIVATE.equalsIgnoreCase(visibilityTag == null ? "" : visibilityTag.trim())) {
                        Toast.makeText(this, "Private invites are available only for private events", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    loadEligibleEntrantProfiles(eventSnapshot);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show());
    }

    private void loadEligibleEntrantProfiles(DocumentSnapshot eventSnapshot) {
        Set<String> excludedEntrantIds = new HashSet<>();
        excludedEntrantIds.addAll(FirestoreFieldUtils.getStringList(eventSnapshot, "waitlistEntrantIds"));
        excludedEntrantIds.addAll(FirestoreFieldUtils.getStringList(eventSnapshot, "selectedEntrantIds"));
        excludedEntrantIds.addAll(FirestoreFieldUtils.getStringList(eventSnapshot, "acceptedEntrantIds"));
        excludedEntrantIds.addAll(FirestoreFieldUtils.getStringList(eventSnapshot, "declinedEntrantIds"));
        excludedEntrantIds.addAll(FirestoreFieldUtils.getStringList(
                eventSnapshot,
                EntrantWaitlistManager.FIELD_PRIVATE_WAITLIST_INVITEE_IDS
        ));

        db.collection("users")
                .get()
                .addOnSuccessListener(userSnapshots -> {
                    allEntrants.clear();
                    for (DocumentSnapshot userSnapshot : userSnapshots.getDocuments()) {
                        if (!UserDocumentUtils.hasRole(userSnapshot, UserDocumentUtils.ROLE_ENTRANT)) {
                            continue;
                        }
                        String entrantId = userSnapshot.getId();
                        if (excludedEntrantIds.contains(entrantId)) {
                            continue;
                        }
                        allEntrants.add(OrganizerWaitlistItem.fromSnapshot(entrantId, userSnapshot));
                    }
                    Collections.sort(allEntrants, Comparator.comparing(
                            item -> item.getDisplayName().toLowerCase(Locale.ROOT)
                    ));
                    applyFilter(searchView.getQuery() == null ? "" : searchView.getQuery().toString());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load entrants", Toast.LENGTH_SHORT).show());
    }

    private void applyFilter(String query) {
        List<OrganizerWaitlistItem> filteredItems = new ArrayList<>();
        for (OrganizerWaitlistItem item : allEntrants) {
            if (query == null || query.trim().isEmpty() || item.matches(query.trim())) {
                filteredItems.add(item);
            }
        }
        adapter.submitList(filteredItems);
        boolean hasItems = !filteredItems.isEmpty();
        emptyState.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        recyclerView.setVisibility(hasItems ? View.VISIBLE : View.GONE);
    }

    private void updateSendButton(int selectedCount) {
        boolean enabled = selectedCount > 0;
        sendInviteButton.setEnabled(enabled);
        sendInviteButton.setText(enabled
                ? "Send Invite (" + selectedCount + ")"
                : "Send Invite");
    }

    private void sendPrivateWaitlistInvites() {
        List<String> selectedEntrantIds = adapter.getSelectedEntrantIds();
        if (selectedEntrantIds.isEmpty()) {
            Toast.makeText(this, "Select at least one entrant", Toast.LENGTH_SHORT).show();
            return;
        }

        sendInviteButton.setEnabled(false);
        db.collection("events").document(eventId)
                .get()
                .addOnSuccessListener(eventSnapshot -> persistPrivateWaitlistInvites(eventSnapshot, selectedEntrantIds))
                .addOnFailureListener(e -> {
                    sendInviteButton.setEnabled(true);
                    Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show();
                });
    }

    private void persistPrivateWaitlistInvites(DocumentSnapshot eventSnapshot, List<String> selectedEntrantIds) {
        if (!eventSnapshot.exists()) {
            sendInviteButton.setEnabled(true);
            Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String visibilityTag = eventSnapshot.getString("visibilityTag");
        if (!Event.VISIBILITY_PRIVATE.equalsIgnoreCase(visibilityTag == null ? "" : visibilityTag.trim())) {
            sendInviteButton.setEnabled(true);
            Toast.makeText(this, "Private invites are available only for private events", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Task<?>> tasks = new ArrayList<>();
        tasks.add(db.collection("events")
                .document(eventId)
                .update(
                        EntrantWaitlistManager.FIELD_PRIVATE_WAITLIST_INVITEE_IDS,
                        FieldValue.arrayUnion(selectedEntrantIds.toArray())
                ));

        String eventName = eventSnapshot.getString("eventName");
        String location = eventSnapshot.getString("location");
        for (String entrantId : selectedEntrantIds) {
            Map<String, Object> historyData = UserEventHistoryHelper.buildHistoryData(
                    eventSnapshot,
                    UserEventRecord.STATUS_WAITLIST_INVITED
            );
            tasks.add(db.collection("users")
                    .document(entrantId)
                    .collection("eventHistory")
                    .document(eventId)
                    .set(historyData, SetOptions.merge()));
            tasks.add(NotificationHelper.sendEventNotification(
                    db,
                    entrantId,
                    organizerId,
                    eventId,
                    eventName == null || eventName.trim().isEmpty() ? "Private Event" : eventName,
                    location == null ? "" : location,
                    DEFAULT_INVITE_MESSAGE,
                    NotificationHelper.TYPE_PRIVATE_WAITLIST_INVITE,
                    eventId
            ));
        }

        Tasks.whenAll(tasks)
                .addOnCompleteListener(task -> {
                    sendInviteButton.setEnabled(true);
                    if (!task.isSuccessful()) {
                        Toast.makeText(this, "Failed to send private invites", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, "Private waitlist invites sent", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }
}
