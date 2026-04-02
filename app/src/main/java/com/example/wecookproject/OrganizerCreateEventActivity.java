package com.example.wecookproject;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
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
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Activity for organizers to configure and create new events from a form-driven workflow. Within
 * the app it acts as the UI controller for the organizer event-creation flow, validating input,
 * uploading an optional poster, and persisting a new Event document directly to Firestore.
 */
public class OrganizerCreateEventActivity extends AppCompatActivity {
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
    private static final int DESCRIPTION_MAX_LENGTH = 500;
    private static final long ONE_HOUR_MILLIS = 60L * 60L * 1000L;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_TIME_PATTERN, Locale.getDefault());

    private FirebaseFirestore db;
    private TextInputEditText etEventName;
    private TextInputEditText etRegistrationStartDate;
    private TextInputEditText etRegistrationEndDate;
    private TextInputEditText etEventTime;
    private TextInputEditText etCapacity;
    private TextInputEditText etMaxWaitlist;
    private TextInputEditText etEventDescription;
    private RadioGroup rgEventVisibility;
    private SwitchMaterial switchGeolocationRequired;
    private ImageView ivPosterPreview;
    private TextView tvPosterUploadTitle;
    private TextView tvPosterUploadSubtitle;
    private Uri selectedPosterUri;
    private ActivityResultLauncher<String> posterPickerLauncher;

    /**
     * Initializes event creation form, validators, and navigation.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_create_event);
        dateFormat.setLenient(false);
        db = FirebaseFirestore.getInstance();

        etEventName = findViewById(R.id.et_event_name);
        etRegistrationStartDate = findViewById(R.id.et_registration_start_date);
        etRegistrationEndDate = findViewById(R.id.et_registration_end_date);
        etEventTime = findViewById(R.id.et_event_time);
        etCapacity = findViewById(R.id.et_capacity);
        etMaxWaitlist = findViewById(R.id.et_max_waitlist);
        etEventDescription = findViewById(R.id.et_event_description);
        rgEventVisibility = findViewById(R.id.rg_event_visibility);
        switchGeolocationRequired = findViewById(R.id.switch_geolocation_required);
        ivPosterPreview = findViewById(R.id.iv_poster_preview);
        tvPosterUploadTitle = findViewById(R.id.tv_poster_upload_title);
        tvPosterUploadSubtitle = findViewById(R.id.tv_poster_upload_subtitle);
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
        flPosterUpload.setOnClickListener(v -> posterPickerLauncher.launch("image/*"));

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_create_events);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_events) {
                startActivity(new Intent(this, OrganizerHomeActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, OrganizerProfileActivity.class));
                return true;
            }
            return true;
        });

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.btn_create_event).setOnClickListener(v -> createEvent());
    }

    private void createEvent() {
        String eventName = getTrimmedText(etEventName);
        String startDateStr = getTrimmedText(etRegistrationStartDate);
        String endDateStr = getTrimmedText(etRegistrationEndDate);
        String eventTimeStr = getTrimmedText(etEventTime);
        String capacityStr = getTrimmedText(etCapacity);
        String maxWaitlistStr = getTrimmedText(etMaxWaitlist);
        String eventDescription = getTrimmedText(etEventDescription);
        boolean geolocationRequired = switchGeolocationRequired.isChecked();

        if (eventName.isEmpty() || startDateStr.isEmpty() || endDateStr.isEmpty()
                || eventTimeStr.isEmpty() || capacityStr.isEmpty()
                || maxWaitlistStr.isEmpty() || eventDescription.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        Date registrationStartDate = parseDateTime(startDateStr);
        Date registrationEndDate = parseDateTime(endDateStr);
        Date eventTime = parseDateTime(eventTimeStr);
        Date now = new Date();
        boolean invalidStartTime = registrationStartDate == null || registrationStartDate.before(now);
        boolean invalidEndTime = registrationEndDate == null
                || registrationEndDate.before(now)
                || (registrationStartDate != null && registrationEndDate.before(registrationStartDate));
        if (invalidStartTime && invalidEndTime) {
            Toast.makeText(this, "invalid start and end time", Toast.LENGTH_SHORT).show();
            return;
        }
        if (invalidStartTime) {
            Toast.makeText(this, "invalid start time", Toast.LENGTH_SHORT).show();
            return;
        }
        if (invalidEndTime) {
            Toast.makeText(this, "invalid end time", Toast.LENGTH_SHORT).show();
            return;
        }

        if (eventTime == null) {
            Toast.makeText(this, "invalid event time", Toast.LENGTH_SHORT).show();
            return;
        }

        Date minimumEventTime = new Date(registrationEndDate.getTime() + ONE_HOUR_MILLIS);
        if (eventTime.before(minimumEventTime)) {
            Toast.makeText(this, "Event time must be at least 1 hour after registration end time", Toast.LENGTH_SHORT).show();
            return;
        }

        if (eventDescription.length() > DESCRIPTION_MAX_LENGTH) {
            Toast.makeText(this, "Event description must be 500 characters or fewer", Toast.LENGTH_SHORT).show();
            return;
        }

        int capacity;
        try {
            capacity = Integer.parseInt(capacityStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid capacity number", Toast.LENGTH_SHORT).show();
            return;
        }

        int maxWaitlist;
        try {
            maxWaitlist = Integer.parseInt(maxWaitlistStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid maximum waitlist number", Toast.LENGTH_SHORT).show();
            return;
        }

        if (capacity <= 0) {
            Toast.makeText(this, "Capacity must be greater than 0", Toast.LENGTH_SHORT).show();
            return;
        }

        if (maxWaitlist < 0) {
            Toast.makeText(this, "Maximum waitlist must be 0 or more", Toast.LENGTH_SHORT).show();
            return;
        }

        if (capacity > maxWaitlist) {
            Toast.makeText(this, "Capacity must not be greater than maximum waitlist", Toast.LENGTH_SHORT).show();
            return;
        }

        @SuppressLint("HardwareIds")
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String eventId = UUID.randomUUID().toString();

        Event newEvent = new Event(
                eventId,
                androidId,
                eventName,
                registrationStartDate,
                registrationEndDate,
                eventTime,
                maxWaitlist,
                0,
                geolocationRequired,
                "Location TBD",
                eventDescription
        );
        newEvent.setCapacity(capacity);

        int selectedVisibilityId = rgEventVisibility.getCheckedRadioButtonId();
        String visibilityTag = selectedVisibilityId == R.id.rb_visibility_private
                ? Event.VISIBILITY_PRIVATE
                : Event.VISIBILITY_PUBLIC;
        newEvent.setVisibilityTag(visibilityTag);
        if (Event.VISIBILITY_PUBLIC.equals(visibilityTag)) {
            newEvent.setQrCodePath(QrCodeUtils.buildPromotionalEventLink(eventId));
        } else {
            newEvent.setQrCodePath("");
        }

        if (selectedPosterUri != null) {
            Toast.makeText(this, "Uploading poster...", Toast.LENGTH_SHORT).show();
            FreeImageHostUploader.uploadPoster(this, db, selectedPosterUri, new FreeImageHostUploader.Callback() {
                @Override
                public void onSuccess(String imageUrl) {
                    newEvent.setPosterPath(imageUrl);
                    saveEvent(newEvent);
                }

                @Override
                public void onFailure(String message) {
                    Toast.makeText(OrganizerCreateEventActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        saveEvent(newEvent);
    }

    private void saveEvent(Event newEvent) {
        db.collection("events").document(newEvent.getEventId())
                .set(newEvent)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Event created successfully", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, OrganizerHomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to create event: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

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
        ivPosterPreview.setPadding(0, 0, 0, 0);
        ivPosterPreview.setImageURI(imageUri);
        tvPosterUploadTitle.setText("Poster ready to upload");
        tvPosterUploadSubtitle.setText("This image will be uploaded when you create the event");
    }

    private boolean isValidPosterMimeType(String mimeType) {
        return "image/jpeg".equalsIgnoreCase(mimeType)
                || "image/jpg".equalsIgnoreCase(mimeType)
                || "image/png".equalsIgnoreCase(mimeType);
    }

    private String getTrimmedText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private Date parseDateTime(String value) {
        try {
            return dateFormat.parse(value);
        } catch (ParseException e) {
            return null;
        }
    }

    private void showDatePicker(TextInputEditText targetView, String dialogTitle) {
        Calendar calendar = Calendar.getInstance();
        Date existingValue = parseDateTime(String.valueOf(targetView.getText()).trim());
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
