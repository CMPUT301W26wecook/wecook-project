package com.example.wecookproject;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
        adapter = new ListElementAdapter<>(userList, viewModel);
        adapter.setShowDetailOption(true);
        adapter.setShowDeleteOption(true);
        recyclerView.setAdapter(adapter);

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

            for (int i = 0; i < userList.size(); i++) {
                if (selected.get(i)) {
                    userIdsToDelete.add(userList.get(i).getAndroidId());
                }
            }

            for (String userId : userIdsToDelete) {
                removeRoleFromUser(userId, UserDocumentUtils.ROLE_ENTRANT, "Selected accounts deleted");
            }
        });

        return view;
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
                    adapter.getSelectedList().clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            if (!UserDocumentUtils.hasRole(doc, UserDocumentUtils.ROLE_ENTRANT)) {
                                continue;
                            }
                            User user = doc.toObject(User.class);
                            userList.add(user);
                            adapter.getSelectedList().add(false);
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }

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
