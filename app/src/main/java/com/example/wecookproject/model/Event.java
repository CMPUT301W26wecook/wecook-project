package com.example.wecookproject.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Event domain model stored in Firestore.
 */
public class Event {
    public static final String VISIBILITY_PUBLIC = "public";
    public static final String VISIBILITY_PRIVATE = "private";

    private String eventId;
    private String organizerId;
    private String eventName;
    private int capacity;
    private Date registrationStartDate;
    private Date registrationEndDate;
    private Date eventTime;
    private int maxWaitlist;
    private int currentWaitlistCount;
    private boolean geolocationRequired;
    private String location; // As seen in details "Edmonton"
    private String description;
    private String visibilityTag = VISIBILITY_PUBLIC;

    private String posterUrl;
    private String posterDeleteUrl;
    private String qrCodePath;
    private List<String> waitlistEntrantIds = new ArrayList<>();
    private List<String> selectedEntrantIds = new ArrayList<>();
    private List<String> replacementEntrantIds = new ArrayList<>();
    private Integer lotteryCount = 0;

    /**
     * Empty constructor required by Firestore deserialization.
     */
    public Event() {
    }

    /**
     * Creates an event model.
     *
     * @param eventId event identifier
     * @param organizerId organizer identifier
     * @param eventName event name
     * @param registrationStartDate registration start date
     * @param registrationEndDate registration end date
     * @param eventTime scheduled event time
     * @param maxWaitlist waitlist capacity
     * @param currentWaitlistCount current waitlist size
     * @param geolocationRequired geolocation requirement flag
     * @param location event location label
     * @param description event description
     */
    public Event(String eventId, String organizerId, String eventName, Date registrationStartDate, Date registrationEndDate,
                 Date eventTime,
                 int maxWaitlist, int currentWaitlistCount,
                 boolean geolocationRequired, String location, String description) {
        this(eventId, organizerId, eventName, registrationStartDate, registrationEndDate, eventTime,
                maxWaitlist, currentWaitlistCount, geolocationRequired, location, description, null, null);
    }

    /**
     * Creates an event model with poster hosting metadata.
     *
     * @param eventId event identifier
     * @param organizerId organizer identifier
     * @param eventName event name
     * @param registrationStartDate registration start date
     * @param registrationEndDate registration end date
     * @param eventTime scheduled event time
     * @param maxWaitlist waitlist capacity
     * @param currentWaitlistCount current waitlist size
     * @param geolocationRequired geolocation requirement flag
     * @param location event location label
     * @param description event description
     * @param posterPath poster path/url
     * @param posterDeleteUrl hosted poster deletion URL
     */
    public Event(String eventId, String organizerId, String eventName, Date registrationStartDate, Date registrationEndDate,
                 Date eventTime,
                 int maxWaitlist, int currentWaitlistCount,
                 boolean geolocationRequired, String location, String description,
                 String posterPath, String posterDeleteUrl) {
        this.eventId = eventId;
        this.organizerId = organizerId;
        this.eventName = eventName;
        this.registrationStartDate = registrationStartDate;
        this.registrationEndDate = registrationEndDate;
        this.eventTime = eventTime;
        this.maxWaitlist = maxWaitlist;
        this.currentWaitlistCount = currentWaitlistCount;
        this.geolocationRequired = geolocationRequired;
        this.location = location;
        this.description = description;
        this.posterUrl = posterPath;
        this.posterDeleteUrl = posterDeleteUrl;
    }

    /**
     * Backward-compatible constructor for call sites that do not yet pass event time.
     *
     * @param eventId event identifier
     * @param organizerId organizer identifier
     * @param eventName event name
     * @param registrationStartDate registration start date
     * @param registrationEndDate registration end date
     * @param maxWaitlist waitlist capacity
     * @param currentWaitlistCount current waitlist size
     * @param geolocationRequired geolocation requirement flag
     * @param location event location label
     * @param description event description
     */
    public Event(String eventId, String organizerId, String eventName, Date registrationStartDate, Date registrationEndDate,
                 int maxWaitlist, int currentWaitlistCount,
                 boolean geolocationRequired, String location, String description) {
        this(eventId, organizerId, eventName, registrationStartDate, registrationEndDate, null,
                maxWaitlist, currentWaitlistCount, geolocationRequired, location, description);
    }

    /**
     * Backward-compatible constructor for call sites that do not yet pass event time but do pass
     * poster hosting metadata.
     *
     * @param eventId event identifier
     * @param organizerId organizer identifier
     * @param eventName event name
     * @param registrationStartDate registration start date
     * @param registrationEndDate registration end date
     * @param maxWaitlist waitlist capacity
     * @param currentWaitlistCount current waitlist size
     * @param geolocationRequired geolocation requirement flag
     * @param location event location label
     * @param description event description
     * @param posterPath poster path/url
     * @param posterDeleteUrl hosted poster deletion URL
     */
    public Event(String eventId, String organizerId, String eventName, Date registrationStartDate, Date registrationEndDate,
                 int maxWaitlist, int currentWaitlistCount,
                 boolean geolocationRequired, String location, String description,
                 String posterPath, String posterDeleteUrl) {
        this(eventId, organizerId, eventName, registrationStartDate, registrationEndDate, null,
                maxWaitlist, currentWaitlistCount, geolocationRequired, location, description,
                posterPath, posterDeleteUrl);
    }

    /**
     * @return event identifier
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * @param eventId event identifier
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * @return organizer identifier
     */
    public String getOrganizerId() {
        return organizerId;
    }

    /**
     * @param organizerId organizer identifier
     */
    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    /**
     * @return event name
     */
    public String getEventName() {
        return eventName;
    }

    /**
     * @param eventName event name
     */
    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    /**
     * @return event capacity
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * @param capacity event capacity
     */
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    /**
     * @return registration start date
     */
    public Date getRegistrationStartDate() {
        return registrationStartDate;
    }

    /**
     * @param registrationStartDate registration start date
     */
    public void setRegistrationStartDate(Date registrationStartDate) {
        this.registrationStartDate = registrationStartDate;
    }

    /**
     * @return registration end date
     */
    public Date getRegistrationEndDate() {
        return registrationEndDate;
    }

    /**
     * @param registrationEndDate registration end date
     */
    public void setRegistrationEndDate(Date registrationEndDate) {
        this.registrationEndDate = registrationEndDate;
    }

    /**
     * @return scheduled event time
     */
    public Date getEventTime() {
        return eventTime;
    }

    /**
     * @param eventTime scheduled event time
     */
    public void setEventTime(Date eventTime) {
        this.eventTime = eventTime;
    }

    /**
     * @return maximum waitlist capacity
     */
    public int getMaxWaitlist() {
        return maxWaitlist;
    }

    /**
     * @param maxWaitlist maximum waitlist capacity
     */
    public void setMaxWaitlist(int maxWaitlist) {
        this.maxWaitlist = maxWaitlist;
    }

    /**
     * @return current waitlist count
     */
    public int getCurrentWaitlistCount() {
        return currentWaitlistCount;
    }

    /**
     * @param currentWaitlistCount current waitlist count
     */
    public void setCurrentWaitlistCount(int currentWaitlistCount) {
        this.currentWaitlistCount = currentWaitlistCount;
    }

    /**
     * @return true when geolocation is required
     */
    public boolean isGeolocationRequired() {
        return geolocationRequired;
    }

    /**
     * @param geolocationRequired geolocation requirement flag
     */
    public void setGeolocationRequired(boolean geolocationRequired) {
        this.geolocationRequired = geolocationRequired;
    }

    /**
     * @return event location label
     */
    public String getLocation() {
        return location;
    }

    /**
     * @param location event location label
     */
    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * @return event description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description event description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return visibility tag ("public" or "private")
     */
    public String getVisibilityTag() {
        return visibilityTag == null || visibilityTag.trim().isEmpty()
                ? VISIBILITY_PUBLIC
                : visibilityTag;
    }

    /**
     * @param visibilityTag visibility tag ("public" or "private")
     */
    public void setVisibilityTag(String visibilityTag) {
        if (visibilityTag == null || visibilityTag.trim().isEmpty()) {
            this.visibilityTag = VISIBILITY_PUBLIC;
            return;
        }
        String normalized = visibilityTag.trim().toLowerCase();
        if (VISIBILITY_PRIVATE.equals(normalized)) {
            this.visibilityTag = VISIBILITY_PRIVATE;
            return;
        }
        this.visibilityTag = VISIBILITY_PUBLIC;
    }

    /**
     * @return poster path/url
     */
    public String getPosterPath() {
        return posterUrl;
    }

    /**
     * @param posterPath poster path/url
     */
    public void setPosterPath(String posterPath) {
        this.posterUrl = posterPath;
    }

    /**
     * @return hosted poster deletion URL, or {@code null}
     */
    public String getPosterDeleteUrl() {
        return posterDeleteUrl;
    }

    /**
     * @param posterDeleteUrl hosted poster deletion URL
     */
    public void setPosterDeleteUrl(String posterDeleteUrl) {
        this.posterDeleteUrl = posterDeleteUrl;
    }

    /**
     * @return qr code path/url
     */
    public String getQrCodePath() {
        return qrCodePath;
    }

    /**
     * @param qrCodePath qr code path/url
     */
    public void setQrCodePath(String qrCodePath) {
        this.qrCodePath = qrCodePath;
    }

    /**
     * @return waitlist entrant ids
     */
    public List<String> getWaitlistEntrantIds() {
        return waitlistEntrantIds;
    }

    /**
     * @param waitlistEntrantIds waitlist entrant ids
     */
    public void setWaitlistEntrantIds(List<String> waitlistEntrantIds) {
        this.waitlistEntrantIds = waitlistEntrantIds;
    }

    /**
     * @return selected entrant ids
     */
    public List<String> getSelectedEntrantIds() {
        return selectedEntrantIds;
    }

    /**
     * @param selectedEntrantIds selected entrant ids
     */
    public void setSelectedEntrantIds(List<String> selectedEntrantIds) {
        this.selectedEntrantIds = selectedEntrantIds;
    }

    /**
     * @return replacement entrant ids
     */
    public List<String> getReplacementEntrantIds() {
        return replacementEntrantIds;
    }

    /**
     * @param replacementEntrantIds replacement entrant ids
     */
    public void setReplacementEntrantIds(List<String> replacementEntrantIds) {
        this.replacementEntrantIds = replacementEntrantIds;
    }

    /**
     * @return lottery draw count
     */
    public Integer getLotteryCount() {
        return lotteryCount;
    }

    /**
     * @param lotteryCount lottery draw count
     */
    public void setLotteryCount(Integer lotteryCount) {
        this.lotteryCount = lotteryCount;
    }
}
