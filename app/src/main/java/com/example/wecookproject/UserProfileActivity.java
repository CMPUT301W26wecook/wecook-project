package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Entrant profile screen for viewing and updating personal settings.
 */
public class UserProfileActivity extends AppCompatActivity {

    private MaterialButton btnUpdate;
    private MaterialButton btnDelete;
    private MaterialButton btnViewInbox;
    private SwitchMaterial switchAutoLogin;
    private BottomNavigationView bottomNav;

    private TextInputEditText etFirstName;
    private TextInputEditText etLastName;
    private TextInputEditText etBirthday;
    private TextInputEditText etAddressLine1;
    private TextInputEditText etCity;
    private TextInputEditText etPostalCode;
    private TextInputEditText etCountry;

    private FirebaseFirestore db;
    private String androidId;

    private boolean isEditing = false;
    private boolean notificationsEnabled = false;

    /**
     * Initializes profile views, loads user data, and wires interactions.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        btnUpdate = findViewById(R.id.btn_update);
        btnDelete = findViewById(R.id.btn_delete);
        btnViewInbox = findViewById(R.id.btn_view_inbox);
        switchAutoLogin = findViewById(R.id.switch_auto_login);
        bottomNav = findViewById(R.id.bottom_nav);

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
        setupBottomNav();

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

        btnViewInbox.setOnClickListener(v -> openInbox());
    }

    private void openInbox() {
        Intent intent = new Intent(UserProfileActivity.this, UserNotificationActivity.class);
        startActivity(intent);
    }

    /**
     * Configures bottom-navigation actions for entrant pages.
     */
    private void setupBottomNav() {
        bottomNav.setSelectedItemId(R.id.nav_profile);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_events) {
                Intent intent = new Intent(UserProfileActivity.this, UserEventActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
                return true;
            } else if (id == R.id.nav_scan) {
                Toast.makeText(this, "Scan (coming soon)", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_history) {
                Intent intent = new Intent(UserProfileActivity.this, UserHistoryActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
                return true;
            } else if (id == R.id.nav_profile) {
                return true;
            }

            return false;
        });
    }

    /**
     * Toggles editability for profile fields.
     *
     * @param enabled true to enable editing
     */
    private void setEditable(boolean enabled) {
        etFirstName.setEnabled(enabled);
        etLastName.setEnabled(enabled);
        etBirthday.setEnabled(enabled);
        etAddressLine1.setEnabled(enabled);
        etCity.setEnabled(enabled);
        etPostalCode.setEnabled(enabled);
        etCountry.setEnabled(enabled);
        switchAutoLogin.setEnabled(enabled);
    }

    /**
     * Adds birthday auto-formatting as {@code MM/DD/YYYY}.
     */
    private void setupBirthdayWatcher() {
        etBirthday.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;

            /**
             * No-op callback required by {@link TextWatcher}.
             *
             * @param s current text
             * @param start changed start index
             * @param count changed length
             * @param after replacement length
             */
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            /**
             * No-op callback required by {@link TextWatcher}.
             *
             * @param s current text
             * @param start changed start index
             * @param before replaced length
             * @param count inserted length
             */
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            /**
             * Normalizes typed birthday digits into slash-separated format.
             *
             * @param s editable field content
             */
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

    /**
     * Loads profile fields from Firestore.
     */
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

                        Boolean notifications = documentSnapshot.getBoolean("notificationsEnabled");
                        if (notifications != null) {
                            notificationsEnabled = notifications;
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(UserProfileActivity.this, "Error loading profile", Toast.LENGTH_SHORT).show());
    }

    /**
     * Validates and persists profile updates.
     */
    private void saveUserProfile() {
        String firstName = textOf(etFirstName);
        String lastName = textOf(etLastName);
        String birthday = textOf(etBirthday);
        String addressLine1 = textOf(etAddressLine1);
        String city = textOf(etCity);
        String postalCode = textOf(etPostalCode);
        String country = textOf(etCountry);
        boolean autoLogin = switchAutoLogin.isChecked();

        if (firstName.isEmpty()) {
            Toast.makeText(this, "First name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("androidId", androidId);
        userData.put("firstName", firstName);
        userData.put("lastName", lastName);
        userData.put("birthday", birthday);
        userData.put("addressLine1", addressLine1);
        userData.put("city", city);
        userData.put("postalCode", postalCode);
        userData.put("country", country);
        userData.put("autoLogin", autoLogin);
        userData.put("notificationsEnabled", notificationsEnabled);
        userData.put("profileCompleted", true);

        db.collection("users").document(androidId)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(UserProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show();
                    isEditing = false;
                    setEditable(false);
                    btnUpdate.setText("Update Info");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(UserProfileActivity.this, "Error updating profile", Toast.LENGTH_SHORT).show());
    }

    /**
     * Displays a confirmation dialog before deleting account.
     */
    private void showDeleteAccountConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) ->
                        db.collection("users").document(androidId)
                                .delete()
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(UserProfileActivity.this, "Account deleted", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(UserProfileActivity.this, LoginActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(UserProfileActivity.this, "Failed to delete account", Toast.LENGTH_SHORT).show())
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Returns a non-null string.
     *
     * @param value input value
     * @return input value or empty string
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Reads and trims text from an input field.
     *
     * @param editText source input
     * @return trimmed text, or empty string when null
     */
    private String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
