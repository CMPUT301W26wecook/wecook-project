package com.example.wecookproject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.Date;
import java.util.Arrays;

public class UserEventUiUtilsTest {

    @Test
    public void getAvatarLetter_withBlankName_returnsDefaultE() {
        assertEquals("E", UserEventUiUtils.getAvatarLetter("   "));
    }

    @Test
    public void getStatusLabel_invitedWithPickedFlag_returnsPicked() {
        String label = UserEventUiUtils.getStatusLabel(UserEventRecord.STATUS_INVITED, true);
        assertEquals("Picked", label);
    }

    @Test
    public void formatDateRange_withBothDates_containsYear() {
        Timestamp start = new Timestamp(new Date(1711929600000L));
        Timestamp end = new Timestamp(new Date(1714521600000L));

        String formatted = UserEventUiUtils.formatDateRange(start, end);

        assertEquals("2024-04-01 00:00 - 2024-05-01 00:00", formatted);
    }

    @Test
    public void formatWaitlistSummary_withLimitedWaitlist_showsCapacity() {
        UserEventRecord record = new UserEventRecord(
                "event-4",
                "Event",
                "Edmonton",
                "org-1",
                "desc",
                null,
                5,
                "entrant-1",
                null,
                null,
                true,
                Arrays.asList("entrant-1", "entrant-2"),
                UserEventRecord.STATUS_WAITLISTED
        );

        assertEquals("Waitlist: 2/5", UserEventUiUtils.formatWaitlistSummary(record));
    }
}
