package com.example.wecookproject.model;

public class Event {
    private String eventId;
    private String organizerId;
    private String eventName;
    private String registrationPeriod;
    private String enrollmentCriteria;
    private int maxWaitlist;
    private int currentWaitlistCount;
    private String lotteryMethodology;
    private boolean geolocationRequired;
    private String location; // As seen in details "Edmonton"
    private String description;

    // Required empty constructor for Firestore
    public Event() {
    }

    public Event(String eventId, String organizerId, String eventName, String registrationPeriod,
                 String enrollmentCriteria, int maxWaitlist, int currentWaitlistCount, String lotteryMethodology,
                 boolean geolocationRequired, String location, String description) {
        this.eventId = eventId;
        this.organizerId = organizerId;
        this.eventName = eventName;
        this.registrationPeriod = registrationPeriod;
        this.enrollmentCriteria = enrollmentCriteria;
        this.maxWaitlist = maxWaitlist;
        this.currentWaitlistCount = currentWaitlistCount;
        this.lotteryMethodology = lotteryMethodology;
        this.geolocationRequired = geolocationRequired;
        this.location = location;
        this.description = description;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getRegistrationPeriod() {
        return registrationPeriod;
    }

    public void setRegistrationPeriod(String registrationPeriod) {
        this.registrationPeriod = registrationPeriod;
    }

    public String getEnrollmentCriteria() {
        return enrollmentCriteria;
    }

    public void setEnrollmentCriteria(String enrollmentCriteria) {
        this.enrollmentCriteria = enrollmentCriteria;
    }

    public int getMaxWaitlist() {
        return maxWaitlist;
    }

    public void setMaxWaitlist(int maxWaitlist) {
        this.maxWaitlist = maxWaitlist;
    }

    public int getCurrentWaitlistCount() {
        return currentWaitlistCount;
    }

    public void setCurrentWaitlistCount(int currentWaitlistCount) {
        this.currentWaitlistCount = currentWaitlistCount;
    }

    public String getLotteryMethodology() {
        return lotteryMethodology;
    }

    public void setLotteryMethodology(String lotteryMethodology) {
        this.lotteryMethodology = lotteryMethodology;
    }

    public boolean isGeolocationRequired() {
        return geolocationRequired;
    }

    public void setGeolocationRequired(boolean geolocationRequired) {
        this.geolocationRequired = geolocationRequired;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
