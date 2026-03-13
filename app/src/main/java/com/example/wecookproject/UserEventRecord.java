package com.example.wecookproject;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

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
    private final int maxWaitlist;
    private final String entrantId;
    private final Timestamp registrationStartDate;
    private final Timestamp registrationEndDate;

    private List<String> waitlistEntrantIds;
    private String historyStatus;

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
                           List<String> waitlistEntrantIds,
                           String historyStatus) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.location = location;
        this.organizerId = organizerId;
        this.description = description;
        this.posterPath = posterPath;
        this.maxWaitlist = maxWaitlist;
        this.entrantId = entrantId;
        this.registrationStartDate = registrationStartDate;
        this.registrationEndDate = registrationEndDate;
        this.waitlistEntrantIds = waitlistEntrantIds;
        this.historyStatus = historyStatus == null ? "" : historyStatus;
    }

    public static UserEventRecord fromEventSnapshot(DocumentSnapshot snapshot, String entrantId, String historyStatus) {
        List<String> waitlistEntrants = getStringList(snapshot, "waitlistEntrantIds");
        String resolvedStatus = historyStatus;
        if ((resolvedStatus == null || resolvedStatus.isEmpty()) && waitlistEntrants.contains(entrantId)) {
            resolvedStatus = STATUS_WAITLISTED;
        }

        Long maxWaitlistValue = snapshot.getLong("maxWaitlist");
        int maxWaitlist = maxWaitlistValue == null ? 0 : maxWaitlistValue.intValue();

        return new UserEventRecord(
                snapshot.getId(),
                getString(snapshot, "eventName", "Unnamed Event"),
                getString(snapshot, "location", "Location TBD"),
                getString(snapshot, "organizerId", "Unknown Organizer"),
                getString(snapshot, "description", "No event description available."),
                getString(snapshot, "posterPath", null),
                maxWaitlist,
                entrantId,
                snapshot.getTimestamp("registrationStartDate"),
                snapshot.getTimestamp("registrationEndDate"),
                waitlistEntrants,
                resolvedStatus
        );
    }

    private static String getString(DocumentSnapshot snapshot, String field, String fallback) {
        String value = snapshot.getString(field);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStringList(DocumentSnapshot snapshot, String field) {
        List<String> values = (List<String>) snapshot.get(field);
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
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

    public String getOrganizerId() {
        return organizerId;
    }

    public String getDescription() {
        return description;
    }

    public String getPosterPath() {
        return posterPath;
    }

    public int getMaxWaitlist() {
        return maxWaitlist;
    }

    public Timestamp getRegistrationStartDate() {
        return registrationStartDate;
    }

    public Timestamp getRegistrationEndDate() {
        return registrationEndDate;
    }

    public List<String> getWaitlistEntrantIds() {
        return new ArrayList<>(waitlistEntrantIds);
    }

    public int getCurrentWaitlistCount() {
        return waitlistEntrantIds.size();
    }

    public boolean isWaitlistFull() {
        return maxWaitlist > 0 && waitlistEntrantIds.size() >= maxWaitlist;
    }

    public boolean isEntrantOnWaitlist() {
        return waitlistEntrantIds.contains(entrantId);
    }

    public String getEffectiveStatus() {
        return historyStatus == null ? "" : historyStatus;
    }

    public void setWaitlistEntrantIds(List<String> waitlistEntrantIds) {
        this.waitlistEntrantIds = waitlistEntrantIds == null ? new ArrayList<>() : new ArrayList<>(waitlistEntrantIds);
    }

    public void setHistoryStatus(String historyStatus) {
        this.historyStatus = historyStatus == null ? "" : historyStatus;
    }
}
