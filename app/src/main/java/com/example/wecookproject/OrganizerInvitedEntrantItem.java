package com.example.wecookproject;

public class OrganizerInvitedEntrantItem {
    private final String entrantId;
    private final String displayName;
    private final String status;
    private boolean selected = true;

    public OrganizerInvitedEntrantItem(String entrantId, String displayName, String status) {
        this.entrantId = entrantId;
        this.displayName = displayName;
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

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
