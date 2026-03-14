package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

/**
 * Captures address information for signup and creates the initial user profile.
 */
public class SignupAddressActivity extends AppCompatActivity {
    /**
     * Initializes address inputs and navigation actions.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup_address);

        ImageView backButton = findViewById(R.id.iv_back);
        Button continueButton = findViewById(R.id.btn_continue);
        EditText etAddressLine1 = findViewById(R.id.et_address_line_1);
        EditText etAddressLine2 = findViewById(R.id.et_address_line_2);
        EditText etCity = findViewById(R.id.et_city);
        EditText etPostalCode = findViewById(R.id.et_postal_code);
        EditText etCountry = findViewById(R.id.et_country);

        backButton.setOnClickListener(v -> finish());
        continueButton.setOnClickListener(v -> handleContinue(
                etAddressLine1,
                etAddressLine2,
                etCity,
                etPostalCode,
                etCountry
        ));
    }

    /**
     * Validates address fields, persists profile data, and routes by role.
     *
     * @param addressLine1Input first address line field
     * @param addressLine2Input second address line field
     * @param cityInput city field
     * @param postalCodeInput postal-code field
     * @param countryInput country field
     */
    private void handleContinue(EditText addressLine1Input,
                                EditText addressLine2Input,
                                EditText cityInput,
                                EditText postalCodeInput,
                                EditText countryInput) {
        String addressLine1 = addressLine1Input.getText().toString().trim();
        String addressLine2 = addressLine2Input.getText().toString().trim();
        String city = cityInput.getText().toString().trim();
        String postalCode = postalCodeInput.getText().toString().trim();
        String country = countryInput.getText().toString().trim();
        if (addressLine1.isEmpty() || city.isEmpty() || postalCode.isEmpty()) {
            Toast.makeText(this, "Address line 1, City, and Postal code cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intentFromDetails = getIntent();
        String firstName = intentFromDetails.getStringExtra("firstName");
        String lastName = intentFromDetails.getStringExtra("lastName");
        String birthday = intentFromDetails.getStringExtra("birthday");
        String clickedRole = intentFromDetails.getStringExtra("clickedRole");
        String role = "ORGANIZER".equals(clickedRole) ? "organizer" : "entrant";

        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        Map<String, Object> userData = new HashMap<>();
        userData.put("androidId", androidId);
        userData.put("firstName", firstName != null ? firstName : "");
        userData.put("lastName", lastName != null ? lastName : "");
        userData.put("birthday", birthday != null ? birthday : "");
        userData.put("addressLine1", addressLine1);
        userData.put("addressLine2", addressLine2);
        userData.put("city", city);
        userData.put("postalCode", postalCode);
        userData.put("country", country);
        userData.put("role", role);
        userData.put("profileCompleted", true);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(androidId).set(userData)
            .addOnSuccessListener(aVoid -> routeAfterSuccessfulSignup(clickedRole))
            .addOnFailureListener(e ->
                    Toast.makeText(this, "Failed to save profile. " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    /**
     * Routes to the post-signup home screen.
     *
     * @param clickedRole original role selected on login
     */
    private void routeAfterSuccessfulSignup(String clickedRole) {
        Intent intent;
        if ("ORGANIZER".equals(clickedRole)) {
            intent = new Intent(SignupAddressActivity.this, OrganizerHomeActivity.class);
        } else {
            intent = new Intent(SignupAddressActivity.this, UserEventActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
