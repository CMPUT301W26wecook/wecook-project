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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fragment for the Administrator to browse and manage the list of all Entrants in the system.
 * It provides functionality to view Entrant profiles and delete entrant accounts.
 */
public class AdminUserFragment extends Fragment {

    private RecyclerView recyclerView;
    private ListElementAdapter<User> adapter;
    private List<User> userList;
    private List<User> filteredUserList;
    private FirebaseFirestore db;
    private AdminViewModel viewModel;

    /**
     * Let fragment show Entrant List UI
     * It initializes the RecyclerView, adapter,
        * and setup event listeners for the menu actions and entrant account deletion.
     *
     * @param inflater           Parent view to which the fragment's UI should be attached.
     * @param container          Parent view for the fragment's UI.
     * @param savedInstanceState Saved state of the fragment.
     * @return The View for the Entrant List UI.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_user_list, container, false);

        db = FirebaseFirestore.getInstance();
        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);
        
        recyclerView = view.findViewById(R.id.rv_user_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        userList = new ArrayList<>();
        filteredUserList = new ArrayList<>();
        adapter = new ListElementAdapter<>(filteredUserList, viewModel);
        adapter.setShowDetailOption(true);
        adapter.setShowDeleteOption(true);
        recyclerView.setAdapter(adapter);

        SearchView searchView = view.findViewById(R.id.sv_user_search);
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

        loadUsersFromFirestore();

        adapter.setOnMenuActionListener(new ListElementAdapter.OnMenuActionListener<User>() {
            /**
             * Opens selected user detail screen.
             *
             * @param user selected user
             */
            @Override
            public void onShowDetail(User user) {
                viewModel.selectUser(user);
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new AdminUserProfileFragment())
                        .addToBackStack(null)
                        .commit();
            }

            /**
             * Deletes selected user account document.
             *
             * @param user selected user
             * @param position adapter position
             */
            @Override
            public void onDelete(User user, int position) {
                removeRoleFromUser(user.getAndroidId(), UserDocumentUtils.ROLE_ENTRANT, "User account deleted");
            }
        });

        view.findViewById(R.id.btn_delete_selected).setOnClickListener(v -> {
            List<Boolean> selected = adapter.getSelectedList();
            List<String> userIdsToDelete = new ArrayList<>();

            for (int i = 0; i < filteredUserList.size(); i++) {
                if (selected.get(i)) {
                    userIdsToDelete.add(filteredUserList.get(i).getAndroidId());
                }
            }

            for (String userId : userIdsToDelete) {
                removeRoleFromUser(userId, UserDocumentUtils.ROLE_ENTRANT, "Selected accounts deleted");
            }
        });

        return view;
    }

    /**
     * Filters the entrant list based on the user's search query.
     * It checks if the entrant name matches the search string.
     *
     * @param text The search query string.
     */
    private void filter(String text) {
        filteredUserList.clear();
        adapter.getSelectedList().clear();
        if (text.isEmpty()) {
            filteredUserList.addAll(userList);
        } else {
            text = text.toLowerCase();
            for (User user : userList) {
                if (user.getName().toLowerCase().contains(text)) {
                    filteredUserList.add(user);
                }
            }
        }
        for (int i = 0; i < filteredUserList.size(); i++) {
            adapter.getSelectedList().add(false);
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * Get list of Entrants from Firebase.
     */
    private void loadUsersFromFirestore() {
        db.collection("users")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.w("AdminUserFragment", "Listen failed.", error);
                        return;
                    }

                    userList.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            if (!UserDocumentUtils.hasRole(doc, UserDocumentUtils.ROLE_ENTRANT)) {
                                continue;
                            }
                            User user = doc.toObject(User.class);
                            userList.add(user);
                        }
                    }
                    
                    SearchView searchView = getView() != null ? getView().findViewById(R.id.sv_user_search) : null;
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
     * Otherwise, only the specified role is removed from the roles map.
     *
     * @param userId         The unique Android ID of the user.
     * @param role           The role string to remove (e.g., "entrant").
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

                    if (UserDocumentUtils.getRoleCount(snapshot) <= 1) {
                        db.collection("users")
                                .document(userId)
                                .delete()
                                .addOnSuccessListener(unused ->
                                        Toast.makeText(getContext(), successMessage, Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(getContext(), "Error deleting account", Toast.LENGTH_SHORT).show());
                        return;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("roles." + role, FieldValue.delete());
                    if (UserDocumentUtils.ROLE_ENTRANT.equals(UserDocumentUtils.getSafeTrimmedString(snapshot, "role"))
                            && UserDocumentUtils.hasRole(snapshot, UserDocumentUtils.ROLE_ORGANIZER)) {
                        updates.put("role", UserDocumentUtils.ROLE_ORGANIZER);
                    }
                    db.collection("users")
                            .document(userId)
                            .update(updates)
                            .addOnSuccessListener(unused ->
                                    Toast.makeText(getContext(), successMessage, Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(getContext(), "Error deleting account", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error deleting account", Toast.LENGTH_SHORT).show());
    }
}
