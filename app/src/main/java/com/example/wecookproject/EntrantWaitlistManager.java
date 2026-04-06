package com.example.wecookproject;

import android.location.Location;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared waitlist transaction helpers for entrant flows.
 */
public final class EntrantWaitlistManager {
    public static final String FIELD_PRIVATE_WAITLIST_INVITEE_IDS = "privateWaitlistInviteeIds";

    private EntrantWaitlistManager() {
    }

    /**
     * Transactionally joins one entrant to an event waitlist.
     */
    public static Task<JoinResult> joinWaitlist(FirebaseFirestore db,
                                                String entrantId,
                                                String eventId,
                                                Location entrantLocation) {
        if (db == null || isBlank(entrantId) || isBlank(eventId)) {
            return Tasks.forException(new IllegalArgumentException("Missing event details"));
        }

        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference historyRef = db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(eventId);

        return db.runTransaction(transaction -> {
            DocumentSnapshot eventSnapshot = transaction.get(eventRef);
            if (!eventSnapshot.exists()) {
                throw new IllegalStateException("Event not found");
            }

            DocumentSnapshot historySnapshot = transaction.get(historyRef);
            String historyStatus = safe(historySnapshot.getString("status"));
            if (!historyStatus.isEmpty()
                    && !UserEventRecord.STATUS_WAITLIST_INVITED.equals(historyStatus)) {
                throw new IllegalStateException("You are not eligible to join this waiting list");
            }

            Timestamp now = Timestamp.now();
            Timestamp registrationStartDate = eventSnapshot.getTimestamp("registrationStartDate");
            Timestamp registrationEndDate = eventSnapshot.getTimestamp("registrationEndDate");
            if (registrationStartDate == null || registrationEndDate == null
                    || registrationStartDate.compareTo(now) > 0
                    || registrationEndDate.compareTo(now) < 0) {
                throw new IllegalStateException("Registration is closed for this event");
            }

            List<String> waitlistEntrants = new ArrayList<>(
                    FirestoreFieldUtils.getStringList(eventSnapshot, "waitlistEntrantIds"));
            if (waitlistEntrants.contains(entrantId)) {
                throw new IllegalStateException("You already joined this waiting list");
            }

            Long maxWaitlistValue = eventSnapshot.getLong("maxWaitlist");
            int maxWaitlist = maxWaitlistValue == null ? 0 : maxWaitlistValue.intValue();
            if (maxWaitlist > 0 && waitlistEntrants.size() >= maxWaitlist) {
                throw new IllegalStateException("This waiting list is full");
            }

            Boolean geolocationRequiredValue = eventSnapshot.getBoolean("geolocationRequired");
            boolean geolocationRequired = geolocationRequiredValue == null || geolocationRequiredValue;
            Object existingEntrantLocation = eventSnapshot.get("waitlistEntrantLocations." + entrantId);
            if (geolocationRequired
                    && entrantLocation == null
                    && !hasStoredEntrantLocation(existingEntrantLocation)) {
                throw new IllegalStateException("Location is required to join this waitlist");
            }

            waitlistEntrants.add(entrantId);

            Map<String, Object> eventUpdates = new HashMap<>();
            eventUpdates.put("waitlistEntrantIds", waitlistEntrants);
            eventUpdates.put("currentWaitlistCount", waitlistEntrants.size());
            eventUpdates.put(FIELD_PRIVATE_WAITLIST_INVITEE_IDS, FieldValue.arrayRemove(entrantId));
            if (entrantLocation != null) {
                eventUpdates.put(
                        "waitlistEntrantLocations." + entrantId,
                        buildEntrantLocationHistory(existingEntrantLocation, entrantLocation)
                );
            }
            transaction.update(eventRef, eventUpdates);
            transaction.set(
                    historyRef,
                    UserEventHistoryHelper.buildHistoryData(eventSnapshot, UserEventRecord.STATUS_WAITLISTED),
                    SetOptions.merge()
            );

            return new JoinResult(waitlistEntrants);
        });
    }

    /**
     * Removes one pending private waitlist invite for an entrant.
     */
    public static Task<Void> declinePrivateWaitlistInvite(FirebaseFirestore db,
                                                          String entrantId,
                                                          String eventId) {
        if (db == null || isBlank(entrantId) || isBlank(eventId)) {
            return Tasks.forException(new IllegalArgumentException("Missing event details"));
        }

        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference historyRef = db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(eventId);

        return db.runTransaction(transaction -> {
            DocumentSnapshot eventSnapshot = transaction.get(eventRef);
            if (eventSnapshot.exists()) {
                transaction.update(
                        eventRef,
                        FIELD_PRIVATE_WAITLIST_INVITEE_IDS,
                        FieldValue.arrayRemove(entrantId)
                );
            }
            transaction.delete(historyRef);
            return null;
        });
    }

    /**
     * Transaction result for one successful waitlist join.
     */
    public static final class JoinResult {
        private final List<String> updatedWaitlistEntrantIds;

        private JoinResult(List<String> updatedWaitlistEntrantIds) {
            this.updatedWaitlistEntrantIds = updatedWaitlistEntrantIds == null
                    ? new ArrayList<>()
                    : new ArrayList<>(updatedWaitlistEntrantIds);
        }

        public List<String> getUpdatedWaitlistEntrantIds() {
            return new ArrayList<>(updatedWaitlistEntrantIds);
        }
    }

    private static boolean hasStoredEntrantLocation(Object existingEntrantLocation) {
        if (existingEntrantLocation instanceof GeoPoint) {
            return true;
        }
        if (existingEntrantLocation instanceof Map<?, ?>) {
            return !((Map<?, ?>) existingEntrantLocation).isEmpty();
        }
        return false;
    }

    private static Map<String, Object> buildEntrantLocationHistory(Object existingEntrantLocation,
                                                                   Location newLocation) {
        Map<String, Object> history = new HashMap<>();
        if (existingEntrantLocation instanceof GeoPoint) {
            history.put("1st location", existingEntrantLocation);
        } else if (existingEntrantLocation instanceof Map<?, ?>) {
            Map<?, ?> raw = (Map<?, ?>) existingEntrantLocation;
            if (isLegacyPointMap(raw)) {
                GeoPoint legacyPoint = mapToGeoPoint(raw);
                if (legacyPoint != null) {
                    history.put("1st location", legacyPoint);
                }
            } else {
                for (Map.Entry<?, ?> entry : raw.entrySet()) {
                    String key = entry.getKey() == null ? "" : entry.getKey().toString().trim();
                    if (!key.isEmpty()) {
                        history.put(key, entry.getValue());
                    }
                }
            }
        }

        int next = maxOrdinal(history.keySet()) + 1;
        history.put(
                formatOrdinal(next) + " location",
                new GeoPoint(newLocation.getLatitude(), newLocation.getLongitude())
        );
        return history;
    }

    private static boolean isLegacyPointMap(Map<?, ?> raw) {
        return (raw.containsKey("lat") && raw.containsKey("lng"))
                || (raw.containsKey("latitude") && raw.containsKey("longitude"));
    }

    private static GeoPoint mapToGeoPoint(Map<?, ?> raw) {
        Object lat = raw.get("lat");
        Object lng = raw.get("lng");
        if (!(lat instanceof Number) || !(lng instanceof Number)) {
            lat = raw.get("latitude");
            lng = raw.get("longitude");
        }
        if (lat instanceof Number && lng instanceof Number) {
            return new GeoPoint(((Number) lat).doubleValue(), ((Number) lng).doubleValue());
        }
        return null;
    }

    private static int maxOrdinal(Set<String> keys) {
        int max = 0;
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            int spaceIndex = normalized.indexOf(' ');
            String firstToken = spaceIndex >= 0 ? normalized.substring(0, spaceIndex) : normalized;
            int number = parseLeadingInt(firstToken);
            if (number > max) {
                max = number;
            }
        }
        return max;
    }

    private static int parseLeadingInt(String value) {
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(value.substring(0, end));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String formatOrdinal(int number) {
        int mod100 = number % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return number + "th";
        }
        switch (number % 10) {
            case 1:
                return number + "st";
            case 2:
                return number + "nd";
            case 3:
                return number + "rd";
            default:
                return number + "th";
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
