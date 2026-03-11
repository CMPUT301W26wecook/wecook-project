package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class OrganizerCreateEventActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_create_event);

        TextInputEditText etRegistrationPeriod = findViewById(R.id.et_registration_period);
        TextInputLayout tilRegistrationPeriod = findViewById(R.id.til_registration_period);

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
            // TODO: validate fields and create event
        });
    }
}
