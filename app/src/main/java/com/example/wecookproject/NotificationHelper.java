package com.example.wecookproject;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralizes entrant inbox notification writes and read-state updates.
 */
public final class NotificationHelper {
    public static final String STATUS_UNREAD = "unread";
    public static final String STATUS_READ = "read";

    public static final String TYPE_MANUAL_WAITLIST_UPDATE = "manual_waitlist_update";
    public static final String TYPE_PRIVATE_INVITE = "private_invite";
    public static final String TYPE_LOTTERY_SELECTED = "lottery_selected";
    public static final String TYPE_REPLACEMENT_SELECTED = "replacement_selected";

    private NotificationHelper() { }

    /**
     * Sends one notification if the recipient exists and has not disabled notifications.
     */
    public static Task<Boolean> sendEventNotification(FirebaseFirestore db,
                                                      String recipientId,
                                                      String senderId,
                                                      String eventId,
                                                      String eventName,
                                                      String location,
                                                      String message,
                                                      String type,
                                                      String actionTarget) {
        if (db == null || isBlank(recipientId)) {
            return Tasks.forResult(false);
        }

        return db.collection("users").document(recipientId).get()
                .continueWithTask(userTask -> {
                    if (!userTask.isSuccessful() || userTask.getResult() == null || !userTask.getResult().exists()) {
                        return Tasks.forResult(false);
                    }

                    Boolean notificationsEnabled = userTask.getResult().getBoolean("notificationsEnabled");
                    if (Boolean.FALSE.equals(notificationsEnabled)) {
                        return Tasks.forResult(false);
                    }

                    Map<String, Object> notification = new HashMap<>();
                    notification.put("eventId", safeValue(eventId));
                    notification.put("eventName", safeValue(eventName));
                    notification.put("location", safeValue(location));
                    notification.put("message", safeValue(message));
                    notification.put("recipientId", recipientId);
                    notification.put("senderId", safeValue(senderId));
                    notification.put("status", STATUS_UNREAD);
                    notification.put("type", safeValue(type));
                    notification.put("actionTarget", isBlank(actionTarget) ? safeValue(eventId) : actionTarget.trim());
                    notification.put("createdAt", Timestamp.now());
                    notification.put("readAt", null);

                    return db.collection("users")
                            .document(recipientId)
                            .collection("notifications")
                            .document()
                            .set(notification, SetOptions.merge())
                            .continueWith(writeTask -> writeTask.isSuccessful());
                });
    }

    /**
     * Marks a recipient notification as read.
     */
    public static Task<Void> markAsRead(FirebaseFirestore db, String recipientId, String notificationId) {
        if (db == null || isBlank(recipientId) || isBlank(notificationId)) {
            return Tasks.forResult(null);
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", STATUS_READ);
        updates.put("readAt", FieldValue.serverTimestamp());

        return db.collection("users")
                .document(recipientId)
                .collection("notifications")
                .document(notificationId)
                .set(updates, SetOptions.merge());
    }

    private static String safeValue(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
