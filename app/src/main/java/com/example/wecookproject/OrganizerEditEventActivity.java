package com.example.wecookproject;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.wecookproject.model.Event;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Activity for organizers to edit an existing event's configurable details and optionally replace
 * its poster image. Within the app it acts as the UI controller for the organizer edit-event flow,
 * coordinating form input, poster upload, and Firebase persistence in a single screen.
 */
public class OrganizerEditEventActivity extends AppCompatActivity {
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
    private static final int DESCRIPTION_MAX_LENGTH = 500;
    private static final long ONE_HOUR_MILLIS = 60L * 60L * 1000L;

    private Date existingRegistrationStartDate;
    private Date existingRegistrationEndDate;
    private Date existingEventTime;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_TIME_PATTERN, Locale.getDefault());
    private String eventId;
    private FirebaseFirestore db;
    private TextInputLayout tilEventName;
    private TextInputLayout tilRegistrationStartDate;
    private TextInputLayout tilRegistrationEndDate;
    private TextInputLayout tilEventTime;
    private TextInputLayout tilCapacity;
    private TextInputLayout tilMaxWaitlist;
    private TextInputLayout tilEventDescription;
    private TextInputEditText etEventName;
    private TextInputEditText etRegistrationStartDate;
    private TextInputEditText etRegistrationEndDate;
    private TextInputEditText etEventTime;
    private TextInputEditText etCapacity;
    private TextInputEditText etMaxWaitlist;
    private TextInputEditText etEventDescription;
    private RadioGroup rgEventVisibility;
    private ImageView ivPosterPreview;
    private TextView tvPosterUploadTitle;
    private TextView tvPosterUploadSubtitle;
    private TextView btnRemovePoster;
    private String originalPosterUrl;
    private String originalVisibilityTag = Event.VISIBILITY_PUBLIC;
    private Uri selectedPosterUri;
    private boolean removeExistingPoster;
    private ActivityResultLauncher<String> posterPickerLauncher;

    /**
     * Initializes edit form, poster picker, and navigation actions.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_edit_event);
        dateFormat.setLenient(false);
        db = FirebaseFirestore.getInstance();
        eventId = getIntent().getStringExtra("eventId");
        if (eventId == null) {
            Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tilEventName = findViewById(R.id.til_event_name);
        tilRegistrationStartDate = findViewById(R.id.til_registration_start_date);
        tilRegistrationEndDate = findViewById(R.id.til_registration_end_date);
        tilEventTime = findViewById(R.id.til_event_time);
        tilCapacity = findViewById(R.id.til_capacity);
        tilMaxWaitlist = findViewById(R.id.til_max_waitlist);
        tilEventDescription = findViewById(R.id.til_event_description);
        etEventName = findViewById(R.id.et_event_name);
        etRegistrationStartDate = findViewById(R.id.et_registration_start_date);
        etRegistrationEndDate = findViewById(R.id.et_registration_end_date);
        etEventTime = findViewById(R.id.et_event_time);
        etCapacity = findViewById(R.id.et_capacity);
        etMaxWaitlist = findViewById(R.id.et_max_waitlist);
        etEventDescription = findViewById(R.id.et_event_description);
        rgEventVisibility = findViewById(R.id.rg_event_visibility);
        ivPosterPreview = findViewById(R.id.iv_poster_preview);
        tvPosterUploadTitle = findViewById(R.id.tv_poster_upload_title);
        tvPosterUploadSubtitle = findViewById(R.id.tv_poster_upload_subtitle);
        btnRemovePoster = findViewById(R.id.btn_remove_poster);
        FrameLayout flPosterUpload = findViewById(R.id.fl_poster_upload);

        posterPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handlePosterSelection
        );

        etRegistrationStartDate.setOnClickListener(v ->
                showDatePicker(etRegistrationStartDate, "Registration Start Time"));
        etRegistrationEndDate.setOnClickListener(v ->
                showDatePicker(etRegistrationEndDate, "Registration End Time"));
        etEventTime.setOnClickListener(v ->
                showDatePicker(etEventTime, "Event Time"));

        loadCurrentEventState();
        bindPosterPickerLaunch(flPosterUpload);
        bindPosterPickerLaunch(ivPosterPreview);
        bindPosterPickerLaunch(tvPosterUploadTitle);
        bindPosterPickerLaunch(tvPosterUploadSubtitle);
        bindPosterPickerLaunch(findViewById(R.id.tv_poster_upload_formats));
        btnRemovePoster.setOnClickListener(v -> removePoster());

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_events) {
                startActivity(new Intent(this, OrganizerHomeActivity.class));
                return true;
            } else if (id == R.id.nav_create_events) {
                startActivity(new Intent(this, OrganizerCreateEventActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, OrganizerProfileActivity.class));
                return true;
            }
            return true;
        });

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.btn_update_event).setOnClickListener(v -> updateEvent());
    }

    /**
     * Validates changed fields and submits event updates.
     */
    private void updateEvent() {
        tilEventName.setError(null);
        tilRegistrationStartDate.setError(null);
        tilRegistrationEndDate.setError(null);
        tilEventTime.setError(null);
        tilCapacity.setError(null);
        tilMaxWaitlist.setError(null);
        tilEventDescription.setError(null);

        String eventName = getTrimmedText(etEventName);
        String registrationStartText = getTrimmedText(etRegistrationStartDate);
        String registrationEndText = getTrimmedText(etRegistrationEndDate);
        String eventTimeText = getTrimmedText(etEventTime);
        String maxWaitlistText = getTrimmedText(etMaxWaitlist);
        String capacityText = getTrimmedText(etCapacity);
        String eventDescriptionText = getTrimmedText(etEventDescription);

        boolean hasError = false;
        Map<String, Object> updates = new HashMap<>();

        if (!TextUtils.isEmpty(eventName)) {
            updates.put("eventName", eventName);
        }

        Date registrationStartDate = parseDateTime(registrationStartText);
        Date registrationEndDate = parseDateTime(registrationEndText);
        Date eventTime = parseDateTime(eventTimeText);

        if (!TextUtils.isEmpty(registrationStartText) && registrationStartDate != null) {
            updates.put("registrationStartDate", registrationStartDate);
        }

        if (!TextUtils.isEmpty(registrationEndText) && registrationEndDate != null) {
            updates.put("registrationEndDate", registrationEndDate);
        }
        if (!TextUtils.isEmpty(eventTimeText) && eventTime != null) {
            updates.put("eventTime", eventTime);
        }
        if (!TextUtils.isEmpty(eventDescriptionText)) {
            if (eventDescriptionText.length() > DESCRIPTION_MAX_LENGTH) {
                tilEventDescription.setError("Description must be 500 characters or fewer");
                hasError = true;
            } else {
                updates.put("description", eventDescriptionText);
            }
        }

        Date effectiveStartDate = registrationStartDate != null ? registrationStartDate : existingRegistrationStartDate;
        Date effectiveEndDate = registrationEndDate != null ? registrationEndDate : existingRegistrationEndDate;
        Date effectiveEventTime = eventTime != null ? eventTime : existingEventTime;
        Date now = new Date();
        boolean invalidStartTime = effectiveStartDate == null || effectiveStartDate.before(now);
        boolean invalidEndTime = effectiveEndDate == null
                || effectiveEndDate.before(now)
                || (effectiveStartDate != null && effectiveEndDate.before(effectiveStartDate));
        if (invalidStartTime && invalidEndTime) {
            Toast.makeText(this, "invalid start and end time", Toast.LENGTH_SHORT).show();
            hasError = true;
        } else if (invalidStartTime) {
            Toast.makeText(this, "invalid start time", Toast.LENGTH_SHORT).show();
            hasError = true;
        } else if (invalidEndTime) {
            Toast.makeText(this, "invalid end time", Toast.LENGTH_SHORT).show();
            hasError = true;
        }

        if (!TextUtils.isEmpty(eventTimeText) && eventTime == null) {
            tilEventTime.setError("Enter a valid event time");
            hasError = true;
        } else if (effectiveEventTime == null) {
            tilEventTime.setError("Event time is required");
            hasError = true;
        } else if (effectiveEndDate != null) {
            Date minimumEventTime = new Date(effectiveEndDate.getTime() + ONE_HOUR_MILLIS);
            if (effectiveEventTime.before(minimumEventTime)) {
                tilEventTime.setError("Event time must be at least 1 hour after registration end time");
                hasError = true;
            }
        }

        if (!TextUtils.isEmpty(maxWaitlistText)) {
            try {
                int maxWaitlist = Integer.parseInt(maxWaitlistText);
                if (maxWaitlist <= 0) {
                    tilMaxWaitlist.setError("Maximum waitlist must be greater than 0");
                    hasError = true;
                } else {
                    updates.put("maxWaitlist", maxWaitlist);
                }
            } catch (NumberFormatException e) {
                tilMaxWaitlist.setError("Enter a valid number");
                hasError = true;
            }
        }

        if (!TextUtils.isEmpty(capacityText)) {
            try {
                int capacity = Integer.parseInt(capacityText);
                if (capacity <= 0) {
                    tilCapacity.setError("Capacity must be greater than 0");
                    hasError = true;
                } else {
                    updates.put("capacity", capacity);
                }
            } catch (NumberFormatException e) {
                tilCapacity.setError("Enter a valid number");
                hasError = true;
            }
        }

        Object updatedCapacityObject = updates.get("capacity");
        Object updatedMaxWaitlistObject = updates.get("maxWaitlist");
        int effectiveCapacity = updatedCapacityObject instanceof Integer
                ? (Integer) updatedCapacityObject
                : 0;
        int effectiveMaxWaitlist = updatedMaxWaitlistObject instanceof Integer
                ? (Integer) updatedMaxWaitlistObject
                : 0;
        if (effectiveCapacity > 0 && effectiveMaxWaitlist >= 0 && effectiveCapacity > effectiveMaxWaitlist) {
            tilCapacity.setError("Capacity must not be greater than maximum waitlist");
            tilMaxWaitlist.setError("Maximum waitlist must be at least capacity");
            hasError = true;
        }

        int checkedVisibilityId = rgEventVisibility.getCheckedRadioButtonId();
        String selectedVisibilityTag = checkedVisibilityId == R.id.rb_visibility_private
                ? Event.VISIBILITY_PRIVATE
                : Event.VISIBILITY_PUBLIC;
        if (!selectedVisibilityTag.equals(originalVisibilityTag)) {
            updates.put("visibilityTag", selectedVisibilityTag);
            if (Event.VISIBILITY_PRIVATE.equals(selectedVisibilityTag)) {
                updates.put("qrCodePath", "");
            }
        }

        if (hasError) {
            return;
        }

        if (removeExistingPoster) {
            updates.put("posterPath", null);
            updates.put("posterDeleteUrl", null);
        }

        if (updates.isEmpty() && selectedPosterUri == null) {
            Toast.makeText(this, "Enter at least one valid field to update", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedPosterUri != null) {
            Toast.makeText(this, "Uploading poster...", Toast.LENGTH_SHORT).show();
            FreeImageHostUploader.uploadPoster(this, db, selectedPosterUri, new FreeImageHostUploader.UploadCallback() {
                @Override
                public void onSuccess(FreeImageHostUploader.UploadResult uploadResult) {
                    updates.put("posterPath", uploadResult.getImageUrl());
                    updates.put("posterDeleteUrl", uploadResult.getDeleteUrl());
                    applyUpdates(updates, selectedVisibilityTag, uploadResult.getImageUrl());
                }

                @Override
                public void onFailure(String message) {
                    Toast.makeText(OrganizerEditEventActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        applyUpdates(updates, selectedVisibilityTag, null);
    }

    private void applyUpdates(Map<String, Object> updates, String selectedVisibilityTag, String newPosterUrl) {
        boolean changedToPrivate = Event.VISIBILITY_PRIVATE.equals(selectedVisibilityTag)
                && !Event.VISIBILITY_PRIVATE.equals(originalVisibilityTag);
        if (changedToPrivate) {
            updateEventAndClearWaitlist(updates, selectedVisibilityTag, newPosterUrl);
            return;
        }

        db.collection("events")
                .document(eventId)
                .update(updates)
                .addOnSuccessListener(unused -> onEventUpdateSuccess(selectedVisibilityTag, newPosterUrl))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update event", Toast.LENGTH_SHORT).show());
    }

    /**
     * Updates event and clears waitlist when visibility changes to private.
     *
     * @param baseUpdates validated field updates
     * @param selectedVisibilityTag final visibility value
     * @param newPosterUrl uploaded poster url if one was selected
     */
    private void updateEventAndClearWaitlist(Map<String, Object> baseUpdates,
                                             String selectedVisibilityTag,
                                             String newPosterUrl) {
        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentReference eventRef = db.collection("events").document(eventId);
            com.google.firebase.firestore.DocumentSnapshot eventSnapshot = transaction.get(eventRef);
            if (!eventSnapshot.exists()) {
                throw new IllegalStateException("Event not found");
            }

            List<String> waitlistEntrants = FirestoreFieldUtils.getStringList(eventSnapshot, "waitlistEntrantIds");
            List<String> removedEntrantIds = new ArrayList<>(waitlistEntrants);

            Map<String, Object> updates = new HashMap<>(baseUpdates);
            updates.put("waitlistEntrantIds", new ArrayList<String>());
            updates.put("currentWaitlistCount", 0);
            updates.put("waitlistEntrantLocations", FieldValue.delete());
            transaction.update(eventRef, updates);
            return removedEntrantIds;
        }).addOnSuccessListener(removedEntrantIds ->
                clearWaitlistHistoryEntries(removedEntrantIds, selectedVisibilityTag, newPosterUrl))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update event", Toast.LENGTH_SHORT).show());
    }

    /**
     * Deletes history documents for removed waitlisted entrants.
     *
     * @param entrantIds entrants removed from waitlist
     * @param selectedVisibilityTag final visibility value
     * @param newPosterUrl uploaded poster url if one was selected
     */
    private void clearWaitlistHistoryEntries(List<String> entrantIds,
                                             String selectedVisibilityTag,
                                             String newPosterUrl) {
        if (entrantIds == null || entrantIds.isEmpty()) {
            onEventUpdateSuccess(selectedVisibilityTag, newPosterUrl);
            return;
        }

        final int batchLimit = 450;
        List<com.google.firebase.firestore.DocumentReference> historyRefs = new ArrayList<>();
        for (String entrantId : entrantIds) {
            if (!TextUtils.isEmpty(entrantId)) {
                historyRefs.add(db.collection("users")
                        .document(entrantId)
                        .collection("eventHistory")
                        .document(eventId));
            }
        }

        if (historyRefs.isEmpty()) {
            onEventUpdateSuccess(selectedVisibilityTag, newPosterUrl);
            return;
        }

        commitHistoryDeleteBatches(historyRefs, 0, batchLimit, selectedVisibilityTag, newPosterUrl);
    }

    /**
     * Commits batched history deletions recursively.
     */
    private void commitHistoryDeleteBatches(List<com.google.firebase.firestore.DocumentReference> historyRefs,
                                            int startIndex,
                                            int batchLimit,
                                            String selectedVisibilityTag,
                                            String newPosterUrl) {
        int endIndex = Math.min(startIndex + batchLimit, historyRefs.size());
        WriteBatch batch = db.batch();
        for (int i = startIndex; i < endIndex; i++) {
            batch.delete(historyRefs.get(i));
        }

        batch.commit()
                .addOnSuccessListener(unused -> {
                    if (endIndex < historyRefs.size()) {
                        commitHistoryDeleteBatches(historyRefs, endIndex, batchLimit, selectedVisibilityTag, newPosterUrl);
                    } else {
                        onEventUpdateSuccess(selectedVisibilityTag, newPosterUrl);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Event updated, but failed to clear waitlist history", Toast.LENGTH_SHORT).show());
    }

    /**
     * Finalizes successful event update state and exits.
     *
     * @param selectedVisibilityTag current visibility tag
     * @param newPosterUrl uploaded poster url if one was selected
     */
    private void onEventUpdateSuccess(String selectedVisibilityTag, String newPosterUrl) {
        if (!TextUtils.isEmpty(newPosterUrl)) {
            originalPosterUrl = newPosterUrl;
            selectedPosterUri = null;
        }
        originalVisibilityTag = selectedVisibilityTag;
        Toast.makeText(this, "Event updated", Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * Handles selected poster image locally and delays upload until save.
     *
     * @param imageUri selected image uri
     */
    private void handlePosterSelection(Uri imageUri) {
        if (imageUri == null) {
            return;
        }

        String mimeType = getContentResolver().getType(imageUri);
        if (!isValidPosterMimeType(mimeType)) {
            Toast.makeText(this, "Only JPG, JPEG, and PNG images are allowed", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedPosterUri = imageUri;
        removeExistingPoster = false;
        ivPosterPreview.setPadding(0, 0, 0, 0);
        ivPosterPreview.setImageURI(imageUri);
        tvPosterUploadTitle.setText("New poster selected");
        tvPosterUploadSubtitle.setText("This image will be uploaded when you update the event");
        btnRemovePoster.setVisibility(TextView.VISIBLE);
    }

    private void removePoster() {
        if (selectedPosterUri != null) {
            selectedPosterUri = null;
            if (!TextUtils.isEmpty(originalPosterUrl) && !removeExistingPoster) {
                showExistingPosterPreview();
            } else {
                showEmptyPosterPreview();
            }
            return;
        }

        if (!TextUtils.isEmpty(originalPosterUrl) && !removeExistingPoster) {
            removeExistingPoster = true;
            showEmptyPosterPreview();
        }
    }

    private void bindPosterPickerLaunch(View view) {
        if (view == null) {
            return;
        }
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(v -> posterPickerLauncher.launch("image/*"));
    }

    /**
     * Validates poster MIME type.
     *
     * @param mimeType detected MIME type
     * @return true when supported image MIME type
     */
    private boolean isValidPosterMimeType(String mimeType) {
        return "image/jpeg".equalsIgnoreCase(mimeType)
                || "image/jpg".equalsIgnoreCase(mimeType)
                || "image/png".equalsIgnoreCase(mimeType);
    }

    /**
     * Loads currently stored event state for validation and preview logic.
     */
    private void loadCurrentEventState() {
        db.collection("events")
                .document(eventId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    existingRegistrationStartDate = documentSnapshot.getDate("registrationStartDate");
                    existingRegistrationEndDate = documentSnapshot.getDate("registrationEndDate");
                    existingEventTime = documentSnapshot.getDate("eventTime");
                    if (existingRegistrationStartDate != null) {
                        etRegistrationStartDate.setText(dateFormat.format(existingRegistrationStartDate));
                    }
                    if (existingRegistrationEndDate != null) {
                        etRegistrationEndDate.setText(dateFormat.format(existingRegistrationEndDate));
                    }
                    if (existingEventTime != null) {
                        etEventTime.setText(dateFormat.format(existingEventTime));
                    }
                    String existingDescription = documentSnapshot.getString("description");
                    if (!TextUtils.isEmpty(existingDescription)) {
                        etEventDescription.setText(existingDescription);
                    }
                    originalPosterUrl = documentSnapshot.getString("posterPath");
                    if (TextUtils.isEmpty(originalPosterUrl)) {
                        originalPosterUrl = documentSnapshot.getString("posterUrl");
                    }
                    if (!TextUtils.isEmpty(originalPosterUrl)) {
                        showExistingPosterPreview();
                    } else {
                        showEmptyPosterPreview();
                    }

                    String visibilityTag = documentSnapshot.getString("visibilityTag");
                    if (TextUtils.isEmpty(visibilityTag)) {
                        originalVisibilityTag = Event.VISIBILITY_PUBLIC;
                    } else if (Event.VISIBILITY_PRIVATE.equalsIgnoreCase(visibilityTag.trim())) {
                        originalVisibilityTag = Event.VISIBILITY_PRIVATE;
                    } else {
                        originalVisibilityTag = Event.VISIBILITY_PUBLIC;
                    }

                    int visibilityId = Event.VISIBILITY_PRIVATE.equals(originalVisibilityTag)
                            ? R.id.rb_visibility_private
                            : R.id.rb_visibility_public;
                    rgEventVisibility.check(visibilityId);
                });
    }

    private void showExistingPosterPreview() {
        removeExistingPoster = false;
        ivPosterPreview.setPadding(0, 0, 0, 0);
        PosterLoader.loadInto(ivPosterPreview, originalPosterUrl);
        tvPosterUploadTitle.setText("Current event poster");
        tvPosterUploadSubtitle.setText("Tap to choose a replacement image");
        btnRemovePoster.setVisibility(TextView.VISIBLE);
    }

    private void showEmptyPosterPreview() {
        ivPosterPreview.setPadding(getPosterPlaceholderPadding(), getPosterPlaceholderPadding(),
                getPosterPlaceholderPadding(), getPosterPlaceholderPadding());
        ivPosterPreview.setImageResource(android.R.drawable.ic_menu_gallery);
        tvPosterUploadTitle.setText("Upload your event poster");
        tvPosterUploadSubtitle.setText("Tap to choose a new image");
        btnRemovePoster.setVisibility(TextView.GONE);
    }

    private int getPosterPlaceholderPadding() {
        return (int) (18 * getResources().getDisplayMetrics().density);
    }

    /**
     * Returns trimmed text from an input field.
     *
     * @param editText source input
     * @return trimmed text or empty string
     */
    private String getTrimmedText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private Date parseDateTime(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        try {
            return dateFormat.parse(value);
        } catch (java.text.ParseException e) {
            return null;
        }
    }

    private void showDatePicker(TextInputEditText targetView, String dialogTitle) {
        Calendar calendar = Calendar.getInstance();
        Date existingValue = parseDateTime(getTrimmedText(targetView));
        if (existingValue != null) {
            calendar.setTime(existingValue);
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    showTimeInputDialog(targetView, dialogTitle, calendar);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void showTimeInputDialog(TextInputEditText targetView, String dialogTitle, Calendar calendar) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, 0);

        TextInputEditText hourInput = new TextInputEditText(this);
        hourInput.setHint("Hour (0-23)");
        hourInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        TextInputEditText minuteInput = new TextInputEditText(this);
        minuteInput.setHint("Minute (0-59)");
        minuteInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        container.addView(hourInput);
        container.addView(minuteInput);

        new AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setView(container)
                .setPositiveButton("OK", (dialog, which) -> {
                    Integer hour = parseTimePart(hourInput.getText() == null ? "" : hourInput.getText().toString().trim(), 23);
                    Integer minute = parseTimePart(minuteInput.getText() == null ? "" : minuteInput.getText().toString().trim(), 59);
                    if (hour == null || minute == null) {
                        Toast.makeText(this, "Please enter a valid hour and minute", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    calendar.set(Calendar.HOUR_OF_DAY, hour);
                    calendar.set(Calendar.MINUTE, minute);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    targetView.setText(dateFormat.format(calendar.getTime()));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Integer parseTimePart(String value, int max) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0 || parsed > max) {
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
