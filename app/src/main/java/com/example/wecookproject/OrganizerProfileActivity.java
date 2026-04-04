package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

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
    private FirebaseFirestore db;
    private String androidId;
    private Button btnUpdateInfo;
    private Button btnDeleteAccount;
    private Button btnLogout;
    private SwitchMaterial switchAutoLogin;
    private TextInputEditText etFirstName;
    private TextInputEditText etLastName;
    private TextInputEditText etDateOfBirth;
    private TextInputEditText etAddress;
    private TextInputEditText etCity;
    private TextInputEditText etPostalCode;
    private TextInputEditText etCountry;
    private TextInputLayout tilFirstName;
    private TextInputLayout tilLastName;
    private TextInputLayout tilDateOfBirth;
    private TextInputLayout tilAddress;
    private TextInputLayout tilCity;
    private TextInputLayout tilPostalCode;
    private TextInputLayout tilCountry;
    private boolean isEditing;

    /**
     * Initializes organizer profile screen and navigation actions.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_profile);
        db = FirebaseFirestore.getInstance();
        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        btnUpdateInfo = findViewById(R.id.btn_update_info);
        btnDeleteAccount = findViewById(R.id.btn_delete_account);
        btnLogout = findViewById(R.id.btn_logout);
        switchAutoLogin = findViewById(R.id.switch_auto_login);
        etFirstName = findViewById(R.id.et_first_name);
        etLastName = findViewById(R.id.et_last_name);
        etDateOfBirth = findViewById(R.id.et_date_of_birth);
        etAddress = findViewById(R.id.et_address);
        etCity = findViewById(R.id.et_city);
        etPostalCode = findViewById(R.id.et_postal_code);
        etCountry = findViewById(R.id.et_country);
        tilFirstName = findViewById(R.id.til_first_name);
        tilLastName = findViewById(R.id.til_last_name);
        tilDateOfBirth = findViewById(R.id.til_date_of_birth);
        tilAddress = findViewById(R.id.til_address);
        tilCity = findViewById(R.id.til_city);
        tilPostalCode = findViewById(R.id.til_postal_code);
        tilCountry = findViewById(R.id.til_country);

        setupBirthdayWatcher();
        setEditable(false);
        loadOrganizerProfile();

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

        btnUpdateInfo.setOnClickListener(v -> {
            if (!isEditing) {
                isEditing = true;
                setEditable(true);
                btnUpdateInfo.setText("Save Changes");
                return;
            }
            saveOrganizerProfile();
        });

        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountConfirm());
        btnLogout.setOnClickListener(v -> showLogoutConfirm());
    }

    /**
     * Shows a warning-style confirmation before logging out.
     */
    private void showLogoutConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of organizer account?")
                .setPositiveButton("Log Out", (dialog, which) -> logoutOrganizer())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Logs out current organizer by disabling auto-login and routing to login screen.
     */
    private void logoutOrganizer() {
        db.collection("users")
                .document(androidId)
                .update("autoLogin", false)
                .addOnSuccessListener(unused -> routeToLogin())
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Logged out locally", Toast.LENGTH_SHORT).show();
                    routeToLogin();
                });
    }

    /**
     * Routes to login and clears back stack.
     */
    private void routeToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loadOrganizerProfile() {
        db.collection("users")
                .document(androidId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        return;
                    }
                    etFirstName.setText(UserDocumentUtils.getSafeTrimmedString(snapshot, "firstName"));
                    etLastName.setText(UserDocumentUtils.getSafeTrimmedString(snapshot, "lastName"));
                    etDateOfBirth.setText(UserDocumentUtils.getSafeTrimmedString(snapshot, "birthday"));
                    etAddress.setText(UserDocumentUtils.getSafeTrimmedString(snapshot, "addressLine1"));
                    etCity.setText(UserDocumentUtils.getSafeTrimmedString(snapshot, "city"));
                    etPostalCode.setText(UserDocumentUtils.getSafeTrimmedString(snapshot, "postalCode"));
                    etCountry.setText(UserDocumentUtils.getSafeTrimmedString(snapshot, "country"));
                    Boolean autoLogin = snapshot.getBoolean("autoLogin");
                    switchAutoLogin.setChecked(Boolean.TRUE.equals(autoLogin));
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error loading organizer profile", Toast.LENGTH_SHORT).show());
    }

    private void saveOrganizerProfile() {
        String firstName = textOf(etFirstName);
        String lastName = textOf(etLastName);
        String birthday = textOf(etDateOfBirth);
        String address = textOf(etAddress);
        String city = textOf(etCity);
        String postalCode = textOf(etPostalCode);
        String country = textOf(etCountry);

        clearValidationErrors();
        Map<String, String> errors = UserInputValidator.validateOrganizerProfile(
                firstName,
                lastName,
                birthday,
                address,
                city,
                postalCode,
                country
        );
        if (!errors.isEmpty()) {
            applyError(tilFirstName, errors.get(UserInputValidator.FIELD_FIRST_NAME));
            applyError(tilLastName, errors.get(UserInputValidator.FIELD_LAST_NAME));
            applyError(tilDateOfBirth, errors.get(UserInputValidator.FIELD_BIRTHDAY));
            applyError(tilAddress, errors.get(UserInputValidator.FIELD_ADDRESS_LINE_1));
            applyError(tilCity, errors.get(UserInputValidator.FIELD_CITY));
            applyError(tilPostalCode, errors.get(UserInputValidator.FIELD_POSTAL_CODE));
            applyError(tilCountry, errors.get(UserInputValidator.FIELD_COUNTRY));
            Toast.makeText(this, errors.values().iterator().next(), Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> organizerData = new HashMap<>();
        organizerData.put("androidId", androidId);
        organizerData.put("firstName", firstName);
        organizerData.put("lastName", lastName);
        organizerData.put("birthday", birthday);
        organizerData.put("addressLine1", address);
        organizerData.put("city", city);
        organizerData.put("postalCode", postalCode);
        organizerData.put("country", country);
        organizerData.put("autoLogin", switchAutoLogin.isChecked());
        organizerData.put("profileCompleted", true);

        db.collection("users")
                .document(androidId)
                .set(organizerData, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Organizer profile updated", Toast.LENGTH_SHORT).show();
                    isEditing = false;
                    setEditable(false);
                    btnUpdateInfo.setText("Update Info");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update organizer profile", Toast.LENGTH_SHORT).show());
    }

    private void showDeleteAccountConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Organizer Access")
                .setMessage("Are you sure you want to remove organizer access from this account?")
                .setPositiveButton("Delete", (dialog, which) -> removeOrganizerRole())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeOrganizerRole() {
        db.collection("users")
                .document(androidId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        routeToLogin();
                        return;
                    }

                    if (UserDocumentUtils.getRoleCount(snapshot) <= 1) {
                        db.collection("users")
                                .document(androidId)
                                .delete()
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(this, "Account deleted", Toast.LENGTH_SHORT).show();
                                    routeToLogin();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Failed to delete account", Toast.LENGTH_SHORT).show());
                        return;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("roles." + UserDocumentUtils.ROLE_ORGANIZER, FieldValue.delete());
                    if (UserDocumentUtils.ROLE_ORGANIZER.equals(UserDocumentUtils.getSafeTrimmedString(snapshot, "role"))
                            && UserDocumentUtils.hasRole(snapshot, UserDocumentUtils.ROLE_ENTRANT)) {
                        updates.put("role", UserDocumentUtils.ROLE_ENTRANT);
                    }

                    db.collection("users")
                            .document(androidId)
                            .update(updates)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Organizer access removed", Toast.LENGTH_SHORT).show();
                                routeToLogin();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed to remove organizer access", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to remove organizer access", Toast.LENGTH_SHORT).show());
    }

    private void setEditable(boolean enabled) {
        etFirstName.setEnabled(enabled);
        etLastName.setEnabled(enabled);
        etDateOfBirth.setEnabled(enabled);
        etAddress.setEnabled(enabled);
        etCity.setEnabled(enabled);
        etPostalCode.setEnabled(enabled);
        etCountry.setEnabled(enabled);
        switchAutoLogin.setEnabled(enabled);
    }

    private void setupBirthdayWatcher() {
        etDateOfBirth.addTextChangedListener(new TextWatcher() {
            private boolean formatting;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (formatting) {
                    return;
                }
                formatting = true;
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
                formatting = false;
            }
        });
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void clearValidationErrors() {
        tilFirstName.setError(null);
        tilLastName.setError(null);
        tilDateOfBirth.setError(null);
        tilAddress.setError(null);
        tilCity.setError(null);
        tilPostalCode.setError(null);
        tilCountry.setError(null);
    }

    private void applyError(TextInputLayout layout, String error) {
        if (error != null && !error.trim().isEmpty()) {
            layout.setError(error);
        }
    }
}
