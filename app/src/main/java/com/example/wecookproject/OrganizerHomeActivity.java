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
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

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
    private ListenerRegistration eventsListener;

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
    
    private void loadEvents() {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Use addSnapshotListener for real-time updates
        eventsListener = db.collection("events")
                .whereEqualTo("organizerId", androidId)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Failed to load events: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    if (value != null) {
                        eventList.clear();
                        for (QueryDocumentSnapshot document : value) {
                            Event event = document.toObject(Event.class);
                            eventList.add(event);
                        }
                        eventAdapter.notifyDataSetChanged();
                    }
                });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detach the listener to prevent memory leaks
        if (eventsListener != null) {
            eventsListener.remove();
        }
    }
}
