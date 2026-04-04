package com.example.wecookproject;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Testing helper for generating and labeling randomized country-based locations.
 */
public final class TestingLocationPool {
    private static final double JITTER_DEGREES = 0.35;
    private static final int COUNTRY_LOOKUP_ATTEMPTS = 12;
    private static final Random RANDOM = new Random();
    private static final String[] ISO_COUNTRY_CODES = Locale.getISOCountries();
    private static final Map<String, LatLng> COUNTRY_CENTER_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> LABEL_CACHE = new ConcurrentHashMap<>();

    private TestingLocationPool() {
    }

    public static Location createRandomCountryLocation(Context context) {
        if (Geocoder.isPresent() && context != null) {
            for (int i = 0; i < COUNTRY_LOOKUP_ATTEMPTS; i++) {
                String code = ISO_COUNTRY_CODES[RANDOM.nextInt(ISO_COUNTRY_CODES.length)];
                String countryName = new Locale.Builder().setRegion(code).build()
                        .getDisplayCountry(Locale.ENGLISH);
                LatLng center = resolveCountryCenter(context, countryName);
                if (center == null) {
                    continue;
                }
                double latitude = center.latitude + ((RANDOM.nextDouble() - 0.5) * JITTER_DEGREES);
                double longitude = center.longitude + ((RANDOM.nextDouble() - 0.5) * JITTER_DEGREES);
                Location location = new Location("random-country-seeded");
                location.setLatitude(clamp(latitude, -89.9999, 89.9999));
                location.setLongitude(normalizeLongitude(longitude));
                return location;
            }
        }

        Location fallback = new Location("random-country-fallback");
        fallback.setLatitude(-60.0 + (120.0 * RANDOM.nextDouble()));
        fallback.setLongitude(-180.0 + (360.0 * RANDOM.nextDouble()));
        return fallback;
    }

    public static String cityCountryLabel(Context context, double latitude, double longitude) {
        if (!Geocoder.isPresent() || context == null) {
            return "Unknown location";
        }
        String cacheKey = round(latitude) + "," + round(longitude);
        String cached = LABEL_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            Geocoder geocoder = new Geocoder(context, Locale.ENGLISH);
            List<Address> addresses = geocoderGetFromLocation(geocoder, latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String city = firstNonBlank(
                        address.getLocality(),
                        address.getSubAdminArea(),
                        address.getAdminArea(),
                        "Unknown city"
                );
                String country = firstNonBlank(address.getCountryName(), "Unknown country");
                String label = city + ", " + country;
                LABEL_CACHE.put(cacheKey, label);
                return label;
            }
        } catch (IOException ignored) {
            // Fall through to unknown label.
        }
        return "Unknown location";
    }

    /**
     * Synchronous geocoding used by test helpers. The blocking {@link Geocoder#getFromLocation}
     * overload remains the practical choice here; the API 33+ listener-based API would require
     * a full async refactor of callers to avoid main-thread deadlock when blocking for results.
     */
    @SuppressWarnings("deprecation")
    private static List<Address> geocoderGetFromLocation(Geocoder geocoder,
                                                         double latitude,
                                                         double longitude,
                                                         int maxResults) throws IOException {
        return geocoder.getFromLocation(latitude, longitude, maxResults);
    }

    @SuppressWarnings("deprecation")
    private static List<Address> geocoderGetFromLocationName(Geocoder geocoder,
                                                             String locationName,
                                                             int maxResults) throws IOException {
        return geocoder.getFromLocationName(locationName, maxResults);
    }

    private static LatLng resolveCountryCenter(Context context, String countryName) {
        LatLng cached = COUNTRY_CENTER_CACHE.get(countryName);
        if (cached != null) {
            return cached;
        }
        try {
            Geocoder geocoder = new Geocoder(context, Locale.ENGLISH);
            List<Address> addresses = geocoderGetFromLocationName(geocoder, countryName, 1);
            if (addresses == null || addresses.isEmpty()) {
                return null;
            }
            Address address = addresses.get(0);
            LatLng center = new LatLng(address.getLatitude(), address.getLongitude());
            COUNTRY_CENTER_CACHE.put(countryName, center);
            return center;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static double normalizeLongitude(double longitude) {
        double normalized = longitude;
        while (normalized > 180.0) {
            normalized -= 360.0;
        }
        while (normalized < -180.0) {
            normalized += 360.0;
        }
        return normalized;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static final class LatLng {
        private final double latitude;
        private final double longitude;

        private LatLng(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
