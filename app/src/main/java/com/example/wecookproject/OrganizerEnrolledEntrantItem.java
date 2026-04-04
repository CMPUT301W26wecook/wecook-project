package com.example.wecookproject;

/**
 * Display row for an entrant confirmed on the event ({@code acceptedEntrantIds}).
 */
public class OrganizerEnrolledEntrantItem {
    private final String entrantId;
    private final String displayName;
    private final String phoneNumber;
    private final String email;

    public OrganizerEnrolledEntrantItem(String entrantId, String displayName, String phoneNumber, String email) {
        this.entrantId = entrantId == null ? "" : entrantId;
        this.displayName = displayName == null ? "" : displayName.trim();
        this.phoneNumber = phoneNumber == null ? "" : phoneNumber.trim();
        this.email = email == null ? "" : email.trim();
    }

    public String getEntrantId() {
        return entrantId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public boolean matchesQuery(String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isEmpty()) {
            return true;
        }
        return displayName.toLowerCase().contains(normalizedQuery)
                || phoneNumber.toLowerCase().contains(normalizedQuery)
                || email.toLowerCase().contains(normalizedQuery)
                || entrantId.toLowerCase().contains(normalizedQuery);
    }
}
