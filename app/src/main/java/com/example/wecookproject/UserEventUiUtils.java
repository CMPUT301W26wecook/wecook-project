package com.example.wecookproject;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Utility helpers for user-event UI rendering.
 */
public final class UserEventUiUtils {
    private static final String DATE_TIME_WITH_ZONE_PATTERN = "yyyy-MM-dd HH:mm z";
    private static final String EVENT_TIME_WITH_ZONE_PATTERN = "MMM d, yyyy h:mm a z";
    public static final String STATUS_OPEN = "open";
    public static final String STATUS_FULL = "full";

    /**
     * Utility class constructor.
     */
    private UserEventUiUtils() {
    }

    /**
     * Returns the avatar letter derived from event name.
     *
     * @param eventName source event name
     * @return first uppercase letter, or {@code E} when name is blank
     */
    public static String getAvatarLetter(String eventName) {
        if (eventName == null || eventName.trim().isEmpty()) {
            return "E";
        }
        return String.valueOf(Character.toUpperCase(eventName.trim().charAt(0)));
    }

    /**
     * Formats registration date range for display.
     *
     * @param startDate registration start timestamp
     * @param endDate registration end timestamp
     * @return formatted date range label
     */
    public static String formatDateRange(Timestamp startDate, Timestamp endDate) {
        if (startDate == null && endDate == null) {
            return "Registration dates unavailable";
        }

        if (startDate == null) {
            return "Until " + formatRegistrationTimestamp(endDate);
        }

        if (endDate == null) {
            return "Starts " + formatRegistrationTimestamp(startDate);
        }

        return formatRegistrationTimestamp(startDate) + " - " + formatRegistrationTimestamp(endDate);
    }

    /**
     * Formats one registration timestamp using the device locale and time zone.
     *
     * @param timestamp timestamp to format
     * @return formatted date string
     */
    public static String formatRegistrationTimestamp(Timestamp timestamp) {
        return formatTimestamp(timestamp, DATE_TIME_WITH_ZONE_PATTERN);
    }

    /**
     * Formats one registration date using the device locale and time zone.
     *
     * @param date date to format
     * @return formatted registration string
     */
    public static String formatRegistrationDate(Date date) {
        return formatDate(date, DATE_TIME_WITH_ZONE_PATTERN);
    }

    /**
     * Formats one event timestamp using the device locale and time zone.
     *
     * @param timestamp timestamp to format
     * @return formatted event-time string
     */
    public static String formatEventTimestamp(Timestamp timestamp) {
        return formatTimestamp(timestamp, EVENT_TIME_WITH_ZONE_PATTERN);
    }

    /**
     * Formats one event date using the device locale and time zone.
     *
     * @param date date to format
     * @return formatted event-time string
     */
    public static String formatEventDate(Date date) {
        return formatDate(date, EVENT_TIME_WITH_ZONE_PATTERN);
    }

    /**
     * Formats one timestamp using the device locale and time zone.
     *
     * @param timestamp timestamp to format
     * @param pattern date-time pattern to apply
     * @return formatted date string
     */
    private static String formatTimestamp(Timestamp timestamp, String pattern) {
        if (timestamp == null) {
            return "";
        }

        return formatDate(timestamp.toDate(), pattern);
    }

    /**
     * Formats one java.util.Date using the device locale and time zone.
     *
     * @param date date to format
     * @param pattern date-time pattern to apply
     * @return formatted date string
     */
    private static String formatDate(Date date, String pattern) {
        if (date == null) {
            return "";
        }

        SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.getDefault());
        formatter.setTimeZone(TimeZone.getDefault());
        return formatter.format(date);
    }

    /**
     * Builds waitlist summary text.
     *
     * @param eventRecord event snapshot model
     * @return waitlist summary label
     */
    public static String formatWaitlistSummary(UserEventRecord eventRecord) {
        if (eventRecord.getMaxWaitlist() > 0) {
            return "Waitlist: " + eventRecord.getCurrentWaitlistCount() + "/" + eventRecord.getMaxWaitlist();
        }
        return "Waitlist: " + eventRecord.getCurrentWaitlistCount();
    }

    /**
     * Returns user-facing description text.
     *
     * @param eventRecord event snapshot model
     * @return description string
     */
    public static String buildDescription(UserEventRecord eventRecord) {
        String description = eventRecord.getDescription();
        Timestamp eventTime = eventRecord.getEventTime();
        if (eventTime == null) {
            return description;
        }
        return "Event time: " + formatEventTimestamp(eventTime) + "\n\n" + description;
    }

    /**
     * Applies status-chip text and colors.
     *
     * @param textView target view
     * @param status status key
     * @param invitedAsPicked true to display invited status as "Picked"
     */
    public static void applyStatusChip(TextView textView, String status, boolean invitedAsPicked) {
        if (status == null || status.trim().isEmpty()) {
            textView.setVisibility(View.GONE);
            return;
        }

        textView.setVisibility(View.VISIBLE);
        textView.setText(getStatusLabel(status, invitedAsPicked));

        int backgroundColor;
        int textColor;

        switch (status) {
            case UserEventRecord.STATUS_WAITLISTED:
                backgroundColor = Color.parseColor("#E8F0FE");
                textColor = Color.parseColor("#2F5FB3");
                break;
            case UserEventRecord.STATUS_WAITLIST_INVITED:
                backgroundColor = Color.parseColor("#FFF4DD");
                textColor = Color.parseColor("#8A5A00");
                break;
            case UserEventRecord.STATUS_INVITED:
                backgroundColor = Color.parseColor("#DFF3E4");
                textColor = Color.parseColor("#2F7A3E");
                break;
            case UserEventRecord.STATUS_ACCEPTED:
                backgroundColor = Color.parseColor("#ECE3FF");
                textColor = Color.parseColor("#5B3E96");
                break;
            case UserEventRecord.STATUS_REJECTED:
            case STATUS_FULL:
                backgroundColor = Color.parseColor("#FDECEC");
                textColor = Color.parseColor("#B3261E");
                break;
            case STATUS_OPEN:
            default:
                backgroundColor = Color.parseColor("#DFF3E4");
                textColor = Color.parseColor("#2F7A3E");
                break;
        }

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(backgroundColor);
        drawable.setCornerRadius(dpToPx(textView.getContext(), 10));
        textView.setBackground(drawable);
        textView.setTextColor(textColor);
    }

    /**
     * Maps status key to display text.
     *
     * @param status status key
     * @param invitedAsPicked true to label invited state as "Picked"
     * @return user-facing status label
     */
    public static String getStatusLabel(String status, boolean invitedAsPicked) {
        switch (status) {
            case UserEventRecord.STATUS_WAITLISTED:
                return "Waitlisted";
            case UserEventRecord.STATUS_WAITLIST_INVITED:
                return "Waitlist Invite";
            case UserEventRecord.STATUS_INVITED:
                return invitedAsPicked ? "Picked" : "Invited";
            case UserEventRecord.STATUS_ACCEPTED:
                return "Accepted";
            case UserEventRecord.STATUS_REJECTED:
                return "Rejected";
            case STATUS_FULL:
                return "Full";
            case STATUS_OPEN:
            default:
                return "Open";
        }
    }

    /**
     * Converts density-independent pixels to physical pixels.
     *
     * @param context context used to access display metrics
     * @param dp density-independent pixel value
     * @return converted pixel value
     */
    private static float dpToPx(Context context, int dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }
}
