package com.example.wecookproject;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Lightweight model used to present entrant data in the organizer waitlist UI. Within the app it
 * acts as a view-model-style adapter object, converting Firestore user documents into display-ready
 * values for list rendering and search matching.
 *
 * Outstanding issues:
 * - Search and avatar-label behavior rely on simple string transformations and do not account for
 *   localization, or more robust name handling.
 */
public class OrganizerWaitlistItem {
    private final String entrantId;
    private final String displayName;
    private final String subtitle;

    public OrganizerWaitlistItem(String entrantId, String displayName, String subtitle) {
        this.entrantId = entrantId;
        this.displayName = displayName;
        this.subtitle = subtitle;
    }

    public static OrganizerWaitlistItem fromSnapshot(String entrantId, DocumentSnapshot snapshot) {
        String firstName = safe(snapshot.getString("firstName"));
        String lastName = safe(snapshot.getString("lastName"));
        String city = safe(snapshot.getString("city"));
        String email = safe(snapshot.getString("email"));

        String displayName = (firstName + " " + lastName).trim();
        if (displayName.isEmpty()) {
            displayName = entrantId;
        }

        String subtitle = !city.isEmpty() ? city : email;
        if (subtitle.isEmpty()) {
            subtitle = "Entrant ID: " + entrantId;
        }

        return new OrganizerWaitlistItem(entrantId, displayName, subtitle);
    }

    public static OrganizerWaitlistItem fallback(String entrantId) {
        return new OrganizerWaitlistItem(entrantId, entrantId, "Entrant profile unavailable");
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getAvatarLabel() {
        if (displayName == null || displayName.trim().isEmpty()) {
            return "?";
        }
        return displayName.trim().substring(0, 1).toUpperCase();
    }

    public boolean matches(String query) {
        String normalized = query == null ? "" : query.toLowerCase();
        return displayName.toLowerCase().contains(normalized)
                || subtitle.toLowerCase().contains(normalized)
                || entrantId.toLowerCase().contains(normalized);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
