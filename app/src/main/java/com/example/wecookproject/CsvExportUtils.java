package com.example.wecookproject;

import java.util.List;

/**
 * Utility methods for building CSV content safely.
 */
public final class CsvExportUtils {

    private CsvExportUtils() {
    }

    public static String buildEnrolledEntrantsCsv(List<OrganizerEnrolledEntrantItem> entrants) {
        StringBuilder builder = new StringBuilder();
        builder.append("Name,Email,Phone Number,Status\n");

        if (entrants == null) {
            return builder.toString();
        }

        for (OrganizerEnrolledEntrantItem item : entrants) {
            builder.append(escape(item == null ? "" : item.getDisplayName())).append(",");
            builder.append(escape(item == null ? "" : item.getEmail())).append(",");
            builder.append(escape(item == null ? "" : item.getPhoneNumber())).append(",");
            builder.append(escape("Enrolled")).append("\n");
        }

        return builder.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "\"\"";
        }

        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
