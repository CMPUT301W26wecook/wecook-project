package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Button loginButton = findViewById(R.id.btn_login);
        TextView signupPrompt = findViewById(R.id.tv_signup_prompt);
        EditText etUsername = findViewById(R.id.et_username);
        EditText etPassword = findViewById(R.id.et_password);

        loginButton.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Username and Password cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            // For demonstration, navigate to SignupDetailsActivity even on login
            Intent intent = new Intent(LoginActivity.this, SignupDetailsActivity.class);
            startActivity(intent);
        });

        signupPrompt.setOnClickListener(v -> {
            // Sign up should not require existing credentials in the login fields
            Intent intent = new Intent(LoginActivity.this, SignupDetailsActivity.class);
            startActivity(intent);
        });
    }
}
