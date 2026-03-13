package com.example.wecookproject;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static org.hamcrest.Matchers.allOf;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.content.Intent;
import android.provider.Settings;
import android.view.View;

import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.Lifecycle;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.example.wecookproject.model.Event;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hamcrest.Matcher;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end instrumentation tests for the organizer-part application flow. This class
 * exercises the main organizer journey across login/signup routing, profile access, event
 * creation, event-detail navigation, event editing, and lottery behavior while verifying the
 * expected UI state and Firestore side effects for each scenario.
 *
 * Test case summary:
 * - `test1_OrganizerLoginWithoutExistingUserRoutesToSignup`: verifies that organizer login routes
 *   a device without an existing user record to the signup details screen.
 * - `test2_OrganizerProfileMandatoryNamesBlockUpdate`: verifies that profile updates are blocked
 *   when required organizer name fields are left blank.
 * - `test3_CreateEventWithoutNameIsBlocked`: verifies that event creation is blocked when the
 *   required event name is missing.
 * - `test4_BottomNavSwitchesBetweenTabs`: verifies that organizer bottom navigation switches
 *   correctly between events, create-event, and profile screens.
 * - `test5_OrganizerProfileUpdateWithValidNames`: verifies that entering valid profile names and
 *   tapping update keeps the organizer on the profile screen without error.
 * - `test6_NotificationScreenIsReachableAndShowsHintField`: verifies that the organizer
 *   notification screen opens and shows its message input field.
 * - `test7_CreateEventAndVerifyInList`: verifies that submitting a valid create-event form returns
 *   the organizer to the home screen after the event is saved.
 * - `test8_EventDetailsScreenDisplaysCorrectly`: verifies that the event-details screen loads the
 *   expected core UI elements for a valid event.
 * - `test9_EditEventLaunchWithoutIdFinishesActivity`: verifies that the edit-event screen exits
 *   immediately when launched without a required event ID.
 * - `test10_EditEventUpdateSingleFieldUpdatesFirestore`: verifies that editing only the event name
 *   updates that field in Firestore without changing unrelated event fields.
 * - `test11_LotteryAvailableOnlyAfterRegistrationEnds`: verifies that a lottery draw after
 *   registration closes selects the requested number of entrants and saves them to Firestore.
 *
 * Outstanding issues:
 * - Several tests interact with real Firestore state, so failures can be influenced by network,
 *   emulator timing, or shared backend conditions.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class OrganizerFlowTest {

    // Sleep durations (ms) generous enough for Firestore + UI transitions on CI/emulator
    private static final int WAIT_SHORT  = 2000;
    private static final int WAIT_MEDIUM = 4000;
    private static final int WAIT_LONG   = 6000;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ActivityScenario<LoginActivity> activityScenario;
    /** Stable event ID used by test9 / test10 to exercise the edit flow. */
    private String editEventId;

    /**
     * Deletes the device user document so every test starts unauthenticated,
     * then pre-creates one event document used by the edit tests.
     */
    @Before
    public void setUp() {
        // Configure Firebase to use emulator
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.useEmulator("10.0.2.2", 8080);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.useEmulator("10.0.2.2", 9099);

        String androidId = Settings.Secure.getString(
                ApplicationProvider.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);

        // Ensure no existing user so LoginActivity always routes to signup
        CountDownLatch userDeleteLatch = new CountDownLatch(1);
        db.collection("users").document(androidId).delete()
                .addOnCompleteListener(task -> userDeleteLatch.countDown());
        awaitLatch(userDeleteLatch, 10, "user document deletion");

        // Pre-create event used by test9 / test10
        editEventId = "edit-test-" + UUID.randomUUID();
        @SuppressWarnings("deprecation")
        Event editTestEvent = new Event(
                editEventId,
                "organizer-test",
                "Original Event",
                new Date(126, 3, 1),   // 2026-04-01
                new Date(126, 3, 30),  // 2026-04-30
                25,
                0,
                false,
                "Edmonton",
                "Original description"
        );
        CountDownLatch eventCreateLatch = new CountDownLatch(1);
        db.collection("events").document(editEventId)
                .set(editTestEvent)
                .addOnCompleteListener(task -> eventCreateLatch.countDown());
        awaitLatch(eventCreateLatch, 10, "edit-test event creation");

        activityScenario = ActivityScenario.launch(LoginActivity.class);
    }

    @After
    public void tearDown() {
        if (activityScenario != null) {
            activityScenario.close();
        }
        if (editEventId != null) {
            CountDownLatch latch = new CountDownLatch(1);
            db.collection("events").document(editEventId)
                    .delete()
                    .addOnCompleteListener(task -> latch.countDown());
            awaitLatch(latch, 10, "edit-test event cleanup");
        }
    }


    /**
     * test1: Without an existing Firestore user, tapping "Login as organizer"
     * must route to the signup Details screen (not OrganizerHomeActivity).
     */
    @Test
    public void test1_OrganizerLoginWithoutExistingUserRoutesToSignup() {
        onView(withId(R.id.btn_organizer_login)).perform(click());
        safeSleep(WAIT_MEDIUM); // allow FCM token fetch + Firestore lookup + navigation
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
    }

    /**
     * test2: Tapping "Update Info" with both First Name and Last Name blank should
     * keep the organizer on the Profile screen both fields are mandatory.
     */
    @Test
    public void test2_OrganizerProfileMandatoryNamesBlockUpdate() {
        performFullSignup();

        ActivityScenario<OrganizerProfileActivity> profileScenario =
                ActivityScenario.launch(OrganizerProfileActivity.class);

        onView(withId(R.id.btn_update_info)).perform(click());

        // Screen must not have navigated away
        onView(withId(R.id.tv_organizer_info_title)).check(matches(withText("Organizer Info")));

        profileScenario.close();
    }

    /**
     * test3: Submitting the Create Event form with no Event Name entered must
     * be blocked Event Name is required.
     */
    @Test
    public void test3_CreateEventWithoutNameIsBlocked() {
        performFullSignup();

        ActivityScenario<OrganizerCreateEventActivity> createScenario =
                ActivityScenario.launch(OrganizerCreateEventActivity.class);

        onView(withId(R.id.btn_create_event)).perform(nestedScrollTo(), click());

        // Create Event button must still be visible (no navigation occurred)
        onView(withId(R.id.btn_create_event)).check(matches(isDisplayed()));

        createScenario.close();
    }

    /**
     * test4: Bottom navigation should switch correctly among the organizer Events,
     * Create Event, and Profile tabs.
     */
    @Test
    public void test4_BottomNavSwitchesBetweenTabs() {
        performFullSignup();

        ActivityScenario<OrganizerHomeActivity> homeScenario =
                ActivityScenario.launch(OrganizerHomeActivity.class);

        // Home Create Events
        onView(withId(R.id.nav_create_events)).perform(click());
        safeSleep(WAIT_SHORT);
        onView(withId(R.id.btn_create_event)).check(matches(isDisplayed()));

        // Create Events Profile
        onView(withId(R.id.nav_profile)).perform(click());
        safeSleep(WAIT_SHORT);
        onView(withId(R.id.tv_organizer_info_title)).check(matches(withText("Organizer Info")));

        // Profile Events
        onView(withId(R.id.nav_events)).perform(click());
        safeSleep(WAIT_SHORT);
        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));

        homeScenario.close();
    }

    /**
     * test5: Supplying valid First Name and Last Name and tapping "Update Info"
     * should leave the organizer on the Profile screen without errors.
     */
    @Test
    public void test5_OrganizerProfileUpdateWithValidNames() {
        performFullSignup();

        ActivityScenario<OrganizerProfileActivity> profileScenario =
                ActivityScenario.launch(OrganizerProfileActivity.class);

        onView(withId(R.id.et_first_name)).perform(replaceText("Alex"), closeSoftKeyboard());
        onView(withId(R.id.et_last_name)).perform(replaceText("Smith"), closeSoftKeyboard());
        onView(withId(R.id.btn_update_info)).perform(click());

        onView(withId(R.id.tv_organizer_info_title)).check(matches(withText("Organizer Info")));

        profileScenario.close();
    }

    /**
     * test6: OrganizerNotificationActivity is reachable and its message input
     * field is visible.
     */
    @Test
    public void test6_NotificationScreenIsReachableAndShowsHintField() {
        performFullSignup();

        ActivityScenario<OrganizerNotificationActivity> notifScenario =
                ActivityScenario.launch(OrganizerNotificationActivity.class);

        onView(withId(R.id.et_notification_message)).check(matches(isDisplayed()));

        notifScenario.close();
    }

    /**
     * test7: Filling all mandatory Create Event fields (name, dates via text
     * input, and max waitlist) and tapping "Create Event"
     * should save to Firestore and navigate back to OrganizerHomeActivity.
     *
     * <p>Date fields accept "yyyy-MM-dd" text directly because
     * OrganizerCreateEventActivity registers a TextWatcher that parses the
     * typed value and sets the internal Date field in addition to the
     * DatePickerDialog that sets the same field when the user taps the view.</p>
     */
    @Test
    public void test7_CreateEventAndVerifyInList() {
        performFullSignup();

        ActivityScenario<OrganizerHomeActivity> homeScenario =
                ActivityScenario.launch(OrganizerHomeActivity.class);

        onView(withId(R.id.nav_create_events)).perform(click());
        safeSleep(WAIT_SHORT);

        onView(withId(R.id.et_event_name))
                .perform(replaceText("Espresso Test Event"), closeSoftKeyboard());
        onView(withId(R.id.et_registration_start_date))
                .perform(replaceText("2026-04-01"), closeSoftKeyboard());
        onView(withId(R.id.et_registration_end_date))
                .perform(replaceText("2026-04-10"), closeSoftKeyboard());
        onView(withId(R.id.et_max_waitlist))
                .perform(replaceText("50"), closeSoftKeyboard());

        onView(withId(R.id.btn_create_event)).perform(nestedScrollTo(), click());

        // Wait for Firestore write and navigation back to Home
        safeSleep(WAIT_LONG);

        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));

        homeScenario.close();
    }

    /**
     * test8: Launching OrganizerEventDetailsActivity with a valid event ID
     * should display required UI elements, show geolocation toggle,
     * and navigate to Registration Map where toggle is also visible.
     */
    @Test
    public void test8_EventDetailsScreenDisplaysAndNavigatesToRegistrationMap() {
        // Use a unique ID to avoid collisions across test runs
        String mockEventId = "mock-details-" + UUID.randomUUID();
        @SuppressWarnings("deprecation")
        Event mockEvent = new Event(
                mockEventId,
                "org123",
                "Test Event Details",
                new Date(126, 0, 1),  // 2026-01-01
                new Date(126, 1, 2),  // 2026-02-02
                100,
                50,
                false,
                "Edmonton",
                "Test description"
        );

        CountDownLatch createLatch = new CountDownLatch(1);
        db.collection("events").document(mockEventId)
                .set(mockEvent)
                .addOnCompleteListener(task -> createLatch.countDown());
        awaitLatch(createLatch, 10, "mock event creation");

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                OrganizerEventDetailsActivity.class);
        intent.putExtra("eventId", mockEventId);
        ActivityScenario<OrganizerEventDetailsActivity> detailsScenario =
                ActivityScenario.launch(intent);

        safeSleep(WAIT_MEDIUM);

        onView(withId(R.id.tv_event_name_detail)).check(matches(isDisplayed()));
        onView(withId(R.id.tv_event_dates)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_edit_event)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_view_waitlist)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_registration_map)).check(matches(isDisplayed()));
        onView(withId(R.id.switch_geolocation)).check(matches(isDisplayed()));

        onView(withId(R.id.btn_registration_map)).perform(click());
        safeSleep(WAIT_MEDIUM);
        onView(withId(R.id.btn_back_to_event)).check(matches(isDisplayed()));
        onView(withId(R.id.switch_geolocation)).check(matches(isDisplayed()));

        detailsScenario.close();

        // Clean up the mock document created for this test
        CountDownLatch deleteLatch = new CountDownLatch(1);
        db.collection("events").document(mockEventId)
                .delete()
                .addOnCompleteListener(task -> deleteLatch.countDown());
        awaitLatch(deleteLatch, 10, "mock event deletion");
    }

    /**
     * test9: Launching OrganizerEditEventActivity without an "eventId" extra
     * must cause the activity to finish immediately (state = DESTROYED).
     */
    @Test
    public void test9_EditEventLaunchWithoutIdFinishesActivity() {
        ActivityScenario<OrganizerEditEventActivity> scenario =
                ActivityScenario.launch(OrganizerEditEventActivity.class);

        assertEquals(Lifecycle.State.DESTROYED, scenario.getState());
        scenario.close();
    }

    /**
     * test10: Editing only the event name in OrganizerEditEventActivity should
     * persist the new name to Firestore while leaving the other original fields
     * such as maxWaitlist unchanged.
     */
    @Test
    public void test10_EditEventUpdateSingleFieldUpdatesFirestore() throws InterruptedException {
        Intent intent = new Intent(
                ApplicationProvider.getApplicationContext(),
                OrganizerEditEventActivity.class);
        intent.putExtra("eventId", editEventId);

        ActivityScenario<OrganizerEditEventActivity> scenario =
                ActivityScenario.launch(intent);

        onView(withId(R.id.et_event_name))
                .perform(replaceText("Updated Event Name"), closeSoftKeyboard());
        onView(withId(R.id.btn_update_event)).perform(nestedScrollTo(), click());

        safeSleep(WAIT_MEDIUM); // allow Firestore update to complete

        AtomicReference<DocumentSnapshot> snapshotRef = new AtomicReference<>();
        CountDownLatch readLatch = new CountDownLatch(1);
        db.collection("events").document(editEventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    snapshotRef.set(snapshot);
                    readLatch.countDown();
                })
                .addOnFailureListener(e -> readLatch.countDown());

        assertTrue("Timed out reading updated event", readLatch.await(10, TimeUnit.SECONDS));
        DocumentSnapshot snapshot = snapshotRef.get();
        assertTrue("Event document must exist", snapshot != null && snapshot.exists());
        assertEquals("Updated Event Name",  snapshot.getString("eventName"));
        assertEquals(Long.valueOf(25),       snapshot.getLong("maxWaitlist"));

        scenario.close();
    }

    /**
     * test11: Running a lottery after registration has ended should select the requested
     * number of winners from the waitlist and persist them to Firestore.
     */
    @Test
    public void test11_LotteryAvailableOnlyAfterRegistrationEnds() throws InterruptedException {
        String lotteryWithEntrantsEventId = "lottery-entrants-test-" + UUID.randomUUID();
        
        // Create an event with a past registration end date
        @SuppressWarnings("deprecation")
        Event lotteryTestEvent = new Event(
                lotteryWithEntrantsEventId,
                "organizer-test",
                "Lottery With Entrants Test Event",
                new Date(126, 2, 1),   // 2026-03-01 (registration start)
                new Date(126, 2, 10),  // 2026-03-10 (registration end - in the past)
                25,
                0,
                false,
                "Edmonton",
                "Test event for lottery with entrants"
        );
        
        // Add some entrants to the waitlist
        List<String> entrants = Arrays.asList("entrant1", "entrant2", "entrant3", "entrant4", "entrant5");
        lotteryTestEvent.setWaitlistEntrantIds(entrants);
        lotteryTestEvent.setCurrentWaitlistCount(entrants.size());
        
        CountDownLatch eventCreateLatch = new CountDownLatch(1);
        db.collection("events").document(lotteryWithEntrantsEventId)
                .set(lotteryTestEvent)
                .addOnCompleteListener(task -> eventCreateLatch.countDown());
        awaitLatch(eventCreateLatch, 15, "lottery with entrants test event creation");
        
        // Launch OrganizerEntrantListActivity with the test event
        Intent intent = new Intent(
                ApplicationProvider.getApplicationContext(),
                OrganizerEntrantListActivity.class);
        intent.putExtra("eventId", lotteryWithEntrantsEventId);
        
        ActivityScenario<OrganizerEntrantListActivity> scenario =
                ActivityScenario.launch(intent);
        
        safeSleep(WAIT_MEDIUM); // allow event data to load
        
        // Perform lottery draw for 3 winners
        onView(withId(R.id.et_lottery_count)).perform(replaceText("3"), closeSoftKeyboard());
        onView(withId(R.id.btn_lottery_draw)).perform(click());
        
        safeSleep(WAIT_LONG); // allow lottery operation and Firestore updates to complete
        
        // Verify that winners were selected by checking the event document
        AtomicReference<DocumentSnapshot> snapshotRef = new AtomicReference<>();
        CountDownLatch readLatch = new CountDownLatch(1);
        db.collection("events").document(lotteryWithEntrantsEventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    snapshotRef.set(snapshot);
                    readLatch.countDown();
                })
                .addOnFailureListener(e -> readLatch.countDown());
        
        assertTrue("Timed out reading updated event after lottery", readLatch.await(15, TimeUnit.SECONDS));
        DocumentSnapshot snapshot = snapshotRef.get();
        assertTrue("Event document must exist after lottery", snapshot != null && snapshot.exists());
        
        // Check that selected entrants list exists and has 3 winners
        List<String> selectedEntrants = (List<String>) snapshot.get("selectedEntrantIds");
        assertNotNull("Selected entrants list should not be null after lottery", selectedEntrants);
        assertEquals("Should have selected 3 winners", 3, selectedEntrants.size());
        
        scenario.close();
        
        // Clean up: delete the test event
        CountDownLatch eventDeleteLatch = new CountDownLatch(1);
        db.collection("events").document(lotteryWithEntrantsEventId)
                .delete()
                .addOnCompleteListener(task -> eventDeleteLatch.countDown());
        awaitLatch(eventDeleteLatch, 15, "lottery with entrants test event cleanup");
    }

    /**
     * test12: Replacement draw should pick a random applicant from the remaining
     * pool and update the replacementEntrantIds field on the event document.
     */
    @Test
    public void test12_ReplacementDrawPicksRandomApplicant() throws InterruptedException {
        String replacementEventId = "replacement-test-" + UUID.randomUUID();
        @SuppressWarnings("deprecation")
        Event replacementEvent = new Event(
                replacementEventId,
                "organizer-test",
                "Replacement Draw Event",
                new Date(126, 2, 1),
                new Date(126, 2, 10),
                25,
                0,
                false,
                "Edmonton",
                "Event for replacement test"
        );
        List<String> entrants = Arrays.asList("a1", "a2", "a3", "a4", "a5");
        replacementEvent.setWaitlistEntrantIds(entrants);
        replacementEvent.setCurrentWaitlistCount(entrants.size());
        replacementEvent.setSelectedEntrantIds(Arrays.asList("a1", "a2"));

        CountDownLatch createLatch = new CountDownLatch(1);
        db.collection("events").document(replacementEventId)
                .set(replacementEvent)
                .addOnCompleteListener(task -> createLatch.countDown());
        awaitLatch(createLatch, 15, "replacement event creation");

        Intent intent = new Intent(
                ApplicationProvider.getApplicationContext(),
                OrganizerEntrantListActivity.class);
        intent.putExtra("eventId", replacementEventId);
        ActivityScenario<OrganizerEntrantListActivity> scenario =
                ActivityScenario.launch(intent);
        safeSleep(WAIT_MEDIUM);

        // trigger replacement draw
        onView(withId(R.id.btn_redraw_entrants)).perform(click());
        safeSleep(WAIT_LONG);

        AtomicReference<DocumentSnapshot> snapshotRef = new AtomicReference<>();
        CountDownLatch readLatch = new CountDownLatch(1);
        db.collection("events").document(replacementEventId)
                .get()
                .addOnSuccessListener(snapshot -> { snapshotRef.set(snapshot); readLatch.countDown(); })
                .addOnFailureListener(e -> readLatch.countDown());

        assertTrue("Timed out reading replacement event", readLatch.await(15, TimeUnit.SECONDS));
        DocumentSnapshot snapshot = snapshotRef.get();
        assertTrue("Event document must exist", snapshot != null && snapshot.exists());
        List<String> replacements = (List<String>) snapshot.get("replacementEntrantIds");
        assertNotNull("Replacement list should not be null", replacements);
        assertEquals("Should have exactly one replacement", 1, replacements.size());
        String chosen = replacements.get(0);
        assertFalse("Replacement should not be an originally selected applicant",
                Arrays.asList("a1", "a2").contains(chosen));
        assertTrue("Replacement should come from waitlist pool", entrants.contains(chosen));

        scenario.close();
        CountDownLatch delLatch = new CountDownLatch(1);
        db.collection("events").document(replacementEventId)
                .delete()
                .addOnCompleteListener(task -> delLatch.countDown());
        awaitLatch(delLatch, 15, "replacement event cleanup");
    }


    /**
     * Drives the full organizer signup flow launched from LoginActivity, progressing through the
     * Details and Address screens until OrganizerHomeActivity becomes visible.
     */
    private void performFullSignup() {
        // LoginActivity was already launched by setUp()
        onView(withId(R.id.btn_organizer_login)).perform(click());

        // Wait for routing to Details screen (FCM token + Firestore lookup)
        safeSleep(WAIT_MEDIUM);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));

        onView(withId(R.id.et_first_name)).perform(replaceText("John"), closeSoftKeyboard());
        onView(withId(R.id.et_last_name)).perform(replaceText("Doe"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(replaceText("01/01/2000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        // Wait for navigation to Address screen
        safeSleep(WAIT_MEDIUM);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));

        onView(withId(R.id.et_address_line_1)).perform(replaceText("123 Main St"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(replaceText("Edmonton"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(replaceText("T6G 2R3"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        // Wait for Firestore write + navigation to OrganizerHomeActivity
        safeSleep(WAIT_LONG);
    }

    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitLatch(CountDownLatch latch, int timeoutSeconds, String operation) {
        try {
            if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                System.err.println("Warning: timed out waiting for: " + operation);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ViewAction nestedScrollTo() {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return allOf(
                        withEffectiveVisibility(androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE),
                        isDescendantOfA(isAssignableFrom(NestedScrollView.class))
                );
            }

            @Override
            public String getDescription() {
                return "scroll to view inside NestedScrollView";
            }

            @Override
            public void perform(UiController uiController, View view) {
                View parent = (View) view.getParent();
                while (parent != null && !(parent instanceof NestedScrollView)) {
                    parent = (View) parent.getParent();
                }
                if (parent instanceof NestedScrollView) {
                    ((NestedScrollView) parent).scrollTo(0, view.getBottom());
                }
                uiController.loopMainThreadUntilIdle();
            }
        };
    }
}
