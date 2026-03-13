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

public final class UserEventUiUtils {
    public static final String STATUS_OPEN = "open";
    public static final String STATUS_FULL = "full";

    private UserEventUiUtils() {
    }

    public static String getAvatarLetter(String eventName) {
        if (eventName == null || eventName.trim().isEmpty()) {
            return "E";
        }
        return String.valueOf(Character.toUpperCase(eventName.trim().charAt(0)));
    }

    public static String formatDateRange(Timestamp startDate, Timestamp endDate) {
        if (startDate == null && endDate == null) {
            return "Registration dates unavailable";
        }

        if (startDate == null) {
            return "Until " + formatTimestamp(endDate);
        }

        if (endDate == null) {
            return "Starts " + formatTimestamp(startDate);
        }

        return formatTimestamp(startDate) + " - " + formatTimestamp(endDate);
    }

    private static String formatTimestamp(Timestamp timestamp) {
        Date date = timestamp.toDate();
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date);
    }

    public static String formatWaitlistSummary(UserEventRecord eventRecord) {
        if (eventRecord.getMaxWaitlist() > 0) {
            return "Waitlist: " + eventRecord.getCurrentWaitlistCount() + "/" + eventRecord.getMaxWaitlist();
        }
        return "Waitlist: " + eventRecord.getCurrentWaitlistCount();
    }

    public static String buildDescription(UserEventRecord eventRecord) {
        return eventRecord.getDescription();
    }

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

    public static String getStatusLabel(String status, boolean invitedAsPicked) {
        switch (status) {
            case UserEventRecord.STATUS_WAITLISTED:
                return "Waitlisted";
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

    private static float dpToPx(Context context, int dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }
}
