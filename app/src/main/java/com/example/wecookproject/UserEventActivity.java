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
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
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
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entrant event list screen with waitlist and invitation actions.
 */
public class UserEventActivity extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<UserEventRecord> eventList = new ArrayList<>();

    private RecyclerView rvEvents;
    private TextView tvEmptyState;
    private UserEventAdapter eventAdapter;
    private String entrantId;
    private BottomNavigationView bottomNav;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private UserEventRecord pendingJoinEventRecord;
    private AlertDialog pendingJoinDialog;

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

        rvEvents.setLayoutManager(new LinearLayoutManager(this));
        eventAdapter = new UserEventAdapter(eventList, this::showEventDetailsDialog);
        rvEvents.setAdapter(eventAdapter);

        setupBottomNav();
        loadEventsAndHistory();
    }

    /**
     * Reloads events when returning to foreground.
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadEventsAndHistory();
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
                Toast.makeText(this, "Scan (coming soon)", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.nav_history) {
                Intent intent = new Intent(UserEventActivity.this, UserHistoryActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (itemId == R.id.nav_profile) {
                Intent intent = new Intent(UserEventActivity.this, UserProfileActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
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
                .get()
                .addOnSuccessListener(historySnapshots -> {
                    Map<String, String> historyStatuses = new HashMap<>();
                    for (QueryDocumentSnapshot historyDocument : historySnapshots) {
                        String eventId = historyDocument.getString("eventId");
                        String status = historyDocument.getString("status");
                        if (eventId != null && status != null) {
                            historyStatuses.put(eventId, status);
                        }
                    }
                    loadEvents(historyStatuses);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show();
                    loadEvents(new HashMap<>());
                });
    }

    /**
     * Loads all events and merges history status for display.
     *
     * @param historyStatuses map of eventId to history status
     */
    private void loadEvents(Map<String, String> historyStatuses) {
        db.collection("events")
                .get()
                .addOnSuccessListener(eventSnapshots -> {
                    eventList.clear();
                    for (QueryDocumentSnapshot document : eventSnapshots) {
                        if (!document.contains("waitlistEntrantIds")) {
                            initializeWaitingList(document.getReference());
                        }

                        UserEventRecord eventRecord = UserEventRecord.fromEventSnapshot(
                                document,
                                entrantId,
                                historyStatuses.get(document.getId())
                        );

                        if (eventRecord.isEntrantOnWaitlist() && historyStatuses.get(document.getId()) == null) {
                            upsertHistoryDocument(eventRecord, UserEventRecord.STATUS_WAITLISTED);
                        }

                        eventList.add(eventRecord);
                    }

                    eventAdapter.notifyDataSetChanged();
                    updateEmptyState();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load events", Toast.LENGTH_SHORT).show());
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
            tvEmptyState.setVisibility(View.VISIBLE);
            rvEvents.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            rvEvents.setVisibility(View.VISIBLE);
        }
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
        Button btnShowQr = dialogView.findViewById(R.id.btn_dialog_show_qr);
        ImageView ivPoster = dialogView.findViewById(R.id.iv_dialog_poster);
        TextView tvDetailName = dialogView.findViewById(R.id.tv_dialog_name_detail);
        TextView tvDateRange = dialogView.findViewById(R.id.tv_dialog_date_range);
        TextView tvWaitlist = dialogView.findViewById(R.id.tv_dialog_waitlist);
        TextView tvStatusChip = dialogView.findViewById(R.id.tv_dialog_status_chip);
        TextView tvDescription = dialogView.findViewById(R.id.tv_dialog_description);
        Button btnSecondary = dialogView.findViewById(R.id.btn_dialog_secondary);
        Button btnJoinWaitlist = dialogView.findViewById(R.id.btn_join_waitlist);

        tvAvatar.setText(UserEventUiUtils.getAvatarLetter(eventRecord.getEventName()));
        tvHeaderName.setText(eventRecord.getEventName());
        tvHeaderLocation.setText(eventRecord.getLocation());
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

        btnShowQr.setOnClickListener(v ->
                showQrDialog(QrCodeUtils.buildPromotionalEventLink(eventRecord.getEventId())));

        configureDialogActions(dialog, eventRecord, btnJoinWaitlist, btnSecondary);
        dialog.show();
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

        if (eventRecord.isWaitlistFull()) {
            btnJoinWaitlist.setText("Waitlist Full");
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
                            joinWaitingList(eventRecord, dialog, location);
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
                                        joinWaitingList(eventRecord, dialog, currentLocation);
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
        updateWaitlistMembership(
                eventRecord,
                true,
                UserEventRecord.STATUS_WAITLISTED,
                false,
                "Joined waiting list successfully",
                dialog,
                entrantLocation
        );
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
     * Accepts invitation for an event.
     *
     * @param eventRecord target event record
     * @param dialog details dialog
     */
    private void acceptInvitation(UserEventRecord eventRecord, AlertDialog dialog) {
        updateWaitlistMembership(
                eventRecord,
                false,
                UserEventRecord.STATUS_ACCEPTED,
                false,
                "Invitation accepted",
                dialog,
                null
        );
    }

    /**
     * Declines invitation for an event.
     *
     * @param eventRecord target event record
     * @param dialog details dialog
     */
    private void declineInvitation(UserEventRecord eventRecord, AlertDialog dialog) {
        updateWaitlistMembership(
                eventRecord,
                false,
                UserEventRecord.STATUS_REJECTED,
                false,
                "Invitation declined",
                dialog,
                null
        );
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

    /**
     * Upserts a history document for the given event and status.
     *
     * @param eventRecord source event record
     * @param status status to persist
     */
    private void upsertHistoryDocument(UserEventRecord eventRecord, String status) {
        Map<String, Object> historyData = new HashMap<>();
        historyData.put("eventId", eventRecord.getEventId());
        historyData.put("eventName", eventRecord.getEventName());
        historyData.put("location", eventRecord.getLocation());
        historyData.put("organizerId", eventRecord.getOrganizerId());
        historyData.put("posterUrl", eventRecord.getPosterPath());
        historyData.put("registrationStartDate", eventRecord.getRegistrationStartDate());
        historyData.put("registrationEndDate", eventRecord.getRegistrationEndDate());
        historyData.put("description", eventRecord.getDescription());
        historyData.put("status", status);
        historyData.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(eventRecord.getEventId())
                .set(historyData);
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
                UserEventUiUtils.applyStatusChip(
                        holder.tvEventStatus,
                        eventItem.isWaitlistFull() ? UserEventUiUtils.STATUS_FULL : UserEventUiUtils.STATUS_OPEN,
                        false
                );
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
                Intent openIntent = new Intent(this, PublicEventLandingActivity.class);
                openIntent.setData(Uri.parse(payload));
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
