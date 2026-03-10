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

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {
    private boolean isDbQueryReady = false;
    private boolean isUserExists = false;
    private boolean isLoginClicked = false;
    private String clickedRole = "";

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

        prefetchLoginData();
    }

    private void prefetchLoginData() {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                return;
            }
            String token = task.getResult();
            Log.d("LOGIN_INFO", "Device ID: " + androidId);
            Log.d("LOGIN_INFO", "FCM Token: " + token);

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("users").document(androidId).get().addOnCompleteListener(userTask -> {
                isDbQueryReady = true;
                if (userTask.isSuccessful()) {
                    DocumentSnapshot document = userTask.getResult();
                    if (document.exists()) {
                        isUserExists = true;
                        Log.d("LOGIN_INFO", "User exists, routing to MainActivity.");
                    } else {
                        isUserExists = false;
                        Log.d("LOGIN_INFO", "User does not exist, routing to SignupDetailsActivity.");
                    }
                } else {
                    isUserExists = false;
                    Log.e("LOGIN_INFO", "Error checking user document", userTask.getException());
                }

                if (isLoginClicked) {
                    routeUser();
                }
            });
        });
    }

    private void handleLogin(String role) {
        clickedRole = role; // Store if needed later
        isLoginClicked = true;

        if (isDbQueryReady) {
            routeUser();
        } else {
            // Wait for prefetchLoginData to complete
            Log.d("LOGIN_INFO", "Waiting for DB query to complete...");
        }
    }

    private void routeUser() {
        if (isUserExists) {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
        } else {
            Intent intent = new Intent(LoginActivity.this, SignupDetailsActivity.class);
            startActivity(intent);
        }
    }
}



