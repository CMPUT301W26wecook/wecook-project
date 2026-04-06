package com.example.wecookproject;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

public class EventSearchMatcherTest {

    @Test
    public void score_exactKeywordMatch_outscoresUnrelatedEvent() {
        UserEventRecord cookingEvent = record(
                "Downtown Cooking Workshop",
                "Hands-on culinary class for beginners"
        );
        UserEventRecord unrelatedEvent = record(
                "Astronomy Night",
                "Bring your telescope for a star-gazing meetup"
        );

        double cookingScore = EventSearchMatcher.score("cooking", cookingEvent);
        double unrelatedScore = EventSearchMatcher.score("cooking", unrelatedEvent);

        assertTrue(cookingScore > unrelatedScore);
    }

    @Test
    public void score_synonymMatch_scoresPositive() {
        UserEventRecord event = record(
                "Sunday Barbecue Social",
                "Community grill and picnic"
        );

        double score = EventSearchMatcher.score("bbq", event);

        assertTrue(score > 0.45d);
    }

    @Test
    public void score_noSharedConcepts_scoresLow() {
        UserEventRecord event = record(
                "Book Club Meetup",
                "Discuss your favorite fiction titles"
        );

        double score = EventSearchMatcher.score("basketball tournament", event);

        assertTrue(score < 0.45d);
    }

    private UserEventRecord record(String name, String description) {
        return new UserEventRecord(
                "event-id",
                name,
                "Edmonton",
                "organizer-id",
                description,
                null,
                100,
                100,
                "entrant-id",
                null,
                null,
                null,
                false,
                Collections.emptyList(),
                ""
        );
    }
}
