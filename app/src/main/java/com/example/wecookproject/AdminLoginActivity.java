package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AdminLoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        EditText etUsername = findViewById(R.id.et_username);
        EditText etPassword = findViewById(R.id.et_password);
        Button btnLogin = findViewById(R.id.btn_login);

        btnLogin.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            // Will be replaced with authentication logic in the future
            if (username.equals("admin") && password.equals("admin")) {
                Intent intent = new Intent(AdminLoginActivity.this, AdminMainActivity.class);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Invalid account", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
