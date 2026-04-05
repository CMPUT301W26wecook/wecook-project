package com.example.wecookproject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

public class UserEventRecordTest {

    private static final Timestamp NOW = new Timestamp(new Date(1714586400000L));
    private static final Timestamp ONE_DAY_BEFORE = new Timestamp(new Date(1714500000000L));
    private static final Timestamp ONE_DAY_AFTER = new Timestamp(new Date(1714672800000L));

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

    @Test
    public void isJoinableAt_whenRegistrationOpenAndWaitlistHasRoom_returnsTrue() {
        UserEventRecord record = createRecord(
                "",
                Collections.singletonList("another-entrant"),
                3,
                ONE_DAY_BEFORE,
                ONE_DAY_AFTER
        );

        assertTrue(record.isJoinableAt(NOW));
    }

    @Test
    public void isJoinableAt_whenWaitlistFull_returnsFalse() {
        UserEventRecord record = createRecord(
                "",
                Arrays.asList("entrant-2", "entrant-3"),
                2,
                ONE_DAY_BEFORE,
                ONE_DAY_AFTER
        );

        assertFalse(record.isJoinableAt(NOW));
    }

    @Test
    public void isJoinableAt_whenEntrantAlreadyWaitlisted_returnsFalse() {
        UserEventRecord record = createRecord(
                "",
                Arrays.asList("entrant-1", "entrant-2"),
                4,
                ONE_DAY_BEFORE,
                ONE_DAY_AFTER
        );

        assertFalse(record.isJoinableAt(NOW));
    }

    @Test
    public void isJoinableAt_whenStatusAlreadyExists_returnsFalse() {
        for (String status : Arrays.asList(
                UserEventRecord.STATUS_WAITLISTED,
                UserEventRecord.STATUS_INVITED,
                UserEventRecord.STATUS_ACCEPTED,
                UserEventRecord.STATUS_REJECTED)) {
            UserEventRecord record = createRecord(
                    status,
                    Collections.emptyList(),
                    4,
                    ONE_DAY_BEFORE,
                    ONE_DAY_AFTER
            );

            assertFalse(record.isJoinableAt(NOW));
        }
    }

    @Test
    public void isJoinableAt_whenRegistrationWindowMissingOrClosed_returnsFalse() {
        UserEventRecord missingWindowRecord = createRecord(
                "",
                Collections.emptyList(),
                4,
                null,
                ONE_DAY_AFTER
        );
        UserEventRecord notStartedRecord = createRecord(
                "",
                Collections.emptyList(),
                4,
                ONE_DAY_AFTER,
                new Timestamp(new Date(1714759200000L))
        );
        UserEventRecord endedRecord = createRecord(
                "",
                Collections.emptyList(),
                4,
                new Timestamp(new Date(1714413600000L)),
                ONE_DAY_BEFORE
        );

        assertFalse(missingWindowRecord.isJoinableAt(NOW));
        assertFalse(notStartedRecord.isJoinableAt(NOW));
        assertFalse(endedRecord.isJoinableAt(NOW));
    }

    private UserEventRecord createRecord(String status,
                                         java.util.List<String> waitlistEntrants,
                                         int maxWaitlist,
                                         Timestamp registrationStartDate,
                                         Timestamp registrationEndDate) {
        return new UserEventRecord(
                "event-joinable",
                "Joinable Event",
                "Edmonton",
                "org-1",
                "desc",
                null,
                maxWaitlist,
                "entrant-1",
                registrationStartDate,
                registrationEndDate,
                null,
                false,
                waitlistEntrants,
                status
        );
    }
}
