package com.example.wecookproject;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Map;

/**
 * Shared helpers for reading user documents that support multiple roles.
 */
public final class UserDocumentUtils {

    public static final String ROLE_ENTRANT = "entrant";
    public static final String ROLE_ORGANIZER = "organizer";

    private UserDocumentUtils() {
    }

    public static boolean hasRole(DocumentSnapshot snapshot, String role) {
        if (snapshot == null || !snapshot.exists() || role == null || role.trim().isEmpty()) {
            return false;
        }

        Boolean explicitRole = snapshot.getBoolean("roles." + role);
        if (Boolean.TRUE.equals(explicitRole)) {
            return true;
        }

        String legacyRole = getSafeTrimmedString(snapshot, "role");
        return role.equalsIgnoreCase(legacyRole);
    }

    public static int getRoleCount(DocumentSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return 0;
        }

        int count = 0;
        Object rolesValue = snapshot.get("roles");
        if (rolesValue instanceof Map<?, ?>) {
            Map<?, ?> rolesMap = (Map<?, ?>) rolesValue;
            if (Boolean.TRUE.equals(rolesMap.get(ROLE_ENTRANT))) {
                count++;
            }
            if (Boolean.TRUE.equals(rolesMap.get(ROLE_ORGANIZER))) {
                count++;
            }
        }

        if (count == 0) {
            String legacyRole = getSafeTrimmedString(snapshot, "role");
            if (!legacyRole.isEmpty()) {
                count = 1;
            }
        }
        return count;
    }

    public static String getSafeTrimmedString(DocumentSnapshot snapshot, String field) {
        if (snapshot == null || field == null) {
            return "";
        }
        String value = snapshot.getString(field);
        return value == null ? "" : value.trim();
    }

    public static String buildDisplayName(DocumentSnapshot snapshot, String fallback) {
        return buildDisplayName(
                getSafeTrimmedString(snapshot, "firstName"),
                getSafeTrimmedString(snapshot, "lastName"),
                fallback
        );
    }

    public static String buildDisplayName(String firstName, String lastName, String fallback) {
        String fullName = ((firstName == null ? "" : firstName.trim()) + " "
                + (lastName == null ? "" : lastName.trim())).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }
        return fallback == null || fallback.trim().isEmpty() ? "Unknown user" : fallback.trim();
    }
}
