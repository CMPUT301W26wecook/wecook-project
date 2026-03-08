package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

public class SignupDetailsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup_details);

        ImageView backButton = findViewById(R.id.iv_back);
        Button continueButton = findViewById(R.id.btn_continue);

        backButton.setOnClickListener(v -> finish());

        continueButton.setOnClickListener(v -> {
            Intent intent = new Intent(SignupDetailsActivity.this, SignupAddressActivity.class);
            startActivity(intent);
        });
    }
}
