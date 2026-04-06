package com.example.wecookproject;

public class OrganizerInvitedEntrantItem {
    private final String entrantId;
    private final String displayName;
    private final String phoneNumber;
    private final String email;
    private final String status;
    private boolean selected = false;

    public OrganizerInvitedEntrantItem(String entrantId, String displayName, String status) {
        this(entrantId, displayName, "", "", status);
    }

    public OrganizerInvitedEntrantItem(String entrantId, String displayName, String phoneNumber, String status) {
        this(entrantId, displayName, phoneNumber, "", status);
    }

    public OrganizerInvitedEntrantItem(String entrantId, String displayName, String phoneNumber, String email, String status) {
        this.entrantId = entrantId;
        this.displayName = displayName;
        this.phoneNumber = phoneNumber == null ? "" : phoneNumber.trim();
        this.email = email == null ? "" : email.trim();
        this.status = status;
    }

    public String getEntrantId() {
        return entrantId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStatus() {
        return status;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
