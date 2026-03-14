package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Activity for organizers to access the notification-sending screen from the organizer workflow.
 * Within the app it acts as the UI controller for notification composition/navigation, connected to
 * the organizer bottom-navigation structure.
 *
 * Outstanding issues:
 * - It is more of a placeholder than a fully implemented feature, as the actual notification-sending logic 
 *   is not yet implemented and the screen primarily serves as a navigation stub. The functionality will be 
 *   implemented in part 4.
 */
public class OrganizerNotificationActivity extends AppCompatActivity {
    /**
     * Initializes organizer notification UI and navigation.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_notification);

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

        findViewById(R.id.btn_send_notification).setOnClickListener(v -> {
            // TODO: send notification
        });
    }
}
