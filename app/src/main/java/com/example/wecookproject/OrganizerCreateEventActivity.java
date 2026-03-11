package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.example.wecookproject.model.Event;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.UUID;

public class OrganizerCreateEventActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_create_event);

        TextInputEditText etEventName = findViewById(R.id.et_event_name);
        TextInputEditText etRegistrationPeriod = findViewById(R.id.et_registration_period);
        TextInputLayout tilRegistrationPeriod = findViewById(R.id.til_registration_period);
        TextInputEditText etMaxWaitlist = findViewById(R.id.et_max_waitlist);
        RadioGroup rgEnrollmentCriteria = findViewById(R.id.rg_enrollment_criteria);
        RadioGroup rgLotteryMethod = findViewById(R.id.rg_lottery_method);

        etRegistrationPeriod.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                tilRegistrationPeriod.setHint("Registration Period");
            } else {
                if (etRegistrationPeriod.getText() != null && etRegistrationPeriod.getText().toString().isEmpty()) {
                    tilRegistrationPeriod.setHint("Registration Period (YYYY-MM-DD)");
                } else {
                    tilRegistrationPeriod.setHint("Registration Period");
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
            String eventName = etEventName.getText().toString().trim();
            String registrationPeriod = etRegistrationPeriod.getText().toString().trim();
            String maxWaitlistStr = etMaxWaitlist.getText().toString().trim();

            if (eventName.isEmpty() || registrationPeriod.isEmpty() || maxWaitlistStr.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
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

            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            String eventId = UUID.randomUUID().toString();

            Event newEvent = new Event(
                    eventId,
                    androidId, // organizer ID
                    eventName,
                    registrationPeriod,
                    enrollmentCriteria,
                    maxWaitlist,
                    0, // currentWaitlistCount starts at 0
                    lotteryMethodology,
                    false, // Default geolocation
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
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to create event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });
    }
}
