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
    private final String phoneNumber;
    private final String email;
    private final boolean entrantRole;
    private final boolean organizerRole;

    /**
     * Creates one organizer waitlist item.
     *
     * @param entrantId entrant identifier
     * @param displayName display name text
     * @param subtitle subtitle text
     */
    public OrganizerWaitlistItem(String entrantId, String displayName, String subtitle) {
        this(entrantId, displayName, subtitle, "", "", false, false);
    }

    /**
     * Creates one organizer waitlist item.
     *
     * @param entrantId entrant identifier
     * @param displayName display name text
     * @param subtitle subtitle text
     * @param phoneNumber entrant phone number
     */
    public OrganizerWaitlistItem(String entrantId, String displayName, String subtitle, String phoneNumber) {
        this(entrantId, displayName, subtitle, phoneNumber, "", false, false);
    }

    /**
     * Creates one organizer waitlist item.
     *
     * @param entrantId entrant identifier
     * @param displayName display name text
     * @param subtitle subtitle text
     * @param phoneNumber entrant phone number
     * @param email entrant email
     */
    public OrganizerWaitlistItem(String entrantId,
                                 String displayName,
                                 String subtitle,
                                 String phoneNumber,
                                 String email,
                                 boolean entrantRole,
                                 boolean organizerRole) {
        this.entrantId = entrantId;
        this.displayName = displayName;
        this.subtitle = subtitle;
        this.phoneNumber = safe(phoneNumber);
        this.email = safe(email);
        this.entrantRole = entrantRole;
        this.organizerRole = organizerRole;
    }

    /**
     * Maps a Firestore user snapshot to waitlist item data.
     *
     * @param entrantId entrant identifier
     * @param snapshot user profile snapshot
     * @return mapped waitlist item
     */
    public static OrganizerWaitlistItem fromSnapshot(String entrantId, DocumentSnapshot snapshot) {
        String firstName = safe(snapshot.getString("firstName"));
        String lastName = safe(snapshot.getString("lastName"));
        String city = safe(snapshot.getString("city"));
        String email = safe(snapshot.getString("email"));
        String phoneNumber = safe(snapshot.getString("phoneNumber"));
        Object rawRoles = snapshot.get("roles");
        boolean entrantRole = false;
        boolean organizerRole = false;
        if (rawRoles instanceof java.util.Map<?, ?>) {
            java.util.Map<?, ?> rolesMap = (java.util.Map<?, ?>) rawRoles;
            entrantRole = UserDocumentUtils.hasRole(rolesMap, UserDocumentUtils.ROLE_ENTRANT);
            organizerRole = UserDocumentUtils.hasRole(rolesMap, UserDocumentUtils.ROLE_ORGANIZER);
        } else {
            String legacyRole = safe(snapshot.getString("role"));
            entrantRole = UserDocumentUtils.ROLE_ENTRANT.equalsIgnoreCase(legacyRole);
            organizerRole = UserDocumentUtils.ROLE_ORGANIZER.equalsIgnoreCase(legacyRole);
        }

        String displayName = (firstName + " " + lastName).trim();
        if (displayName.isEmpty()) {
            displayName = entrantId;
        }

        String subtitle = !city.isEmpty() ? city : email;
        if (subtitle.isEmpty()) {
            subtitle = "Entrant ID: " + entrantId;
        }

        return new OrganizerWaitlistItem(
                entrantId,
                displayName,
                subtitle,
                phoneNumber,
                email,
                entrantRole,
                organizerRole
        );
    }

    /**
     * Creates fallback waitlist item when profile data is unavailable.
     *
     * @param entrantId entrant identifier
     * @return fallback item
     */
    public static OrganizerWaitlistItem fallback(String entrantId) {
        return new OrganizerWaitlistItem(entrantId, entrantId, "Entrant profile unavailable", "", "", false, false);
    }

    /**
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return entrant id
     */
    public String getEntrantId() {
        return entrantId;
    }

    /**
     * @return subtitle text
     */
    public String getSubtitle() {
        return subtitle;
    }

    /**
     * @return one-character avatar label
     */
    public String getAvatarLabel() {
        if (displayName == null || displayName.trim().isEmpty()) {
            return "?";
        }
        return displayName.trim().substring(0, 1).toUpperCase();
    }

    public boolean hasEntrantRole() {
        return entrantRole;
    }

    public boolean hasOrganizerRole() {
        return organizerRole;
    }

    /**
     * Checks whether this entrant matches a search query.
     *
     * @param query search text
     * @return true when matched by name, subtitle, or id
     */
    public boolean matches(String query) {
        String normalized = query == null ? "" : query.toLowerCase();
        return displayName.toLowerCase().contains(normalized)
                || subtitle.toLowerCase().contains(normalized)
                || phoneNumber.toLowerCase().contains(normalized)
                || email.toLowerCase().contains(normalized)
                || entrantId.toLowerCase().contains(normalized);
    }

    /**
     * Returns trimmed non-null text.
     *
     * @param value input text
     * @return trimmed text or empty string
     */
    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
