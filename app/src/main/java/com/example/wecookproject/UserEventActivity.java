package com.example.wecookproject;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wecookproject.model.Event;
import com.google.zxing.WriterException;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Entrant event list screen with waitlist and invitation actions.
 */
public class UserEventActivity extends AppCompatActivity {
    private static final String CAPACITY_ALL = UserEventFilterLogic.CAPACITY_ALL;
    private static final String CAPACITY_SMALL = UserEventFilterLogic.CAPACITY_SMALL;
    private static final String CAPACITY_MEDIUM = UserEventFilterLogic.CAPACITY_MEDIUM;
    private static final String CAPACITY_LARGE = UserEventFilterLogic.CAPACITY_LARGE;
    private static final String CAPACITY_VERY_LARGE = UserEventFilterLogic.CAPACITY_VERY_LARGE;

    private static final String AVAILABILITY_ALL = UserEventFilterLogic.AVAILABILITY_ALL;
    private static final String AVAILABILITY_EARLY_MORNING = UserEventFilterLogic.AVAILABILITY_EARLY_MORNING;
    private static final String AVAILABILITY_MORNING = UserEventFilterLogic.AVAILABILITY_MORNING;
    private static final String AVAILABILITY_AFTERNOON = UserEventFilterLogic.AVAILABILITY_AFTERNOON;
    private static final String AVAILABILITY_EVENING = UserEventFilterLogic.AVAILABILITY_EVENING;
    private static final String AVAILABILITY_NIGHT = UserEventFilterLogic.AVAILABILITY_NIGHT;
    private static final String ELIGIBILITY_ALL = UserEventFilterLogic.ELIGIBILITY_ALL;
    private static final String ELIGIBILITY_JOINABLE = UserEventFilterLogic.ELIGIBILITY_JOINABLE;
    private static final double KEYWORD_SCORE_THRESHOLD = UserEventFilterLogic.KEYWORD_SCORE_THRESHOLD;
    private static final int SEMANTIC_TOP_N = 40;
    private static final double LEXICAL_WEIGHT = 0.55d;
    private static final double SEMANTIC_WEIGHT = 0.45d;
    private static final long KEYWORD_SEARCH_DEBOUNCE_MS = 300L;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<UserEventRecord> eventList = new ArrayList<>();
    private final List<UserEventRecord> allEventRecords = new ArrayList<>();

    private RecyclerView rvEvents;
    private TextView tvEmptyState;
    private UserEventAdapter eventAdapter;
    private String entrantId;
    private BottomNavigationView bottomNav;
    private Spinner spinnerCapacityFilter;
    private Spinner spinnerAvailabilityFilter;
    private Spinner spinnerEligibilityFilter;
    private SearchView searchEventKeyword;
    private String selectedCapacityLabel = CAPACITY_ALL;
    private String selectedAvailabilityLabel = AVAILABILITY_ALL;
    private String selectedEligibilityLabel = ELIGIBILITY_ALL;
    private String keywordQuery = "";
    private Runnable pendingKeywordSearch;
    private OnDeviceSemanticSearchEngine semanticSearchEngine;
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private int keywordSearchGeneration = 0;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private UserEventRecord pendingJoinEventRecord;
    private AlertDialog pendingJoinDialog;
    private boolean loginSelectionPopupChecked = false;

    /**
     * Initializes list UI, permissions launcher, and data loading.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_event_list);

        entrantId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                            || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (granted && pendingJoinEventRecord != null && pendingJoinDialog != null) {
                        fetchLocationAndJoinWaitlist(pendingJoinEventRecord, pendingJoinDialog);
                    } else if (!granted) {
                        Toast.makeText(this, "Location permission is required to join the waitlist", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        rvEvents = findViewById(R.id.rv_events);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        bottomNav = findViewById(R.id.bottom_nav);
        spinnerCapacityFilter = findViewById(R.id.spinner_capacity_filter);
        spinnerAvailabilityFilter = findViewById(R.id.spinner_availability_filter);
        spinnerEligibilityFilter = findViewById(R.id.spinner_eligibility_filter);
        searchEventKeyword = findViewById(R.id.search_event_keyword);
        semanticSearchEngine = new OnDeviceSemanticSearchEngine(getApplicationContext());
        searchExecutor.execute(() -> semanticSearchEngine.warmup());
        findViewById(R.id.btn_view_lottery_criteria).setOnClickListener(v ->
                startActivity(new Intent(this, UserLotteryCriteriaActivity.class)));

        rvEvents.setLayoutManager(new LinearLayoutManager(this));
        eventAdapter = new UserEventAdapter(eventList, this::showEventDetailsDialog);
        rvEvents.setAdapter(eventAdapter);

        setupFilterControls();
        setupBottomNav();
        loadEventsAndHistory();
        maybeShowSelectionConfirmationPopup();
    }

    /**
     * Reloads events when returning to foreground.
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadEventsAndHistory();
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_events);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingKeywordSearch != null) {
            mainThreadHandler.removeCallbacks(pendingKeywordSearch);
            pendingKeywordSearch = null;
        }
        searchExecutor.shutdownNow();
        try {
            if (!searchExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                searchExecutor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            searchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            semanticSearchEngine.close();
        }
    }

    /**
     * Configures entrant bottom-navigation actions.
     */
    private void setupBottomNav() {
        bottomNav.setSelectedItemId(R.id.nav_events);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_events) {
                return true;
            } else if (itemId == R.id.nav_scan) {
                startActivity(new Intent(UserEventActivity.this, UserScanActivity.class));
                return true;
            } else if (itemId == R.id.nav_history) {
                Intent intent = new Intent(UserEventActivity.this, UserHistoryActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
                return true;
            } else if (itemId == R.id.nav_profile) {
                Intent intent = new Intent(UserEventActivity.this, UserProfileActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
                return true;
            }

            return false;
        });
    }


    /**
     * Loads event history statuses, then event list.
     */
    private void loadEventsAndHistory() {
        db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .get(Source.SERVER)
                .addOnSuccessListener(historySnapshots -> {
                    Map<String, String> historyStatuses = new HashMap<>();
                    Set<String> historyEventIds = new HashSet<>();
                    for (QueryDocumentSnapshot historyDocument : historySnapshots) {
                        String eventId = historyDocument.getString("eventId");
                        String status = historyDocument.getString("status");
                        if (eventId != null) {
                            historyEventIds.add(eventId);
                            if (status != null) {
                                historyStatuses.put(eventId, status);
                            }
                        }
                    }
                    loadEvents(historyStatuses, historyEventIds);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load history from server. Loading cached history.", Toast.LENGTH_SHORT).show();
                    db.collection("users")
                            .document(entrantId)
                            .collection("eventHistory")
                            .get(Source.CACHE)
                            .addOnSuccessListener(cacheSnapshots -> {
                                Map<String, String> historyStatuses = new HashMap<>();
                                Set<String> historyEventIds = new HashSet<>();
                                for (QueryDocumentSnapshot historyDocument : cacheSnapshots) {
                                    String eventId = historyDocument.getString("eventId");
                                    String status = historyDocument.getString("status");
                                    if (eventId != null) {
                                        historyEventIds.add(eventId);
                                        if (status != null) {
                                            historyStatuses.put(eventId, status);
                                        }
                                    }
                                }
                                loadEvents(historyStatuses, historyEventIds);
                            })
                            .addOnFailureListener(cacheError -> {
                                Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show();
                                loadEvents(new HashMap<>(), new HashSet<>(), Source.SERVER);
                            });
                });
    }

    /**
     * Loads all events and merges history status for display.
     *
     * @param historyStatuses map of eventId to history status
     * @param historyEventIds set of eventIds present in entrant history
     */
    private void loadEvents(Map<String, String> historyStatuses, Set<String> historyEventIds) {
        loadEvents(historyStatuses, historyEventIds, Source.SERVER);
    }

    private void loadEvents(Map<String, String> historyStatuses, Set<String> historyEventIds, Source source) {
        db.collection("events")
                .get(source)
                .addOnSuccessListener(eventSnapshots -> {
                    allEventRecords.clear();
                    for (QueryDocumentSnapshot document : eventSnapshots) {
                        String eventId = document.getId();
                        String visibilityTag = document.getString("visibilityTag");
                        List<String> waitlistEntrants = FirestoreFieldUtils.getStringList(document, "waitlistEntrantIds");
                        List<String> pendingCoOrganizers = FirestoreFieldUtils.getStringList(document, "pendingCoOrganizerIds");
                        List<String> coOrganizers = FirestoreFieldUtils.getStringList(document, "coOrganizerIds");
                        boolean entrantOnWaitlist = waitlistEntrants != null && waitlistEntrants.contains(entrantId);
                        boolean entrantPendingCoOrganizer = pendingCoOrganizers.contains(entrantId);
                        boolean entrantIsCoOrganizer = coOrganizers.contains(entrantId);
                        boolean entrantHasHistory = historyEventIds.contains(eventId);

                        if (Event.VISIBILITY_PRIVATE.equalsIgnoreCase(visibilityTag)
                                && !entrantOnWaitlist
                                && !entrantPendingCoOrganizer
                                && !entrantIsCoOrganizer
                                && !entrantHasHistory) {
                            continue;
                        }

                        if (!document.contains("waitlistEntrantIds")) {
                            initializeWaitingList(document.getReference());
                        }

                        UserEventRecord eventRecord = UserEventRecord.fromEventSnapshot(
                                document,
                                entrantId,
                                historyStatuses.get(eventId)
                        );

                        if (eventRecord.isEntrantOnWaitlist() && historyStatuses.get(eventId) == null) {
                            upsertHistoryDocument(eventRecord, UserEventRecord.STATUS_WAITLISTED);
                        }

                        // If the organizer has picked this entrant as a lottery winner, promote
                        // it to "invited" unless the entrant has already accepted.
                        List<String> selectedEntrantIds = FirestoreFieldUtils.getStringList(document, "selectedEntrantIds");
                        String currentStatus = historyStatuses.get(eventId);
                        boolean isSelected = selectedEntrantIds != null && selectedEntrantIds.contains(entrantId);
                        boolean isAccepted = UserEventRecord.STATUS_ACCEPTED.equals(currentStatus)
                                || UserEventRecord.STATUS_ACCEPTED.equals(eventRecord.getEffectiveStatus());
                        if (isSelected && !isAccepted) {
                            eventRecord.setHistoryStatus(UserEventRecord.STATUS_INVITED);
                            upsertHistoryDocument(eventRecord, UserEventRecord.STATUS_INVITED);
                        }

                        allEventRecords.add(eventRecord);
                    }

                    applyFiltersAndRender();
                })
                .addOnFailureListener(e -> {
                    if (source == Source.SERVER) {
                        Toast.makeText(this, "Failed to load events from server. Showing cached events.", Toast.LENGTH_SHORT).show();
                        loadEvents(historyStatuses, historyEventIds, Source.CACHE);
                        return;
                    }
                    Toast.makeText(this, "Failed to load events", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Initializes waitlist fields on legacy event documents.
     *
     * @param eventReference event document reference
     */
    private void initializeWaitingList(DocumentReference eventReference) {
        eventReference.update(
                "waitlistEntrantIds", new ArrayList<String>(),
                "currentWaitlistCount", 0
        );
    }

    /**
     * Toggles empty-state visibility for the event list.
     */
    private void updateEmptyState() {
        if (eventList.isEmpty()) {
            tvEmptyState.setText(resolveEmptyStateMessage());
            tvEmptyState.setVisibility(View.VISIBLE);
            rvEvents.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            rvEvents.setVisibility(View.VISIBLE);
        }
    }

    private String resolveEmptyStateMessage() {
        return UserEventFilterLogic.resolveEmptyStateMessage(
                selectedCapacityLabel,
                selectedAvailabilityLabel,
                selectedEligibilityLabel,
                keywordQuery
        );
    }

    private void setupFilterControls() {
        ArrayAdapter<String> capacityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{
                        CAPACITY_ALL,
                        CAPACITY_SMALL,
                        CAPACITY_MEDIUM,
                        CAPACITY_LARGE,
                        CAPACITY_VERY_LARGE
                }
        );
        capacityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCapacityFilter.setAdapter(capacityAdapter);

        ArrayAdapter<String> availabilityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{
                        AVAILABILITY_ALL,
                        AVAILABILITY_EARLY_MORNING,
                        AVAILABILITY_MORNING,
                        AVAILABILITY_AFTERNOON,
                        AVAILABILITY_EVENING,
                        AVAILABILITY_NIGHT
                }
        );
        availabilityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAvailabilityFilter.setAdapter(availabilityAdapter);

        ArrayAdapter<String> eligibilityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{
                        ELIGIBILITY_ALL,
                        ELIGIBILITY_JOINABLE
                }
        );
        eligibilityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEligibilityFilter.setAdapter(eligibilityAdapter);

        spinnerCapacityFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCapacityLabel = (String) parent.getItemAtPosition(position);
                applyFiltersAndRender();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });

        spinnerAvailabilityFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedAvailabilityLabel = (String) parent.getItemAtPosition(position);
                applyFiltersAndRender();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });

        spinnerEligibilityFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedEligibilityLabel = (String) parent.getItemAtPosition(position);
                applyFiltersAndRender();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });

        searchEventKeyword.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (pendingKeywordSearch != null) {
                    mainThreadHandler.removeCallbacks(pendingKeywordSearch);
                    pendingKeywordSearch = null;
                }
                keywordQuery = query == null ? "" : query.trim();
                applyFiltersAndRender();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                keywordQuery = newText == null ? "" : newText.trim();
                if (pendingKeywordSearch != null) {
                    mainThreadHandler.removeCallbacks(pendingKeywordSearch);
                }
                pendingKeywordSearch = () -> {
                    pendingKeywordSearch = null;
                    applyFiltersAndRender();
                };
                mainThreadHandler.postDelayed(pendingKeywordSearch, KEYWORD_SEARCH_DEBOUNCE_MS);
                return true;
            }
        });
    }

    private void applyFiltersAndRender() {
        keywordSearchGeneration++;
        eventList.clear();
        com.google.firebase.Timestamp currentTime = com.google.firebase.Timestamp.now();
        boolean isKeywordSearchEnabled = keywordQuery != null && !keywordQuery.trim().isEmpty();
        List<ScoredEventRecord> scoredRecords = new ArrayList<>();
        for (UserEventRecord eventRecord : allEventRecords) {
            if (!matchesCapacityFilter(eventRecord.getCapacity())) {
                continue;
            }
            if (!matchesAvailabilityFilter(eventRecord.getEventTime())) {
                continue;
            }
            if (!matchesEligibilityFilter(eventRecord, currentTime)) {
                continue;
            }

            if (!isKeywordSearchEnabled) {
                eventList.add(eventRecord);
                continue;
            }

            double score = EventSearchMatcher.score(keywordQuery, eventRecord);
            if (score < KEYWORD_SCORE_THRESHOLD) {
                continue;
            }
            scoredRecords.add(new ScoredEventRecord(eventRecord, score));
        }

        if (isKeywordSearchEnabled) {
            Collections.sort(scoredRecords, Comparator.comparingDouble(ScoredEventRecord::getScore).reversed());
            for (ScoredEventRecord scoredRecord : scoredRecords) {
                eventList.add(scoredRecord.getEventRecord());
            }
            requestSemanticRerank(scoredRecords, keywordQuery, keywordSearchGeneration);
        }

        eventAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void requestSemanticRerank(List<ScoredEventRecord> lexicalScores,
                                       String query,
                                       int generation) {
        if (lexicalScores.isEmpty()) {
            return;
        }

        List<ScoredEventRecord> semanticWindow = SemanticRankingUtils.topN(lexicalScores, SEMANTIC_TOP_N);
        List<OnDeviceSemanticSearchEngine.EventCandidate> candidates = new ArrayList<>();
        Map<String, UserEventRecord> semanticRecordsById = new HashMap<>();
        List<SemanticRankingUtils.ScoredId> lexicalWindowForBlend = new ArrayList<>();
        for (ScoredEventRecord item : semanticWindow) {
            candidates.add(new OnDeviceSemanticSearchEngine.EventCandidate(
                    item.getEventRecord().getEventId(),
                    EventSearchMatcher.buildSearchDocument(item.getEventRecord())
            ));
            semanticRecordsById.put(item.getEventRecord().getEventId(), item.getEventRecord());
            lexicalWindowForBlend.add(new SemanticRankingUtils.ScoredId(item.getEventRecord().getEventId(), item.getScore()));
        }

        searchExecutor.execute(() -> {
            Map<String, Double> semanticScores = semanticSearchEngine.score(query, candidates);
            if (semanticScores.isEmpty()) {
                return;
            }

            List<SemanticRankingUtils.ScoredId> blendedWindow = SemanticRankingUtils.blendTopWindow(
                    lexicalWindowForBlend,
                    semanticScores,
                    LEXICAL_WEIGHT,
                    SEMANTIC_WEIGHT
            );

            mainThreadHandler.post(() -> {
                if (generation != keywordSearchGeneration) {
                    return;
                }
                if (!query.equals(keywordQuery)) {
                    return;
                }
                eventList.clear();
                for (SemanticRankingUtils.ScoredId scoredItem : blendedWindow) {
                    UserEventRecord record = semanticRecordsById.get(scoredItem.id);
                    if (record != null) {
                        eventList.add(record);
                    }
                }
                for (int i = semanticWindow.size(); i < lexicalScores.size(); i++) {
                    eventList.add(lexicalScores.get(i).getEventRecord());
                }
                eventAdapter.notifyDataSetChanged();
                updateEmptyState();
            });
        });
    }

    private static final class ScoredEventRecord {
        private final UserEventRecord eventRecord;
        private final double score;

        private ScoredEventRecord(UserEventRecord eventRecord, double score) {
            this.eventRecord = eventRecord;
            this.score = score;
        }

        private UserEventRecord getEventRecord() {
            return eventRecord;
        }

        private double getScore() {
            return score;
        }
    }

    private boolean matchesEligibilityFilter(UserEventRecord eventRecord, com.google.firebase.Timestamp currentTime) {
        return UserEventFilterLogic.matchesEligibilityFilter(
                selectedEligibilityLabel,
                eventRecord,
                currentTime
        );
    }

    private boolean matchesCapacityFilter(int capacity) {
        return UserEventFilterLogic.matchesCapacityFilter(selectedCapacityLabel, capacity);
    }

    private boolean matchesAvailabilityFilter(com.google.firebase.Timestamp eventTime) {
        return UserEventFilterLogic.matchesAvailabilityFilter(selectedAvailabilityLabel, eventTime);
    }

    private void maybeShowSelectionConfirmationPopup() {
        if (loginSelectionPopupChecked || entrantId == null || entrantId.trim().isEmpty()) {
            return;
        }
        loginSelectionPopupChecked = true;

        db.collection("users")
                .document(entrantId)
                .collection("notifications")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    DocumentSnapshot candidate = null;
                    for (QueryDocumentSnapshot snapshot : querySnapshot) {
                        String type = snapshot.getString("type");
                        if (!isSelectionNotificationType(type)) {
                            continue;
                        }

                        String status = snapshot.getString("status");
                        boolean alreadyConfirmed = NotificationHelper.STATUS_CONFIRMED.equalsIgnoreCase(status)
                                || snapshot.getTimestamp("confirmedAt") != null;
                        if (!alreadyConfirmed) {
                            candidate = snapshot;
                            break;
                        }
                    }

                    if (candidate != null) {
                        showSelectionConfirmationPopup(candidate);
                    }
                });
    }

    private boolean isSelectionNotificationType(String type) {
        return NotificationHelper.TYPE_PRIVATE_INVITE.equals(type)
                || NotificationHelper.TYPE_LOTTERY_SELECTED.equals(type)
                || NotificationHelper.TYPE_REPLACEMENT_SELECTED.equals(type);
    }

    private void showSelectionConfirmationPopup(DocumentSnapshot notificationSnapshot) {
        String notificationId = notificationSnapshot.getId();
        String eventName = notificationSnapshot.getString("eventName");
        String message = notificationSnapshot.getString("message");
        String safeEventName = (eventName == null || eventName.trim().isEmpty()) ? "this event" : eventName.trim();
        String safeMessage = (message == null || message.trim().isEmpty())
                ? "Congratulations on being selected to this event! Hope you have fun!!"
                : message.trim();

        new AlertDialog.Builder(this)
                .setTitle("Selection Notice")
                .setMessage(safeMessage + "\n\nEvent: " + safeEventName + "\n\nConfirm now?")
                .setPositiveButton("Confirm", (dialog, which) ->
                        NotificationHelper.markAsConfirmed(db, entrantId, notificationId)
                                .addOnSuccessListener(unused ->
                                        Toast.makeText(this, "Selection confirmed", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Failed to confirm selection", Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Later", null)
                .show();
    }

    /**
     * Shows the event-details dialog for one event record.
     *
     * @param eventRecord selected event
     */
    private void showEventDetailsDialog(UserEventRecord eventRecord) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_user_event_details, null, false);

        TextView tvAvatar = dialogView.findViewById(R.id.tv_dialog_avatar);
        TextView tvHeaderName = dialogView.findViewById(R.id.tv_dialog_event_name);
        TextView tvHeaderLocation = dialogView.findViewById(R.id.tv_dialog_location);
        TextView tvOrganizer = dialogView.findViewById(R.id.tv_dialog_organizer);
        Button btnShowQr = dialogView.findViewById(R.id.btn_dialog_show_qr);
        ImageView ivPoster = dialogView.findViewById(R.id.iv_dialog_poster);
        TextView tvDetailName = dialogView.findViewById(R.id.tv_dialog_name_detail);
        TextView tvDateRange = dialogView.findViewById(R.id.tv_dialog_date_range);
        TextView tvWaitlist = dialogView.findViewById(R.id.tv_dialog_waitlist);
        TextView tvStatusChip = dialogView.findViewById(R.id.tv_dialog_status_chip);
        TextView tvDescription = dialogView.findViewById(R.id.tv_dialog_description);
        Button btnOpenDetails = dialogView.findViewById(R.id.btn_dialog_open_details);
        Button btnSecondary = dialogView.findViewById(R.id.btn_dialog_secondary);
        Button btnJoinWaitlist = dialogView.findViewById(R.id.btn_join_waitlist);

        tvAvatar.setText(UserEventUiUtils.getAvatarLetter(eventRecord.getEventName()));
        tvHeaderName.setText(eventRecord.getEventName());
        tvHeaderLocation.setText(eventRecord.getLocation());
        tvOrganizer.setText("Organizer: Loading...");
        tvDetailName.setText(eventRecord.getEventName());
        tvDateRange.setText(UserEventUiUtils.formatDateRange(eventRecord.getRegistrationStartDate(), eventRecord.getRegistrationEndDate()));
        tvWaitlist.setText(UserEventUiUtils.formatWaitlistSummary(eventRecord));
        tvDescription.setText(UserEventUiUtils.buildDescription(eventRecord));
        PosterLoader.loadInto(ivPoster, eventRecord.getPosterPath());

        String status = eventRecord.getEffectiveStatus();
        if (status.isEmpty()) {
            tvStatusChip.setVisibility(View.GONE);
        } else {
            tvStatusChip.setVisibility(View.VISIBLE);
            UserEventUiUtils.applyStatusChip(tvStatusChip, status, true);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialog.setCanceledOnTouchOutside(true);
        populateOrganizerName(tvOrganizer, eventRecord.getOrganizerId());

        btnShowQr.setOnClickListener(v ->
                showQrDialog(QrCodeUtils.buildPromotionalEventLink(eventRecord.getEventId())));
        btnOpenDetails.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(this, UserEventDetailsActivity.class);
            intent.putExtra("eventId", eventRecord.getEventId());
            startActivity(intent);
        });

        configureDialogActions(dialog, eventRecord, btnJoinWaitlist, btnSecondary);
        dialog.show();
    }

    private void populateOrganizerName(TextView organizerView, String organizerId) {
        if (organizerView == null) {
            return;
        }
        if (organizerId == null || organizerId.trim().isEmpty()) {
            organizerView.setText("Organizer: Organizer");
            return;
        }
        db.collection("users")
                .document(organizerId)
                .get()
                .addOnSuccessListener(snapshot ->
                        organizerView.setText("Organizer: "
                                + UserDocumentUtils.buildDisplayName(snapshot, "Organizer")))
                .addOnFailureListener(e -> organizerView.setText("Organizer: Organizer"));
    }

    /**
     * Configures dialog action buttons based on current status.
     *
     * @param dialog details dialog
     * @param eventRecord selected event record
     * @param btnJoinWaitlist primary action button
     * @param btnSecondary secondary action button
     */
    private void configureDialogActions(AlertDialog dialog,
                                        UserEventRecord eventRecord,
                                        Button btnJoinWaitlist,
                                        Button btnSecondary) {
        String status = eventRecord.getEffectiveStatus();
        btnSecondary.setVisibility(View.GONE);
        btnJoinWaitlist.setEnabled(true);

        if (UserEventRecord.STATUS_INVITED.equals(status)) {
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText("Decline");
            btnJoinWaitlist.setText("Accept");
            btnSecondary.setOnClickListener(v -> declineInvitation(eventRecord, dialog));
            btnJoinWaitlist.setOnClickListener(v -> acceptInvitation(eventRecord, dialog));
            return;
        }

        if (UserEventRecord.STATUS_ACCEPTED.equals(status)) {
            btnJoinWaitlist.setText("Accepted");
            btnJoinWaitlist.setEnabled(false);
            return;
        }

        if (UserEventRecord.STATUS_CO_ORGANIZER_PENDING.equals(status)) {
            btnJoinWaitlist.setText("Co-organizer Invite Pending");
            btnJoinWaitlist.setEnabled(false);
            return;
        }

        if (UserEventRecord.STATUS_CO_ORGANIZER.equals(status)) {
            btnJoinWaitlist.setText("Sign in as Organizer");
            btnJoinWaitlist.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
            return;
        }

        if (UserEventRecord.STATUS_REJECTED.equals(status)) {
            btnJoinWaitlist.setText("Rejected");
            btnJoinWaitlist.setEnabled(false);
            return;
        }

        if (UserEventRecord.STATUS_WAITLISTED.equals(status)) {
            btnJoinWaitlist.setText("Leave Waitlist");
            btnJoinWaitlist.setOnClickListener(v -> leaveWaitlist(eventRecord, dialog));
            return;
        }

        if (UserEventRecord.STATUS_WAITLIST_INVITED.equals(status)) {
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText("Reject");
            btnSecondary.setOnClickListener(v -> rejectPrivateWaitlistInvite(eventRecord, dialog));
            if (eventRecord.isWaitlistFull()) {
                btnJoinWaitlist.setText("Waitlist Full");
                btnJoinWaitlist.setEnabled(false);
                return;
            }
            btnJoinWaitlist.setText("Join the Waitlist");
            btnJoinWaitlist.setOnClickListener(v -> requestLocationAndJoinWaitlist(eventRecord, dialog));
            return;
        }

        if (eventRecord.isWaitlistFull()) {
            btnJoinWaitlist.setText("Waitlist Full");
            btnJoinWaitlist.setEnabled(false);
            return;
        }

        if (eventRecord.isRegistrationClosed()) {
            btnJoinWaitlist.setText("Registration Closed");
            btnJoinWaitlist.setEnabled(false);
            return;
        }

        if (eventRecord.isRegistrationNotStarted()) {
            btnJoinWaitlist.setText("Not Started");
            btnJoinWaitlist.setEnabled(false);
            return;
        }

        btnJoinWaitlist.setText("Join the Waitlist");
        btnJoinWaitlist.setOnClickListener(v -> requestLocationAndJoinWaitlist(eventRecord, dialog));
    }

    /**
     * Requests location permission if required before joining waitlist.
     *
     * @param eventRecord target event record
     * @param dialog details dialog
     */
    private void requestLocationAndJoinWaitlist(UserEventRecord eventRecord, AlertDialog dialog) {
        if (!eventRecord.isGeolocationRequired()) {
            joinWaitingList(eventRecord, dialog, null);
            return;
        }
        pendingJoinEventRecord = eventRecord;
        pendingJoinDialog = dialog;
        if (hasLocationPermission()) {
            fetchLocationAndJoinWaitlist(eventRecord, dialog);
            return;
        }

        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    /**
     * Reads current location and continues waitlist join flow.
     *
     * @param eventRecord target event record
     * @param dialog details dialog
     */
    private void fetchLocationAndJoinWaitlist(UserEventRecord eventRecord, AlertDialog dialog) {
        if (!hasLocationPermission()) {
            Toast.makeText(this, "Location permission is required to join the waitlist", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            joinWaitingList(eventRecord, dialog, TestingLocationPool.createRandomCountryLocation(this));
                            return;
                        }

                        CancellationTokenSource tokenSource = new CancellationTokenSource();
                        try {
                            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.getToken())
                                    .addOnSuccessListener(currentLocation -> {
                                        if (currentLocation == null) {
                                            Toast.makeText(this, "Unable to read location. Please enable location and try again.", Toast.LENGTH_SHORT).show();
                                            return;
                                        }
                                        joinWaitingList(eventRecord, dialog, TestingLocationPool.createRandomCountryLocation(this));
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(this, "Unable to read location. Please try again.", Toast.LENGTH_SHORT).show());
                        } catch (SecurityException securityException) {
                            Toast.makeText(this, "Location permission is required to join the waitlist", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Unable to read location. Please try again.", Toast.LENGTH_SHORT).show());
        } catch (SecurityException securityException) {
            Toast.makeText(this, "Location permission is required to join the waitlist", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * @return true when location permission is granted
     */
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Adds entrant to waitlist.
     *
     * @param eventRecord target event record
     * @param dialog details dialog
     * @param entrantLocation entrant location if available
     */
    private void joinWaitingList(UserEventRecord eventRecord, AlertDialog dialog, Location entrantLocation) {
        EntrantWaitlistManager.joinWaitlist(db, entrantId, eventRecord.getEventId(), entrantLocation)
                .addOnSuccessListener(result -> {
                    eventRecord.setWaitlistEntrantIds(result.getUpdatedWaitlistEntrantIds());
                    eventRecord.setHistoryStatus(UserEventRecord.STATUS_WAITLISTED);
                    NotificationHelper.markMatchingNotificationsAsConfirmed(
                            db,
                            entrantId,
                            eventRecord.getEventId(),
                            NotificationHelper.TYPE_PRIVATE_WAITLIST_INVITE
                    );
                    eventAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "Joined waiting list successfully", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadEventsAndHistory();
                })
                .addOnFailureListener(e -> {
                    String message = e.getMessage();
                    if (message == null || message.trim().isEmpty()) {
                        message = "Unable to update event status";
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Removes entrant from waitlist and history.
     *
     * @param eventRecord target event record
     * @param dialog details dialog
     */
    private void leaveWaitlist(UserEventRecord eventRecord, AlertDialog dialog) {
        updateWaitlistMembership(
                eventRecord,
                false,
                null,
                true,
                "Left waiting list",
                dialog,
                null
        );
    }

    /**
     * Declines a private waitlist invitation and removes private-event access.
     */
    private void rejectPrivateWaitlistInvite(UserEventRecord eventRecord, AlertDialog dialog) {
        EntrantWaitlistManager.declinePrivateWaitlistInvite(db, entrantId, eventRecord.getEventId())
                .addOnSuccessListener(unused ->
                        NotificationHelper.markMatchingNotificationsAsDeclined(
                                        db,
                                        entrantId,
                                        eventRecord.getEventId(),
                                        NotificationHelper.TYPE_PRIVATE_WAITLIST_INVITE
                                )
                                .addOnCompleteListener(task -> {
                                    eventRecord.setHistoryStatus("");
                                    eventAdapter.notifyDataSetChanged();
                                    Toast.makeText(this, "Private waitlist invitation declined", Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                    loadEventsAndHistory();
                                }))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to decline private waitlist invite", Toast.LENGTH_SHORT).show());
    }

    /**
     * Accepts invitation for an event.
     *
     * @param eventRecord target event record
     * @param dialog details dialog
     */
    private void acceptInvitation(UserEventRecord eventRecord, AlertDialog dialog) {
        db.collection("events").document(eventRecord.getEventId())
                .update(
                        "acceptedEntrantIds", FieldValue.arrayUnion(entrantId),
                        "declinedEntrantIds", FieldValue.arrayRemove(entrantId)
                )
                .addOnSuccessListener(unused ->
                        updateWaitlistMembership(
                                eventRecord,
                                false,
                                UserEventRecord.STATUS_ACCEPTED,
                                false,
                                "Invitation accepted",
                                dialog,
                                null
                        )
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to accept invitation", Toast.LENGTH_SHORT).show()
                );
    }

    /**
     * Declines invitation for an event.
     *
     * @param eventRecord target event record
     * @param dialog details dialog
     */
    private void declineInvitation(UserEventRecord eventRecord, AlertDialog dialog) {
        // Remove the entrant from selected/replacement pools and mark as declined.
        // Declined entrants are not re-added to waitlist and are no longer eligible for lottery.
        db.collection("events").document(eventRecord.getEventId())
                .update(
                        "selectedEntrantIds", FieldValue.arrayRemove(entrantId),
                        "replacementEntrantIds", FieldValue.arrayRemove(entrantId),
                        "declinedEntrantIds", FieldValue.arrayUnion(entrantId),
                        "acceptedEntrantIds", FieldValue.arrayRemove(entrantId)
                )
                .addOnSuccessListener(unused -> {
                    eventRecord.setHistoryStatus(UserEventRecord.STATUS_REJECTED);
                    upsertHistoryDocument(eventRecord, UserEventRecord.STATUS_REJECTED);
                    triggerAutomaticReplacementDraw(eventRecord.getEventId());
                    eventAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "Invitation declined", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadEventsAndHistory();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to decline invitation", Toast.LENGTH_SHORT).show()
                );
    }

    private void triggerAutomaticReplacementDraw(String eventId) {
        DocumentReference eventReference = db.collection("events").document(eventId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(eventReference);
            if (!snapshot.exists()) {
                return false;
            }

            Long lotteryCountValue = snapshot.getLong("lotteryCount");
            int lotteryCount = lotteryCountValue == null ? 0 : lotteryCountValue.intValue();
            if (lotteryCount <= 0) {
                return false;
            }

            List<String> waitlist = FirestoreFieldUtils.getStringList(snapshot, "waitlistEntrantIds");
            waitlist = waitlist == null ? new ArrayList<>() : new ArrayList<>(waitlist);
            List<String> selected = FirestoreFieldUtils.getStringList(snapshot, "selectedEntrantIds");
            selected = selected == null ? new ArrayList<>() : new ArrayList<>(selected);
            List<String> replacements = FirestoreFieldUtils.getStringList(snapshot, "replacementEntrantIds");
            replacements = replacements == null ? new ArrayList<>() : new ArrayList<>(replacements);
            List<String> declined = FirestoreFieldUtils.getStringList(snapshot, "declinedEntrantIds");
            declined = declined == null ? new ArrayList<>() : new ArrayList<>(declined);

            int vacancies = lotteryCount - selected.size();
            if (vacancies <= 0) {
                return false;
            }

            List<String> pool = new ArrayList<>(waitlist);
            pool.removeAll(declined);
            pool.removeAll(selected);
            pool.removeAll(replacements);
            if (pool.isEmpty()) {
                return false;
            }

            java.util.Collections.shuffle(pool);
            int drawCount = Math.min(vacancies, pool.size());
            List<String> drawn = new ArrayList<>(pool.subList(0, drawCount));

            selected.addAll(drawn);
            replacements.addAll(drawn);
            waitlist.removeAll(drawn);

            transaction.update(eventReference,
                    "selectedEntrantIds", selected,
                    "replacementEntrantIds", replacements,
                    "waitlistEntrantIds", waitlist,
                    "currentWaitlistCount", waitlist.size(),
                    "declinedEntrantIds", FieldValue.arrayRemove(drawn.toArray()));
            return true;
        });
    }

    /**
     * Updates waitlist membership and history in Firestore transaction.
     *
     * @param eventRecord target event record
     * @param addEntrant true to add entrant, false to remove
     * @param newStatus new history status
     * @param deleteHistory true to delete history record
     * @param successMessage success toast message
     * @param dialog details dialog
     * @param entrantLocation entrant location if available
     */
    private void updateWaitlistMembership(UserEventRecord eventRecord,
                                          boolean addEntrant,
                                          String newStatus,
                                          boolean deleteHistory,
                                          String successMessage,
                                          AlertDialog dialog,
                                          Location entrantLocation) {
        DocumentReference eventReference = db.collection("events").document(eventRecord.getEventId());

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(eventReference);
            if (!snapshot.exists()) {
                throw new IllegalStateException("Event not found");
            }

            List<String> waitlistEntrants = FirestoreFieldUtils.getStringList(snapshot, "waitlistEntrantIds");
            if (waitlistEntrants == null) {
                waitlistEntrants = new ArrayList<>();
            } else {
                waitlistEntrants = new ArrayList<>(waitlistEntrants);
            }

            Long maxWaitlistValue = snapshot.getLong("maxWaitlist");
            int maxWaitlist = maxWaitlistValue == null ? 0 : maxWaitlistValue.intValue();
            Boolean geolocationRequiredValue = snapshot.getBoolean("geolocationRequired");
            boolean geolocationRequired = geolocationRequiredValue == null || geolocationRequiredValue;
            Object existingEntrantLocation = snapshot.get("waitlistEntrantLocations." + entrantId);
            List<String> pendingCoOrganizers = FirestoreFieldUtils.getStringList(snapshot, "pendingCoOrganizerIds");
            List<String> coOrganizers = FirestoreFieldUtils.getStringList(snapshot, "coOrganizerIds");
            if (addEntrant) {
                if (pendingCoOrganizers.contains(entrantId) || coOrganizers.contains(entrantId)) {
                    throw new IllegalStateException("Co-organizers cannot join the entrant pool for this event");
                }
                if (geolocationRequired && entrantLocation == null && !hasStoredEntrantLocation(existingEntrantLocation)) {
                    throw new IllegalStateException("Location is required to join this waitlist");
                }
                if (waitlistEntrants.contains(entrantId)) {
                    throw new IllegalStateException("You already joined this waiting list");
                }
                if (maxWaitlist > 0 && waitlistEntrants.size() >= maxWaitlist) {
                    throw new IllegalStateException("This waiting list is full");
                }
                waitlistEntrants.add(entrantId);
            } else {
                waitlistEntrants.remove(entrantId);
            }

            boolean shouldStoreLocation = addEntrant && entrantLocation != null;

            if (shouldStoreLocation) {
                Map<String, Object> locationHistory = buildEntrantLocationHistory(existingEntrantLocation, entrantLocation);
                transaction.update(eventReference,
                        "waitlistEntrantIds", waitlistEntrants,
                        "currentWaitlistCount", waitlistEntrants.size(),
                        "waitlistEntrantLocations." + entrantId,
                        locationHistory);
            } else {
                transaction.update(eventReference,
                        "waitlistEntrantIds", waitlistEntrants,
                        "currentWaitlistCount", waitlistEntrants.size());
            }
            return waitlistEntrants;
        }).addOnSuccessListener(updatedWaitlist -> {
            eventRecord.setWaitlistEntrantIds(new ArrayList<>(updatedWaitlist));
            if (deleteHistory) {
                deleteHistoryDocument(eventRecord.getEventId());
                eventRecord.setHistoryStatus("");
            } else if (newStatus != null) {
                eventRecord.setHistoryStatus(newStatus);
                upsertHistoryDocument(eventRecord, newStatus);
            }

            eventAdapter.notifyDataSetChanged();
            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            loadEventsAndHistory();
        }).addOnFailureListener(e -> {
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = "Unable to update event status";
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private boolean hasStoredEntrantLocation(Object existingEntrantLocation) {
        if (existingEntrantLocation instanceof GeoPoint) {
            return true;
        }
        if (existingEntrantLocation instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) existingEntrantLocation;
            return !map.isEmpty();
        }
        return false;
    }

    private Map<String, Object> buildEntrantLocationHistory(Object existingEntrantLocation, Location newLocation) {
        Map<String, Object> history = new HashMap<>();
        if (existingEntrantLocation instanceof GeoPoint) {
            history.put("1st location", existingEntrantLocation);
        } else if (existingEntrantLocation instanceof Map<?, ?>) {
            Map<?, ?> raw = (Map<?, ?>) existingEntrantLocation;
            if (isLegacyPointMap(raw)) {
                GeoPoint legacyPoint = mapToGeoPoint(raw);
                if (legacyPoint != null) {
                    history.put("1st location", legacyPoint);
                }
            } else {
                for (Map.Entry<?, ?> entry : raw.entrySet()) {
                    String key = entry.getKey() == null ? "" : entry.getKey().toString().trim();
                    if (!key.isEmpty()) {
                        history.put(key, entry.getValue());
                    }
                }
            }
        }

        int next = maxOrdinal(history.keySet()) + 1;
        history.put(formatOrdinal(next) + " location", new GeoPoint(newLocation.getLatitude(), newLocation.getLongitude()));
        return history;
    }

    private boolean isLegacyPointMap(Map<?, ?> raw) {
        return (raw.containsKey("lat") && raw.containsKey("lng"))
                || (raw.containsKey("latitude") && raw.containsKey("longitude"));
    }

    private GeoPoint mapToGeoPoint(Map<?, ?> raw) {
        Object lat = raw.get("lat");
        Object lng = raw.get("lng");
        if (!(lat instanceof Number) || !(lng instanceof Number)) {
            lat = raw.get("latitude");
            lng = raw.get("longitude");
        }
        if (lat instanceof Number && lng instanceof Number) {
            return new GeoPoint(((Number) lat).doubleValue(), ((Number) lng).doubleValue());
        }
        return null;
    }

    private int maxOrdinal(Set<String> keys) {
        int max = 0;
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            int spaceIndex = normalized.indexOf(' ');
            String firstToken = spaceIndex >= 0 ? normalized.substring(0, spaceIndex) : normalized;
            int number = parseLeadingInt(firstToken);
            if (number > max) {
                max = number;
            }
        }
        return max;
    }

    private int parseLeadingInt(String value) {
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(value.substring(0, end));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String formatOrdinal(int number) {
        int mod100 = number % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return number + "th";
        }
        switch (number % 10) {
            case 1:
                return number + "st";
            case 2:
                return number + "nd";
            case 3:
                return number + "rd";
            default:
                return number + "th";
        }
    }

    /**
     * Upserts a history document for the given event and status.
     *
     * @param eventRecord source event record
     * @param status status to persist
     */
    private void upsertHistoryDocument(UserEventRecord eventRecord, String status) {
        db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(eventRecord.getEventId())
                .set(UserEventHistoryHelper.buildHistoryData(eventRecord, status));
    }

    /**
     * Deletes one history document by event id.
     *
     * @param eventId event identifier
     */
    private void deleteHistoryDocument(String eventId) {
        db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(eventId)
                .delete();
    }

    /**
     * RecyclerView adapter for entrant event list rows.
     */
    private static class UserEventAdapter extends RecyclerView.Adapter<UserEventAdapter.UserEventViewHolder> {
        private final List<UserEventRecord> eventItems;
        private final OnEventClickListener listener;

        /**
         * Creates an event adapter.
         *
         * @param eventItems backing event list
         * @param listener click listener
         */
        private UserEventAdapter(List<UserEventRecord> eventItems, OnEventClickListener listener) {
            this.eventItems = eventItems;
            this.listener = listener;
        }

        /**
         * Inflates one event row.
         *
         * @param parent parent RecyclerView
         * @param viewType view type id
         * @return created view holder
         */
        @NonNull
        @Override
        public UserEventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
            return new UserEventViewHolder(view);
        }

        /**
         * Binds one event row.
         *
         * @param holder row holder
         * @param position adapter position
         */
        @Override
        public void onBindViewHolder(@NonNull UserEventViewHolder holder, int position) {
            UserEventRecord eventItem = eventItems.get(position);
            holder.tvEventName.setText(eventItem.getEventName());

            if (eventItem.getEffectiveStatus().isEmpty()) {
                if (eventItem.isWaitlistFull()) {
                    UserEventUiUtils.applyStatusChip(holder.tvEventStatus, UserEventUiUtils.STATUS_FULL, false);
                } else if (eventItem.isRegistrationClosed()) {
                    UserEventUiUtils.applyStatusChip(holder.tvEventStatus, UserEventUiUtils.STATUS_CLOSED, false);
                } else if (eventItem.isRegistrationNotStarted()) {
                    UserEventUiUtils.applyStatusChip(holder.tvEventStatus, UserEventUiUtils.STATUS_NOT_STARTED, false);
                } else {
                    UserEventUiUtils.applyStatusChip(holder.tvEventStatus, UserEventUiUtils.STATUS_OPEN, false);
                }
            } else {
                UserEventUiUtils.applyStatusChip(holder.tvEventStatus, eventItem.getEffectiveStatus(), false);
            }

            holder.itemView.setOnClickListener(v -> listener.onEventClick(eventItem));
        }

        /**
         * @return number of event rows
         */
        @Override
        public int getItemCount() {
            return eventItems.size();
        }

        /**
         * ViewHolder for event row rendering.
         */
        private static class UserEventViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvEventName;
            private final TextView tvEventStatus;

            /**
             * Creates a row holder and binds row subviews.
             *
             * @param itemView row root view
             */
            private UserEventViewHolder(@NonNull View itemView) {
                super(itemView);
                tvEventName = itemView.findViewById(R.id.tv_event_name);
                tvEventStatus = itemView.findViewById(R.id.tv_event_status);
            }
        }
    }

    /**
     * Listener invoked when an event list row is tapped.
     */
    private interface OnEventClickListener {
        /**
         * Handles event row click.
         *
         * @param eventRecord selected event record
         */
        void onEventClick(UserEventRecord eventRecord);
    }

    private void showQrDialog(String payload) {
        try {
            int qrSize = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    280,
                    getResources().getDisplayMetrics()
            );
            Bitmap qrBitmap = QrCodeUtils.generateQrBitmap(payload, qrSize);
            ImageView qrImage = new ImageView(this);
            qrImage.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            qrImage.setAdjustViewBounds(true);
            qrImage.setImageBitmap(qrBitmap);

            TextView linkView = new TextView(this);
            linkView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            linkView.setText(payload);
            linkView.setTextColor(Color.BLUE);
            linkView.setPaintFlags(linkView.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            linkView.setOnClickListener(v -> {
                Intent openIntent = new Intent(this, UserEventDetailsActivity.class);
                String eventIdFromPayload = payload.substring("https://wecook.app/event/".length());
                openIntent.putExtra("eventId", eventIdFromPayload);
                startActivity(openIntent);
            });
            linkView.setOnLongClickListener(v -> {
                ClipboardManager clipboard =
                        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Event link", payload));
                    Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            int topPadding = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
            linkView.setPadding(0, topPadding, 0, 0);

            LinearLayout dialogContent = new LinearLayout(this);
            dialogContent.setOrientation(LinearLayout.VERTICAL);
            dialogContent.setGravity(Gravity.CENTER_HORIZONTAL);
            int padding = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
            dialogContent.setPadding(padding, padding, padding, padding);
            dialogContent.addView(qrImage);
            dialogContent.addView(linkView);

            new AlertDialog.Builder(this)
                    .setTitle("Event QR Code")
                    .setView(dialogContent)
                    .setPositiveButton("Close", null)
                    .show();
        } catch (WriterException e) {
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show();
        }
    }
}
