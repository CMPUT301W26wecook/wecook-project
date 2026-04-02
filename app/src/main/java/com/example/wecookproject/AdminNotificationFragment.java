package com.example.wecookproject;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for the Administrator to browse all notification logs in the Firebase.
 */
public class AdminNotificationFragment extends Fragment {

    private RecyclerView recyclerView;
    private AdminNotificationAdapter adapter;
    private List<UserNotificationItem> notificationList;
    private FirebaseFirestore db;

    /**
     * Let fragment show Admin Notification List UI.
     * It initializes the RecyclerView and its adapter, and loads notifications.
     *
     * @param inflater           Parent view to which the fragment's UI should be attached.
     * @param container          Parent view for the fragment's UI.
     * @param savedInstanceState Saved state of the fragment.
     * @return The View for the Admin Notification List UI.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_notification_list, container, false);

        db = FirebaseFirestore.getInstance();
        recyclerView = view.findViewById(R.id.rv_notification_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        notificationList = new ArrayList<>();
        adapter = new AdminNotificationAdapter(notificationList);
        recyclerView.setAdapter(adapter);

        loadAllNotifications();

        return view;
    }

    /**
     * Fetches all notification logs from Firebase.
     * Notifications are sorted by creation time (newest first).
     */
    private void loadAllNotifications() {
        db.collectionGroup("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.w("AdminNotificationFragment", "Listen failed.", error);
                        return;
                    }

                    notificationList.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            notificationList.add(UserNotificationItem.fromSnapshot(doc));
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }
}
