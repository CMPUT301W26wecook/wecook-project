package com.example.wecookproject;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wecookproject.model.Event;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for the Administrator to browse and manage the list of all events in the system.
 * It also provides functionality to view event details and delete multiple events.
 */
public class AdminEventFragment extends Fragment {

    private RecyclerView recyclerView;
    private ListElementAdapter<Event> adapter;
    private List<Event> eventList;
    private List<Event> filteredEventList;
    private FirebaseFirestore db;
    private AdminViewModel viewModel;

    /**
     * Let fragment show Event List UI
     * It initializes the RecyclerView, adapter,
     * and setup event listeners for the menu actions and Event deletion.
     *
     * @param inflater           Parent view to which the fragment's UI should be attached.
     * @param container          Parent view for the fragment's UI.
     * @param savedInstanceState Saved state of the fragment.
     * @return The View for the Event List UI.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_event_list, container, false);
        db = FirebaseFirestore.getInstance();
        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);

        recyclerView = view.findViewById(R.id.rv_event_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        eventList = new ArrayList<>();
        filteredEventList = new ArrayList<>();
        adapter = new ListElementAdapter<>(filteredEventList, viewModel);
        adapter.setShowDetailOption(true);
        adapter.setShowDeleteOption(true);
        recyclerView.setAdapter(adapter);

        SearchView searchView = view.findViewById(R.id.sv_event_search);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filter(newText);
                return true;
            }
        });

        loadEventsFromFirestore();

        adapter.setOnMenuActionListener(new ListElementAdapter.OnMenuActionListener<Event>() {
            /**
             * Opens selected event detail screen.
             *
             * @param event selected event
             */
            @Override
            public void onShowDetail(Event event) {
                viewModel.selectEvent(event);
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new AdminEventDetailFragment())
                        .addToBackStack(null)
                        .commit();
            }

            /**
             * Deletes selected event document.
             *
             * @param event selected event
             * @param position adapter position
             */
            @Override
            public void onDelete(Event event, int position) {
                deleteEvent(event.getEventId(), true);
            }
        });

        view.findViewById(R.id.btn_delete_selected).setOnClickListener(v -> {
            List<Boolean> selected = adapter.getSelectedList();
            for (int i = 0; i < filteredEventList.size(); i++) {
                if (selected.get(i)) {
                    Event event = filteredEventList.get(i);
                    deleteEvent(event.getEventId(), false);
                }
            }
            Toast.makeText(getContext(), "Selected events deleted", Toast.LENGTH_SHORT).show();
        });
        return view;
    }

    /**
     * Deletes an event and its associated comments from Firestore.
     * 
     * @param eventId    The ID of the event to delete.
     * @param showToast  Whether to show a Toast message on success.
     */
    private void deleteEvent(String eventId, boolean showToast) {
        if (eventId == null) return;

        db.collection("events").document(eventId).collection("comments")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        doc.getReference().delete();
                    }

                    db.collection("events").document(eventId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                if (showToast) {
                                    Toast.makeText(getContext(), "Event and comments deleted", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (showToast) {
                                    Toast.makeText(getContext(), "Error deleting event", Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("AdminEventFragment", "Failed to delete comments for event: " + eventId, e);
                    db.collection("events").document(eventId).delete();
                });
    }

    /**
     * Filters the event list based on the user's search query.
     * It checks if the event name matches the search string.
     *
     * @param text The search query string.
     */
    private void filter(String text) {
        filteredEventList.clear();
        adapter.getSelectedList().clear();
        if (text.isEmpty()) {
            filteredEventList.addAll(eventList);
        } else {
            text = text.toLowerCase();
            for (Event event : eventList) {
                if (event.getEventName().toLowerCase().contains(text)) {
                    filteredEventList.add(event);
                }
            }
        }
        for (int i = 0; i < filteredEventList.size(); i++) {
            adapter.getSelectedList().add(false);
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * Get list of Events from Firebase.
     */
    private void loadEventsFromFirestore() {
        db.collection("events")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.w("AdminEventFragment", "Listen failed.", error);
                        return;
                    }

                    eventList.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            Event event = doc.toObject(Event.class);
                            eventList.add(event);
                        }
                    }
                    
                    SearchView searchView = getView() != null ? getView().findViewById(R.id.sv_event_search) : null;
                    if (searchView != null) {
                        filter(searchView.getQuery().toString());
                    } else {
                        filter("");
                    }
                });
    }
}
