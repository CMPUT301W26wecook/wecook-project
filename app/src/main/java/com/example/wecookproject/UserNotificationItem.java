package com.example.wecookproject;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Entrant notification row model.
 */
public class UserNotificationItem {
    private final String id;
    private final String eventName;
    private final String location;
    private final String message;
    private final String status;
    private final Date createdAt;

    public UserNotificationItem(String id,
                                String eventName,
                                String location,
                                String message,
                                String status,
                                Date createdAt) {
        this.id = id;
        this.eventName = eventName;
        this.location = location;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static UserNotificationItem fromSnapshot(DocumentSnapshot snapshot) {
        Timestamp ts = snapshot.getTimestamp("createdAt");
        return new UserNotificationItem(
                snapshot.getId(),
                value(snapshot.getString("eventName"), "Event Update"),
                value(snapshot.getString("location"), ""),
                value(snapshot.getString("message"), ""),
                value(snapshot.getString("status"), "unread"),
                ts == null ? null : ts.toDate()
        );
    }

    public String getEventName() {
        return eventName;
    }

    public String getLocation() {
        return location;
    }

    public String getMessage() {
        return message;
    }

    public String getStatus() {
        return status;
    }

    public String getFormattedTime() {
        if (createdAt == null) {
            return "Unknown time";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(createdAt);
    }

    public String getId() {
        return id;
    }

    private static String value(String input, String fallback) {
        return input == null || input.trim().isEmpty() ? fallback : input.trim();
    }
}
