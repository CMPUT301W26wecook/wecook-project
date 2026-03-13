package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class OrganizerEventMapActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_event_map);

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

        findViewById(R.id.btn_back_to_event).setOnClickListener(v -> finish());

        findViewById(R.id.btn_show_qr).setOnClickListener(v -> {
            // TODO: show QR code dialog
        });
    }
}
