package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * AdminLoginActivity handles the login and registration process for administrators.
 * It verifies a global admin key from Firestore and checks the device ID.
 */
public class AdminLoginActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String androidId;

    /**
     * Initializes the activity and sets up the login logic.
     * 
     * @param savedInstanceState Saved state of the activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        db = FirebaseFirestore.getInstance();
        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        EditText etPassword = findViewById(R.id.et_password);
        Button btnLogin = findViewById(R.id.btn_login);

        btnLogin.setOnClickListener(v -> {
            String inputKey = etPassword.getText().toString().trim();
            if (inputKey.isEmpty()) {
                Toast.makeText(this, "Please enter the admin key", Toast.LENGTH_SHORT).show();
                return;
            }
            verifyAdminKeyAndLogin(inputKey);
        });
    }

    /**
     * Verifies the entered key against the "admin_key" in Firestore.
     * If correct, it proceeds to check if the admin is already registered on this device.
     * 
     * @param inputKey The key entered by the user.
     */
    private void verifyAdminKeyAndLogin(String inputKey) {
        db.collection("app_config").document("admin_key").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document != null && document.exists()) {
                    String correctKey = document.getString("key");
                    if (inputKey.equals(correctKey)) {
                        checkAdminRegistration();
                    } else {
                        Toast.makeText(this, "Invalid admin key", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e("AdminLogin", "Admin key document not found in Firestore");
                    Toast.makeText(this, "Server configuration error", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e("AdminLogin", "Error fetching admin key", task.getException());
                Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Checks if a user with the current device ID and "admin" role exists in Firestore.
     * If not, it routes to the signup flow.
     */
    private void checkAdminRegistration() {
        db.collection("users").document(androidId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document != null && document.exists()) {
                    if (UserDocumentUtils.hasRole(document, UserDocumentUtils.ROLE_ADMIN)) {
                        navigateToAdminMain();
                    } else {
                        grantAdminRole();
                    }
                } else {
                    navigateToSignup();
                }
            } else {
                Toast.makeText(this, "Error checking registration", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Routes the user to the signup details screen to complete their admin profile.
     */
    private void navigateToSignup() {
        Intent intent = new Intent(AdminLoginActivity.this, SignupDetailsActivity.class);
        intent.putExtra("clickedRole", "ADMIN");
        startActivity(intent);
        finish();
    }

    /**
     * Grants the admin role to an existing user document using the 'roles' map.
     */
    private void grantAdminRole() {
        Map<String, Object> updates = new HashMap<>();
        Map<String, Object> roles = new HashMap<>();
        roles.put(UserDocumentUtils.ROLE_ADMIN, true);
        updates.put("roles", roles);

        db.collection("users").document(androidId).set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Admin role granted", Toast.LENGTH_SHORT).show();
                    navigateToAdminMain();
                })
                .addOnFailureListener(e -> {
                    Log.e("AdminLogin", "Failed to grant admin role", e);
                    Toast.makeText(this, "Access update failed", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Navigates to the Admin main activity.
     */
    private void navigateToAdminMain() {
        Intent intent = new Intent(AdminLoginActivity.this, AdminMainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
