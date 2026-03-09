package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SignupAddressActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup_address);

        ImageView backButton = findViewById(R.id.iv_back);
        Button continueButton = findViewById(R.id.btn_continue);
        EditText etAddressLine1 = findViewById(R.id.et_address_line_1);
        EditText etCity = findViewById(R.id.et_city);
        EditText etPostalCode = findViewById(R.id.et_postal_code);

        backButton.setOnClickListener(v -> finish());

        continueButton.setOnClickListener(v -> {
            String addressLine1 = etAddressLine1.getText().toString().trim();
            String city = etCity.getText().toString().trim();
            String postalCode = etPostalCode.getText().toString().trim();
            if (addressLine1.isEmpty() || city.isEmpty() || postalCode.isEmpty()) {
                Toast.makeText(this, "Address line 1, City, and Postal code cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Profile complete!", Toast.LENGTH_SHORT).show();
            // In a real app, save the data and go to the home screen
            Intent intent = new Intent(SignupAddressActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }
}
