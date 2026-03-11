package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wecookproject.model.Event;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;

public class OrganizerEventDetailsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_event_details);

        String eventId = getIntent().getStringExtra("eventId");
        
        TextView tvEventNameBig = findViewById(R.id.tv_event_name);
        TextView tvEventLocation = findViewById(R.id.tv_event_location);
        TextView tvEventNameDetail = findViewById(R.id.tv_event_name_detail);
        TextView tvEventDates = findViewById(R.id.tv_event_dates);
        TextView tvOrganizerLabel = findViewById(R.id.tv_organizer_label);
        TextView tvWaitlistLabel = findViewById(R.id.tv_waitlist_label);
        TextView tvEventDescription = findViewById(R.id.tv_event_description);

        if (eventId != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("events").document(eventId).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Event event = documentSnapshot.toObject(Event.class);
                    if (event != null) {
                        tvEventNameBig.setText(event.getEventName());
                        tvEventLocation.setText(event.getLocation());
                        tvEventNameDetail.setText(event.getEventName());
                        tvEventDates.setText(event.getRegistrationPeriod());
                        tvOrganizerLabel.setText("Organizer: " + event.getOrganizerId().substring(0, Math.min(event.getOrganizerId().length(), 5)) + "...");
                        tvWaitlistLabel.setText("Waitlist: " + event.getCurrentWaitlistCount() + "/" + event.getMaxWaitlist());
                        
                        String description = "Enrollment: " + event.getEnrollmentCriteria() + "\n" +
                                             "Methodology: " + event.getLotteryMethodology() + "\n" +
                                             event.getDescription();
                        tvEventDescription.setText(description.trim());
                    }
                } else {
                    Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to load event details", Toast.LENGTH_SHORT).show();
            });
        } else {
            Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
            finish();
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        // Deselect if not exactly a main tab, or keep highlighting "events"
        bottomNav.setSelectedItemId(R.id.nav_events);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_create_events) {
                startActivity(new Intent(this, OrganizerCreateEventActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, OrganizerProfileActivity.class));
                return true;
            } else if (id == R.id.nav_events) {
                Intent intent = new Intent(this, OrganizerHomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            }
            return true;
        });
        
        findViewById(R.id.btn_edit_event).setOnClickListener(v -> {
             Toast.makeText(this, "Edit event clicked", Toast.LENGTH_SHORT).show();
        });
        
        findViewById(R.id.btn_view_waitlist).setOnClickListener(v -> {
             Toast.makeText(this, "View Waitlist clicked", Toast.LENGTH_SHORT).show();
        });
        
        findViewById(R.id.btn_registration_map).setOnClickListener(v -> {
             Toast.makeText(this, "Map clicked", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_show_qr).setOnClickListener(v -> {
            // TODO: show QR code dialog
        });
    }
}
