package com.example.wecookproject;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.content.pm.PackageManager;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
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
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.zxing.WriterException;

import com.example.wecookproject.model.EventComment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private TextView tvOrganizer;
    private TextView tvStatus;
    private TextView tvDescription;
    private EditText etComment;
    private TextView tvCommentsEmpty;
    private LinearLayout commentsContainer;
    private ListenerRegistration commentsListener;
    private Button btnSecondary;
    private Button btnPrimary;
    private Button btnPostComment;
    private String entrantDisplayName;

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
        tvOrganizer = findViewById(R.id.tv_detail_organizer);
        tvStatus = findViewById(R.id.tv_detail_status_chip);
        tvDescription = findViewById(R.id.tv_detail_description);
        TextView tvAvailability = findViewById(R.id.tv_detail_event_availability);
        etComment = findViewById(R.id.et_event_comment);
        tvCommentsEmpty = findViewById(R.id.tv_comments_empty);
        commentsContainer = findViewById(R.id.layout_comments_container);
        btnSecondary = findViewById(R.id.btn_detail_secondary);
        btnPrimary = findViewById(R.id.btn_detail_primary);
        btnPostComment = findViewById(R.id.btn_post_comment);

        findViewById(R.id.iv_detail_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_detail_show_qr).setOnClickListener(v -> {
            if (eventId == null || eventId.trim().isEmpty()) {
                Toast.makeText(this, "Missing event details", Toast.LENGTH_SHORT).show();
                return;
            }
            showQrDialog(QrCodeUtils.buildPromotionalEventLink(eventId));
        });
        findViewById(R.id.btn_view_lottery_criteria).setOnClickListener(v ->
                startActivity(new Intent(this, UserLotteryCriteriaActivity.class)));
        btnPostComment.setOnClickListener(v -> submitComment());

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

        loadEntrantProfile();
        loadEvent();
        observeComments();
    }

    /**
     * Reloads event details when returning to the foreground.
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadEvent();

        // loadEvent() should contains eventId
        TextView tvAvailability = findViewById(R.id.tv_detail_event_availability);

        if (eventId != null) {
            db.collection("events").document(eventId).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    com.example.wecookproject.model.Event event =
                            documentSnapshot.toObject(com.example.wecookproject.model.Event.class);
                    if (event == null) {
                        return;
                    }

                    int waitlistCount = FirestoreFieldUtils
                            .getStringList(documentSnapshot, "waitlistEntrantIds")
                            .size();
                    int maxWaitlist = event.getMaxWaitlist();
                    java.util.List<String> selectedEntrants =
                            FirestoreFieldUtils.getStringList(documentSnapshot, "selectedEntrantIds");
                    int finalCount = selectedEntrants.size();
                    int capacity = event.getCapacity();

                    boolean available = (waitlistCount < maxWaitlist) && (finalCount < capacity);
                    if (available) {
                        tvAvailability.setText("Availability: Open");
                        tvAvailability.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                    } else {
                        tvAvailability.setText("Availability: Full");
                        tvAvailability.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                    }
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (commentsListener != null) {
            commentsListener.remove();
            commentsListener = null;
        }
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
                            .addOnSuccessListener(eventSnapshot ->
                                    bindEvent(eventSnapshot, historyStatus, historySnapshot.exists()))
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show());
    }

    /**
     * Loads the entrant profile so new comments carry a readable author name.
     */
    private void loadEntrantProfile() {
        db.collection("users")
                .document(entrantId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    entrantDisplayName = UserDocumentUtils.buildDisplayName(snapshot, entrantId);
                })
                .addOnFailureListener(e -> entrantDisplayName = entrantId);
    }

    /**
     * Binds event content to view fields.
     *
     * @param eventSnapshot event document snapshot
     * @param historyStatus entrant history status
     */
    private void bindEvent(DocumentSnapshot eventSnapshot, String historyStatus, boolean hasHistory) {
        if (!eventSnapshot.exists()) {
            Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentEvent = UserEventRecord.fromEventSnapshot(eventSnapshot, entrantId, historyStatus);

        List<String> selectedEntrantIds = FirestoreFieldUtils.getStringList(eventSnapshot, "selectedEntrantIds");
        List<String> privateInviteeIds = FirestoreFieldUtils.getStringList(
                eventSnapshot,
                EntrantWaitlistManager.FIELD_PRIVATE_WAITLIST_INVITEE_IDS
        );
        String visibilityTag = getSafeTrimmedString(eventSnapshot, "visibilityTag");
        boolean hasPrivateAccess = hasHistory
                || currentEvent.isEntrantOnWaitlist()
                || selectedEntrantIds.contains(entrantId)
                || privateInviteeIds.contains(entrantId);
        if ("private".equalsIgnoreCase(visibilityTag) && !hasPrivateAccess) {
            Toast.makeText(this, "This private event is no longer available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // If the organizer has selected this entrant in the lottery, promote status to "invited"
        // unless they have already accepted.
        boolean isSelected = selectedEntrantIds != null && selectedEntrantIds.contains(entrantId);
        String effectiveStatus = currentEvent.getEffectiveStatus();
        boolean isAccepted = UserEventRecord.STATUS_ACCEPTED.equals(effectiveStatus);
        if (isSelected && !isAccepted) {
            currentEvent.setHistoryStatus(UserEventRecord.STATUS_INVITED);
            upsertHistoryDocument(UserEventRecord.STATUS_INVITED);
        }

        tvAvatar.setText(UserEventUiUtils.getAvatarLetter(currentEvent.getEventName()));
        tvHeaderName.setText(currentEvent.getEventName());
        tvHeaderLocation.setText(currentEvent.getLocation());
        tvEventName.setText(currentEvent.getEventName());
        tvDateRange.setText(UserEventUiUtils.formatDateRange(currentEvent.getRegistrationStartDate(), currentEvent.getRegistrationEndDate()));
        tvWaitlist.setText(UserEventUiUtils.formatWaitlistSummary(currentEvent));
        tvOrganizer.setText("Organizer: Loading...");
        tvDescription.setText(UserEventUiUtils.buildDescription(currentEvent));
        PosterLoader.loadInto(ivPoster, currentEvent.getPosterPath());
        loadOrganizerName(currentEvent.getOrganizerId());

        if (currentEvent.getEffectiveStatus().isEmpty()) {
            tvStatus.setVisibility(View.GONE);
        } else {
            tvStatus.setVisibility(View.VISIBLE);
            UserEventUiUtils.applyStatusChip(tvStatus, currentEvent.getEffectiveStatus(), true);
        }

        configureActionButtons();
    }

    private void loadOrganizerName(String organizerId) {
        if (tvOrganizer == null) {
            return;
        }
        if (organizerId == null || organizerId.trim().isEmpty()) {
            tvOrganizer.setText("Organizer: Organizer");
            return;
        }
        db.collection("users")
                .document(organizerId)
                .get()
                .addOnSuccessListener(snapshot ->
                        tvOrganizer.setText("Organizer: "
                                + UserDocumentUtils.buildDisplayName(snapshot, "Organizer")))
                .addOnFailureListener(e -> tvOrganizer.setText("Organizer: Organizer"));
    }

    /**
     * Attaches a real-time listener for event comments.
     */
    private void observeComments() {
        if (commentsListener != null) {
            commentsListener.remove();
        }
        commentsListener = db.collection("events")
                .document(eventId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Failed to load comments", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<EventComment> comments = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot snapshot : snapshots.getDocuments()) {
                            EventComment comment = snapshot.toObject(EventComment.class);
                            if (comment == null) {
                                continue;
                            }
                            comment.setCommentId(snapshot.getId());
                            comments.add(comment);
                        }
                    }
                    renderComments(comments);
                });
    }

    /**
     * Creates a new event comment on behalf of the current entrant.
     */
    private void submitComment() {
        String commentText = etComment.getText() == null ? "" : etComment.getText().toString().trim();
        if (commentText.isEmpty()) {
            etComment.setError("Comment cannot be empty");
            return;
        }

        btnPostComment.setEnabled(false);
        DocumentReference commentReference = db.collection("events")
                .document(eventId)
                .collection("comments")
                .document();

        Map<String, Object> commentData = new HashMap<>();
        commentData.put("commentId", commentReference.getId());
        commentData.put("eventId", eventId);
        commentData.put("authorId", entrantId);
        commentData.put("authorName", entrantDisplayName == null || entrantDisplayName.trim().isEmpty()
                ? entrantId
                : entrantDisplayName);
        commentData.put("authorRole", "entrant");
        commentData.put("commentText", commentText);
        commentData.put("createdAt", FieldValue.serverTimestamp());

        commentReference.set(commentData)
                .addOnSuccessListener(unused -> {
                    etComment.setText("");
                    Toast.makeText(this, "Comment posted", Toast.LENGTH_SHORT).show();
                    btnPostComment.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    btnPostComment.setEnabled(true);
                    Toast.makeText(this, "Failed to post comment", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Renders the current event comments into the details screen.
     *
     * @param comments loaded event comments
     */
    private void renderComments(List<EventComment> comments) {
        commentsContainer.removeAllViews();
        boolean hasComments = comments != null && !comments.isEmpty();
        tvCommentsEmpty.setVisibility(hasComments ? View.GONE : View.VISIBLE);
        if (!hasComments) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (EventComment comment : comments) {
            View itemView = inflater.inflate(R.layout.item_user_event_comment, commentsContainer, false);
            TextView tvAuthor = itemView.findViewById(R.id.tv_comment_author);
            TextView tvAuthorTag = itemView.findViewById(R.id.tv_comment_author_tag);
            TextView tvCreatedAt = itemView.findViewById(R.id.tv_comment_created_at);
            TextView tvText = itemView.findViewById(R.id.tv_comment_text);

            tvAuthor.setText(getDisplayAuthorName(comment));
            tvCreatedAt.setText(formatCommentDate(comment.getCreatedAt()));
            tvText.setText(comment.getCommentText() == null ? "" : comment.getCommentText());
            boolean organizerComment = "organizer".equalsIgnoreCase(comment.getAuthorRole());
            tvAuthorTag.setVisibility(organizerComment ? View.VISIBLE : View.GONE);

            commentsContainer.addView(itemView);
        }
    }

    private String getDisplayAuthorName(EventComment comment) {
        if (comment == null) {
            return "";
        }
        String authorName = comment.getAuthorName();
        if (authorName != null && !authorName.trim().isEmpty()) {
            return authorName.trim();
        }
        String authorId = comment.getAuthorId();
        return authorId == null ? "Unknown user" : authorId;
    }

    private String formatCommentDate(com.google.firebase.Timestamp timestamp) {
        if (timestamp == null) {
            return "Just now";
        }
        Date createdAt = timestamp.toDate();
        SimpleDateFormat formatter = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());
        return formatter.format(createdAt);
    }

    private String getSafeTrimmedString(DocumentSnapshot snapshot, String fieldName) {
        if (snapshot == null) {
            return "";
        }
        String value = snapshot.getString(fieldName);
        return value == null ? "" : value.trim();
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

        if (UserEventRecord.STATUS_WAITLIST_INVITED.equals(status)) {
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText("Reject");
            btnSecondary.setOnClickListener(v -> rejectPrivateWaitlistInvite());
            if (currentEvent.isWaitlistFull()) {
                btnPrimary.setText("Waitlist Full");
                btnPrimary.setEnabled(false);
                return;
            }
            btnPrimary.setText("Join the Waitlist");
            btnPrimary.setOnClickListener(v -> requestLocationAndJoinWaitlist());
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
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            joinWaitlist(TestingLocationPool.createRandomCountryLocation(this));
                            return;
                        }

                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this, "Location permission is required to join the waitlist", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        CancellationTokenSource tokenSource = new CancellationTokenSource();
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.getToken())
                                .addOnSuccessListener(currentLocation -> {
                                    if (currentLocation == null) {
                                        Toast.makeText(this, "Unable to read location. Please enable location and try again.", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    joinWaitlist(TestingLocationPool.createRandomCountryLocation(this));
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Unable to read location. Please try again.", Toast.LENGTH_SHORT).show());
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Unable to read location. Please try again.", Toast.LENGTH_SHORT).show());
        } catch (SecurityException e) {
            Toast.makeText(this, "Location permission is required to join the waitlist", Toast.LENGTH_SHORT).show();
        }
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
        EntrantWaitlistManager.joinWaitlist(db, entrantId, eventId, entrantLocation)
                .addOnSuccessListener(result -> {
                    currentEvent.setWaitlistEntrantIds(result.getUpdatedWaitlistEntrantIds());
                    currentEvent.setHistoryStatus(UserEventRecord.STATUS_WAITLISTED);
                    NotificationHelper.markMatchingNotificationsAsConfirmed(
                            db,
                            entrantId,
                            eventId,
                            NotificationHelper.TYPE_PRIVATE_WAITLIST_INVITE
                    );
                    Toast.makeText(this, "Joined waiting list successfully", Toast.LENGTH_SHORT).show();
                    loadEvent();
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
     */
    private void leaveWaitlist() {
        updateWaitlistMembership(false, null, true, "Left waiting list", null);
    }

    /**
     * Declines a private waitlist invitation and removes private-event access.
     */
    private void rejectPrivateWaitlistInvite() {
        EntrantWaitlistManager.declinePrivateWaitlistInvite(db, entrantId, eventId)
                .addOnSuccessListener(unused ->
                        NotificationHelper.markMatchingNotificationsAsDeclined(
                                        db,
                                        entrantId,
                                        eventId,
                                        NotificationHelper.TYPE_PRIVATE_WAITLIST_INVITE
                                )
                                .addOnCompleteListener(task -> {
                                    Toast.makeText(this, "Private waitlist invitation declined", Toast.LENGTH_SHORT).show();
                                    finish();
                                }))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to decline private waitlist invite", Toast.LENGTH_SHORT).show());
    }

    /**
     * Accepts an invitation.
     */
    private void acceptInvitation() {
        db.collection("events").document(eventId)
                .update(
                        "acceptedEntrantIds", FieldValue.arrayUnion(entrantId),
                        "declinedEntrantIds", FieldValue.arrayRemove(entrantId)
                )
                .addOnSuccessListener(unused ->
                        updateWaitlistMembership(false, UserEventRecord.STATUS_ACCEPTED, false, "Invitation accepted", null)
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to accept invitation", Toast.LENGTH_SHORT).show()
                );
    }

    /**
     * Declines an invitation.
     */
    private void declineInvitation() {
        // Remove the entrant from selected/replacement pools and mark as declined.
        // Declined entrants are not re-added to waitlist and are no longer eligible for lottery.
        db.collection("events").document(eventId)
                .update(
                        "selectedEntrantIds", FieldValue.arrayRemove(entrantId),
                        "replacementEntrantIds", FieldValue.arrayRemove(entrantId),
                        "declinedEntrantIds", FieldValue.arrayUnion(entrantId),
                        "acceptedEntrantIds", FieldValue.arrayRemove(entrantId)
                )
                .addOnSuccessListener(unused -> {
                    currentEvent.setHistoryStatus(UserEventRecord.STATUS_REJECTED);
                    upsertHistoryDocument(UserEventRecord.STATUS_REJECTED);
                    triggerAutomaticReplacementDraw();
                    Toast.makeText(this, "Invitation declined", Toast.LENGTH_SHORT).show();
                    loadEvent();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to decline invitation", Toast.LENGTH_SHORT).show()
                );
    }

    private void triggerAutomaticReplacementDraw() {
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
            if (addEntrant) {
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
     * Upserts event history entry for current entrant.
     *
     * @param status status to store
     */
    private void upsertHistoryDocument(String status) {
        db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(currentEvent.getEventId())
                .set(UserEventHistoryHelper.buildHistoryData(currentEvent, status));
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
