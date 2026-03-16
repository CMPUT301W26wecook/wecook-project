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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for the Administrator to browse and manage the list of all Organizers in the system.
 * It also provides functionality to delete multiple Organizer accounts.
 */
public class AdminOrganizerFragment extends Fragment {

    private RecyclerView recyclerView;
    private ListElementAdapter<User> adapter;
    private List<User> organizerList;
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
        adapter = new ListElementAdapter<>(organizerList, viewModel);
        adapter.setShowDetailOption(false);
        adapter.setShowDeleteOption(true);
        recyclerView.setAdapter(adapter);

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
                db.collection("users").document(user.getAndroidId())
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(getContext(), "Organizer account deleted", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Error deleting organizer", Toast.LENGTH_SHORT).show());
            }
        });

        view.findViewById(R.id.btn_delete_selected).setOnClickListener(v -> {
            List<Boolean> selected = adapter.getSelectedList();
            for (int i = 0; i < organizerList.size(); i++) {
                if (selected.get(i)) {
                    User user = organizerList.get(i);
                    db.collection("users").document(user.getAndroidId()).delete();
                }
            }
            Toast.makeText(getContext(), "Selected organizers deleted", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    /**
     * Get list of Organizers from Firebase.
     */
    private void loadOrganizersFromFirestore() {
        db.collection("users")
                .whereEqualTo("role", "organizer")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.w("AdminOrganizerFragment", "Listen failed.", error);
                        return;
                    }

                    organizerList.clear();
                    adapter.getSelectedList().clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            User user = doc.toObject(User.class);
                            organizerList.add(user);
                            adapter.getSelectedList().add(false);
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }
}
