package com.example.wecookproject;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Centralizes waitlist redraw behavior when open spots need to be refilled.
 */
public final class WaitlistLotteryHelper {
    private static final String REPLACEMENT_NOTIFICATION_MESSAGE =
            "A spot opened up and you have been selected from the waitlist. Please confirm your participation in the app.";

    private WaitlistLotteryHelper() {
    }

    /**
     * Selects replacements from the waitlist to fill any open invited spots and writes follow-up
     * notifications/history for the chosen entrants.
     */
    public static Task<ReplacementDrawResult> fillOpenSpotsFromWaitlist(FirebaseFirestore db,
                                                                        String eventId) {
        if (db == null || isBlank(eventId)) {
            return Tasks.forResult(ReplacementDrawResult.empty());
        }

        DocumentReference eventReference = db.collection("events").document(eventId.trim());
        return db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(eventReference);
            if (!snapshot.exists()) {
                return ReplacementDrawResult.empty();
            }

            Long lotteryCountValue = snapshot.getLong("lotteryCount");
            int lotteryCount = lotteryCountValue == null ? 0 : lotteryCountValue.intValue();
            if (lotteryCount <= 0) {
                return ReplacementDrawResult.empty();
            }

            List<String> waitlist = new ArrayList<>(FirestoreFieldUtils.getStringList(snapshot, "waitlistEntrantIds"));
            List<String> selected = new ArrayList<>(FirestoreFieldUtils.getStringList(snapshot, "selectedEntrantIds"));
            List<String> replacements = new ArrayList<>(FirestoreFieldUtils.getStringList(snapshot, "replacementEntrantIds"));
            List<String> declined = new ArrayList<>(FirestoreFieldUtils.getStringList(snapshot, "declinedEntrantIds"));

            int vacancies = lotteryCount - selected.size();
            if (vacancies <= 0) {
                return ReplacementDrawResult.empty();
            }

            List<String> pool = new ArrayList<>(waitlist);
            pool.removeAll(declined);
            pool.removeAll(selected);
            pool.removeAll(replacements);
            if (pool.isEmpty()) {
                return ReplacementDrawResult.empty();
            }

            java.util.Collections.shuffle(pool);
            int drawCount = Math.min(vacancies, pool.size());
            List<String> drawn = new ArrayList<>(pool.subList(0, drawCount));

            selected.addAll(drawn);
            replacements.addAll(drawn);
            waitlist.removeAll(drawn);

            transaction.update(eventReference,
                    "selectedEntrantIds", selected,
                    "replacementEntrantIds", replacements,
                    "waitlistEntrantIds", waitlist,
                    "currentWaitlistCount", waitlist.size(),
                    "declinedEntrantIds", FieldValue.arrayRemove(drawn.toArray()));

            return ReplacementDrawResult.fromSnapshot(snapshot, drawn, waitlist, selected, replacements);
        }).continueWithTask(transactionTask -> {
            if (!transactionTask.isSuccessful()) {
                Exception error = transactionTask.getException();
                return error == null ? Tasks.forResult(ReplacementDrawResult.empty()) : Tasks.forException(error);
            }

            ReplacementDrawResult result = transactionTask.getResult();
            if (result == null || !result.hasDrawnEntrants()) {
                return Tasks.forResult(result == null ? ReplacementDrawResult.empty() : result);
            }

            List<Task<?>> postTasks = buildPostProcessingTasks(db, result);
            if (postTasks.isEmpty()) {
                return Tasks.forResult(result);
            }

            return Tasks.whenAll(postTasks).continueWith(done -> result);
        });
    }

    private static List<Task<?>> buildPostProcessingTasks(FirebaseFirestore db, ReplacementDrawResult result) {
        List<Task<?>> postTasks = new ArrayList<>();
        for (String entrantId : result.getDrawnEntrantIds()) {
            postTasks.add(writeInvitedHistory(db, entrantId, result));
            postTasks.add(NotificationHelper.sendEventNotification(
                    db,
                    entrantId,
                    result.getOrganizerId(),
                    result.getEventId(),
                    result.getEventName(),
                    result.getLocation(),
                    REPLACEMENT_NOTIFICATION_MESSAGE,
                    NotificationHelper.TYPE_REPLACEMENT_SELECTED,
                    result.getEventId()
            ));
        }

        if (!isBlank(result.getOrganizerId())) {
            String organizerMessage = String.format(
                    Locale.getDefault(),
                    "%d waitlisted entrant(s) were moved into the invited roster after spaces opened up.",
                    result.getDrawnEntrantIds().size()
            );
            postTasks.add(NotificationHelper.sendEventNotification(
                    db,
                    result.getOrganizerId(),
                    result.getOrganizerId(),
                    result.getEventId(),
                    result.getEventName(),
                    result.getLocation(),
                    organizerMessage,
                    NotificationHelper.TYPE_ROSTER_UPDATED,
                    result.getEventId()
            ));
        }

        return postTasks;
    }

    private static Task<Void> writeInvitedHistory(FirebaseFirestore db,
                                                  String entrantId,
                                                  ReplacementDrawResult result) {
        Map<String, Object> historyData = new HashMap<>();
        historyData.put("eventId", result.getEventId());
        historyData.put("eventName", result.getEventName());
        historyData.put("location", result.getLocation());
        historyData.put("organizerId", result.getOrganizerId());
        historyData.put("organizerName", "");
        historyData.put("posterUrl", result.getPosterPath());
        historyData.put("posterPath", result.getPosterPath());
        historyData.put("eventTime", result.getEventTime());
        historyData.put("registrationStartDate", result.getRegistrationStartDate());
        historyData.put("registrationEndDate", result.getRegistrationEndDate());
        historyData.put("description", result.getDescription());
        historyData.put("status", UserEventRecord.STATUS_INVITED);
        historyData.put("eventDeleted", false);
        historyData.put("updatedAt", FieldValue.serverTimestamp());

        return db.collection("users")
                .document(entrantId)
                .collection("eventHistory")
                .document(result.getEventId())
                .set(historyData, SetOptions.merge());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Result data returned from a redraw transaction.
     */
    public static final class ReplacementDrawResult {
        private final String eventId;
        private final String eventName;
        private final String location;
        private final String organizerId;
        private final String description;
        private final String posterPath;
        private final Object eventTime;
        private final Object registrationStartDate;
        private final Object registrationEndDate;
        private final List<String> drawnEntrantIds;
        private final List<String> updatedWaitlistEntrantIds;
        private final List<String> updatedSelectedEntrantIds;
        private final List<String> updatedReplacementEntrantIds;

        private ReplacementDrawResult(String eventId,
                                      String eventName,
                                      String location,
                                      String organizerId,
                                      String description,
                                      String posterPath,
                                      Object eventTime,
                                      Object registrationStartDate,
                                      Object registrationEndDate,
                                      List<String> drawnEntrantIds,
                                      List<String> updatedWaitlistEntrantIds,
                                      List<String> updatedSelectedEntrantIds,
                                      List<String> updatedReplacementEntrantIds) {
            this.eventId = eventId;
            this.eventName = eventName;
            this.location = location;
            this.organizerId = organizerId;
            this.description = description;
            this.posterPath = posterPath;
            this.eventTime = eventTime;
            this.registrationStartDate = registrationStartDate;
            this.registrationEndDate = registrationEndDate;
            this.drawnEntrantIds = drawnEntrantIds;
            this.updatedWaitlistEntrantIds = updatedWaitlistEntrantIds;
            this.updatedSelectedEntrantIds = updatedSelectedEntrantIds;
            this.updatedReplacementEntrantIds = updatedReplacementEntrantIds;
        }

        public static ReplacementDrawResult empty() {
            return new ReplacementDrawResult(
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    null,
                    null,
                    null,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>()
            );
        }

        public static ReplacementDrawResult fromSnapshot(DocumentSnapshot snapshot,
                                                         List<String> drawnEntrantIds,
                                                         List<String> updatedWaitlistEntrantIds,
                                                         List<String> updatedSelectedEntrantIds,
                                                         List<String> updatedReplacementEntrantIds) {
            String posterPath = safe(snapshot.getString("posterPath"));
            if (posterPath.isEmpty()) {
                posterPath = safe(snapshot.getString("posterUrl"));
            }

            return new ReplacementDrawResult(
                    snapshot.getId(),
                    safe(snapshot.getString("eventName")),
                    safe(snapshot.getString("location")),
                    safe(snapshot.getString("organizerId")),
                    safe(snapshot.getString("description")),
                    posterPath,
                    snapshot.getTimestamp("eventTime"),
                    snapshot.getTimestamp("registrationStartDate"),
                    snapshot.getTimestamp("registrationEndDate"),
                    new ArrayList<>(drawnEntrantIds),
                    new ArrayList<>(updatedWaitlistEntrantIds),
                    new ArrayList<>(updatedSelectedEntrantIds),
                    new ArrayList<>(updatedReplacementEntrantIds)
            );
        }

        public String getEventId() {
            return eventId;
        }

        public String getEventName() {
            return eventName;
        }

        public String getLocation() {
            return location;
        }

        public String getOrganizerId() {
            return organizerId;
        }

        public String getDescription() {
            return description;
        }

        public String getPosterPath() {
            return posterPath;
        }

        public Object getEventTime() {
            return eventTime;
        }

        public Object getRegistrationStartDate() {
            return registrationStartDate;
        }

        public Object getRegistrationEndDate() {
            return registrationEndDate;
        }

        public List<String> getDrawnEntrantIds() {
            return new ArrayList<>(drawnEntrantIds);
        }

        public List<String> getUpdatedWaitlistEntrantIds() {
            return new ArrayList<>(updatedWaitlistEntrantIds);
        }

        public List<String> getUpdatedSelectedEntrantIds() {
            return new ArrayList<>(updatedSelectedEntrantIds);
        }

        public List<String> getUpdatedReplacementEntrantIds() {
            return new ArrayList<>(updatedReplacementEntrantIds);
        }

        public boolean hasDrawnEntrants() {
            return !drawnEntrantIds.isEmpty();
        }

        private static String safe(String value) {
            return value == null ? "" : value.trim();
        }
    }
}