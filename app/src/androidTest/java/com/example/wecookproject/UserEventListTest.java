package com.example.wecookproject;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.provider.Settings;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.example.wecookproject.model.Event;
import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class UserEventListTest {

    private ActivityScenario<UserEventActivity> activityScenario;
    private final String testEventId = "test_event_123";

    @Before
    public void setUp() {
        // Ensure a user exists so we don't get stuck on Login/Signup
        ensureUserExists();

        // Create a test event in Firestore
        createTestEvent();

        // Launch MainActivity
        activityScenario = ActivityScenario.launch(UserEventActivity.class);
    }

    @After
    public void tearDown() {
        if (activityScenario != null) {
            activityScenario.close();
        }
        deleteTestEvent();
    }

    @Test
    public void testEventListIsDisplayed() {
        // Verify the title "Events" is displayed
        onView(withId(R.id.tv_events_title)).check(matches(withText("Events")));
        
        // Verify RecyclerView is visible
        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));
    }

    @Test
    public void testClickEventOpensDetailsDialog() {
        safeSleep(2000); // Wait for events to load

        // Click on the first item in the list (our test event)
        onView(withText("Test Espresso Event")).perform(click());

        // Verify the dialog elements are displayed
        onView(withId(R.id.tv_dialog_event_name)).check(matches(withText("Test Espresso Event")));
        onView(withId(R.id.tv_dialog_location)).check(matches(withText("Location: Test City")));
        onView(withId(R.id.btn_join_waitlist)).check(matches(isDisplayed()));
        
        // Close the dialog
        onView(withId(R.id.btn_close_dialog)).perform(click());
    }

    @Test
    public void testBottomNavigation() {
        // Verify we are on Events tab initially
        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));

        // Click on Profile tab
        onView(withId(R.id.nav_profile)).perform(click());

        // Verify we navigated to Profile (OrganizerProfileActivity based on previous fix)
        onView(withId(R.id.tv_organizer_info_title)).check(matches(withText("Organizer Info")));
    }

    private void ensureUserExists() {
        String androidId = Settings.Secure.getString(
                ApplicationProvider.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);

        CountDownLatch latch = new CountDownLatch(1);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(androidId).set(new java.util.HashMap<String, Object>() {{
            put("firstName", "Test");
            put("lastName", "User");
            put("profileCompleted", true);
        }}).addOnCompleteListener(task -> latch.countDown());

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void createTestEvent() {
        Event testEvent = new Event(
                testEventId,
                "organizer_123",
                "Test Espresso Event",
                "2026-01-01 to 2026-12-31",
                "Open",
                100,
                0,
                "Random",
                false,
                "Test City",
                "Description for Espresso Test"
        );

        CountDownLatch latch = new CountDownLatch(1);
        FirebaseFirestore.getInstance().collection("events").document(testEventId)
                .set(testEvent).addOnCompleteListener(task -> latch.countDown());
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void deleteTestEvent() {
        FirebaseFirestore.getInstance().collection("events").document(testEventId).delete();
    }

    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
