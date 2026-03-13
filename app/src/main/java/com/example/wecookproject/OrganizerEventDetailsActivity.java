package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wecookproject.model.Event;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Activity for organizers to view an event's details, observe live updates, and navigate to
 * related management flows such as editing the event or reviewing its waitlist. Within the app it
 * acts as the UI controller for the organizer event-details screen, binding Firestore snapshot
 * data directly to the view layer.
 *
 * Outstanding issues:
 * - Some actions are incomplete or placeholder-driven, including the registration map button and
 *   the QR-code flow and will be implemented in part 4.
 * - Presentation and Firestore listener logic are handled directly in the Activity, which tightly
 *   couples UI and data updates instead of separating them through a repository or ViewModel-style
 *   layer.
 */
public class OrganizerEventDetailsActivity extends AppCompatActivity {
    
    private ListenerRegistration eventListener;
    private SwitchMaterial geolocationSwitch;
    private boolean suppressSwitchCallback;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    /**
     * Initializes event detail rendering and related navigation actions.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
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
        geolocationSwitch = findViewById(R.id.switch_geolocation);

        if (eventId != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            geolocationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressSwitchCallback) {
                    return;
                }
                db.collection("events")
                        .document(eventId)
                        .update("geolocationRequired", isChecked)
                        .addOnFailureListener(e -> {
                            suppressSwitchCallback = true;
                            buttonView.setChecked(!isChecked);
                            suppressSwitchCallback = false;
                            Toast.makeText(this, "Failed to update geolocation requirement", Toast.LENGTH_SHORT).show();
                        });
            });

            // Use addSnapshotListener for real-time updates
            eventListener = db.collection("events").document(eventId)
                    .addSnapshotListener((documentSnapshot, error) -> {
                        if (error != null) {
                            Toast.makeText(this, "Failed to load event details: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        
                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            Event event = documentSnapshot.toObject(Event.class);
                            if (event != null) {
                                tvEventNameBig.setText(event.getEventName());
                                tvEventLocation.setText(event.getLocation());
                                tvEventNameDetail.setText(event.getEventName());
                                
                                // Format registration dates
                                String registrationDateText = "TBD";
                                if (event.getRegistrationStartDate() != null && event.getRegistrationEndDate() != null) {
                                    registrationDateText = dateFormat.format(event.getRegistrationStartDate()) + " to " + dateFormat.format(event.getRegistrationEndDate());
                                } else if (event.getRegistrationStartDate() != null) {
                                    registrationDateText = "From " + dateFormat.format(event.getRegistrationStartDate());
                                } else if (event.getRegistrationEndDate() != null) {
                                    registrationDateText = "Until " + dateFormat.format(event.getRegistrationEndDate());
                                }
                                tvEventDates.setText(registrationDateText);
                                
                                tvOrganizerLabel.setText("Organizer: " + event.getOrganizerId().substring(0, Math.min(event.getOrganizerId().length(), 5)) + "...");
                                tvWaitlistLabel.setText("Waitlist: " + event.getCurrentWaitlistCount() + "/" + event.getMaxWaitlist());
                                suppressSwitchCallback = true;
                                geolocationSwitch.setChecked(event.isGeolocationRequired());
                                suppressSwitchCallback = false;
                                
                                String description = event.getDescription() == null
                                        ? ""
                                        : event.getDescription().trim();
                                tvEventDescription.setText(description);
                            }
                        } else {
                            // Event was deleted or doesn't exist
                            Toast.makeText(this, "Event no longer exists", Toast.LENGTH_SHORT).show();
                            finish();
                        }
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
             if (eventId != null) {
                 Intent intent = new Intent(this, OrganizerEditEventActivity.class);
                 intent.putExtra("eventId", eventId);
                 startActivity(intent);
             } else {
                 Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
             }
        });
        
        findViewById(R.id.btn_view_waitlist).setOnClickListener(v -> {
             if (eventId != null) {
                 Intent intent = new Intent(this, OrganizerEntrantListActivity.class);
                 intent.putExtra("eventId", eventId);
                 startActivity(intent);
             } else {
                 Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
             }
        });
        
        findViewById(R.id.btn_registration_map).setOnClickListener(v -> {
             if (eventId != null) {
                 Intent intent = new Intent(this, OrganizerEventMapActivity.class);
                 intent.putExtra("eventId", eventId);
                 startActivity(intent);
             } else {
                 Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
             }
        });

        findViewById(R.id.btn_show_qr).setOnClickListener(v -> {
            // TODO: show QR code dialog
        });
    }
    
    /**
     * Removes snapshot listeners to avoid leaks.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detach the listener to prevent memory leaks
        if (eventListener != null) {
            eventListener.remove();
        }
    }
}
