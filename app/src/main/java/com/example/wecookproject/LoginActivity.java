package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;

import java.util.HashMap;
import java.util.Map;

/**
 * Entry point for role-based login routing.
 *
 * <p>This activity identifies the current device, preloads user existence from Firestore,
 * and routes to the appropriate screen based on role and profile state.</p>
 */
public class LoginActivity extends AppCompatActivity {
    private boolean isDbQueryReady = false;
    private boolean isLoginClicked = false;
    private String clickedRole = "";
    private DocumentSnapshot userDocument;

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
            db.collection("users").document(androidId).get(Source.SERVER).addOnCompleteListener(userTask -> {
                isDbQueryReady = true;
                if (userTask.isSuccessful()) {
                    userDocument = userTask.getResult();
                    if (userDocument != null && userDocument.exists()) {
                        Log.d("LOGIN_INFO", "User exists, routing to MainActivity.");
                    } else {
                        Log.d("LOGIN_INFO", "User does not exist, routing to SignupDetailsActivity.");
                    }
                } else {
                    userDocument = null;
                    Log.e("LOGIN_INFO", "Error checking user document from server", userTask.getException());
                    Toast.makeText(
                            this,
                            "Unable to reach Firebase. Check Firestore rules/project config.",
                            Toast.LENGTH_LONG
                    ).show();
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
        else if (hasSelectedRole()) {
            if ("ORGANIZER".equals(clickedRole)) {
                jumpIntent = new Intent(LoginActivity.this, OrganizerHomeActivity.class);
            } else {
                jumpIntent = new Intent(LoginActivity.this, UserEventActivity.class);
            }
        } else {
            if (userDocument != null && userDocument.exists()) {
                grantSelectedRoleAndRoute();
                return;
            }
            jumpIntent = new Intent(LoginActivity.this, SignupDetailsActivity.class);
            jumpIntent.putExtra("clickedRole", clickedRole);
            startActivity(jumpIntent);
        }
        if (!"ADMIN".equals(clickedRole) && hasSelectedRole()) {
            startActivity(jumpIntent);
            return;
        }
        if ("ADMIN".equals(clickedRole)) {
            startActivity(jumpIntent);
        }
    }

    private boolean hasSelectedRole() {
        if (userDocument == null || !userDocument.exists()) {
            return false;
        }
        if ("ORGANIZER".equals(clickedRole)) {
            return UserDocumentUtils.hasRole(userDocument, UserDocumentUtils.ROLE_ORGANIZER);
        }
        if ("ENTRANT".equals(clickedRole)) {
            return UserDocumentUtils.hasRole(userDocument, UserDocumentUtils.ROLE_ENTRANT);
        }
        return false;
    }

    private void grantSelectedRoleAndRoute() {
        String roleKey = getSelectedRoleKey();
        if (roleKey == null) {
            return;
        }

        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        Map<String, Object> updates = new HashMap<>();
        updates.put("roles." + roleKey, true);

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(androidId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    if (UserDocumentUtils.ROLE_ORGANIZER.equals(roleKey)) {
                        startActivity(new Intent(LoginActivity.this, OrganizerHomeActivity.class));
                    } else {
                        startActivity(new Intent(LoginActivity.this, UserEventActivity.class));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("LOGIN_INFO", "Failed to grant missing role", e);
                    Toast.makeText(
                            this,
                            "Login failed: unable to update your Firebase user record.",
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private String getSelectedRoleKey() {
        if ("ORGANIZER".equals(clickedRole)) {
            return UserDocumentUtils.ROLE_ORGANIZER;
        }
        if ("ENTRANT".equals(clickedRole)) {
            return UserDocumentUtils.ROLE_ENTRANT;
        }
        return null;
    }
}


