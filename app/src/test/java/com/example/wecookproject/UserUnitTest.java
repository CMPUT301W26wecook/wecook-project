package com.example.wecookproject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.wecookproject.model.User;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class UserUnitTest {
    private Map<String, Boolean> roles(String... enabledRoles) {
        Map<String, Boolean> roles = new HashMap<>();
        for (String role : enabledRoles) {
            roles.put(role, true);
        }
        return roles;
    }

    @Test
    public void testGetNameWithFirstAndLastName() {
        User user = new User(
                "123 Main St", "", "device123", "1990-01-01",
                "Edmonton", "Canada", "John", "Doe", "T6G 2R3",
                true, roles("entrant")
        );

        assertEquals("John Doe", user.getName());
    }

    @Test
    public void testGetNameWithNullFirstName() {
        User user = new User(
                "123 Main St", "", "device123", "1990-01-01",
                "Edmonton", "Canada", null, "Doe", "T6G 2R3",
                true, roles("entrant")
        );

        assertEquals("", user.getName());
    }

    @Test
    public void testGetNameWithNullLastName() {
        User user = new User(
                "123 Main St", "", "device123", "1990-01-01",
                "Edmonton", "Canada", "John", null, "T6G 2R3",
                true, roles("entrant")
        );

        assertEquals("John", user.getName());
    }

    @Test
    public void testClearProfile() {
        User user = new User(
                "123 Main St", "Apt 4", "device123", "1990-01-01",
                "Edmonton", "Canada", "John", "Doe", "T6G 2R3",
                true, roles("entrant")
        );

        user.clearProfile();

        assertEquals("Deleted", user.getFirstName());
        assertEquals("User", user.getLastName());
        assertEquals("", user.getBirthday());
        assertEquals("", user.getAddressLine1());
        assertEquals("", user.getAddressLine2());
        assertEquals("", user.getCity());
        assertEquals("", user.getPostalCode());
        assertEquals("", user.getCountry());
        assertFalse(user.isProfileCompleted());

        assertEquals(Boolean.TRUE, user.getRoles().get("entrant"));
        assertEquals("device123", user.getAndroidId());
    }

    @Test
    public void testToFirestoreMapIncludesAllFields() {
        User user = new User(
                "123 Main St", "Apt 4", "device123", "1990-01-01",
                "Edmonton", "Canada", "John", "Doe", "T6G 2R3",
                true, roles("organizer")
        );

        Map<String, Object> map = user.toFirestoreMap();

        assertEquals("123 Main St", map.get("addressLine1"));
        assertEquals("Apt 4", map.get("addressLine2"));
        assertEquals("device123", map.get("androidId"));
        assertEquals("1990-01-01", map.get("birthday"));
        assertEquals("Edmonton", map.get("city"));
        assertEquals("Canada", map.get("country"));
        assertEquals("John", map.get("firstName"));
        assertEquals("Doe", map.get("lastName"));
        assertEquals("T6G 2R3", map.get("postalCode"));
        assertEquals(true, map.get("profileCompleted"));
        assertEquals(roles("organizer"), map.get("roles"));
        assertEquals(12, map.size());
    }

    @Test
    public void testToFirestoreMapAllowsNullFields() {
        User user = new User();
        user.setAndroidId("device456");

        Map<String, Object> map = user.toFirestoreMap();

        assertNull(map.get("addressLine1"));
        assertEquals("device456", map.get("androidId"));
        assertNull(map.get("firstName"));
        assertTrue(map.containsKey("roles"));
    }

}
