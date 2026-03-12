package com.example.wecookproject;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wecookproject.model.Event;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AdminEventActivity extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<Event> eventList = new ArrayList<>();

    private SelectableEventAdapter eventAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.acticity_admin_event_home);

        RecyclerView eventRecyclerView = findViewById(R.id.eventRecyclerView);
        MaterialButton deleteSelectedEventsButton = findViewById(R.id.deleteSelectedEventsButton);

        eventRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        eventAdapter = new SelectableEventAdapter(eventList);
        eventRecyclerView.setAdapter(eventAdapter);

        deleteSelectedEventsButton.setOnClickListener(v -> deleteLoadedEvents());

        loadEvents();
    }

    private void loadEvents() {
        db.collection("events")
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(this, "Failed to load events", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    eventList.clear();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Event event = document.toObject(Event.class);
                        if (event.getEventId() == null || event.getEventId().isEmpty()) {
                            event.setEventId(document.getId());
                        }
                        eventList.add(event);
                    }
                    eventAdapter.notifyDataSetChanged();
                });
    }

    private void deleteLoadedEvents() {
        List<String> selectedEventIds = eventAdapter.getSelectedEventIds();
        if (selectedEventIds.isEmpty()) {
            Toast.makeText(this, "No events selected", Toast.LENGTH_SHORT).show();
            return;
        }

        WriteBatch batch = db.batch();
        for (String eventId : selectedEventIds) {
            batch.delete(db.collection("events").document(eventId));
        }

        batch.commit()
                .addOnSuccessListener(unused -> {
                    Iterator<Event> iterator = eventList.iterator();
                    while (iterator.hasNext()) {
                        Event event = iterator.next();
                        if (selectedEventIds.contains(event.getEventId())) {
                            iterator.remove();
                        }
                    }
                    eventAdapter.clearSelection();
                    eventAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "Selected events deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to delete selected events", Toast.LENGTH_SHORT).show());
    }
}
