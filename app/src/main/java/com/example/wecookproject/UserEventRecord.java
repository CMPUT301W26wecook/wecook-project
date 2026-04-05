package com.example.wecookproject;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable-ish record used by entrant event screens.
 */
public class UserEventRecord {
    public static final String STATUS_WAITLISTED = "waitlisted";
    public static final String STATUS_INVITED = "invited";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";

    private final String eventId;
    private final String eventName;
    private final String location;
    private final String organizerId;
    private final String description;
    private final String posterPath;
    private final int capacity;
    private final int maxWaitlist;
    private final String entrantId;
    private final Timestamp registrationStartDate;
    private final Timestamp registrationEndDate;
    private final Timestamp eventTime;
    private final boolean geolocationRequired;

    private List<String> waitlistEntrantIds;
    private String historyStatus;

    /**
     * Creates an event record.
     *
     * @param eventId event identifier
     * @param eventName event name
     * @param location location label
     * @param organizerId organizer identifier
     * @param description event description
     * @param posterPath poster path/url
     * @param capacity event capacity
     * @param maxWaitlist waitlist capacity
     * @param entrantId current entrant identifier
     * @param registrationStartDate registration start timestamp
     * @param registrationEndDate registration end timestamp
     * @param eventTime event time timestamp
     * @param geolocationRequired geolocation requirement flag
     * @param waitlistEntrantIds current waitlist entrant ids
     * @param historyStatus current entrant history status
     */
    public UserEventRecord(String eventId,
                           String eventName,
                           String location,
                           String organizerId,
                           String description,
                           String posterPath,
                           int capacity,
                           int maxWaitlist,
                           String entrantId,
                           Timestamp registrationStartDate,
                           Timestamp registrationEndDate,
                           Timestamp eventTime,
                           boolean geolocationRequired,
                           List<String> waitlistEntrantIds,
                           String historyStatus) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.location = location;
        this.organizerId = organizerId;
        this.description = description;
        this.posterPath = posterPath;
        this.capacity = capacity;
        this.maxWaitlist = maxWaitlist;
        this.entrantId = entrantId;
        this.registrationStartDate = registrationStartDate;
        this.registrationEndDate = registrationEndDate;
        this.eventTime = eventTime;
        this.geolocationRequired = geolocationRequired;
        this.waitlistEntrantIds = waitlistEntrantIds == null ? new ArrayList<>() : new ArrayList<>(waitlistEntrantIds);
        this.historyStatus = historyStatus == null ? "" : historyStatus;
    }

    /**
     * Backward-compatible constructor for callers that do not pass event capacity.
     *
     * @param eventId event identifier
     * @param eventName event name
     * @param location location label
     * @param organizerId organizer identifier
     * @param description event description
     * @param posterPath poster path/url
     * @param maxWaitlist waitlist capacity
     * @param entrantId current entrant identifier
     * @param registrationStartDate registration start timestamp
     * @param registrationEndDate registration end timestamp
     * @param eventTime event time timestamp
     * @param geolocationRequired geolocation requirement flag
     * @param waitlistEntrantIds current waitlist entrant ids
     * @param historyStatus current entrant history status
     */
    public UserEventRecord(String eventId,
                           String eventName,
                           String location,
                           String organizerId,
                           String description,
                           String posterPath,
                           int maxWaitlist,
                           String entrantId,
                           Timestamp registrationStartDate,
                           Timestamp registrationEndDate,
                           Timestamp eventTime,
                           boolean geolocationRequired,
                           List<String> waitlistEntrantIds,
                           String historyStatus) {
        this(
                eventId,
                eventName,
                location,
                organizerId,
                description,
                posterPath,
                0,
                maxWaitlist,
                entrantId,
                registrationStartDate,
                registrationEndDate,
                eventTime,
                geolocationRequired,
                waitlistEntrantIds,
                historyStatus
        );
    }

    /**
     * Maps Firestore event data into a UI record.
     *
     * @param snapshot event document snapshot
     * @param entrantId current entrant id
     * @param historyStatus known history status
     * @return populated user event record
     */
    public static UserEventRecord fromEventSnapshot(DocumentSnapshot snapshot, String entrantId, String historyStatus) {
        List<String> waitlistEntrants = getStringList(snapshot, "waitlistEntrantIds");
        String resolvedStatus = historyStatus;
        if ((resolvedStatus == null || resolvedStatus.isEmpty()) && waitlistEntrants.contains(entrantId)) {
            resolvedStatus = STATUS_WAITLISTED;
        }

        Long maxWaitlistValue = snapshot.getLong("maxWaitlist");
        int maxWaitlist = maxWaitlistValue == null ? 0 : maxWaitlistValue.intValue();
        Long capacityValue = snapshot.getLong("capacity");
        int capacity = capacityValue == null ? 0 : capacityValue.intValue();

        return new UserEventRecord(
                snapshot.getId(),
                getString(snapshot, "eventName", "Unnamed Event"),
                getString(snapshot, "location", "Location TBD"),
                getString(snapshot, "organizerId", "Unknown Organizer"),
                getString(snapshot, "description", "No event description available."),
                getPosterPath(snapshot),
                capacity,
                maxWaitlist,
                entrantId,
                snapshot.getTimestamp("registrationStartDate"),
                snapshot.getTimestamp("registrationEndDate"),
                snapshot.getTimestamp("eventTime"),
                getBoolean(snapshot, "geolocationRequired", true),
                waitlistEntrants,
                resolvedStatus
        );
    }

    /**
     * Reads a non-blank string field with fallback.
     *
     * @param snapshot source document
     * @param field field name
     * @param fallback fallback value
     * @return resolved string value
     */
    private static String getString(DocumentSnapshot snapshot, String field, String fallback) {
        String value = snapshot.getString(field);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    /**
     * Resolves poster path from current and legacy keys.
     *
     * @param snapshot source document
     * @return poster path/url, or {@code null}
     */
    private static String getPosterPath(DocumentSnapshot snapshot) {
        String posterPath = snapshot.getString("posterPath");
        if (posterPath != null && !posterPath.trim().isEmpty()) {
            return posterPath;
        }
        String legacyPosterUrl = snapshot.getString("posterUrl");
        return legacyPosterUrl == null || legacyPosterUrl.trim().isEmpty() ? null : legacyPosterUrl;
    }

    /**
     * Reads a boolean with fallback.
     *
     * @param snapshot source document
     * @param field field name
     * @param fallback fallback value
     * @return resolved boolean value
     */
    private static boolean getBoolean(DocumentSnapshot snapshot, String field, boolean fallback) {
        Boolean value = snapshot.getBoolean(field);
        return value == null ? fallback : value;
    }

    /**
     * Reads a list of strings and returns a defensive copy.
     *
     * @param snapshot source document
     * @param field field name
     * @return string list (never {@code null})
     */
    private static List<String> getStringList(DocumentSnapshot snapshot, String field) {
        return FirestoreFieldUtils.getStringList(snapshot, field);
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
     * @return location label
     */
    public String getLocation() {
        return location;
    }

    /**
     * @return organizer identifier
     */
    public String getOrganizerId() {
        return organizerId;
    }

    /**
     * @return event description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return poster path/url, or {@code null}
     */
    public String getPosterPath() {
        return posterPath;
    }

    /**
     * @return event capacity
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * @return max waitlist capacity
     */
    public int getMaxWaitlist() {
        return maxWaitlist;
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
     * @return event time timestamp, or {@code null}
     */
    public Timestamp getEventTime() {
        return eventTime;
    }

    /**
     * @return true when geolocation is required for joining
     */
    public boolean isGeolocationRequired() {
        return geolocationRequired;
    }

    /**
     * @return defensive copy of waitlist entrant ids
     */
    public List<String> getWaitlistEntrantIds() {
        return new ArrayList<>(waitlistEntrantIds);
    }

    /**
     * @return current waitlist size
     */
    public int getCurrentWaitlistCount() {
        return waitlistEntrantIds.size();
    }

    /**
     * @return true when waitlist is at capacity
     */
    public boolean isWaitlistFull() {
        return maxWaitlist > 0 && waitlistEntrantIds.size() >= maxWaitlist;
    }

    /**
     * @return true when current entrant is in waitlist
     */
    public boolean isEntrantOnWaitlist() {
        return waitlistEntrantIds.contains(entrantId);
    }

    /**
     * @return true when the entrant can currently join the waitlist
     */
    public boolean isJoinableNow() {
        return isJoinableAt(Timestamp.now());
    }

    /**
     * Checks whether the entrant can join the waitlist at the provided timestamp.
     *
     * @param currentTime timestamp used for registration-window evaluation
     * @return true when the entrant is currently eligible to join
     */
    public boolean isJoinableAt(Timestamp currentTime) {
        if (currentTime == null) {
            return false;
        }
        if (!getEffectiveStatus().isEmpty()) {
            return false;
        }
        if (isEntrantOnWaitlist() || isWaitlistFull()) {
            return false;
        }
        if (registrationStartDate == null || registrationEndDate == null) {
            return false;
        }
        return registrationStartDate.compareTo(currentTime) <= 0
                && registrationEndDate.compareTo(currentTime) >= 0;
    }

    /**
     * @return effective history status, or empty string
     */
    public String getEffectiveStatus() {
        return historyStatus == null ? "" : historyStatus;
    }

    /**
     * Replaces waitlist entrants with a defensive copy.
     *
     * @param waitlistEntrantIds new waitlist ids
     */
    public void setWaitlistEntrantIds(List<String> waitlistEntrantIds) {
        this.waitlistEntrantIds = waitlistEntrantIds == null ? new ArrayList<>() : new ArrayList<>(waitlistEntrantIds);
    }

    /**
     * Updates history status.
     *
     * @param historyStatus new history status
     */
    public void setHistoryStatus(String historyStatus) {
        this.historyStatus = historyStatus == null ? "" : historyStatus;
    }
}
