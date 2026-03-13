package com.example.wecookproject;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

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

public class OrganizerCreateEventActivity extends AppCompatActivity {
    private Date registrationStartDate;
    private Date registrationEndDate;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_create_event);

        TextInputEditText etEventName = findViewById(R.id.et_event_name);
        TextInputEditText etRegistrationStartDate = findViewById(R.id.et_registration_start_date);
        TextInputEditText etRegistrationEndDate = findViewById(R.id.et_registration_end_date);
        TextInputEditText etMaxWaitlist = findViewById(R.id.et_max_waitlist);
        RadioGroup rgEnrollmentCriteria = findViewById(R.id.rg_enrollment_criteria);
        RadioGroup rgLotteryMethod = findViewById(R.id.rg_lottery_method);

        // Set up date picker for start date
        etRegistrationStartDate.setOnClickListener(v -> showStartDatePicker(etRegistrationStartDate));
        etRegistrationStartDate.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
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
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
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

            int selectedEnrollmentId = rgEnrollmentCriteria.getCheckedRadioButtonId();
            int selectedLotteryId = rgLotteryMethod.getCheckedRadioButtonId();

            if (selectedEnrollmentId == -1 || selectedLotteryId == -1) {
                Toast.makeText(this, "Please select criteria and methodology", Toast.LENGTH_SHORT).show();
                return;
            }

            String enrollmentCriteria = ((RadioButton) findViewById(selectedEnrollmentId)).getText().toString();
            String lotteryMethodology = ((RadioButton) findViewById(selectedLotteryId)).getText().toString();

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
                    enrollmentCriteria,
                    maxWaitlist,
                    0, // currentWaitlistCount starts at 0
                    lotteryMethodology,
                    true, // Geolocation is mandatory
                    "Location TBD", // Default location
                    "" // Default description
            );

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
