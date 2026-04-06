package com.example.wecookproject;

import com.google.firebase.Timestamp;

import java.util.Calendar;

/**
 * Shared filter and empty-state rules for entrant event discovery.
 */
public final class UserEventFilterLogic {
    public static final String CAPACITY_ALL = "Capacity: All";
    public static final String CAPACITY_SMALL = "Capacity: Small";
    public static final String CAPACITY_MEDIUM = "Capacity: Medium";
    public static final String CAPACITY_LARGE = "Capacity: Large";
    public static final String CAPACITY_VERY_LARGE = "Capacity: Very Large";

    public static final String AVAILABILITY_ALL = "Availability: All";
    public static final String AVAILABILITY_EARLY_MORNING = "Early Morning (06:00-09:59)";
    public static final String AVAILABILITY_MORNING = "Morning (10:00-11:59)";
    public static final String AVAILABILITY_AFTERNOON = "Afternoon (12:00-16:59)";
    public static final String AVAILABILITY_EVENING = "Evening (17:00-20:59)";
    public static final String AVAILABILITY_NIGHT = "Night (21:00-23:59)";

    public static final String ELIGIBILITY_ALL = "Eligibility: All";
    public static final String ELIGIBILITY_JOINABLE = "Eligibility: Joinable";

    public static final double KEYWORD_SCORE_THRESHOLD = 0.45d;

    private UserEventFilterLogic() {
    }

    public static String resolveEmptyStateMessage(String selectedCapacityLabel,
                                                  String selectedAvailabilityLabel,
                                                  String selectedEligibilityLabel,
                                                  String keywordQuery) {
        boolean capacityFiltered = !CAPACITY_ALL.equals(selectedCapacityLabel);
        boolean availabilityFiltered = !AVAILABILITY_ALL.equals(selectedAvailabilityLabel);
        boolean eligibilityFiltered = ELIGIBILITY_JOINABLE.equals(selectedEligibilityLabel);
        boolean searchingByKeyword = keywordQuery != null && !keywordQuery.trim().isEmpty();

        if (eligibilityFiltered) {
            if (searchingByKeyword) {
                return capacityFiltered || availabilityFiltered
                        ? "No joinable events match your keyword and filters."
                        : "No joinable events match your keyword.";
            }
            return capacityFiltered || availabilityFiltered
                    ? "No joinable events match the current filters."
                    : "No joinable events right now.";
        }
        if (searchingByKeyword) {
            return capacityFiltered || availabilityFiltered
                    ? "No events match your keyword and filters."
                    : "No events match your keyword.";
        }
        if (capacityFiltered || availabilityFiltered) {
            return "No events match the current filters.";
        }
        return "No events available yet.";
    }

    public static boolean matchesEligibilityFilter(String selectedEligibilityLabel,
                                                   UserEventRecord eventRecord,
                                                   Timestamp currentTime) {
        if (ELIGIBILITY_ALL.equals(selectedEligibilityLabel)) {
            return true;
        }
        return eventRecord.isJoinableAt(currentTime);
    }

    public static boolean matchesCapacityFilter(String selectedCapacityLabel, int capacity) {
        if (CAPACITY_ALL.equals(selectedCapacityLabel)) {
            return true;
        }
        if (capacity <= 0) {
            return false;
        }
        if (CAPACITY_SMALL.equals(selectedCapacityLabel)) {
            return capacity <= 20;
        }
        if (CAPACITY_MEDIUM.equals(selectedCapacityLabel)) {
            return capacity >= 21 && capacity <= 50;
        }
        if (CAPACITY_LARGE.equals(selectedCapacityLabel)) {
            return capacity >= 51 && capacity <= 100;
        }
        if (CAPACITY_VERY_LARGE.equals(selectedCapacityLabel)) {
            return capacity >= 101;
        }
        return true;
    }

    public static boolean matchesAvailabilityFilter(String selectedAvailabilityLabel, Timestamp eventTime) {
        if (AVAILABILITY_ALL.equals(selectedAvailabilityLabel)) {
            return true;
        }
        if (eventTime == null) {
            return false;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(eventTime.toDate());
        int minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);

        if (AVAILABILITY_EARLY_MORNING.equals(selectedAvailabilityLabel)) {
            return inMinuteRange(minutes, 6 * 60, 9 * 60 + 59);
        }
        if (AVAILABILITY_MORNING.equals(selectedAvailabilityLabel)) {
            return inMinuteRange(minutes, 10 * 60, 11 * 60 + 59);
        }
        if (AVAILABILITY_AFTERNOON.equals(selectedAvailabilityLabel)) {
            return inMinuteRange(minutes, 12 * 60, 16 * 60 + 59);
        }
        if (AVAILABILITY_EVENING.equals(selectedAvailabilityLabel)) {
            return inMinuteRange(minutes, 17 * 60, 20 * 60 + 59);
        }
        if (AVAILABILITY_NIGHT.equals(selectedAvailabilityLabel)) {
            return inMinuteRange(minutes, 21 * 60, 23 * 60 + 59);
        }
        return true;
    }

    private static boolean inMinuteRange(int value, int minInclusive, int maxInclusive) {
        return value >= minInclusive && value <= maxInclusive;
    }
}
