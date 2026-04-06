package com.example.wecookproject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class UserEventFilterLogicTest {

    @Test
    public void resolveEmptyStateMessage_keywordAndExtraFilters_usesCombinedMessage() {
        String message = UserEventFilterLogic.resolveEmptyStateMessage(
                UserEventFilterLogic.CAPACITY_SMALL,
                UserEventFilterLogic.AVAILABILITY_MORNING,
                UserEventFilterLogic.ELIGIBILITY_ALL,
                "bbq"
        );

        assertEquals("No events match your keyword and filters.", message);
    }

    @Test
    public void matchesCapacityFilter_respectsBucketBoundaries() {
        assertTrue(UserEventFilterLogic.matchesCapacityFilter(UserEventFilterLogic.CAPACITY_SMALL, 20));
        assertFalse(UserEventFilterLogic.matchesCapacityFilter(UserEventFilterLogic.CAPACITY_SMALL, 21));
        assertTrue(UserEventFilterLogic.matchesCapacityFilter(UserEventFilterLogic.CAPACITY_VERY_LARGE, 150));
    }

    @Test
    public void matchesAvailabilityFilter_respectsTimeWindows() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 10);
        cal.set(Calendar.MINUTE, 30);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Timestamp morningTime = new Timestamp(cal.getTime());

        cal.set(Calendar.HOUR_OF_DAY, 17);
        cal.set(Calendar.MINUTE, 30);
        Timestamp eveningTime = new Timestamp(cal.getTime());

        assertTrue(UserEventFilterLogic.matchesAvailabilityFilter(
                UserEventFilterLogic.AVAILABILITY_MORNING,
                morningTime
        ));
        assertFalse(UserEventFilterLogic.matchesAvailabilityFilter(
                UserEventFilterLogic.AVAILABILITY_MORNING,
                eveningTime
        ));
    }

    @Test
    public void keywordAndFilterIntersection_returnsOnlyMatchingEvent() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 10);
        cal.set(Calendar.MINUTE, 30);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date morningEventDate = cal.getTime();

        Timestamp now = new Timestamp(new Date());
        List<UserEventRecord> events = Arrays.asList(
                record("event_bbq_small_morning", "Backyard Barbecue Social", "Community bbq grill meetup", 10, morningEventDate, now),
                record("event_bbq_large_morning", "City Barbecue Gathering", "Big barbecue event", 120, morningEventDate, now),
                record("event_hike_small_morning", "Trail Walk Group", "Morning hiking together", 10, morningEventDate, now)
        );

        List<String> resultIds = applyKeywordAndFilters(
                events,
                "bbq",
                UserEventFilterLogic.CAPACITY_SMALL,
                UserEventFilterLogic.AVAILABILITY_MORNING,
                UserEventFilterLogic.ELIGIBILITY_JOINABLE,
                now
        );

        assertEquals(Collections.singletonList("event_bbq_small_morning"), resultIds);
    }

    private List<String> applyKeywordAndFilters(List<UserEventRecord> events,
                                                String query,
                                                String selectedCapacity,
                                                String selectedAvailability,
                                                String selectedEligibility,
                                                Timestamp now) {
        List<ScoredRecord> scored = new ArrayList<>();
        for (UserEventRecord event : events) {
            if (!UserEventFilterLogic.matchesCapacityFilter(selectedCapacity, event.getCapacity())) {
                continue;
            }
            if (!UserEventFilterLogic.matchesAvailabilityFilter(selectedAvailability, event.getEventTime())) {
                continue;
            }
            if (!UserEventFilterLogic.matchesEligibilityFilter(selectedEligibility, event, now)) {
                continue;
            }
            double score = EventSearchMatcher.score(query, event);
            if (score >= UserEventFilterLogic.KEYWORD_SCORE_THRESHOLD) {
                scored.add(new ScoredRecord(event.getEventId(), score));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredRecord::getScore).reversed());
        List<String> ids = new ArrayList<>();
        for (ScoredRecord item : scored) {
            ids.add(item.getId());
        }
        return ids;
    }

    private UserEventRecord record(String id,
                                   String name,
                                   String description,
                                   int capacity,
                                   Date eventTime,
                                   Timestamp now) {
        Timestamp registrationStart = new Timestamp(new Date(now.toDate().getTime() - 60_000L));
        Timestamp registrationEnd = new Timestamp(new Date(now.toDate().getTime() + 60_000L));
        return new UserEventRecord(
                id,
                name,
                "Edmonton",
                "org-" + id,
                description,
                null,
                capacity,
                100,
                "entrant-test",
                registrationStart,
                registrationEnd,
                new Timestamp(eventTime),
                false,
                Collections.emptyList(),
                ""
        );
    }

    private static final class ScoredRecord {
        private final String id;
        private final double score;

        private ScoredRecord(String id, double score) {
            this.id = id;
            this.score = score;
        }

        private String getId() {
            return id;
        }

        private double getScore() {
            return score;
        }
    }
}
