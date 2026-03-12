package com.example.wecookproject.model;

import java.util.List;
import java.util.ArrayList;

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

    private String posterPath;
    private String qrCodePath;
    private List<String> waitlistEntrantIds = new ArrayList<>();
    private List<String> selectedEntrantIds = new ArrayList<>();
    private List<String> replacementEntrantIds = new ArrayList<>();
    private Integer lotteryCount = 0;

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

    public String getPosterPath() {
        return posterPath;
    }

    public void setPosterPath(String posterPath) {
        this.posterPath = posterPath;
    }

    public String getQrCodePath() {
        return qrCodePath;
    }

    public void setQrCodePath(String qrCodePath) {
        this.qrCodePath = qrCodePath;
    }

    public List<String> getWaitlistEntrantIds() {
        return waitlistEntrantIds;
    }

    public void setWaitlistEntrantIds(List<String> waitlistEntrantIds) {
        this.waitlistEntrantIds = waitlistEntrantIds;
    }

    public List<String> getSelectedEntrantIds() {
        return selectedEntrantIds;
    }

    public void setSelectedEntrantIds(List<String> selectedEntrantIds) {
        this.selectedEntrantIds = selectedEntrantIds;
    }

    public List<String> getReplacementEntrantIds() {
        return replacementEntrantIds;
    }

    public void setReplacementEntrantIds(List<String> replacementEntrantIds) {
        this.replacementEntrantIds = replacementEntrantIds;
    }

    public Integer getLotteryCount() {
        return lotteryCount;
    }

    public void setLotteryCount(Integer lotteryCount) {
        this.lotteryCount = lotteryCount;
    }
}
