package com.example.wecookproject;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;


public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Button btnEntrant = findViewById(R.id.btn_entrant_login);
        Button btnOrganizer = findViewById(R.id.btn_organizer_login);
        TextView adminLogin = findViewById(R.id.text_Admin_login);

        btnEntrant.setOnClickListener(v -> handleLogin("ENTRANT"));
        btnOrganizer.setOnClickListener(v -> handleLogin("ORGANIZER"));

        adminLogin.setOnClickListener(v -> {
            // 管理员登录通常有专门的逻辑，或者也走 ID 登录
            handleLogin("ADMIN");
        });
    }

    private void handleLogin(String role) {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                return;
            }
            String token = task.getResult();
            Log.d("LOGIN_INFO", "Device ID: " + androidId);
            Log.d("LOGIN_INFO", "FCM Token: " + token);
            Intent intent = new Intent(LoginActivity.this, SignupDetailsActivity.class);
            startActivity(intent);
        });
    }
}



