package com.example.wecookproject;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Date;

public class UserNotificationItemTest {

    @Test
    public void privateWaitlistInvite_doesNotRequireConfirmation() {
        UserNotificationItem item = new UserNotificationItem(
                "notification-1",
                "event-1",
                "Private Event",
                "Edmonton",
                "Join this private waitlist",
                NotificationHelper.STATUS_UNREAD,
                NotificationHelper.TYPE_PRIVATE_WAITLIST_INVITE,
                "event-1",
                new Date(),
                null,
                null
        );

        assertTrue(item.isPrivateWaitlistInvite());
        assertFalse(item.requiresConfirmation());
    }
}
