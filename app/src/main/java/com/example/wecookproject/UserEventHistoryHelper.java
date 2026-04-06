package com.example.wecookproject;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared helpers for entrant event-history document payloads.
 */
public final class UserEventHistoryHelper {

    private UserEventHistoryHelper() {
    }

    /**
     * Builds event-history data from one event snapshot.
     */
    public static Map<String, Object> buildHistoryData(DocumentSnapshot eventSnapshot, String status) {
        Map<String, Object> historyData = new HashMap<>();
        if (eventSnapshot == null) {
            historyData.put("status", safe(status));
            historyData.put("updatedAt", FieldValue.serverTimestamp());
            return historyData;
        }

        String posterPath = resolvePosterPath(eventSnapshot);
        historyData.put("eventId", eventSnapshot.getId());
        historyData.put("eventName", safe(eventSnapshot.getString("eventName")));
        historyData.put("location", safe(eventSnapshot.getString("location")));
        historyData.put("organizerId", safe(eventSnapshot.getString("organizerId")));
        historyData.put("organizerName", "");
        historyData.put("posterPath", posterPath);
        historyData.put("posterUrl", posterPath);
        historyData.put("eventTime", eventSnapshot.getTimestamp("eventTime"));
        historyData.put("registrationStartDate", eventSnapshot.getTimestamp("registrationStartDate"));
        historyData.put("registrationEndDate", eventSnapshot.getTimestamp("registrationEndDate"));
        historyData.put("description", safe(eventSnapshot.getString("description")));
        historyData.put("status", safe(status));
        historyData.put("eventDeleted", false);
        historyData.put("updatedAt", FieldValue.serverTimestamp());
        return historyData;
    }

    /**
     * Builds event-history data from one event record.
     */
    public static Map<String, Object> buildHistoryData(UserEventRecord eventRecord, String status) {
        Map<String, Object> historyData = new HashMap<>();
        if (eventRecord == null) {
            historyData.put("status", safe(status));
            historyData.put("updatedAt", FieldValue.serverTimestamp());
            return historyData;
        }

        historyData.put("eventId", eventRecord.getEventId());
        historyData.put("eventName", safe(eventRecord.getEventName()));
        historyData.put("location", safe(eventRecord.getLocation()));
        historyData.put("organizerId", safe(eventRecord.getOrganizerId()));
        historyData.put("organizerName", "");
        historyData.put("posterPath", eventRecord.getPosterPath());
        historyData.put("posterUrl", eventRecord.getPosterPath());
        historyData.put("eventTime", eventRecord.getEventTime());
        historyData.put("registrationStartDate", eventRecord.getRegistrationStartDate());
        historyData.put("registrationEndDate", eventRecord.getRegistrationEndDate());
        historyData.put("description", safe(eventRecord.getDescription()));
        historyData.put("status", safe(status));
        historyData.put("eventDeleted", false);
        historyData.put("updatedAt", FieldValue.serverTimestamp());
        return historyData;
    }

    private static String resolvePosterPath(DocumentSnapshot snapshot) {
        String posterPath = safe(snapshot.getString("posterPath"));
        if (!posterPath.isEmpty()) {
            return posterPath;
        }
        return safe(snapshot.getString("posterUrl"));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
