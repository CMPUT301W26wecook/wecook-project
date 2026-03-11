package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class OrganizerEntrantListActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_entrant_list);

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

        // Action buttons
        findViewById(R.id.btn_send_invitation_to_selected).setOnClickListener(v -> {
            // TODO: send invitation to selected entrants
        });
        findViewById(R.id.btn_send_notification_to_all).setOnClickListener(v ->
            startActivity(new Intent(this, OrganizerNotificationActivity.class)));
        findViewById(R.id.btn_view_invited).setOnClickListener(v -> {
            // TODO: switch to invited view
        });
        findViewById(R.id.btn_manual_draw).setOnClickListener(v -> {
            // TODO: perform manual draw
        });
        findViewById(R.id.btn_redraw_entrants).setOnClickListener(v -> {
            // TODO: redraw entrants
        });
        findViewById(R.id.btn_delete_all_selected).setOnClickListener(v -> {
            // TODO: confirm and delete selected entrants
        });
    }
}
