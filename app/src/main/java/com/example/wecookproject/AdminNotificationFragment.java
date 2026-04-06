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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fragment for the Administrator to browse all notification logs in the Firebase.
 */
public class AdminNotificationFragment extends Fragment {

    private RecyclerView recyclerView;
    private AdminNotificationAdapter adapter;
    private List<UserNotificationItem> notificationList;
    private List<UserNotificationItem> filteredNotificationList;
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
        filteredNotificationList = new ArrayList<>();
        adapter = new AdminNotificationAdapter(filteredNotificationList);
        recyclerView.setAdapter(adapter);

        SearchView searchView = view.findViewById(R.id.sv_notification_search);
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

        loadAllNotifications();

        return view;
    }

    /**
     * Filters the notification list based on the user's search query.
     * It checks if either the event name or the message content matches the search string.
     *
     * @param text The search query string.
     */
    private void filter(String text) {
        filteredNotificationList.clear();
        if (text.isEmpty()) {
            filteredNotificationList.addAll(notificationList);
        } else {
            text = text.toLowerCase();
            for (UserNotificationItem item : notificationList) {
                if (item.getEventName().toLowerCase().contains(text) ||
                    item.getMessage().toLowerCase().contains(text)) {
                    filteredNotificationList.add(item);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * Fetches all notification logs from Firebase.
     * Notifications are sorted by creation time (newest first).
     */
    private void loadAllNotifications() {
        db.collectionGroup("notifications")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("AdminNotificationFragment", "Listen failed.", error);
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Error loading notifications: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                        return;
                    }

                    notificationList.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            try {
                                notificationList.add(UserNotificationItem.fromSnapshot(doc));
                            } catch (Exception e) {
                                Log.e("AdminNotificationFragment", "Error parsing notification: " + doc.getId(), e);
                            }
                        }

                        // Sort newest first in memory
                        Collections.sort(notificationList, (a, b) -> {
                            if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                            return b.getCreatedAt().compareTo(a.getCreatedAt());
                        });
                    }
                    
                    SearchView searchView = getView() != null ? getView().findViewById(R.id.sv_notification_search) : null;
                    if (searchView != null) {
                        filter(searchView.getQuery().toString());
                    } else {
                        filter("");
                    }
                });
    }
}
