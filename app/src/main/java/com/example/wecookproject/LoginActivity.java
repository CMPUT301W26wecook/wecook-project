package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Entry point for role-based login routing.
 *
 * <p>This activity identifies the current device, preloads user existence from Firestore,
 * and routes to the appropriate screen based on role and profile state.</p>
 */
public class LoginActivity extends AppCompatActivity {
    private boolean isDbQueryReady = false;
    private boolean isUserExists = false;
    private boolean isLoginClicked = false;
    private String clickedRole = "";

    /**
     * Initializes login controls and starts prefetching login context.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Button btnEntrant = findViewById(R.id.btn_entrant_login);
        Button btnOrganizer = findViewById(R.id.btn_organizer_login);
        TextView adminLogin = findViewById(R.id.text_Admin_login);

        btnEntrant.setOnClickListener(v -> handleLogin("ENTRANT"));
        btnOrganizer.setOnClickListener(v -> handleLogin("ORGANIZER"));

        adminLogin.setOnClickListener(v -> {
            handleLogin("ADMIN");
        });

        prefetchLoginData();
    }

    /**
     * Prefetches Firebase token and user existence for the current device.
     *
     * <p>When prefetch completes and the user has already tapped a login role,
     * routing continues automatically.</p>
     */
    private void prefetchLoginData() {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w("FCM", "Fetching FCM registration token failed", task.getException());
            } else {
                String token = task.getResult();
                Log.d("LOGIN_INFO", "Device ID: " + androidId);
                Log.d("LOGIN_INFO", "FCM Token: " + token);
            }

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("users").document(androidId).get().addOnCompleteListener(userTask -> {
                isDbQueryReady = true;
                if (userTask.isSuccessful()) {
                    DocumentSnapshot document = userTask.getResult();
                    if (document.exists()) {
                        isUserExists = true;
                        Log.d("LOGIN_INFO", "User exists, routing to MainActivity.");
                    } else {
                        isUserExists = false;
                        Log.d("LOGIN_INFO", "User does not exist, routing to SignupDetailsActivity.");
                    }
                } else {
                    isUserExists = false;
                    Log.e("LOGIN_INFO", "Error checking user document", userTask.getException());
                }

                if (isLoginClicked) {
                    routeUser();
                }
            });
        });
    }

    /**
     * Handles a role-selection click from the login screen.
     *
     * @param role selected role identifier ({@code ENTRANT}, {@code ORGANIZER}, or {@code ADMIN})
     */
    private void handleLogin(String role) {
        clickedRole = role; // Store if needed later
        isLoginClicked = true;

        if (isDbQueryReady) {
            routeUser();
        } else {
            // Wait for prefetchLoginData to complete
            Log.d("LOGIN_INFO", "Waiting for DB query to complete...");
        }
    }

    /**
     * Routes the user to the next activity according to role and profile existence.
     */
    private void routeUser() {
        Intent jumpIntent;
        if (clickedRole.equals("ADMIN")) {
            jumpIntent = new Intent(LoginActivity.this, AdminLoginActivity.class);
        }
        else if (isUserExists) {
            if ("ORGANIZER".equals(clickedRole)) {
                jumpIntent = new Intent(LoginActivity.this, OrganizerHomeActivity.class);
            } else {
                jumpIntent = new Intent(LoginActivity.this, UserEventActivity.class);
            }
        } else {
            jumpIntent = new Intent(LoginActivity.this, SignupDetailsActivity.class);
            jumpIntent.putExtra("clickedRole", clickedRole);
        }
        startActivity(jumpIntent);
    }
}






