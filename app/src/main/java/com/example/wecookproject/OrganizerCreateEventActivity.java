package com.example.wecookproject;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;
import android.widget.RadioGroup;

import android.annotation.SuppressLint;
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
    private Date registrationStartDate;
    private Date registrationEndDate;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    /**
     * Initializes event creation form, validators, and navigation.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_create_event);

        TextInputEditText etEventName = findViewById(R.id.et_event_name);
        TextInputEditText etRegistrationStartDate = findViewById(R.id.et_registration_start_date);
        TextInputEditText etRegistrationEndDate = findViewById(R.id.et_registration_end_date);
        TextInputEditText etMaxWaitlist = findViewById(R.id.et_max_waitlist);
        RadioGroup rgEventVisibility = findViewById(R.id.rg_event_visibility);

        // Set up date picker for start date
        etRegistrationStartDate.setOnClickListener(v -> showStartDatePicker(etRegistrationStartDate));
        etRegistrationStartDate.addTextChangedListener(new TextWatcher() {
            /**
             * No-op callback required by {@link TextWatcher}.
             *
             * @param s current text
             * @param start changed start index
             * @param count changed length
             * @param after replacement length
             */
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            /**
             * No-op callback required by {@link TextWatcher}.
             *
             * @param s current text
             * @param start changed start index
             * @param before replaced length
             * @param count inserted length
             */
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            /**
             * Parses typed start-date text into internal date state.
             *
             * @param s editable content
             */
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    registrationStartDate = dateFormat.parse(s.toString().trim());
                } catch (ParseException e) {
                    registrationStartDate = null;
                }
            }
        });

        // Set up date picker for end date
        etRegistrationEndDate.setOnClickListener(v -> showEndDatePicker(etRegistrationEndDate));
        etRegistrationEndDate.addTextChangedListener(new TextWatcher() {
            /**
             * No-op callback required by {@link TextWatcher}.
             *
             * @param s current text
             * @param start changed start index
             * @param count changed length
             * @param after replacement length
             */
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            /**
             * No-op callback required by {@link TextWatcher}.
             *
             * @param s current text
             * @param start changed start index
             * @param before replaced length
             * @param count inserted length
             */
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            /**
             * Parses typed end-date text into internal date state.
             *
             * @param s editable content
             */
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    registrationEndDate = dateFormat.parse(s.toString().trim());
                } catch (ParseException e) {
                    registrationEndDate = null;
                }
            }
        });

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
            String maxWaitlistStr = etMaxWaitlist.getText() != null ? etMaxWaitlist.getText().toString().trim() : "";

            if (eventName.isEmpty() || startDateStr.isEmpty() || endDateStr.isEmpty() || maxWaitlistStr.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (registrationStartDate == null || registrationEndDate == null) {
                Toast.makeText(this, "Please select valid dates", Toast.LENGTH_SHORT).show();
                return;
            }

            if (registrationEndDate.before(registrationStartDate)) {
                Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show();
                return;
            }

            int maxWaitlist;
            try {
                maxWaitlist = Integer.parseInt(maxWaitlistStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid maximum waitlist number", Toast.LENGTH_SHORT).show();
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

    /**
     * Opens a date picker for registration start date.
     *
     * @param editText target input field
     */
    private void showStartDatePicker(TextInputEditText editText) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                calendar.set(year, month, dayOfMonth);
                registrationStartDate = calendar.getTime();
                editText.setText(dateFormat.format(registrationStartDate));
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    /**
     * Opens a date picker for registration end date.
     *
     * @param editText target input field
     */
    private void showEndDatePicker(TextInputEditText editText) {
        Calendar calendar = Calendar.getInstance();
        if (registrationStartDate != null) {
            calendar.setTime(registrationStartDate);
        }
        
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                calendar.set(year, month, dayOfMonth);
                registrationEndDate = calendar.getTime();
                editText.setText(dateFormat.format(registrationEndDate));
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        
        // Set minimum date to start date if selected
        if (registrationStartDate != null) {
            datePickerDialog.getDatePicker().setMinDate(registrationStartDate.getTime());
        }
        
        datePickerDialog.show();
    }
}
