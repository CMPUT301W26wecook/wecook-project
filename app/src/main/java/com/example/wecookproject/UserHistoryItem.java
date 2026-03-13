package com.example.wecookproject;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

public class UserHistoryItem {
    private final String eventId;
    private final String eventName;
    private final String location;
    private final String posterPath;
    private final String status;
    private final Timestamp registrationStartDate;
    private final Timestamp registrationEndDate;

    public UserHistoryItem(String eventId,
                           String eventName,
                           String location,
                           String posterPath,
                           String status,
                           Timestamp registrationStartDate,
                           Timestamp registrationEndDate) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.location = location;
        this.posterPath = posterPath;
        this.status = status;
        this.registrationStartDate = registrationStartDate;
        this.registrationEndDate = registrationEndDate;
    }

    public static UserHistoryItem fromSnapshot(DocumentSnapshot snapshot) {
        return new UserHistoryItem(
                value(snapshot.getString("eventId"), snapshot.getId()),
                value(snapshot.getString("eventName"), "Unnamed Event"),
                value(snapshot.getString("location"), "Location TBD"),
                posterPath(snapshot),
                value(snapshot.getString("status"), UserEventRecord.STATUS_WAITLISTED),
                snapshot.getTimestamp("registrationStartDate"),
                snapshot.getTimestamp("registrationEndDate")
        );
    }

    private static String value(String text, String fallback) {
        return text == null || text.trim().isEmpty() ? fallback : text;
    }

    private static String posterPath(DocumentSnapshot snapshot) {
        String posterPath = snapshot.getString("posterPath");
        if (posterPath != null && !posterPath.trim().isEmpty()) {
            return posterPath;
        }
        String legacyPosterUrl = snapshot.getString("posterUrl");
        return legacyPosterUrl == null || legacyPosterUrl.trim().isEmpty() ? null : legacyPosterUrl;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public String getLocation() {
        return location;
    }

    public String getPosterPath() {
        return posterPath;
    }

    public String getStatus() {
        return status;
    }

    public Timestamp getRegistrationStartDate() {
        return registrationStartDate;
    }

    public Timestamp getRegistrationEndDate() {
        return registrationEndDate;
    }
}
