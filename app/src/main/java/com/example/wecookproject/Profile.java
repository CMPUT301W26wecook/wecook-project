package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class Profile extends AppCompatActivity {

    private MaterialButton btnUpdate, btnDelete;
    private SwitchMaterial switchAutoLogin;
    private ImageView ivNotifications;
    private LinearLayout bottomNavEvents, bottomNavScan, bottomNavHistory, bottomNavProfile;

    private EditText etFirstName, etLastName, etBirthday;
    private EditText etAddressLine1, etCity, etPostalCode, etCountry;

    private FirebaseFirestore db;
    private String androidId;
    private boolean notificationsEnabled = false;
    private boolean isEditing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        btnUpdate = findViewById(R.id.btn_update);
        btnDelete = findViewById(R.id.btn_delete);
        switchAutoLogin = findViewById(R.id.switch_auto_login);
        ivNotifications = findViewById(R.id.iv_notifications);
        bottomNavEvents = findViewById(R.id.bottom_nav_events);
        bottomNavScan = findViewById(R.id.bottom_nav_scan);
        bottomNavHistory = findViewById(R.id.bottom_nav_history);
        bottomNavProfile = findViewById(R.id.bottom_nav_profile);

        etFirstName = findViewById(R.id.et_first_name);
        etLastName = findViewById(R.id.et_last_name);
        etBirthday = findViewById(R.id.et_birthday);
        etAddressLine1 = findViewById(R.id.et_address_line_1);
        etCity = findViewById(R.id.et_city);
        etPostalCode = findViewById(R.id.et_postal_code);
        etCountry = findViewById(R.id.et_country);

        db = FirebaseFirestore.getInstance();
        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        setupBirthdayWatcher();
        setEditable(false);
        loadUserProfile();

        btnUpdate.setOnClickListener(v -> {
            if (!isEditing) {
                isEditing = true;
                setEditable(true);
                btnUpdate.setText("Save Changes");
            } else {
                saveUserProfile();
            }
        });

        btnDelete.setOnClickListener(v -> showDeleteAccountConfirm());

        ivNotifications.setOnClickListener(v -> {

            notificationsEnabled = !notificationsEnabled;

            if (notificationsEnabled) {
                ivNotifications.setImageResource(R.drawable.ic_notifications);
                Toast.makeText(this, "Notifications ON", Toast.LENGTH_SHORT).show();
            } else {
                ivNotifications.setImageResource(R.drawable.ic_notifications_off);
                Toast.makeText(this, "Notifications OFF", Toast.LENGTH_SHORT).show();
            }

        });

        bottomNavEvents.setOnClickListener(v -> navigateToMain());
        bottomNavScan.setOnClickListener(v ->
                Toast.makeText(this, "Scan (coming soon)", Toast.LENGTH_SHORT).show());
        bottomNavHistory.setOnClickListener(v ->
                Toast.makeText(this, "History (coming soon)", Toast.LENGTH_SHORT).show());
        bottomNavProfile.setOnClickListener(v -> { });
    }

    private void setEditable(boolean enabled) {
        etFirstName.setEnabled(enabled);
        etLastName.setEnabled(enabled);
        etBirthday.setEnabled(enabled);
        etAddressLine1.setEnabled(enabled);
        etCity.setEnabled(enabled);
        etPostalCode.setEnabled(enabled);
        etCountry.setEnabled(enabled);
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void showDeleteAccountConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.collection("users").document(androidId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(Profile.this, "Account deleted", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(Profile.this, LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(Profile.this, "Failed to delete account", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupBirthdayWatcher() {
        etBirthday.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String digits = s.toString().replaceAll("[^\\d]", "");
                if (digits.length() > 8) {
                    digits = digits.substring(0, 8);
                }

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < digits.length(); i++) {
                    if (i == 2 || i == 4) {
                        formatted.append('/');
                    }
                    formatted.append(digits.charAt(i));
                }

                s.replace(0, s.length(), formatted.toString());
                isFormatting = false;
            }
        });
    }

    private void loadUserProfile() {
        db.collection("users").document(androidId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        etFirstName.setText(safe(documentSnapshot.getString("firstName")));
                        etLastName.setText(safe(documentSnapshot.getString("lastName")));
                        etBirthday.setText(safe(documentSnapshot.getString("birthday")));
                        etAddressLine1.setText(safe(documentSnapshot.getString("addressLine1")));
                        etCity.setText(safe(documentSnapshot.getString("city")));
                        etPostalCode.setText(safe(documentSnapshot.getString("postalCode")));
                        etCountry.setText(safe(documentSnapshot.getString("country")));

                        Boolean autoLogin = documentSnapshot.getBoolean("autoLogin");
                        if (autoLogin != null) {
                            switchAutoLogin.setChecked(autoLogin);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(Profile.this, "Error loading profile", Toast.LENGTH_SHORT).show());
    }

    private void saveUserProfile() {
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String birthday = etBirthday.getText().toString().trim();
        String addressLine1 = etAddressLine1.getText().toString().trim();
        String city = etCity.getText().toString().trim();
        String postalCode = etPostalCode.getText().toString().trim();
        String country = etCountry.getText().toString().trim();
        boolean autoLogin = switchAutoLogin.isChecked();

        if (firstName.isEmpty()) {
            Toast.makeText(this, "First name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("firstName", firstName);
        userData.put("lastName", lastName);
        userData.put("birthday", birthday);
        userData.put("addressLine1", addressLine1);
        userData.put("city", city);
        userData.put("postalCode", postalCode);
        userData.put("country", country);
        userData.put("autoLogin", autoLogin);
        userData.put("profileCompleted", true);

        db.collection("users").document(androidId)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(Profile.this, "Profile updated", Toast.LENGTH_SHORT).show();
                    isEditing = false;
                    setEditable(false);
                    btnUpdate.setText("Update Info");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(Profile.this, "Error updating profile", Toast.LENGTH_SHORT).show());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}