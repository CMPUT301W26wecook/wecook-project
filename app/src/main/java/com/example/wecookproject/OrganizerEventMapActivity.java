package com.example.wecookproject;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.android.material.switchmaterial.SwitchMaterial;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
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

public class OrganizerEventMapActivity extends AppCompatActivity {
    private static final String TAG = "OrganizerEventMap";
    private static final OnlineTileSourceBase OSM_XYZ_TILE_SOURCE = new XYTileSource(
            "OSM-XYZ",
            0,
            19,
            256,
            ".png",
            new String[]{
                    "https://a.tile.openstreetmap.org/",
                    "https://b.tile.openstreetmap.org/",
                    "https://c.tile.openstreetmap.org/"
            }
    );

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private MapView mapView;
    private FolderOverlay entrantMarkersOverlay;
    private String eventId;
    private SwitchMaterial geolocationSwitch;
    private boolean suppressSwitchCallback;

    /**
     * Initializes map screen, event context, and navigation actions.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences osmdroidPrefs = getSharedPreferences("osmdroid", MODE_PRIVATE);
        File osmdroidBase = new File(getCacheDir(), "osmdroid");
        File osmdroidTiles = new File(osmdroidBase, "tiles");
        if (!osmdroidBase.exists()) {
            //noinspection ResultOfMethodCallIgnored
            osmdroidBase.mkdirs();
        }
        if (!osmdroidTiles.exists()) {
            //noinspection ResultOfMethodCallIgnored
            osmdroidTiles.mkdirs();
        }
        Configuration.getInstance().setOsmdroidBasePath(osmdroidBase);
        Configuration.getInstance().setOsmdroidTileCache(osmdroidTiles);
        Configuration.getInstance().load(getApplicationContext(), osmdroidPrefs);
        Configuration.getInstance().setUserAgentValue(getPackageName() + "/1.0 (wecook-project)");
        setContentView(R.layout.activity_organizer_event_map);

        eventId = getIntent().getStringExtra("eventId");
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        geolocationSwitch = findViewById(R.id.switch_geolocation);
        geolocationSwitch.setEnabled(false);
        geolocationSwitch.setClickable(false);

        mapView = findViewById(R.id.map_view);
        if (mapView == null) {
            Toast.makeText(this, "Map initialization failed", Toast.LENGTH_SHORT).show();
        } else {
            mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            mapView.setTileSource(OSM_XYZ_TILE_SOURCE);
            Log.i(TAG, "Tile source set to: " + mapView.getTileProvider().getTileSource().name());
            mapView.setUseDataConnection(true);
            mapView.setTilesScaledToDpi(true);
            mapView.setMultiTouchControls(true);
            mapView.getController().setZoom(3.0);
            entrantMarkersOverlay = new FolderOverlay();
            mapView.getOverlays().add(entrantMarkersOverlay);
            logTileProbe();
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
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        findViewById(R.id.btn_show_qr).setOnClickListener(v -> {
            // TODO: show QR code dialog
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        loadEntrantLocations();
    }

    @Override
    protected void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    /**
     * Loads event header metadata and geolocation toggle state.
     */
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
                    geolocationSwitch.setChecked(geolocationRequired);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load event details", Toast.LENGTH_SHORT).show());
    }

    /**
     * Loads and renders entrant location markers on the map.
     */
    private void loadEntrantLocations() {
        if (mapView == null) {
            return;
        }

        db.collection("events").document(eventId).get()
                .addOnSuccessListener(this::renderEntrantLocations)
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load waitlist locations", Toast.LENGTH_SHORT).show());
    }

    /**
     * Renders entrant map markers when geolocation requirement is enabled.
     *
     * @param snapshot event snapshot
     */
    private void renderEntrantLocations(DocumentSnapshot snapshot) {
        if (!snapshot.exists()) {
            Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (entrantMarkersOverlay != null) {
            entrantMarkersOverlay.getItems().clear();
        }
        Boolean geolocationRequiredValue = snapshot.getBoolean("geolocationRequired");
        boolean geolocationRequired = geolocationRequiredValue == null || geolocationRequiredValue;
        if (!geolocationRequired) {
            Toast.makeText(this, "Enable geolocation requirement to view entrant pins", Toast.LENGTH_SHORT).show();
            mapView.invalidate();
            return;
        }

        List<MarkerData> markers = extractMarkers(snapshot.get("waitlistEntrantLocations"));
        if (markers.isEmpty()) {
            int waitlistCount = getListSize(snapshot.get("waitlistEntrantIds"));
            int selectedCount = getListSize(snapshot.get("selectedEntrantIds"));
            int acceptedCount = getListSize(snapshot.get("acceptedEntrantIds"));
            int totalRegistrations = waitlistCount + selectedCount + acceptedCount;
            if (totalRegistrations > 0) {
                Toast.makeText(this,
                        "Found " + totalRegistrations + " registrations but 0 stored map coordinates",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "No waitlist locations yet", Toast.LENGTH_SHORT).show();
            }
            mapView.invalidate();
            return;
        }

        List<org.osmdroid.util.GeoPoint> points = new ArrayList<>();
        for (MarkerData marker : markers) {
            org.osmdroid.util.GeoPoint geoPoint = new org.osmdroid.util.GeoPoint(marker.latitude, marker.longitude);
            Marker markerOverlay = new Marker(mapView);
            markerOverlay.setPosition(geoPoint);
            markerOverlay.setTitle(marker.title);
            if (entrantMarkersOverlay != null) {
                entrantMarkersOverlay.add(markerOverlay);
            }
            points.add(geoPoint);
        }

        if (markers.size() == 1) {
            MarkerData single = markers.get(0);
            mapView.getController().animateTo(new org.osmdroid.util.GeoPoint(single.latitude, single.longitude));
            mapView.getController().setZoom(13.0);
        } else {
            BoundingBox boundingBox = BoundingBox.fromGeoPoints(points);
            double latSpan = Math.abs(boundingBox.getLatNorth() - boundingBox.getLatSouth());
            double lonSpan = Math.abs(boundingBox.getLonEast() - boundingBox.getLonWest());
            if (latSpan < 0.0005 && lonSpan < 0.0005) {
                org.osmdroid.util.GeoPoint center = points.get(0);
                mapView.getController().animateTo(center);
                mapView.getController().setZoom(14.0);
            } else {
                mapView.zoomToBoundingBox(boundingBox, true, 120);
            }
        }
        mapView.invalidate();
        Toast.makeText(this, "Loaded " + markers.size() + " entrant location pin(s)", Toast.LENGTH_SHORT).show();
    }

    /**
     * Extracts map markers from raw Firestore location payload.
     *
     * @param rawLocations raw waitlistEntrantLocations object
     * @return marker data list
     */
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
                if (!(latObj instanceof Number) || !(lngObj instanceof Number)) {
                    latObj = nestedMap.get("latitude");
                    lngObj = nestedMap.get("longitude");
                }
                if (latObj instanceof Number && lngObj instanceof Number) {
                    geoPoint = new GeoPoint(((Number) latObj).doubleValue(), ((Number) lngObj).doubleValue());
                }
            }

            if (geoPoint != null) {
                String suffix = entrantId.length() > 6 ? entrantId.substring(entrantId.length() - 6) : entrantId;
                String cityCountry = TestingLocationPool.cityCountryLabel(
                        this,
                        geoPoint.getLatitude(),
                        geoPoint.getLongitude()
                );
                markers.add(new MarkerData(
                        geoPoint.getLatitude(),
                        geoPoint.getLongitude(),
                        "Entrant " + suffix + ": " + cityCountry
                ));
            }
        }
        return markers;
    }

    private int getListSize(Object value) {
        if (!(value instanceof List<?>)) {
            return 0;
        }
        return ((List<?>) value).size();
    }

    private void logTileProbe() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://tile.openstreetmap.org/0/0/0.png");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                connection.setRequestProperty("User-Agent", getPackageName() + "/1.0 (wecook-project)");
                int code = connection.getResponseCode();
                Log.i(TAG, "Tile probe HTTP status: " + code);
            } catch (Exception e) {
                Log.e(TAG, "Tile probe failed", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    /**
     * Lightweight marker DTO.
     */
    private static class MarkerData {
        private final double latitude;
        private final double longitude;
        private final String title;

        /**
         * Creates marker data for map rendering.
         *
         * @param latitude marker latitude
         * @param longitude marker longitude
         * @param title marker title
         */
        private MarkerData(double latitude, double longitude, String title) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.title = title;
        }
    }
}
