package com.example.wecookproject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OrganizerWaitlistItemTest {

    @Test
    public void getAvatarLabel_withName_returnsFirstUppercaseLetter() {
        OrganizerWaitlistItem item = new OrganizerWaitlistItem("id-1", "alice", "edmonton");
        assertEquals("A", item.getAvatarLabel());
    }

    @Test
    public void matches_withDisplayNameOrSubtitleOrId_returnsTrue() {
        OrganizerWaitlistItem item = new OrganizerWaitlistItem("abc123", "Alice Smith", "Edmonton");

        assertTrue(item.matches("alice"));
        assertTrue(item.matches("edmonton"));
        assertTrue(item.matches("abc"));
        assertFalse(item.matches("calgary"));
    }

    @Test
    public void fallback_returnsUnavailableSubtitle() {
        OrganizerWaitlistItem fallback = OrganizerWaitlistItem.fallback("id-404");

        assertEquals("id-404", fallback.getDisplayName());
        assertEquals("Entrant profile unavailable", fallback.getSubtitle());
    }
}
