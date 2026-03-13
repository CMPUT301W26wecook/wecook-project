package com.example.wecookproject;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Immutable model representing one user history entry.
 */
public class UserHistoryItem {
    private final String eventId;
    private final String eventName;
    private final String location;
    private final String posterPath;
    private final String status;
    private final Timestamp registrationStartDate;
    private final Timestamp registrationEndDate;

    /**
     * Creates a history item.
     *
     * @param eventId event identifier
     * @param eventName event name
     * @param location event location label
     * @param posterPath poster URL/path
     * @param status history status
     * @param registrationStartDate registration start timestamp
     * @param registrationEndDate registration end timestamp
     */
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

    /**
     * Creates a {@link UserHistoryItem} from a Firestore snapshot.
     *
     * @param snapshot source history document
     * @return mapped history item with fallback values when fields are missing
     */
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

    /**
     * Returns non-empty text or fallback.
     *
     * @param text input text
     * @param fallback fallback text when input is blank
     * @return resolved text value
     */
    private static String value(String text, String fallback) {
        return text == null || text.trim().isEmpty() ? fallback : text;
    }

    /**
     * Resolves poster path from primary and legacy fields.
     *
     * @param snapshot source document
     * @return poster path/url, or {@code null} when missing
     */
    private static String posterPath(DocumentSnapshot snapshot) {
        String posterPath = snapshot.getString("posterPath");
        if (posterPath != null && !posterPath.trim().isEmpty()) {
            return posterPath;
        }
        String legacyPosterUrl = snapshot.getString("posterUrl");
        return legacyPosterUrl == null || legacyPosterUrl.trim().isEmpty() ? null : legacyPosterUrl;
    }

    /**
     * @return event identifier
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * @return event name
     */
    public String getEventName() {
        return eventName;
    }

    /**
     * @return event location label
     */
    public String getLocation() {
        return location;
    }

    /**
     * @return poster path/url, or {@code null}
     */
    public String getPosterPath() {
        return posterPath;
    }

    /**
     * @return stored history status
     */
    public String getStatus() {
        return status;
    }

    /**
     * @return registration start timestamp, or {@code null}
     */
    public Timestamp getRegistrationStartDate() {
        return registrationStartDate;
    }

    /**
     * @return registration end timestamp, or {@code null}
     */
    public Timestamp getRegistrationEndDate() {
        return registrationEndDate;
    }
}
