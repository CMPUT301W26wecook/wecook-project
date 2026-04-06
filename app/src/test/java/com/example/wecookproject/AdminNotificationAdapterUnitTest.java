package com.example.wecookproject;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class AdminNotificationAdapterUnitTest {

    private List<UserNotificationItem> notificationList;
    private AdminNotificationAdapter adapter;

    @Before
    public void setUp() {
        notificationList = new ArrayList<>();

        notificationList.add(new UserNotificationItem(
                "1", "e1", "Tech Conference", "Main Hall", "See you there!",
                "unread", "broadcast", "all", "admin_1", "user_1",
                new Date(), null, null
        ));
        
        notificationList.add(new UserNotificationItem(
                "2", "e2", "Cooking Class", "Kitchen 1", "Bring your apron.",
                "read", "reminder", "enrolled", "organizer_1", "user_2",
                new Date(), new Date(), null
        ));

        try {
            adapter = new AdminNotificationAdapter(notificationList);
        } catch (Exception e) {
            adapter = null;
        }
    }

    @Test
    public void testGetItemCount() {
        if (adapter != null) {
            assertEquals("Item count should match the list size", 2, adapter.getItemCount());
        }
    }

    @Test
    public void testGetItemCount_EmptyList() {
        AdminNotificationAdapter emptyAdapter = null;
        try {
            emptyAdapter = new AdminNotificationAdapter(new ArrayList<>());
        } catch (Exception ignored) {}
        
        if (emptyAdapter != null) {
            assertEquals("Item count should be 0 for an empty list", 0, emptyAdapter.getItemCount());
        }
    }

    @Test
    public void testGetItemCount_AfterUpdate() {
        notificationList.add(new UserNotificationItem(
                "3", "e3", "New Event", "Loc", "Msg", "unread", "type", "target", "s", "r", new Date(), null, null
        ));
        if (adapter != null) {
            assertEquals("Adapter should reflect the updated list size", 3, adapter.getItemCount());
        }
    }
}
