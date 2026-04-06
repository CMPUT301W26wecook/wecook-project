package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wecookproject.model.Event;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity for organizers to view the list of events they manage and navigate into event-specific
 * management screens. Within the app it acts as the UI controller for the organizer home/dashboard
 * flow, binding Firestore event updates to a RecyclerView-backed list.
 *
 * Outstanding issues:
 * - Event loading depends directly on the device Android ID as the organizer identifier, which is a
 *   weak coupling point for account identity and portability.
 * - Firestore listener and list-management logic are handled directly in the Activity, which
 *   tightly couples UI and data logic instead of separating them through a repository or
 *   ViewModel-style layer.
 */
public class OrganizerHomeActivity extends AppCompatActivity {
    
    private EventAdapter eventAdapter;
    private List<Event> eventList;

    /**
     * Initializes organizer home list and bottom navigation.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_home);

        RecyclerView rvEvents = findViewById(R.id.rv_events);
        rvEvents.setLayoutManager(new LinearLayoutManager(this));
        
        eventList = new ArrayList<>();
        eventAdapter = new EventAdapter(eventList, eventId -> {
            Intent intent = new Intent(OrganizerHomeActivity.this, OrganizerEventDetailsActivity.class);
            intent.putExtra("eventId", eventId);
            startActivity(intent);
        });
        rvEvents.setAdapter(eventAdapter);

        loadEvents();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_events);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_create_events) {
                startActivity(new Intent(this, OrganizerCreateEventActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, OrganizerProfileActivity.class));
                return true;
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEvents();
    }
    
    /**
     * Subscribes to organizer events with real-time updates.
     */
    private void loadEvents() {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Event> eventsById = new LinkedHashMap<>();

        db.collection("events")
                .whereEqualTo("organizerId", androidId)
                .get(Source.SERVER)
                .addOnSuccessListener(value -> {
                    mergeEvents(eventsById, value);
                    loadCoOrganizerEvents(db, androidId, eventsById, Source.SERVER);
                })
                .addOnFailureListener(error -> {
                    Toast.makeText(this, "Failed to load organizer events from server. Showing cached events.", Toast.LENGTH_SHORT).show();
                    db.collection("events")
                            .whereEqualTo("organizerId", androidId)
                            .get(Source.CACHE)
                            .addOnSuccessListener(value -> {
                                mergeEvents(eventsById, value);
                                loadCoOrganizerEvents(db, androidId, eventsById, Source.CACHE);
                            })
                            .addOnFailureListener(cacheError ->
                                    Toast.makeText(this, "Failed to load events: " + cacheError.getMessage(), Toast.LENGTH_SHORT).show());
                });
    }

    private void loadCoOrganizerEvents(FirebaseFirestore db,
                                       String androidId,
                                       Map<String, Event> eventsById,
                                       Source source) {
        db.collection("events")
                .whereArrayContains("coOrganizerIds", androidId)
                .get(source)
                .addOnSuccessListener(value -> {
                    mergeEvents(eventsById, value);
                    bindMergedEvents(eventsById);
                })
                .addOnFailureListener(error -> {
                    bindMergedEvents(eventsById);
                    Toast.makeText(this, "Some co-organized events could not be loaded", Toast.LENGTH_SHORT).show();
                });
    }

    private void mergeEvents(Map<String, Event> eventsById, com.google.firebase.firestore.QuerySnapshot value) {
        if (value == null) {
            return;
        }
        for (QueryDocumentSnapshot document : value) {
            Event event = document.toObject(Event.class);
            if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
                event.setEventId(document.getId());
            }
            eventsById.put(document.getId(), event);
        }
    }

    private void bindMergedEvents(Map<String, Event> eventsById) {
        eventList.clear();
        eventList.addAll(eventsById.values());
        eventAdapter.notifyDataSetChanged();
    }
    
    /**
     * Removes Firestore listeners to avoid leaks.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
