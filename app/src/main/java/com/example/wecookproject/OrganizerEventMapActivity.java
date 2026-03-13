package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Activity intended to show organizers a map-oriented view related to an event. Within the app it
 * acts as the UI controller stub for the organizer event-map flow and provides navigation back into
 * the broader organizer feature set.
 *
 * Outstanding issues:
 * - The QR-code action is still a placeholder and does not yet present the expected organizer
 *   workflow.
 */

public class OrganizerEventMapActivity extends AppCompatActivity implements OnMapReadyCallback {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private GoogleMap googleMap;
    private String eventId;
    private SwitchMaterial geolocationSwitch;
    private boolean suppressSwitchCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_event_map);

        eventId = getIntent().getStringExtra("eventId");
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        geolocationSwitch = findViewById(R.id.switch_geolocation);
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

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Toast.makeText(this, "Map initialization failed", Toast.LENGTH_SHORT).show();
        }

        loadEventHeader();
        loadEntrantLocations();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_events);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_events) {
                startActivity(new Intent(this, OrganizerHomeActivity.class));
                return true;
            } else if (id == R.id.nav_create_events) {
                startActivity(new Intent(this, OrganizerCreateEventActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, OrganizerProfileActivity.class));
                return true;
            }
            return true;
        });

        findViewById(R.id.btn_back_to_event).setOnClickListener(v -> finish());

        findViewById(R.id.btn_show_qr).setOnClickListener(v -> {
            // TODO: show QR code dialog
        });
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMapToolbarEnabled(true);
        loadEntrantLocations();
    }

    private void loadEventHeader() {
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    TextView tvEventName = findViewById(R.id.tv_event_name);
                    TextView tvEventLocation = findViewById(R.id.tv_event_location);
                    tvEventName.setText(snapshot.getString("eventName"));
                    String location = snapshot.getString("location");
                    tvEventLocation.setText(location == null || location.trim().isEmpty() ? "Location TBD" : location);
                    Boolean geolocationRequiredValue = snapshot.getBoolean("geolocationRequired");
                    boolean geolocationRequired = geolocationRequiredValue == null || geolocationRequiredValue;
                    suppressSwitchCallback = true;
                    geolocationSwitch.setChecked(geolocationRequired);
                    suppressSwitchCallback = false;
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load event details", Toast.LENGTH_SHORT).show());
    }

    private void loadEntrantLocations() {
        if (googleMap == null) {
            return;
        }

        db.collection("events").document(eventId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    googleMap.clear();
                    List<MarkerData> markers = extractMarkers(snapshot.get("waitlistEntrantLocations"));
                    if (markers.isEmpty()) {
                        Toast.makeText(this, "No waitlist locations yet", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                    for (MarkerData marker : markers) {
                        LatLng latLng = new LatLng(marker.latitude, marker.longitude);
                        googleMap.addMarker(new MarkerOptions().position(latLng).title(marker.title));
                        boundsBuilder.include(latLng);
                    }

                    if (markers.size() == 1) {
                        MarkerData single = markers.get(0);
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(single.latitude, single.longitude), 13f));
                    } else {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120));
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load waitlist locations", Toast.LENGTH_SHORT).show());
    }

    private List<MarkerData> extractMarkers(Object rawLocations) {
        List<MarkerData> markers = new ArrayList<>();
        if (!(rawLocations instanceof Map<?, ?>)) {
            return markers;
        }

        Map<?, ?> locationsByEntrantId = (Map<?, ?>) rawLocations;
        for (Map.Entry<?, ?> entry : locationsByEntrantId.entrySet()) {
            String entrantId = Objects.toString(entry.getKey(), "");
            Object value = entry.getValue();
            GeoPoint geoPoint = null;

            if (value instanceof GeoPoint) {
                geoPoint = (GeoPoint) value;
            } else if (value instanceof Map<?, ?>) {
                Map<?, ?> nestedMap = (Map<?, ?>) value;
                Object latObj = nestedMap.get("lat");
                Object lngObj = nestedMap.get("lng");
                if (latObj instanceof Number && lngObj instanceof Number) {
                    geoPoint = new GeoPoint(((Number) latObj).doubleValue(), ((Number) lngObj).doubleValue());
                }
            }

            if (geoPoint != null) {
                String suffix = entrantId.length() > 6 ? entrantId.substring(entrantId.length() - 6) : entrantId;
                markers.add(new MarkerData(geoPoint.getLatitude(), geoPoint.getLongitude(), "Entrant " + suffix));
            }
        }
        return markers;
    }

    private static class MarkerData {
        private final double latitude;
        private final double longitude;
        private final String title;

        private MarkerData(double latitude, double longitude, String title) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.title = title;
        }
    }
}
