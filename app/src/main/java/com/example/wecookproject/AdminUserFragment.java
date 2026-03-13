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

public class AdminUserFragment extends Fragment {

    private RecyclerView recyclerView;
    private ListElementAdapter<User> adapter;
    private List<User> userList;
    private FirebaseFirestore db;
    private AdminViewModel viewModel;

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
            @Override
            public void onShowDetail(User user) {
                viewModel.selectUser(user);
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new AdminUserProfileFragment())
                        .addToBackStack(null)
                        .commit();
            }

            @Override
            public void onDelete(User user, int position) {
                user.clearProfile();
                db.collection("users").document(user.getAndroidId())
                        .set(user.toFirestoreMap())
                        .addOnSuccessListener(aVoid -> {
                            adapter.notifyItemChanged(position);
                            Toast.makeText(getContext(), "User profile info cleared", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Error clearing profile", Toast.LENGTH_SHORT).show());
            }
        });

        view.findViewById(R.id.btn_delete_selected).setOnClickListener(v -> {
            List<Boolean> selected = adapter.getSelectedList();
            for (int i = 0; i < userList.size(); i++) {
                if (selected.get(i)) {
                    final int index = i;
                    User user = userList.get(index);
                    user.clearProfile();
                    db.collection("users").document(user.getAndroidId())
                            .set(user.toFirestoreMap())
                            .addOnSuccessListener(aVoid -> {
                                selected.set(index, false);
                                adapter.notifyItemChanged(index);
                            });
                }
            }
            Toast.makeText(getContext(), "Selected profiles cleared", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    private void loadUsersFromFirestore() {
        db.collection("users")
                .whereEqualTo("role", "entrant")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.w("AdminUserFragment", "Listen failed.", error);
                        return;
                    }

                    userList.clear();
                    adapter.getSelectedList().clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            User user = doc.toObject(User.class);
                            userList.add(user);
                            adapter.getSelectedList().add(false);
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    // For testing purposes only
    /*
    private void addSampleUsers() {
        String[] firstNames = {"John", "Jane", "Alice", "Bob", "Charlie"};
        String[] lastNames = {"Doe", "Smith", "Johnson", "Brown", "Wilson"};
        String[] roles = {"entrant", "entrant", "organizer", "entrant", "organizer"};

        for (int i = 0; i < 5; i++) {
            String androidId = "sample_device_" + i;
            User user = new User(
                    firstNames[i],
                    lastNames[i],
                    "1990-01-01",
                    "123 Sample St",
                    "Apt " + (i + 1),
                    "Sample City",
                    "T6G 2R3",
                    "Canada",
                    androidId,
                    roles[i]
            );

            db.collection("users").document(androidId)
                    .set(user.toFirestoreMap())
                    .addOnSuccessListener(aVoid -> Log.d("AdminUserFragment", "Sample user added: " + androidId))
                    .addOnFailureListener(e -> Log.e("AdminUserFragment", "Error adding sample user", e));
        }
        Toast.makeText(getContext(), "5 Sample users added to database", Toast.LENGTH_SHORT).show();
    }
     */
}
