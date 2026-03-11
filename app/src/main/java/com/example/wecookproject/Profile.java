package com.example.wecookproject;

import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class Profile extends AppCompatActivity {
    private ImageView ivBack;
    private Button btnSave;

    private EditText etFirstName, etLastName, etBirthday;
    private EditText etEmail, etPhone;
    private EditText etAddressLine1, etAddressLine2, etCity, etPostalCode, etCountry;

    private FirebaseFirestore db;
    private String androidId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        ivBack = findViewById(R.id.iv_back);
        btnSave = findViewById(R.id.btn_save);

        etFirstName = findViewById(R.id.et_first_name);
        etLastName = findViewById(R.id.et_last_name);
        etBirthday = findViewById(R.id.et_birthday);
        etEmail = findViewById(R.id.et_email);
        etPhone = findViewById(R.id.et_phone);
        etAddressLine1 = findViewById(R.id.et_address_line_1);
        etAddressLine2 = findViewById(R.id.et_address_line_2);
        etCity = findViewById(R.id.et_city);
        etPostalCode = findViewById(R.id.et_postal_code);
        etCountry = findViewById(R.id.et_country);

        db = FirebaseFirestore.getInstance();
        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        ivBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveUserProfile());

        loadUserProfile();
    }

    private void loadUserProfile() {
        db.collection("users").document(androidId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        etFirstName.setText(getSafeString(documentSnapshot.getString("firstName")));
                        etLastName.setText(getSafeString(documentSnapshot.getString("lastName")));
                        etBirthday.setText(getSafeString(documentSnapshot.getString("birthday")));
                        etEmail.setText(getSafeString(documentSnapshot.getString("email")));
                        etPhone.setText(getSafeString(documentSnapshot.getString("phone")));
                        etAddressLine1.setText(getSafeString(documentSnapshot.getString("addressLine1")));
                        etAddressLine2.setText(getSafeString(documentSnapshot.getString("addressLine2")));
                        etCity.setText(getSafeString(documentSnapshot.getString("city")));
                        etPostalCode.setText(getSafeString(documentSnapshot.getString("postalCode")));
                        etCountry.setText(getSafeString(documentSnapshot.getString("country")));
                    } else {
                        Toast.makeText(this, "Profile not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                );
    }

    private void saveUserProfile() {
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String birthday = etBirthday.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String addressLine1 = etAddressLine1.getText().toString().trim();
        String addressLine2 = etAddressLine2.getText().toString().trim();
        String city = etCity.getText().toString().trim();
        String postalCode = etPostalCode.getText().toString().trim();
        String country = etCountry.getText().toString().trim();

        if (firstName.isEmpty() || birthday.isEmpty() || addressLine1.isEmpty() || city.isEmpty() || postalCode.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("androidId", androidId);
        updates.put("firstName", firstName);
        updates.put("lastName", lastName);
        updates.put("birthday", birthday);
        updates.put("email", email);
        updates.put("phone", phone);
        updates.put("addressLine1", addressLine1);
        updates.put("addressLine2", addressLine2);
        updates.put("city", city);
        updates.put("postalCode", postalCode);
        updates.put("country", country);
        updates.put("profileCompleted", true);

        db.collection("users").document(androidId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                );
    }

    private String getSafeString(String value) {
        return value == null ? "" : value;
    }
}