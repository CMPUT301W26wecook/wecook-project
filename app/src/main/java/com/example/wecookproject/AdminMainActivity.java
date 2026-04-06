package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * It uses the BottomNavigationView that allows administrators to switch between different management views.
 */
public class AdminMainActivity extends AppCompatActivity {

    /**
     * Initializes the activity, sets up the layout and the bottom navigation menu.
     * Loads the default fragment (AdminUserFragment) when the activity is first created.
     * 
     * @param savedInstanceState  Saved state of the activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new AdminUserFragment())
                    .commit();
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_users) {
                selectedFragment = new AdminUserFragment();
            } else if (itemId == R.id.nav_events) {
                selectedFragment = new AdminEventFragment();
            } else if (itemId == R.id.nav_organizers) {
                selectedFragment = new AdminOrganizerFragment();
            } else if (itemId == R.id.nav_notifications) {
                selectedFragment = new AdminNotificationFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }
            return true;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                } else {
                    Intent intent = new Intent(AdminMainActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            }
        });
    }
}
