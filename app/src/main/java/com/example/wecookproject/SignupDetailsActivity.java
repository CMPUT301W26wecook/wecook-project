package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
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
