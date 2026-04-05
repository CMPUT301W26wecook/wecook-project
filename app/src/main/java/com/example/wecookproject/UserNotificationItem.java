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
    private final String eventId;
    private final String eventName;
    private final String location;
    private final String message;
    private final String status;
    private final String type;
    private final String actionTarget;
    private final String senderId;
    private final String recipientId;
    private final Date createdAt;
    private final Date readAt;
    private final Date confirmedAt;

    public UserNotificationItem(String id,
                                String eventId,
                                String eventName,
                                String location,
                                String message,
                                String status,
                                String type,
                                String actionTarget,
                                String senderId,
                                String recipientId,
                                Date createdAt,
                                Date readAt,
                                Date confirmedAt) {
        this.id = id;
        this.eventId = eventId;
        this.eventName = eventName;
        this.location = location;
        this.message = message;
        this.status = status;
        this.type = type;
        this.actionTarget = actionTarget;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.createdAt = createdAt;
        this.readAt = readAt;
        this.confirmedAt = confirmedAt;
    }

    public static UserNotificationItem fromSnapshot(DocumentSnapshot snapshot) {
        Timestamp created = snapshot.getTimestamp("createdAt");
        Timestamp read = snapshot.getTimestamp("readAt");
        Timestamp confirmed = snapshot.getTimestamp("confirmedAt");
        return new UserNotificationItem(
                snapshot.getId(),
                value(snapshot.getString("eventId"), ""),
                value(snapshot.getString("eventName"), "Event Update"),
                value(snapshot.getString("location"), ""),
                value(snapshot.getString("message"), ""),
                value(snapshot.getString("status"), NotificationHelper.STATUS_UNREAD),
                value(snapshot.getString("type"), NotificationHelper.TYPE_MANUAL_WAITLIST_UPDATE),
                value(snapshot.getString("actionTarget"), value(snapshot.getString("eventId"), "")),
                value(snapshot.getString("senderId"), "Unknown Sender"),
                value(snapshot.getString("recipientId"), "Unknown Recipient"),
                created == null ? null : created.toDate(),
                read == null ? null : read.toDate(),
                confirmed == null ? null : confirmed.toDate()
        );
    }

    public String getEventName() {
        return eventName;
    }

    public String getEventId() {
        return eventId;
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

    public String getType() {
        return type;
    }

    public String getActionTarget() {
        return actionTarget;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public Date getCreatedAt() {
        return createdAt;
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

    public boolean isUnread() {
        return NotificationHelper.STATUS_UNREAD.equalsIgnoreCase(status);
    }

    public Date getReadAt() {
        return readAt;
    }

    public boolean isConfirmed() {
        return NotificationHelper.STATUS_CONFIRMED.equalsIgnoreCase(status) || confirmedAt != null;
    }

    public boolean requiresConfirmation() {
        return NotificationHelper.TYPE_PRIVATE_INVITE.equals(type)
                || NotificationHelper.TYPE_LOTTERY_SELECTED.equals(type)
                || NotificationHelper.TYPE_REPLACEMENT_SELECTED.equals(type);
    }

    private static String value(String input, String fallback) {
        return input == null || input.trim().isEmpty() ? fallback : input.trim();
    }
}
