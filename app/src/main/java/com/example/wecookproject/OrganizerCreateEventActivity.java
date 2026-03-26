package com.example.wecookproject;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.RadioGroup;

import android.annotation.SuppressLint;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.wecookproject.model.Event;
import com.google.android.material.bottomnavigation.BottomNavigationView;
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
 * the app it acts as the UI controller for the organizer event-creation flow, validating input and
 * persisting a new Event document directly to Firestore.
 *
 * Outstanding issues:
 * - Creation relies on default placeholder values for fields such as location and description,
 *   which leaves newly created events only partially configured.
 * - Firestore writes and organizer-identity lookup are handled directly in the Activity, which
 *   tightly couples UI and data logic instead of separating them through a repository or
 *   ViewModel-style layer.
 * - Firestore and Storage access are handled directly in the Activity, which is not implemented yet,
 *   as connecting to Firebase storage require addition setup that might incur extra costs.
 */
public class OrganizerCreateEventActivity extends AppCompatActivity {
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_TIME_PATTERN, Locale.getDefault());

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

        TextInputEditText etEventName = findViewById(R.id.et_event_name);
        TextInputEditText etRegistrationStartDate = findViewById(R.id.et_registration_start_date);
        TextInputEditText etRegistrationEndDate = findViewById(R.id.et_registration_end_date);
        TextInputEditText etCapacity = findViewById(R.id.et_capacity);
        TextInputEditText etMaxWaitlist = findViewById(R.id.et_max_waitlist);
        RadioGroup rgEventVisibility = findViewById(R.id.rg_event_visibility);

        etRegistrationStartDate.setOnClickListener(v ->
                showDatePicker(etRegistrationStartDate, "Registration Start Time"));
        etRegistrationEndDate.setOnClickListener(v ->
                showDatePicker(etRegistrationEndDate, "Registration End Time"));

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

        findViewById(R.id.btn_create_event).setOnClickListener(v -> {
            String eventName = etEventName.getText() != null ? etEventName.getText().toString().trim() : "";
            String startDateStr = etRegistrationStartDate.getText() != null ? etRegistrationStartDate.getText().toString().trim() : "";
            String endDateStr = etRegistrationEndDate.getText() != null ? etRegistrationEndDate.getText().toString().trim() : "";
            String capacityStr = etCapacity.getText() != null ? etCapacity.getText().toString().trim() : "";
            String maxWaitlistStr = etMaxWaitlist.getText() != null ? etMaxWaitlist.getText().toString().trim() : "";

            if (eventName.isEmpty() || startDateStr.isEmpty() || endDateStr.isEmpty() || capacityStr.isEmpty() || maxWaitlistStr.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            Date registrationStartDate = parseDateTime(startDateStr);
            Date registrationEndDate = parseDateTime(endDateStr);
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

            @SuppressLint("HardwareIds")
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            String eventId = UUID.randomUUID().toString();

            Event newEvent = new Event(
                    eventId,
                    androidId, // organizer ID
                    eventName,
                    registrationStartDate,
                    registrationEndDate,
                    maxWaitlist,
                    0, // currentWaitlistCount starts at 0
                    false, // Default geolocation
                    "Location TBD", // Default location
                    "" // Default description
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

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("events").document(eventId)
                    .set(newEvent)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Event created successfully", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, OrganizerHomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to create event: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
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
