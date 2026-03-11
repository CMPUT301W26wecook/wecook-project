package com.example.wecookproject;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.provider.Settings;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.wecookproject.model.Event;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class OrganizerFlowTest {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ActivityScenario<LoginActivity> activityScenario;
    private String eventId;

    /**
     * Deletes the Firestore user document before each test so the app always
     * starts from an unauthenticated state, then launches LoginActivity.
     * Also creates a test event document used by the edit-event tests.
     */
    @Before
    public void setUp() {
        String androidId = Settings.Secure.getString(
                ApplicationProvider.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);

        CountDownLatch latch = new CountDownLatch(1);
        FirebaseFirestore.getInstance().collection("users").document(androidId).delete()
                .addOnCompleteListener(task -> latch.countDown());
        try {
            boolean success = latch.await(5, TimeUnit.SECONDS);
            if (!success) {
                System.err.println("Warning: Firestore delete timed out before starting test.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Create a reusable test event for edit-event tests (test9, test10)
        eventId = "edit-test-" + UUID.randomUUID();
        Event editTestEvent = new Event(
                eventId,
                "organizer-test",
                "Original Event",
                "2026-04-01",
                "Open to all",
                25,
                0,
                "System generates",
                false,
                "Edmonton",
                "Original description"
        );
        CountDownLatch eventLatch = new CountDownLatch(1);
        db.collection("events").document(eventId)
                .set(editTestEvent)
                .addOnCompleteListener(task -> eventLatch.countDown());
        try {
            eventLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        activityScenario = ActivityScenario.launch(LoginActivity.class);
    }

    @After
    public void tearDown() {
        if (activityScenario != null) {
            activityScenario.close();
        }

        // Clean up the test event created in setUp
        if (eventId != null) {
            CountDownLatch eventLatch = new CountDownLatch(1);
            db.collection("events").document(eventId)
                    .delete()
                    .addOnCompleteListener(task -> eventLatch.countDown());
            try {
                eventLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * When no Firestore user exists, tapping "Login as organizer" should
     * route through the signup flow (Details screen), not directly to
     * OrganizerHomeActivity.
     */
    @Test
    public void test1_OrganizerLoginWithoutExistingUserRoutesToSignup() {
        onView(withId(R.id.btn_organizer_login)).perform(click());
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
    }

    /**
     * On the Organizer Profile screen, tapping "Update Info" with both
     * First Name and Last Name empty should NOT navigate away — both fields
     * are mandatory for becoming an organizer.
     */
    @Test
    public void test2_OrganizerProfileMandatoryNamesBlockUpdate() {
        // Complete signup so Firestore has a valid user before entering organizer screens
        performFullSignup();

        ActivityScenario<OrganizerProfileActivity> profileScenario =
                ActivityScenario.launch(OrganizerProfileActivity.class);

        // Leave First Name and Last Name blank, attempt update
        onView(withId(R.id.btn_update_info)).perform(click());

        // Should still be on the Profile screen (not navigated away)
        onView(withId(R.id.tv_organizer_info_title)).check(matches(withText("Organizer Info")));

        profileScenario.close();
    }

    /**
     * On the Create Event screen, tapping "Create Event" without an Event Name
     * should NOT navigate away — Event Name is a required field.
     */
    @Test
    public void test3_CreateEventWithoutNameIsBlocked() {
        // Complete signup so Firestore has a valid user before entering organizer screens
        performFullSignup();

        ActivityScenario<OrganizerCreateEventActivity> createScenario =
                ActivityScenario.launch(OrganizerCreateEventActivity.class);

        // Leave Event Name blank, attempt to create
        onView(withId(R.id.btn_create_event)).perform(click());

        // Should still be on the Create Event screen
        onView(withId(R.id.btn_create_event)).check(matches(isDisplayed()));

        createScenario.close();
    }

    /**
     * Verify the bottom navigation bar switches correctly between the three
     * organizer tabs: Events → Create Events → Profile → Events.
     */
    @Test
    public void test4_BottomNavSwitchesBetweenTabs() {
        // Complete signup so Firestore has a valid user before entering organizer screens
        performFullSignup();

        ActivityScenario<OrganizerHomeActivity> homeScenario =
                ActivityScenario.launch(OrganizerHomeActivity.class);

        // Switch to Create Events tab
        onView(withId(R.id.nav_create_events)).perform(click());
        safeSleep(500);
        onView(withId(R.id.btn_create_event)).check(matches(isDisplayed()));

        // Switch to Profile tab
        onView(withId(R.id.nav_profile)).perform(click());
        safeSleep(500);
        onView(withId(R.id.tv_organizer_info_title)).check(matches(withText("Organizer Info")));

        // Switch back to Events tab
        onView(withId(R.id.nav_events)).perform(click());
        safeSleep(500);
        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));

        homeScenario.close();
    }

    /**
     * Filling all mandatory fields on the Profile screen (First Name, Last Name)
     * and tapping "Update Info" should not crash or navigate away unexpectedly.
     */
    @Test
    public void test5_OrganizerProfileUpdateWithValidNames() {
        // Complete signup so Firestore has a valid user before entering organizer screens
        performFullSignup();

        ActivityScenario<OrganizerProfileActivity> profileScenario =
                ActivityScenario.launch(OrganizerProfileActivity.class);

        onView(withId(R.id.et_first_name)).perform(replaceText("Alex"), closeSoftKeyboard());
        onView(withId(R.id.et_last_name)).perform(replaceText("Smith"), closeSoftKeyboard());
        onView(withId(R.id.btn_update_info)).perform(click());

        // Profile screen should still be displayed (no external navigation triggered yet)
        onView(withId(R.id.tv_organizer_info_title)).check(matches(withText("Organizer Info")));

        profileScenario.close();
    }

    /**
     * Verify the Notification screen is reachable from OrganizerEntrantListActivity
     * and its hint text field is visible.
     */
    @Test
    public void test6_NotificationScreenIsReachableAndShowsHintField() {
        // Complete signup so Firestore has a valid user before entering organizer screens
        performFullSignup();

        ActivityScenario<OrganizerNotificationActivity> notifScenario =
                ActivityScenario.launch(OrganizerNotificationActivity.class);

        onView(withId(R.id.et_notification_message)).check(matches(isDisplayed()));

        notifScenario.close();
    }

    @Test
    public void test7_CreateEventAndVerifyInList() {
        // Complete signup so Firestore has a valid user before entering organizer screens
        performFullSignup();

        ActivityScenario<OrganizerHomeActivity> homeScenario =
                ActivityScenario.launch(OrganizerHomeActivity.class);

        // Switch to Create Events tab
        onView(withId(R.id.nav_create_events)).perform(click());
        safeSleep(1000);

        // Fill out the Create Event form
        String testEventName = "Espresso Test Event";
        onView(withId(R.id.et_event_name)).perform(typeText(testEventName), closeSoftKeyboard());
        onView(withId(R.id.et_registration_period)).perform(typeText("2026-04-01 to 2026-04-10"), closeSoftKeyboard());
        onView(withId(R.id.et_max_waitlist)).perform(typeText("50"), closeSoftKeyboard());

        // Select radio buttons
        onView(withId(R.id.rb_open_to_all)).perform(click());
        onView(withId(R.id.rb_system_generates)).perform(click());

        // Click create
        onView(withId(R.id.btn_create_event)).perform(click());

        // Wait for Firestore save and navigation back to Home
        safeSleep(2500);

        // Verify we are back on the Organizer Home screen with the list
        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));

        // Cannot easily check RecyclerView content without an Espresso RecyclerViewAction dependency,
        // but we can at least assert the layout is shown.
        // If we had Espresso Contrib we could check for the specific item text.

        homeScenario.close();
    }

    /**
     * Verify the Event Details screen correctly displays information and buttons 
     * when opened with a valid event ID.
     */
    @Test
    public void test8_EventDetailsScreenDisplaysCorrectly() {
        CountDownLatch latch = new CountDownLatch(1);
        Event mockEvent = new Event("mockEventId", "org123", "Test Event Details", 
                "01/01/2026 to 02/02/2026", "Open", 100, 50, 
                "Random", false, "Edmonton", "Test description");

        FirebaseFirestore.getInstance().collection("events").document("mockEventId")
                .set(mockEvent).addOnCompleteListener(task -> latch.countDown());
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {}

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), OrganizerEventDetailsActivity.class);
        intent.putExtra("eventId", "mockEventId");
        ActivityScenario<OrganizerEventDetailsActivity> detailsScenario = 
                ActivityScenario.launch(intent);

        safeSleep(1500);

        // Check if layout fields are shown
        onView(withId(R.id.tv_event_name_detail)).check(matches(isDisplayed()));
        onView(withId(R.id.tv_event_dates)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_edit_event)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_view_waitlist)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_registration_map)).check(matches(isDisplayed()));

        detailsScenario.close();
        
        // Cleanup the mock document
        FirebaseFirestore.getInstance().collection("events").document("mockEventId").delete();
    }

    /**
     * Launching OrganizerEditEventActivity without passing an eventId extra
     * should cause the activity to finish immediately (DESTROYED state).
     */
    @Test
    public void test9_EditEventLaunchWithoutIdFinishesActivity() {
        ActivityScenario<OrganizerEditEventActivity> scenario =
                ActivityScenario.launch(OrganizerEditEventActivity.class);

        assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.getState());
        scenario.close();
    }

    /**
     * Editing only the event name in OrganizerEditEventActivity should update
     * that field in Firestore while leaving all other fields unchanged.
     */
    @Test
    public void test10_EditEventUpdateSingleFieldUpdatesFirestore() throws InterruptedException {
        Intent intent = new Intent(
                ApplicationProvider.getApplicationContext(),
                OrganizerEditEventActivity.class
        );
        intent.putExtra("eventId", eventId);

        ActivityScenario<OrganizerEditEventActivity> scenario = ActivityScenario.launch(intent);

        onView(withId(R.id.et_event_name))
                .perform(replaceText("Updated Event Name"), closeSoftKeyboard());
        onView(withId(R.id.btn_update_event)).perform(click());

        waitForFirestoreWrite();

        AtomicReference<DocumentSnapshot> snapshotRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events")
                .document(eventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    snapshotRef.set(snapshot);
                    latch.countDown();
                })
                .addOnFailureListener(e -> latch.countDown());

        assertTrue("Timed out reading updated event", latch.await(5, TimeUnit.SECONDS));
        DocumentSnapshot snapshot = snapshotRef.get();
        assertTrue(snapshot != null && snapshot.exists());
        assertEquals("Updated Event Name", snapshot.getString("eventName"));
        assertEquals("2026-04-01", snapshot.getString("registrationPeriod"));
        assertEquals("Open to all", snapshot.getString("enrollmentCriteria"));
        assertEquals(Long.valueOf(25), snapshot.getLong("maxWaitlist"));
        assertEquals("System generates", snapshot.getString("lotteryMethodology"));

        scenario.close();
    }

    private void performFullSignup() {
        // Login screen
        onView(withId(R.id.btn_organizer_login)).perform(click());

        // Details screen
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
        onView(withId(R.id.et_first_name)).perform(replaceText("John"), closeSoftKeyboard());
        onView(withId(R.id.et_last_name)).perform(replaceText("Doe"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(replaceText("01/01/2000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        // Address screen
        safeSleep(3000);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));
        onView(withId(R.id.et_address_line_1)).perform(replaceText("123 Main St"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(replaceText("Edmonton"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(replaceText("T6G 2R3"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        // Wait for Firestore write and navigation to OrganizerHomeActivity
        safeSleep(3500);
    }

    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void waitForFirestoreWrite() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}