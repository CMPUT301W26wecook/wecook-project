package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private LinearLayout bottomNavEvents, bottomNavScan, bottomNavHistory, bottomNavProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavEvents = findViewById(R.id.bottom_nav_events);
        bottomNavScan = findViewById(R.id.bottom_nav_scan);
        bottomNavHistory = findViewById(R.id.bottom_nav_history);
        bottomNavProfile = findViewById(R.id.bottom_nav_profile);

        bottomNavEvents.setOnClickListener(v -> {
            // already on Events page
        });

        bottomNavScan.setOnClickListener(v ->
                Toast.makeText(this, "Scan (coming soon)", Toast.LENGTH_SHORT).show());

        bottomNavHistory.setOnClickListener(v ->
                Toast.makeText(this, "History (coming soon)", Toast.LENGTH_SHORT).show());

        bottomNavProfile.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, Profile.class);
            startActivity(intent);
        });
    }
}