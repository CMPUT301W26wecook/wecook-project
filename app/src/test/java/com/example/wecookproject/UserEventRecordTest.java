package com.example.wecookproject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class UserEventRecordTest {

    @Test
    public void isWaitlistFull_whenMaxReached_returnsTrue() {
        UserEventRecord record = new UserEventRecord(
                "event-1",
                "Event",
                "Edmonton",
                "org-1",
                "desc",
                null,
                2,
                "entrant-1",
                null,
                null,
                null,
                true,
                Arrays.asList("entrant-1", "entrant-2"),
                UserEventRecord.STATUS_WAITLISTED
        );

        assertTrue(record.isWaitlistFull());
        assertTrue(record.isEntrantOnWaitlist());
        assertEquals(2, record.getCurrentWaitlistCount());
    }

    @Test
    public void setWaitlistEntrantIds_withNull_resetsList() {
        UserEventRecord record = new UserEventRecord(
                "event-2",
                "Event",
                "Edmonton",
                "org-1",
                "desc",
                null,
                3,
                "entrant-1",
                null,
                null,
                null,
                false,
                Arrays.asList("entrant-1"),
                ""
        );

        record.setWaitlistEntrantIds(null);

        assertEquals(0, record.getCurrentWaitlistCount());
        assertFalse(record.isEntrantOnWaitlist());
    }

    @Test
    public void setHistoryStatus_withNull_returnsEmptyEffectiveStatus() {
        UserEventRecord record = new UserEventRecord(
                "event-3",
                "Event",
                "Edmonton",
                "org-1",
                "desc",
                null,
                0,
                "entrant-1",
                Timestamp.now(),
                Timestamp.now(),
                null,
                true,
                Collections.emptyList(),
                UserEventRecord.STATUS_INVITED
        );

        record.setHistoryStatus(null);

        assertEquals("", record.getEffectiveStatus());
    }
}
