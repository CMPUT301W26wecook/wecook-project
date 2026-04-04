package com.example.wecookproject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Arrays;
import java.util.Locale;
import java.util.TimeZone;

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

        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Edmonton"));

            String formatted = UserEventUiUtils.formatDateRange(start, end);

            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.getDefault());
            formatter.setTimeZone(TimeZone.getDefault());
            String expected = formatter.format(start.toDate()) + " - " + formatter.format(end.toDate());
            assertEquals(expected, formatted);
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    public void formatEventTimestamp_usesCurrentDeviceTimeZone() {
        Timestamp eventTime = new Timestamp(new Date(1714586400000L));
        TimeZone originalTimeZone = TimeZone.getDefault();

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Edmonton"));
            String edmontonFormatted = UserEventUiUtils.formatEventTimestamp(eventTime);

            TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"));
            String torontoFormatted = UserEventUiUtils.formatEventTimestamp(eventTime);

            assertTrue(edmontonFormatted.endsWith("MDT"));
            assertTrue(torontoFormatted.endsWith("EDT"));
            assertTrue(!edmontonFormatted.equals(torontoFormatted));
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
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
                null,
                true,
                Arrays.asList("entrant-1", "entrant-2"),
                UserEventRecord.STATUS_WAITLISTED
        );

        assertEquals("Waitlist: 2/5", UserEventUiUtils.formatWaitlistSummary(record));
    }
}
