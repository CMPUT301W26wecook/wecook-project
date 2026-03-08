package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Button loginButton = findViewById(R.id.btn_login);
        TextView signupPrompt = findViewById(R.id.tv_signup_prompt);

        // For now, both login and sign up prompt take the user to the Signup Details flow to demonstrate the UI
        loginButton.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupDetailsActivity.class);
            startActivity(intent);
        });

        signupPrompt.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupDetailsActivity.class);
            startActivity(intent);
        });
    }
}
