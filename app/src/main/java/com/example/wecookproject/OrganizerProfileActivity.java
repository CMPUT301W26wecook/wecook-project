package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Activity for organizers to access their profile screen and related account-management actions.
 * Within the app it acts as the UI controller for the organizer profile flow and as one destination
 * in the organizer bottom-navigation structure.
 *
 * Outstanding issues:
 * - Profile update and account deletion actions are still unimplemented placeholders. Because
 *   these requirements do not appear in the current user stories, they are not planned for part 4.
 *   If extra time is available, these features can be implemented later.
 */
public class OrganizerProfileActivity extends AppCompatActivity {
    /**
     * Initializes organizer profile screen and navigation actions.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_profile);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_profile);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_events) {
                startActivity(new Intent(this, OrganizerHomeActivity.class));
                return true;
            } else if (id == R.id.nav_create_events) {
                startActivity(new Intent(this, OrganizerCreateEventActivity.class));
                return true;
            }
            return true;
        });

        // Update Info button
        findViewById(R.id.btn_update_info).setOnClickListener(v -> {
            // TODO: collect fields and persist
        });

        // Delete Account button
        findViewById(R.id.btn_delete_account).setOnClickListener(v -> {
            // TODO: show confirmation dialog and delete account
        });
    }
}
