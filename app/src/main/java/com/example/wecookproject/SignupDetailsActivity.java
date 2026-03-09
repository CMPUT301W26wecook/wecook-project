package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SignupDetailsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup_details);

        ImageView backButton = findViewById(R.id.iv_back);
        Button continueButton = findViewById(R.id.btn_continue);
        EditText etFirstName = findViewById(R.id.et_first_name);
        EditText etBirthday = findViewById(R.id.et_birthday);

        backButton.setOnClickListener(v -> finish());

        // Auto-format birthday as MM/DD/YYYY while user types
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

                // Strip all non-digit characters to work with raw digits only
                String digits = s.toString().replaceAll("[^\\d]", "");

                // Limit to 8 digits (MMDDYYYY)
                if (digits.length() > 8) {
                    digits = digits.substring(0, 8);
                }

                // Build the formatted string: MM/DD/YYYY
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

        continueButton.setOnClickListener(v -> {
            String firstName = etFirstName.getText().toString().trim();
            String birthday = etBirthday.getText().toString().trim();
            if (firstName.isEmpty() || birthday.isEmpty()) {
                Toast.makeText(this, "First name and Birthday cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(SignupDetailsActivity.this, SignupAddressActivity.class);
            startActivity(intent);
        });
    }
}
