package com.example.wecookproject;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Immutable model representing one user history entry.
 */
public class UserHistoryItem {
    private final String eventId;
    private final String eventName;
    private final String organizerId;
    private final String organizerName;
    private final String posterUrl;
    private final String status;
    private final Timestamp eventTime;
    private final Timestamp registrationStartDate;
    private final Timestamp registrationEndDate;
    private final boolean deleted;

    /**
     * Creates a history item.
     *
     * @param eventId event identifier
     * @param eventName event name
     * @param location event location label
     * @param posterUrl poster URL/path
     * @param status history status
     * @param registrationStartDate registration start timestamp
     * @param registrationEndDate registration end timestamp
     */
    public UserHistoryItem(String eventId,
                           String eventName,
                           String organizerId,
                           String organizerName,
                           String posterUrl,
                           String status,
                           Timestamp eventTime,
                           Timestamp registrationStartDate,
                           Timestamp registrationEndDate,
                           boolean deleted) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.organizerId = organizerId;
        this.organizerName = organizerName;
        this.posterUrl = posterUrl;
        this.status = status;
        this.eventTime = eventTime;
        this.registrationStartDate = registrationStartDate;
        this.registrationEndDate = registrationEndDate;
        this.deleted = deleted;
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
                value(snapshot.getString("organizerId"), ""),
                value(snapshot.getString("organizerName"), "Organizer"),
                posterPath(snapshot),
                value(snapshot.getString("status"), UserEventRecord.STATUS_WAITLISTED),
                snapshot.getTimestamp("eventTime"),
                snapshot.getTimestamp("registrationStartDate"),
                snapshot.getTimestamp("registrationEndDate"),
                Boolean.TRUE.equals(snapshot.getBoolean("eventDeleted"))
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
    public String getOrganizerId() {
        return organizerId;
    }

    /**
     * @return organizer display name
     */
    public String getOrganizerName() {
        return organizerName;
    }

    /**
     * @return poster path/url, or {@code null}
     */
    public String getPosterPath() {
        return posterUrl;
    }

    private static String getPosterUrl(com.google.firebase.firestore.DocumentSnapshot snapshot) {
        String url = snapshot.getString("posterUrl");
        if (url != null && !url.trim().isEmpty()) {
            return url;
        }
        return snapshot.getString("posterPath");
    }

    /**
     * @return stored history status
     */
    public String getStatus() {
        return status;
    }

    /**
     * @return stored event time, or {@code null}
     */
    public Timestamp getEventTime() {
        return eventTime;
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

    /**
     * @return true when the source event no longer exists
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Returns a copy with enriched organizer display data and deletion state.
     */
    public UserHistoryItem withResolvedState(String resolvedOrganizerName,
                                            Timestamp resolvedEventTime,
                                            boolean eventDeleted) {
        return new UserHistoryItem(
                eventId,
                eventName,
                organizerId,
                value(resolvedOrganizerName, organizerName),
                posterUrl,
                status,
                resolvedEventTime != null ? resolvedEventTime : eventTime,
                registrationStartDate,
                registrationEndDate,
                eventDeleted
        );
    }
}
