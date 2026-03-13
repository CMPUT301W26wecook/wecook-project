package com.example.wecookproject;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wecookproject.model.Event;
import com.example.wecookproject.model.User;
import com.example.wecookproject.model.Organizer;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AdminEvent extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    
    // User management
    private final List<User> userList = new ArrayList<>();
    private final List<User> filteredUserList = new ArrayList<>();
    private SelectableUserAdapter userAdapter;
    
    // Organizer management
    private final List<Organizer> organizerList = new ArrayList<>();
    private final List<Organizer> filteredOrganizerList = new ArrayList<>();
    private SelectableOrganizerAdapter organizerAdapter;
    
    // Event management  
    private final List<Event> eventList = new ArrayList<>();
    private final List<Event> filteredEventList = new ArrayList<>();
    private SelectableEventAdapter eventAdapter;

    // UI Components
    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private TextInputEditText searchEditText;
    private TextInputLayout searchLayout;
    private MaterialButton deleteButton;
    
    private int currentTabIndex = 0;  // 0 = Users, 1 = Organizers, 2 = Events

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_event);

        initializeViews();
        setupTabLayout();
        setupRecyclerView();
        setupSearchFunctionality();
        setupDeleteButton();
        updateUIForCurrentTab();

        // Load initial data (Users tab by default)
        loadUsers();
    }

    private void initializeViews() {
        tabLayout = findViewById(R.id.tabLayout);
        recyclerView = findViewById(R.id.recyclerView);
        searchEditText = findViewById(R.id.searchEditText);
        searchLayout = findViewById(R.id.searchLayout);
        deleteButton = findViewById(R.id.deleteSelectedEventsButton);
    }

    private void setupTabLayout() {
        if (tabLayout.getTabCount() == 0) {
            tabLayout.addTab(tabLayout.newTab().setText("User"));
            tabLayout.addTab(tabLayout.newTab().setText("Organizer"));
            tabLayout.addTab(tabLayout.newTab().setText("Events"));
        }

        TabLayout.Tab initialTab = tabLayout.getTabAt(currentTabIndex);
        if (initialTab != null) {
            initialTab.select();
        }
        
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTabIndex = tab.getPosition();
                updateUIForCurrentTab();
                
                switch (currentTabIndex) {
                    case 0: // Users
                        loadUsers();
                        break;
                    case 1: // Organizers
                        loadOrganizers();
                        break;
                    case 2: // Events
                        loadEvents();
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void updateUIForCurrentTab() {
        switch (currentTabIndex) {
            case 0: // Users
                searchLayout.setHint("Search by User Name");
                deleteButton.setText("Delete Selected Users");
                showUserItems();
                break;
            case 1: // Organizers
                searchLayout.setHint("Search by Organizer Name");
                deleteButton.setText("Delete Selected Organizers");
                showOrganizerItems();
                break;
            case 2: // Events
                searchLayout.setHint("Search by Event Name");
                deleteButton.setText("Delete Selected Events");
                showEventItems();
                break;
        }
        
        // Clear search text when switching tabs
        searchEditText.setText("");
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void showUserItems() {
        userAdapter = new SelectableUserAdapter(filteredUserList);
        recyclerView.setAdapter(userAdapter);
    }

    private void showOrganizerItems() {
        organizerAdapter = new SelectableOrganizerAdapter(filteredOrganizerList);
        recyclerView.setAdapter(organizerAdapter);
    }

    private void showEventItems() {
        eventAdapter = new SelectableEventAdapter(filteredEventList);
        recyclerView.setAdapter(eventAdapter);
    }

    private void setupSearchFunctionality() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                switch (currentTabIndex) {
                    case 0: // Users
                        filterUsers(s.toString());
                        break;
                    case 1: // Organizers
                        filterOrganizers(s.toString());
                        break;
                    case 2: // Events
                        filterEvents(s.toString());
                        break;
                }
            }
        });
    }

    private void setupDeleteButton() {
        deleteButton.setOnClickListener(v -> {
            switch (currentTabIndex) {
                case 0: // Users
                    deleteSelectedUsers();
                    break;
                case 1: // Organizers
                    deleteSelectedOrganizers();
                    break;
                case 2: // Events
                    deleteSelectedEvents();
                    break;
            }
        });
    }

    // USER MANAGEMENT METHODS
    private void loadUsers() {
        db.collection("users")
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(this, "Failed to load users", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    userList.clear();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        User user = document.toObject(User.class);
                        if (user.getAndroidId() == null || user.getAndroidId().isEmpty()) {
                            user.setAndroidId(document.getId());
                        }
                        userList.add(user);
                    }
                    
                    filterUsers(searchEditText.getText().toString());
                    Toast.makeText(this, "Loaded " + userList.size() + " users", Toast.LENGTH_SHORT).show();
                });
    }

    private void filterUsers(String searchText) {
        filteredUserList.clear();
        
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredUserList.addAll(userList);
        } else {
            String searchLower = searchText.toLowerCase().trim();
            for (User user : userList) {
                if (userMatchesSearch(user, searchLower)) {
                    filteredUserList.add(user);
                }
            }
        }
        
        if (userAdapter != null) {
            userAdapter.notifyDataSetChanged();
        }
    }

    private boolean userMatchesSearch(User user, String searchLower) {
        return (user.getFullName().toLowerCase().contains(searchLower)) ||
               (user.getFirstName() != null && user.getFirstName().toLowerCase().contains(searchLower)) ||
               (user.getLastName() != null && user.getLastName().toLowerCase().contains(searchLower)) ||
               (user.getCity() != null && user.getCity().toLowerCase().contains(searchLower)) ||
               (user.getCountry() != null && user.getCountry().toLowerCase().contains(searchLower)) ||
               (user.getAndroidId() != null && user.getAndroidId().toLowerCase().contains(searchLower));
    }

    private void deleteSelectedUsers() {
        if (userAdapter == null) return;
        
        List<String> selectedUserIds = userAdapter.getSelectedUserIds();
        if (selectedUserIds.isEmpty()) {
            Toast.makeText(this, "No users selected", Toast.LENGTH_SHORT).show();
            return;
        }

        showUserDeleteConfirmation(selectedUserIds);
    }

    private void showUserDeleteConfirmation(List<String> selectedUserIds) {
        String message = selectedUserIds.size() == 1
                ? "Delete the selected user? This will permanently remove their account and all associated data."
                : "Delete " + selectedUserIds.size() + " selected users? This will permanently remove their accounts and all associated data.";

        new AlertDialog.Builder(this)
                .setTitle("⚠️ Confirm User Deletion")
                .setMessage(message + "\n\nThis action cannot be undone!")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Delete", (dialog, which) -> performUserDeletion(selectedUserIds))
                .show();
    }

    private void performUserDeletion(List<String> selectedUserIds) {
        WriteBatch batch = db.batch();
        for (String userId : selectedUserIds) {
            batch.delete(db.collection("users").document(userId));
        }

        batch.commit()
                .addOnSuccessListener(unused -> {
                    removeUsersFromLists(selectedUserIds);
                    userAdapter.clearSelection();
                    userAdapter.notifyDataSetChanged();
                    
                    String successMessage = selectedUserIds.size() == 1 
                            ? "User deleted successfully" 
                            : selectedUserIds.size() + " users deleted successfully";
                    Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete selected users", Toast.LENGTH_SHORT).show();
                });
    }

    private void removeUsersFromLists(List<String> selectedUserIds) {
        Iterator<User> mainIterator = userList.iterator();
        while (mainIterator.hasNext()) {
            User user = mainIterator.next();
            if (selectedUserIds.contains(user.getAndroidId())) {
                mainIterator.remove();
            }
        }
        
        Iterator<User> filteredIterator = filteredUserList.iterator();
        while (filteredIterator.hasNext()) {
            User user = filteredIterator.next();
            if (selectedUserIds.contains(user.getAndroidId())) {
                filteredIterator.remove();
            }
        }
    }

    // ORGANIZER MANAGEMENT METHODS
    private void loadOrganizers() {
        // Load organizers from users collection with role = "ORGANIZER" 
        // or from a separate organizers collection
        db.collection("users")
                .whereEqualTo("role", "ORGANIZER")  // Filter for organizer role
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        // Try loading from organizers collection as fallback
                        loadOrganizersFromOrganizerCollection();
                        return;
                    }

                    organizerList.clear();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Organizer organizer = document.toObject(Organizer.class);
                        if (organizer.getAndroidId() == null || organizer.getAndroidId().isEmpty()) {
                            organizer.setAndroidId(document.getId());
                        }
                        organizerList.add(organizer);
                    }
                    
                    filterOrganizers(searchEditText.getText().toString());
                    Toast.makeText(this, "Loaded " + organizerList.size() + " organizers", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadOrganizersFromOrganizerCollection() {
        // Fallback: try loading from organizers collection
        db.collection("organizers")
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        // If both fail, create mock organizers from existing users
                        createMockOrganizers();
                        return;
                    }

                    organizerList.clear();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Organizer organizer = document.toObject(Organizer.class);
                        if (organizer.getAndroidId() == null || organizer.getAndroidId().isEmpty()) {
                            organizer.setAndroidId(document.getId());
                        }
                        organizerList.add(organizer);
                    }
                    
                    filterOrganizers(searchEditText.getText().toString());
                    Toast.makeText(this, "Loaded " + organizerList.size() + " organizers", Toast.LENGTH_SHORT).show();
                });
    }

    private void createMockOrganizers() {
        // Create mock organizers for demonstration
        organizerList.clear();
        for (int i = 0; i < 5; i++) {
            Organizer mockOrganizer = new Organizer();
            mockOrganizer.setAndroidId("organizer_" + i);
            mockOrganizer.setFirstName("Organizer");
            mockOrganizer.setLastName("" + (i + 1));
            mockOrganizer.setCity("City " + (i + 1));
            mockOrganizer.setCountry("Country " + (i + 1));
            mockOrganizer.setProfileCompleted(i % 2 == 0);
            mockOrganizer.setRole("ORGANIZER");
            
            List<String> eventIds = new ArrayList<>();
            for (int j = 0; j < (i + 1); j++) {
                eventIds.add("event_" + i + "_" + j);
            }
            mockOrganizer.setManagedEventIds(eventIds);
            
            organizerList.add(mockOrganizer);
        }
        
        filterOrganizers(searchEditText.getText().toString());
        Toast.makeText(this, "Loaded " + organizerList.size() + " mock organizers", Toast.LENGTH_SHORT).show();
    }

    private void filterOrganizers(String searchText) {
        filteredOrganizerList.clear();
        
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredOrganizerList.addAll(organizerList);
        } else {
            String searchLower = searchText.toLowerCase().trim();
            for (Organizer organizer : organizerList) {
                if (organizerMatchesSearch(organizer, searchLower)) {
                    filteredOrganizerList.add(organizer);
                }
            }
        }
        
        if (organizerAdapter != null) {
            organizerAdapter.notifyDataSetChanged();
        }
    }

    private boolean organizerMatchesSearch(Organizer organizer, String searchLower) {
        return (organizer.getFullName().toLowerCase().contains(searchLower)) ||
               (organizer.getFirstName() != null && organizer.getFirstName().toLowerCase().contains(searchLower)) ||
               (organizer.getLastName() != null && organizer.getLastName().toLowerCase().contains(searchLower)) ||
               (organizer.getCity() != null && organizer.getCity().toLowerCase().contains(searchLower)) ||
               (organizer.getCountry() != null && organizer.getCountry().toLowerCase().contains(searchLower)) ||
               (organizer.getAndroidId() != null && organizer.getAndroidId().toLowerCase().contains(searchLower));
    }

    private void deleteSelectedOrganizers() {
        if (organizerAdapter == null) return;
        
        List<String> selectedOrganizerIds = organizerAdapter.getSelectedOrganizerIds();
        if (selectedOrganizerIds.isEmpty()) {
            Toast.makeText(this, "No organizers selected", Toast.LENGTH_SHORT).show();
            return;
        }

        showOrganizerDeleteConfirmation(selectedOrganizerIds);
    }

    private void showOrganizerDeleteConfirmation(List<String> selectedOrganizerIds) {
        String message = selectedOrganizerIds.size() == 1
                ? "Delete the selected organizer? This will permanently remove their account and all associated data."
                : "Delete " + selectedOrganizerIds.size() + " selected organizers? This will permanently remove their accounts and all associated data.";

        new AlertDialog.Builder(this)
                .setTitle("⚠️ Confirm Organizer Deletion")
                .setMessage(message + "\n\nThis action cannot be undone!")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Delete", (dialog, which) -> performOrganizerDeletion(selectedOrganizerIds))
                .show();
    }

    private void performOrganizerDeletion(List<String> selectedOrganizerIds) {
        WriteBatch batch = db.batch();
        
        // Try deleting from users collection first
        for (String organizerId : selectedOrganizerIds) {
            batch.delete(db.collection("users").document(organizerId));
            // Also try deleting from organizers collection if it exists
            batch.delete(db.collection("organizers").document(organizerId));
        }

        batch.commit()
                .addOnSuccessListener(unused -> {
                    removeOrganizersFromLists(selectedOrganizerIds);
                    organizerAdapter.clearSelection();
                    organizerAdapter.notifyDataSetChanged();
                    
                    String successMessage = selectedOrganizerIds.size() == 1 
                            ? "Organizer deleted successfully" 
                            : selectedOrganizerIds.size() + " organizers deleted successfully";
                    Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete selected organizers", Toast.LENGTH_SHORT).show();
                });
    }

    private void removeOrganizersFromLists(List<String> selectedOrganizerIds) {
        Iterator<Organizer> mainIterator = organizerList.iterator();
        while (mainIterator.hasNext()) {
            Organizer organizer = mainIterator.next();
            if (selectedOrganizerIds.contains(organizer.getAndroidId())) {
                mainIterator.remove();
            }
        }
        
        Iterator<Organizer> filteredIterator = filteredOrganizerList.iterator();
        while (filteredIterator.hasNext()) {
            Organizer organizer = filteredIterator.next();
            if (selectedOrganizerIds.contains(organizer.getAndroidId())) {
                filteredIterator.remove();
            }
        }
    }

    // EVENT MANAGEMENT METHODS
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
                    
                    filterEvents(searchEditText.getText().toString());
                    Toast.makeText(this, "Loaded " + eventList.size() + " events", Toast.LENGTH_SHORT).show();
                });
    }

    private void filterEvents(String searchText) {
        filteredEventList.clear();
        
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredEventList.addAll(eventList);
        } else {
            String searchLower = searchText.toLowerCase().trim();
            for (Event event : eventList) {
                if (eventMatchesSearch(event, searchLower)) {
                    filteredEventList.add(event);
                }
            }
        }
        
        if (eventAdapter != null) {
            eventAdapter.notifyDataSetChanged();
        }
    }

    private boolean eventMatchesSearch(Event event, String searchLower) {
        return (event.getEventName() != null && event.getEventName().toLowerCase().contains(searchLower)) ||
               (event.getLocation() != null && event.getLocation().toLowerCase().contains(searchLower)) ||
               (event.getOrganizerId() != null && event.getOrganizerId().toLowerCase().contains(searchLower));
    }

    private void deleteSelectedEvents() {
        if (eventAdapter == null) return;
        
        List<String> selectedEventIds = eventAdapter.getSelectedEventIds();
        if (selectedEventIds.isEmpty()) {
            Toast.makeText(this, "No events selected", Toast.LENGTH_SHORT).show();
            return;
        }

        showEventDeleteConfirmation(selectedEventIds);
    }

    private void showEventDeleteConfirmation(List<String> selectedEventIds) {
        String message = selectedEventIds.size() == 1
                ? "Delete the selected event?"
                : "Delete " + selectedEventIds.size() + " selected events?";

        new AlertDialog.Builder(this)
                .setTitle("Confirm deletion")
                .setMessage(message)
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Delete", (dialog, which) -> performEventDeletion(selectedEventIds))
                .show();
    }

    private void performEventDeletion(List<String> selectedEventIds) {
        WriteBatch batch = db.batch();
        for (String eventId : selectedEventIds) {
            batch.delete(db.collection("events").document(eventId));
        }

        batch.commit()
                .addOnSuccessListener(unused -> {
                    removeEventsFromLists(selectedEventIds);
                    eventAdapter.clearSelection();
                    eventAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "Selected events deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to delete selected events", Toast.LENGTH_SHORT).show());
    }

    private void removeEventsFromLists(List<String> selectedEventIds) {
        Iterator<Event> mainIterator = eventList.iterator();
        while (mainIterator.hasNext()) {
            Event event = mainIterator.next();
            if (selectedEventIds.contains(event.getEventId())) {
                mainIterator.remove();
            }
        }
        
        Iterator<Event> filteredIterator = filteredEventList.iterator();
        while (filteredIterator.hasNext()) {
            Event event = filteredIterator.next();
            if (selectedEventIds.contains(event.getEventId())) {
                filteredIterator.remove();
            }
        }
    }
}

