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

import com.example.wecookproject.model.User;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fragment for the Administrator to browse and manage the list of all Organizers in the system.
 * It also provides functionality to delete multiple Organizer accounts.
 */
public class AdminOrganizerFragment extends Fragment {

    private RecyclerView recyclerView;
    private ListElementAdapter<User> adapter;
    private List<User> organizerList;
    private List<User> filteredOrganizerList;
    private FirebaseFirestore db;
    private AdminViewModel viewModel;

    /**
     * Let fragment show Organizer List UI
     * It initializes the RecyclerView, adapter,
     * and setup event listeners for the menu actions and Organizer deletion.
     *
     * @param inflater           Parent view to which the fragment's UI should be attached.
     * @param container          Parent view for the fragment's UI.
     * @param savedInstanceState Saved state of the fragment.
     * @return The View for the Organizer List UI.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_organizer_list, container, false);

        db = FirebaseFirestore.getInstance();
        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);
        
        recyclerView = view.findViewById(R.id.rv_organizer_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        organizerList = new ArrayList<>();
        filteredOrganizerList = new ArrayList<>();
        adapter = new ListElementAdapter<>(filteredOrganizerList, viewModel);
        adapter.setShowDetailOption(false);
        adapter.setShowDeleteOption(true);
        recyclerView.setAdapter(adapter);

        SearchView searchView = view.findViewById(R.id.sv_organizer_search);
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

        loadOrganizersFromFirestore();

        adapter.setOnMenuActionListener(new ListElementAdapter.OnMenuActionListener<User>() {
            /**
             * Deletes selected organizer account.
             *
             * @param user selected organizer
             * @param position adapter position
             */
            @Override
            public void onDelete(User user, int position) {
                removeRoleFromUser(user.getAndroidId(), UserDocumentUtils.ROLE_ORGANIZER, "Organizer account cleaned");
            }
        });

        view.findViewById(R.id.btn_delete_selected).setOnClickListener(v -> {
            List<Boolean> selected = adapter.getSelectedList();
            for (int i = 0; i < filteredOrganizerList.size(); i++) {
                if (selected.get(i)) {
                    User user = filteredOrganizerList.get(i);
                    removeRoleFromUser(user.getAndroidId(), UserDocumentUtils.ROLE_ORGANIZER, "Selected organizers cleaned");
                }
            }
        });

        return view;
    }

    /**
     * Filters the organizer list based on the user's search query.
     * It checks if the organizer name matches the search string.
     *
     * @param text The search query string.
     */
    private void filter(String text) {
        filteredOrganizerList.clear();
        adapter.getSelectedList().clear();
        if (text.isEmpty()) {
            filteredOrganizerList.addAll(organizerList);
        } else {
            text = text.toLowerCase();
            for (User user : organizerList) {
                if (user.getName().toLowerCase().contains(text)) {
                    filteredOrganizerList.add(user);
                }
            }
        }
        for (int i = 0; i < filteredOrganizerList.size(); i++) {
            adapter.getSelectedList().add(false);
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * Get list of Organizers from Firebase.
     */
    private void loadOrganizersFromFirestore() {
        db.collection("users")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.w("AdminOrganizerFragment", "Listen failed.", error);
                        return;
                    }

                    organizerList.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            if (!UserDocumentUtils.hasRole(doc, UserDocumentUtils.ROLE_ORGANIZER)) {
                                continue;
                            }
                            User user = doc.toObject(User.class);
                            organizerList.add(user);
                        }
                    }
                    
                    SearchView searchView = getView() != null ? getView().findViewById(R.id.sv_organizer_search) : null;
                    if (searchView != null) {
                        filter(searchView.getQuery().toString());
                    } else {
                        filter("");
                    }
                });
    }

    /**
     * Removes a specific role from a user document in Firestore.
     * If the user only has the specified role, the entire document is deleted.
     * Otherwise, only the specified role is removed from the roles map and a cleanup flag is added.
     * When an organizer is removed, also remove all events owned by them.
     *
     * @param userId         The unique Android ID of the user.
     * @param role           The role string to remove (e.g., "organizer").
     * @param successMessage Message to display in a Toast upon successful deletion.
     */
    private void removeRoleFromUser(String userId, String role, String successMessage) {
        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        return;
                    }

                    // If removing Organizer role, also clean up their events
                    if (UserDocumentUtils.ROLE_ORGANIZER.equals(role)) {
                        cleanupOrganizerEvents(userId);
                    }

                    if (UserDocumentUtils.getRoleCount(snapshot) <= 1) {
                        db.collection("users")
                                .document(userId)
                                .delete()
                                .addOnSuccessListener(unused ->
                                        Toast.makeText(getContext(), successMessage, Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(getContext(), "Error deleting organizer", Toast.LENGTH_SHORT).show());
                        return;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("roles." + role, FieldValue.delete());
                    updates.put("roles.OrganizerRoleCleaned", true);

                    if (UserDocumentUtils.ROLE_ORGANIZER.equals(UserDocumentUtils.getSafeTrimmedString(snapshot, "role"))
                            && UserDocumentUtils.hasRole(snapshot, UserDocumentUtils.ROLE_ENTRANT)) {
                        updates.put("role", UserDocumentUtils.ROLE_ENTRANT);
                    } else if (role.equals(UserDocumentUtils.getSafeTrimmedString(snapshot, "role"))) {
                        updates.put("role", "");
                    }

                    db.collection("users")
                            .document(userId)
                            .update(updates)
                            .addOnSuccessListener(unused ->
                                    Toast.makeText(getContext(), successMessage, Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(getContext(), "Error deleting organizer", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error deleting organizer", Toast.LENGTH_SHORT).show());
    }

    /**
     * Finds and deletes all events owned by the specified organizer.
     * For each event, it also cleans up its associated comments.
     * @param organizerId The ID of the organizer whose events should be removed.
     */
    private void cleanupOrganizerEvents(String organizerId) {
        db.collection("events")
                .whereEqualTo("organizerId", organizerId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String eventId = doc.getId();
                        db.collection("events").document(eventId).collection("comments")
                                .get()
                                .addOnSuccessListener(commentSnapshots -> {
                                    for (DocumentSnapshot commentDoc : commentSnapshots) {
                                        commentDoc.getReference().delete();
                                    }
                                    // Then delete the event itself
                                    db.collection("events").document(eventId).delete()
                                            .addOnSuccessListener(unused -> Log.d("AdminOrganizerFragment", "Deleted event and comments owned by removed organizer: " + eventId))
                                            .addOnFailureListener(e -> Log.e("AdminOrganizerFragment", "Failed to delete event owned by removed organizer: " + eventId, e));
                                });
                    }
                })
                .addOnFailureListener(e -> Log.e("AdminOrganizerFragment", "Failed to query events for cleanup", e));
    }
}
