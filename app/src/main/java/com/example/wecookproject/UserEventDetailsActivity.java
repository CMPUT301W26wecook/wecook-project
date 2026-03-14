package com.example.wecookproject;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Displays detailed event information for entrants and manages status actions.
 */
public class UserEventDetailsActivity extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String entrantId;
    private String eventId;
    private UserEventRecord currentEvent;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    private TextView tvAvatar;
    private TextView tvHeaderName;
    private TextView tvHeaderLocation;
    private ImageView ivPoster;
    private TextView tvEventName;
    private TextView tvDateRange;
    private TextView tvWaitlist;
    private TextView tvStatus;
    private TextView tvDescription;
    private Button btnSecondary;
    private Button btnPrimary;

    /**
     * Initializes details UI, navigation, and event loading.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_event_details);

        entrantId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        eventId = getIntent().getStringExtra("eventId");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                            || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (granted) {
                        fetchLocationAndJoinWaitlist();
                    } else {
                        Toast.makeText(this, "Location permission is required to join the waitlist", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "Missing event details", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvAvatar = findViewById(R.id.tv_detail_avatar);
        tvHeaderName = findViewById(R.id.tv_detail_event_name_header);
        tvHeaderLocation = findViewById(R.id.tv_detail_location_header);
        ivPoster = findViewById(R.id.iv_detail_poster);
        tvEventName = findViewById(R.id.tv_detail_event_name);
        tvDateRange = findViewById(R.id.tv_detail_date_range);
        tvWaitlist = findViewById(R.id.tv_detail_waitlist);
        tvStatus = findViewById(R.id.tv_detail_status_chip);
        tvDescription = findViewById(R.id.tv_detail_description);
        btnSecondary = findViewById(R.id.btn_detail_secondary);
        btnPrimary = findViewById(R.id.btn_detail_primary);

        findViewById(R.id.iv_detail_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_detail_show_qr).setOnClickListener(v ->
                Toast.makeText(this, "QR code preview coming soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btn_view_lottery_criteria).setOnClickListener(v ->
                startActivity(new Intent(this, UserLotteryCriteriaActivity.class)));

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_events);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_events) {
                Intent intent = new Intent(this, UserEventActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
                return true;
            }
            if (itemId == R.id.nav_history) {
                startActivity(new Intent(this, UserHistoryActivity.class));
                return true;
            }
            if (itemId == R.id.nav_scan) {
                Toast.makeText(this, "Scan (coming soon)", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (itemId == R.id.nav_profile) {
                startActivity(new Intent(this, UserProfileActivity.class));
                return true;
            }
            return true;
        });

        loadEvent();
    }

    /**
     * Reloads event details when returning to the foreground.
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadEvent();
    }

    /**
     * Loads history status and event document, then binds UI.
     */
    private void loadEvent() {
        db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(eventId)
                .get()
                .addOnSuccessListener(historySnapshot -> {
                    String historyStatus = historySnapshot.getString("status");
                    db.collection("events")
                            .document(eventId)
                            .get()
                            .addOnSuccessListener(eventSnapshot -> bindEvent(eventSnapshot, historyStatus))
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show());
    }

    /**
     * Binds event content to view fields.
     *
     * @param eventSnapshot event document snapshot
     * @param historyStatus entrant history status
     */
    private void bindEvent(DocumentSnapshot eventSnapshot, String historyStatus) {
        if (!eventSnapshot.exists()) {
            Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentEvent = UserEventRecord.fromEventSnapshot(eventSnapshot, entrantId, historyStatus);

        tvAvatar.setText(UserEventUiUtils.getAvatarLetter(currentEvent.getEventName()));
        tvHeaderName.setText(currentEvent.getEventName());
        tvHeaderLocation.setText(currentEvent.getLocation());
        tvEventName.setText(currentEvent.getEventName());
        tvDateRange.setText(UserEventUiUtils.formatDateRange(currentEvent.getRegistrationStartDate(), currentEvent.getRegistrationEndDate()));
        tvWaitlist.setText(UserEventUiUtils.formatWaitlistSummary(currentEvent));
        tvDescription.setText(UserEventUiUtils.buildDescription(currentEvent));
        PosterLoader.loadInto(ivPoster, currentEvent.getPosterPath());

        if (currentEvent.getEffectiveStatus().isEmpty()) {
            tvStatus.setVisibility(View.GONE);
        } else {
            tvStatus.setVisibility(View.VISIBLE);
            UserEventUiUtils.applyStatusChip(tvStatus, currentEvent.getEffectiveStatus(), true);
        }

        configureActionButtons();
    }

    /**
     * Configures action buttons based on effective entrant status.
     */
    private void configureActionButtons() {
        String status = currentEvent.getEffectiveStatus();
        btnSecondary.setVisibility(View.GONE);
        btnPrimary.setEnabled(true);

        if (UserEventRecord.STATUS_INVITED.equals(status)) {
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText("Decline");
            btnPrimary.setText("Accept");
            btnSecondary.setOnClickListener(v -> declineInvitation());
            btnPrimary.setOnClickListener(v -> acceptInvitation());
            return;
        }

        if (UserEventRecord.STATUS_ACCEPTED.equals(status)) {
            btnPrimary.setText("Accepted");
            btnPrimary.setEnabled(false);
            return;
        }

        if (UserEventRecord.STATUS_REJECTED.equals(status)) {
            btnPrimary.setText("Rejected");
            btnPrimary.setEnabled(false);
            return;
        }

        if (UserEventRecord.STATUS_WAITLISTED.equals(status)) {
            btnPrimary.setText("Leave Waitlist");
            btnPrimary.setOnClickListener(v -> leaveWaitlist());
            return;
        }

        if (currentEvent.isWaitlistFull()) {
            btnPrimary.setText("Waitlist Full");
            btnPrimary.setEnabled(false);
            return;
        }

        btnPrimary.setText("Join the Waitlist");
        btnPrimary.setOnClickListener(v -> requestLocationAndJoinWaitlist());
    }

    /**
     * Requests location permission when needed and starts join flow.
     */
    private void requestLocationAndJoinWaitlist() {
        if (!currentEvent.isGeolocationRequired()) {
            joinWaitlist(null);
            return;
        }
        if (hasLocationPermission()) {
            fetchLocationAndJoinWaitlist();
            return;
        }
        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    /**
     * Reads current location and proceeds with waitlist join.
     */
    private void fetchLocationAndJoinWaitlist() {
        if (!hasLocationPermission()) {
            Toast.makeText(this, "Location permission is required to join the waitlist", Toast.LENGTH_SHORT).show();
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        joinWaitlist(location);
                        return;
                    }

                    CancellationTokenSource tokenSource = new CancellationTokenSource();
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.getToken())
                            .addOnSuccessListener(currentLocation -> {
                                if (currentLocation == null) {
                                    Toast.makeText(this, "Unable to read location. Please enable location and try again.", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                joinWaitlist(currentLocation);
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Unable to read location. Please try again.", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Unable to read location. Please try again.", Toast.LENGTH_SHORT).show());
    }

    /**
     * @return true when coarse or fine location permission is granted
     */
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Adds entrant to waitlist.
     *
     * @param entrantLocation entrant location, if available
     */
    private void joinWaitlist(Location entrantLocation) {
        updateWaitlistMembership(true, UserEventRecord.STATUS_WAITLISTED, false, "Joined waiting list successfully", entrantLocation);
    }

    /**
     * Removes entrant from waitlist and history.
     */
    private void leaveWaitlist() {
        updateWaitlistMembership(false, null, true, "Left waiting list", null);
    }

    /**
     * Accepts an invitation.
     */
    private void acceptInvitation() {
        updateWaitlistMembership(false, UserEventRecord.STATUS_ACCEPTED, false, "Invitation accepted", null);
    }

    /**
     * Declines an invitation.
     */
    private void declineInvitation() {
        updateWaitlistMembership(false, UserEventRecord.STATUS_REJECTED, false, "Invitation declined", null);
    }

    /**
     * Updates waitlist membership and history status in a transaction.
     *
     * @param addEntrant true to add entrant, false to remove
     * @param newStatus new history status to persist
     * @param deleteHistory true to delete history item
     * @param successMessage toast message for success
     * @param entrantLocation entrant location when required
     */
    private void updateWaitlistMembership(boolean addEntrant,
                                          String newStatus,
                                          boolean deleteHistory,
                                          String successMessage,
                                          Location entrantLocation) {
        DocumentReference eventReference = db.collection("events").document(eventId);

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(eventReference);
            if (!snapshot.exists()) {
                throw new IllegalStateException("Event not found");
            }

            @SuppressWarnings("unchecked")
            List<String> waitlistEntrants = (List<String>) snapshot.get("waitlistEntrantIds");
            if (waitlistEntrants == null) {
                waitlistEntrants = new ArrayList<>();
            } else {
                waitlistEntrants = new ArrayList<>(waitlistEntrants);
            }

            Long maxWaitlistValue = snapshot.getLong("maxWaitlist");
            int maxWaitlist = maxWaitlistValue == null ? 0 : maxWaitlistValue.intValue();
            Boolean geolocationRequiredValue = snapshot.getBoolean("geolocationRequired");
            boolean geolocationRequired = geolocationRequiredValue == null || geolocationRequiredValue;

            if (addEntrant) {
                if (geolocationRequired && entrantLocation == null) {
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

            if (addEntrant && entrantLocation != null) {
                transaction.update(eventReference,
                        "waitlistEntrantIds", waitlistEntrants,
                        "currentWaitlistCount", waitlistEntrants.size(),
                        "waitlistEntrantLocations." + entrantId,
                        new GeoPoint(entrantLocation.getLatitude(), entrantLocation.getLongitude()));
            } else {
                transaction.update(eventReference,
                        "waitlistEntrantIds", waitlistEntrants,
                        "currentWaitlistCount", waitlistEntrants.size(),
                        "waitlistEntrantLocations." + entrantId, FieldValue.delete());
            }
            return waitlistEntrants;
        }).addOnSuccessListener(updatedWaitlist -> {
            currentEvent.setWaitlistEntrantIds(updatedWaitlist);
            if (deleteHistory) {
                deleteHistoryDocument();
                currentEvent.setHistoryStatus("");
            } else if (newStatus != null) {
                currentEvent.setHistoryStatus(newStatus);
                upsertHistoryDocument(newStatus);
            }

            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
            loadEvent();
        }).addOnFailureListener(e -> {
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = "Unable to update event status";
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Upserts event history entry for current entrant.
     *
     * @param status status to store
     */
    private void upsertHistoryDocument(String status) {
        Map<String, Object> historyData = new HashMap<>();
        historyData.put("eventId", currentEvent.getEventId());
        historyData.put("eventName", currentEvent.getEventName());
        historyData.put("location", currentEvent.getLocation());
        historyData.put("organizerId", currentEvent.getOrganizerId());
        historyData.put("posterPath", currentEvent.getPosterPath());
        historyData.put("registrationStartDate", currentEvent.getRegistrationStartDate());
        historyData.put("registrationEndDate", currentEvent.getRegistrationEndDate());
        historyData.put("description", currentEvent.getDescription());
        historyData.put("status", status);
        historyData.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(currentEvent.getEventId())
                .set(historyData);
    }

    /**
     * Deletes current event history entry.
     */
    private void deleteHistoryDocument() {
        db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(currentEvent.getEventId())
                .delete();
    }
}
