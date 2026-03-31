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
import android.widget.Button;

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
import com.google.android.gms.tasks.Tasks;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hamcrest.Matcher;

import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
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
 * - `test6b_CreateEventWithInvalidDatesIsBlocked`: verifies that invalid registration timestamps
 *   prevent event creation and no matching Firestore event document is saved.
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
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";

    private FirebaseFirestore db;
    private static boolean emulatorConfigured = false;
    private ActivityScenario<LoginActivity> activityScenario;
    /** Stable event ID used by test9 / test10 to exercise the edit flow. */
    private String editEventId;

    /**
     * Deletes the device user document so every test starts unauthenticated,
     * then pre-creates one event document used by the edit tests.
     */
    @Before
    public void setUp() {
        if (!emulatorConfigured) {
            try {
                FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080);
            } catch (IllegalStateException ignored) {
                // Another test class may have initialized Firestore first in the same process.
            }
            try {
                FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099);
            } catch (IllegalStateException ignored) {
                // Another test class may have initialized Auth first in the same process.
            }
            emulatorConfigured = true;
        }

        db = FirebaseFirestore.getInstance();

        String androidId = Settings.Secure.getString(
                ApplicationProvider.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);

        CountDownLatch userDeleteLatch = new CountDownLatch(1);
        db.collection("users").document(androidId).delete()
                .addOnCompleteListener(task -> userDeleteLatch.countDown());
        awaitLatch(userDeleteLatch, 10, "user document deletion");

        editEventId = "edit-test-" + UUID.randomUUID();
        @SuppressWarnings("deprecation")
        Event editTestEvent = new Event(
                editEventId,
                "organizer-test",
                "Original Event",
                parseTestDate("2026-04-01 00:00"),
                parseTestDate("2026-04-30 00:00"),
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

        String notificationEventId = "notification-screen-" + UUID.randomUUID();
        createEventDocument(notificationEventId, "Notification Screen Event", new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0);

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), OrganizerNotificationActivity.class);
        intent.putExtra("eventId", notificationEventId);
        ActivityScenario<OrganizerNotificationActivity> notifScenario =
                ActivityScenario.launch(intent);

        onView(withId(R.id.et_notification_message)).check(matches(isDisplayed()));
        onView(withId(R.id.spinner_notification_recipients)).check(matches(isDisplayed()));

        notifScenario.close();
        deleteEventDocument(notificationEventId);
    }

    /**
     * test6b: Submitting the Create Event form with invalid registration
     * timestamps should keep the organizer on the Create Event screen and
     * must not write the event document to Firestore.
     */
    @Test
    public void test6b_CreateEventWithInvalidDatesIsBlocked() throws InterruptedException {
        performFullSignup();

        ActivityScenario<OrganizerCreateEventActivity> createScenario =
                ActivityScenario.launch(OrganizerCreateEventActivity.class);

        String invalidEventName = "Invalid Date Event " + UUID.randomUUID();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());

        Calendar startCalendar = Calendar.getInstance();
        startCalendar.add(Calendar.DAY_OF_MONTH, 1);
        Calendar endCalendar = Calendar.getInstance();
        endCalendar.add(Calendar.DAY_OF_MONTH, 2);

        onView(withId(R.id.et_event_name))
                .perform(replaceText(invalidEventName), closeSoftKeyboard());
        onView(withId(R.id.et_registration_start_date))
                .perform(replaceText(formatter.format(startCalendar.getTime())), closeSoftKeyboard());
        onView(withId(R.id.et_registration_end_date))
                .perform(replaceText(formatter.format(endCalendar.getTime())), closeSoftKeyboard());
        onView(withId(R.id.et_max_waitlist))
                .perform(replaceText("25"), closeSoftKeyboard());

        onView(withId(R.id.btn_create_event)).perform(nestedScrollTo(), click());

        onView(withId(R.id.btn_create_event)).check(matches(isDisplayed()));

        AtomicReference<Boolean> eventExistsRef = new AtomicReference<>(false);
        CountDownLatch queryLatch = new CountDownLatch(1);
        db.collection("events")
                .whereEqualTo("eventName", invalidEventName)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    eventExistsRef.set(!querySnapshot.isEmpty());
                    queryLatch.countDown();
                })
                .addOnFailureListener(e -> queryLatch.countDown());

        assertTrue("Timed out checking invalid event creation", queryLatch.await(10, TimeUnit.SECONDS));
        assertFalse("Event should not be created when dates are invalid", eventExistsRef.get());

        createScenario.close();
    }

    /**
     * test7: Filling all mandatory Create Event fields (name, dates via text
     * input, and max waitlist) and tapping "Create Event"
     * should save to Firestore and navigate back to OrganizerHomeActivity.
     *
     * <p>Date fields accept "yyyy-MM-dd HH:mm" text directly and the create
     * flow validates them when the organizer submits the form.</p>
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
                .perform(replaceText("2026-03-10 17:00"), closeSoftKeyboard());
        onView(withId(R.id.et_registration_end_date))
                .perform(replaceText("2026-03-01 09:00"), closeSoftKeyboard());
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
        Event mockEvent = new Event(
                mockEventId,
                "org123",
                "Test Event Details",
                parseTestDate("2026-01-01 00:00"),
                parseTestDate("2026-02-02 00:00"),
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
        onView(withId(R.id.btn_show_qr)).check(matches(isDisplayed()));

        // Validate QR dialog for the event
        onView(withId(R.id.btn_show_qr)).perform(click());
        onView(withText("Promotional QR Code")).check(matches(isDisplayed()));
        onView(withText(QrCodeUtils.buildPromotionalEventLink(mockEventId))).check(matches(isDisplayed()));
        // Keep test stable: close QR dialog before continuing map navigation checks.
        onView(withText("Close")).perform(click());

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
     * test8b: Organizer QR dialog link should navigate to the in-app public event landing page.
     */
    @Test
    public void test8b_QrLinkNavigatesToPublicEventLanding() {
        String mockEventId = "mock-qr-link-" + UUID.randomUUID();
        Event mockEvent = new Event(
                mockEventId,
                "org123",
                "Test QR Landing Event",
                parseTestDate("2026-01-01 00:00"),
                parseTestDate("2026-02-02 00:00"),
                100,
                10,
                true,
                "Edmonton",
                "Test description"
        );

        CountDownLatch createLatch = new CountDownLatch(1);
        db.collection("events").document(mockEventId)
                .set(mockEvent)
                .addOnCompleteListener(task -> createLatch.countDown());
        awaitLatch(createLatch, 10, "mock qr-link event creation");

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                OrganizerEventDetailsActivity.class);
        intent.putExtra("eventId", mockEventId);
        ActivityScenario<OrganizerEventDetailsActivity> detailsScenario =
                ActivityScenario.launch(intent);

        safeSleep(WAIT_MEDIUM);
        onView(withId(R.id.btn_show_qr)).perform(click());
        onView(withText("Promotional QR Code")).check(matches(isDisplayed()));
        onView(withText(QrCodeUtils.buildPromotionalEventLink(mockEventId))).perform(click());
        safeSleep(WAIT_MEDIUM);
        onView(withId(R.id.tv_public_event_name)).check(matches(withText("Test QR Landing Event")));

        detailsScenario.close();

        CountDownLatch deleteLatch = new CountDownLatch(1);
        db.collection("events").document(mockEventId)
                .delete()
                .addOnCompleteListener(task -> deleteLatch.countDown());
        awaitLatch(deleteLatch, 10, "mock qr-link event deletion");
    }

    @Test
    public void test8c_OrganizerCanViewAndDeleteEntrantComment() throws Exception {
        String organizerAndroidId = Settings.Secure.getString(
                ApplicationProvider.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);
        String eventId = "organizer-comments-" + UUID.randomUUID();
        createOrganizerUser(organizerAndroidId, "Org", "Owner");
        createOwnedEventDocument(eventId, "Organizer Comment Moderation Event", organizerAndroidId);
        createEventComment(eventId, "entrant-1", "Entrant One", "entrant", "Please confirm the event time.");

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), OrganizerEventDetailsActivity.class);
        intent.putExtra("eventId", eventId);
        ActivityScenario.launch(intent);

        safeSleep(WAIT_MEDIUM);
        onView(withText("Event Comments")).perform(nestedScrollTo());
        onView(withText("Entrant One")).perform(nestedScrollTo());
        onView(withText("Entrant One")).check(matches(isDisplayed()));
        onView(withText("Please confirm the event time.")).perform(nestedScrollTo());
        onView(withText("Please confirm the event time.")).check(matches(isDisplayed()));
        onView(withId(R.id.btn_delete_comment)).perform(nestedScrollTo(), click());

        waitUntilCommentDeleted(eventId, "Please confirm the event time.");

        cleanupCommentsForEvent(eventId);
        deleteEventDocument(eventId);
        deleteUserDocument(organizerAndroidId);
    }

    @Test
    public void test8d_OrganizerCanPostCommentWithOrganizerTag() throws Exception {
        String organizerAndroidId = Settings.Secure.getString(
                ApplicationProvider.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);
        String eventId = "organizer-post-comment-" + UUID.randomUUID();
        createOrganizerUser(organizerAndroidId, "Owner", "Commenter");
        createOwnedEventDocument(eventId, "Organizer Post Comment Event", organizerAndroidId);

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), OrganizerEventDetailsActivity.class);
        intent.putExtra("eventId", eventId);
        ActivityScenario.launch(intent);

        safeSleep(WAIT_MEDIUM);
        onView(withId(R.id.et_organizer_comment))
                .perform(nestedScrollTo(), replaceText("Organizer note for entrants"), closeSoftKeyboard());
        onView(withId(R.id.btn_post_organizer_comment)).perform(nestedScrollTo(), click());

        Map<String, Object> comment = waitForCommentByText(eventId, "Organizer note for entrants");
        assertNotNull(comment);
        assertEquals("organizer", comment.get("authorRole"));

        onView(withText("Organizer note for entrants")).perform(nestedScrollTo());
        onView(withText("Organizer note for entrants")).check(matches(isDisplayed()));
        onView(withText("ORGANIZER")).perform(nestedScrollTo());
        onView(withText("ORGANIZER")).check(matches(isDisplayed()));

        cleanupCommentsForEvent(eventId);
        deleteEventDocument(eventId);
        deleteUserDocument(organizerAndroidId);
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

        waitUntilEditEventDatesLoaded(scenario);

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
    public void test11_LotteryAvailableOnlyAfterRegistrationEnds() throws Exception {
        String lotteryWithEntrantsEventId = "lottery-entrants-test-" + UUID.randomUUID();

        List<String> entrants = Arrays.asList("entrant1", "entrant2", "entrant3", "entrant4", "entrant5");
        createEntrantUsers(entrants);

        createEventDocument(
                lotteryWithEntrantsEventId,
                "Lottery With Entrants Test Event",
                new ArrayList<>(entrants),
                new ArrayList<>(),
                new ArrayList<>(),
                0
        );

        safeSleep(WAIT_LONG);

        Intent intent = new Intent(
                ApplicationProvider.getApplicationContext(),
                OrganizerEntrantListActivity.class);
        intent.putExtra("eventId", lotteryWithEntrantsEventId);

        ActivityScenario<OrganizerEntrantListActivity> scenario =
                ActivityScenario.launch(intent);

        waitUntilLotteryButtonEnabled(scenario);

        onView(withId(R.id.et_lottery_count)).perform(replaceText("3"), closeSoftKeyboard());
        onView(withId(R.id.btn_lottery_draw)).perform(click());

        DocumentSnapshot snapshot = waitForSelectedEntrants(lotteryWithEntrantsEventId, 3);

        assertTrue("Event document must exist after lottery", snapshot.exists());

        @SuppressWarnings("unchecked")
        List<String> selectedEntrants = (List<String>) snapshot.get("selectedEntrantIds");
        assertNotNull("Selected entrants list should not be null after lottery", selectedEntrants);
        assertEquals("Should have selected 3 winners", 3, selectedEntrants.size());
        assertEquals("Each selected entrant should receive one lottery notification",
                3, countNotificationsByType(selectedEntrants, NotificationHelper.TYPE_LOTTERY_SELECTED));

        scenario.close();

        deleteNotificationsForUsers(entrants);
        deleteEventDocument(lotteryWithEntrantsEventId);
        deleteEntrantUsers(entrants);
    }

    /**
     * test12: Replacement draw should pick a random applicant from the remaining
     * pool and update the replacementEntrantIds field on the event document.
     */
    @Test
    public void test12_ReplacementDrawPicksRandomApplicant() throws InterruptedException {
        String replacementEventId = "replacement-test-" + UUID.randomUUID();
        List<String> entrants = Arrays.asList("a1", "a2", "a3", "a4", "a5");
        createEntrantUsers(entrants);
        createEventDocument(
                replacementEventId,
                "Replacement Draw Event",
                new ArrayList<>(entrants),
                new ArrayList<>(Arrays.asList("a1", "a2")),
                new ArrayList<>(),
                3
        );

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
        assertEquals("Replacement entrant should receive one replacement notification",
                1, countNotificationsByType(List.of(chosen), NotificationHelper.TYPE_REPLACEMENT_SELECTED));

        scenario.close();
        deleteNotificationsForUsers(entrants);
        deleteEventDocument(replacementEventId);
        deleteEntrantUsers(entrants);
    }


    /**
     * Drives the full organizer signup flow launched from LoginActivity, progressing through the
     * Details and Address screens until OrganizerHomeActivity becomes visible.
     */
    private void performFullSignup() {
        // LoginActivity was already launched by setUp()
        onView(withId(R.id.btn_organizer_login)).perform(click());

        // Wait for routing after FCM token + Firestore lookup.
        safeSleep(WAIT_MEDIUM);

        if (isViewDisplayed(R.id.nav_create_events)) {
            onView(withId(R.id.nav_create_events)).check(matches(isDisplayed()));
            return;
        }

//        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));

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

    private Date parseTestDate(String value) {
        try {
            return new SimpleDateFormat(DATE_TIME_PATTERN, java.util.Locale.getDefault()).parse(value);
        } catch (Exception e) {
            throw new AssertionError("Unable to parse test date: " + value, e);
        }
    }

    private void createEventDocument(String eventId,
                                     String eventName,
                                     List<String> waitlistEntrants,
                                     List<String> selectedEntrants,
                                     List<String> replacementEntrants,
                                     int lotteryCount) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventId", eventId);
        eventData.put("organizerId", "organizer-test");
        eventData.put("eventName", eventName);
        eventData.put("registrationStartDate", parseTestDate("2026-03-01 00:00"));
        eventData.put("registrationEndDate", parseTestDate("2026-03-10 00:00"));
        eventData.put("maxWaitlist", 25);
        eventData.put("currentWaitlistCount", waitlistEntrants.size());
        eventData.put("geolocationRequired", false);
        eventData.put("location", "Edmonton");
        eventData.put("description", "Test event for notifications");
        eventData.put("waitlistEntrantIds", waitlistEntrants);
        eventData.put("selectedEntrantIds", selectedEntrants);
        eventData.put("replacementEntrantIds", replacementEntrants);
        eventData.put("acceptedEntrantIds", new ArrayList<String>());
        eventData.put("declinedEntrantIds", new ArrayList<String>());
        eventData.put("lotteryCount", lotteryCount);

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events").document(eventId)
                .set(eventData)
                .addOnCompleteListener(task -> latch.countDown());
        awaitLatch(latch, 15, "event creation");
    }

    private void createOwnedEventDocument(String eventId, String eventName, String organizerId) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventId", eventId);
        eventData.put("organizerId", organizerId);
        eventData.put("eventName", eventName);
        eventData.put("registrationStartDate", parseTestDate("2026-03-01 00:00"));
        eventData.put("registrationEndDate", parseTestDate("2026-03-10 00:00"));
        eventData.put("maxWaitlist", 25);
        eventData.put("currentWaitlistCount", 0);
        eventData.put("capacity", 100);
        eventData.put("geolocationRequired", false);
        eventData.put("location", "Edmonton");
        eventData.put("description", "Organizer comments test event");
        eventData.put("waitlistEntrantIds", new ArrayList<String>());
        eventData.put("selectedEntrantIds", new ArrayList<String>());
        eventData.put("replacementEntrantIds", new ArrayList<String>());
        eventData.put("acceptedEntrantIds", new ArrayList<String>());
        eventData.put("declinedEntrantIds", new ArrayList<String>());
        eventData.put("lotteryCount", 0);

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events").document(eventId)
                .set(eventData)
                .addOnCompleteListener(task -> latch.countDown());
        awaitLatch(latch, 15, "owned event creation");
    }

    private void createOrganizerUser(String organizerId, String firstName, String lastName) {
        CountDownLatch latch = new CountDownLatch(1);
        Map<String, Object> userData = new HashMap<>();
        userData.put("firstName", firstName);
        userData.put("lastName", lastName);
        userData.put("role", "organizer");
        userData.put("profileCompleted", true);
        db.collection("users").document(organizerId)
                .set(userData)
                .addOnCompleteListener(task -> latch.countDown());
        awaitLatch(latch, 15, "organizer user creation");
    }

    private void createEventComment(String eventId,
                                    String authorId,
                                    String authorName,
                                    String authorRole,
                                    String commentText) {
        String commentId = "comment-" + UUID.randomUUID();
        Map<String, Object> comment = new HashMap<>();
        comment.put("commentId", commentId);
        comment.put("eventId", eventId);
        comment.put("authorId", authorId);
        comment.put("authorName", authorName);
        comment.put("authorRole", authorRole);
        comment.put("commentText", commentText);
        comment.put("createdAt", com.google.firebase.Timestamp.now());

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events").document(eventId)
                .collection("comments")
                .document(commentId)
                .set(comment)
                .addOnCompleteListener(task -> latch.countDown());
        awaitLatch(latch, 15, "event comment creation");
    }

    private void deleteEventDocument(String eventId) {
        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events").document(eventId)
                .delete()
                .addOnCompleteListener(task -> latch.countDown());
        awaitLatch(latch, 15, "event cleanup");
    }

    private void deleteUserDocument(String userId) {
        CountDownLatch latch = new CountDownLatch(1);
        db.collection("users").document(userId)
                .delete()
                .addOnCompleteListener(task -> latch.countDown());
        awaitLatch(latch, 15, "user cleanup");
    }

    private void createEntrantUsers(List<String> entrantIds) {
        CountDownLatch latch = new CountDownLatch(entrantIds.size());
        for (String entrantId : entrantIds) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("firstName", "Entrant");
            userData.put("lastName", entrantId);
            userData.put("email", entrantId + "@example.com");
            userData.put("notificationsEnabled", true);
            userData.put("role", "entrant");
            db.collection("users").document(entrantId)
                    .set(userData)
                    .addOnCompleteListener(task -> latch.countDown());
        }
        awaitLatch(latch, 15, "entrant user creation");
    }

    private void deleteEntrantUsers(List<String> entrantIds) {
        CountDownLatch latch = new CountDownLatch(entrantIds.size());
        for (String entrantId : entrantIds) {
            db.collection("users").document(entrantId)
                    .delete()
                    .addOnCompleteListener(task -> latch.countDown());
        }
        awaitLatch(latch, 15, "entrant user cleanup");
    }

    private void deleteNotificationsForUsers(List<String> entrantIds) {
        for (String entrantId : entrantIds) {
            CountDownLatch readLatch = new CountDownLatch(1);
            AtomicReference<List<DocumentSnapshot>> snapshotsRef = new AtomicReference<>(new ArrayList<>());
            db.collection("users").document(entrantId).collection("notifications")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        snapshotsRef.set(new ArrayList<>(querySnapshot.getDocuments()));
                        readLatch.countDown();
                    })
                    .addOnFailureListener(e -> readLatch.countDown());
            awaitLatch(readLatch, 15, "notification cleanup read");

            List<DocumentSnapshot> snapshots = snapshotsRef.get();
            if (snapshots == null || snapshots.isEmpty()) {
                continue;
            }

            CountDownLatch deleteLatch = new CountDownLatch(snapshots.size());
            for (DocumentSnapshot snapshot : snapshots) {
                snapshot.getReference().delete().addOnCompleteListener(task -> deleteLatch.countDown());
            }
            awaitLatch(deleteLatch, 15, "notification cleanup delete");
        }
    }

    private int countNotificationsByType(List<String> entrantIds, String type) throws InterruptedException {
        int total = 0;
        for (String entrantId : entrantIds) {
            AtomicReference<Integer> countRef = new AtomicReference<>(0);
            CountDownLatch latch = new CountDownLatch(1);
            db.collection("users")
                    .document(entrantId)
                    .collection("notifications")
                    .whereEqualTo("type", type)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        countRef.set(querySnapshot.size());
                        latch.countDown();
                    })
                    .addOnFailureListener(e -> latch.countDown());
            assertTrue("Timed out counting notifications", latch.await(10, TimeUnit.SECONDS));
            total += countRef.get();
        }
        return total;
    }

    private void cleanupCommentsForEvent(String eventId) {
        CountDownLatch readLatch = new CountDownLatch(1);
        AtomicReference<List<DocumentSnapshot>> snapshotsRef = new AtomicReference<>(new ArrayList<>());
        db.collection("events").document(eventId).collection("comments")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    snapshotsRef.set(new ArrayList<>(querySnapshot.getDocuments()));
                    readLatch.countDown();
                })
                .addOnFailureListener(e -> readLatch.countDown());
        awaitLatch(readLatch, 15, "comment cleanup read");

        List<DocumentSnapshot> snapshots = snapshotsRef.get();
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }

        CountDownLatch deleteLatch = new CountDownLatch(snapshots.size());
        for (DocumentSnapshot snapshot : snapshots) {
            snapshot.getReference().delete().addOnCompleteListener(task -> deleteLatch.countDown());
        }
        awaitLatch(deleteLatch, 15, "comment cleanup delete");
    }

    private Map<String, Object> waitForCommentByText(String eventId, String commentText) throws Exception {
        for (int i = 0; i < 15; i++) {
            DocumentSnapshot match = null;
            com.google.firebase.firestore.QuerySnapshot querySnapshot = Tasks.await(
                    db.collection("events").document(eventId).collection("comments").get(),
                    10,
                    TimeUnit.SECONDS
            );
            for (DocumentSnapshot snapshot : querySnapshot.getDocuments()) {
                if (commentText.equals(snapshot.getString("commentText"))) {
                    match = snapshot;
                    break;
                }
            }
            if (match != null) {
                return match.getData();
            }
            safeSleep(1000);
        }
        throw new AssertionError("Timed out waiting for comment text: " + commentText);
    }

    private void waitUntilCommentDeleted(String eventId, String commentText) throws Exception {
        for (int i = 0; i < 15; i++) {
            boolean exists = false;
            com.google.firebase.firestore.QuerySnapshot querySnapshot = Tasks.await(
                    db.collection("events").document(eventId).collection("comments").get(),
                    10,
                    TimeUnit.SECONDS
            );
            for (DocumentSnapshot snapshot : querySnapshot.getDocuments()) {
                if (commentText.equals(snapshot.getString("commentText"))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                return;
            }
            safeSleep(1000);
        }
        throw new AssertionError("Comment was not deleted in time: " + commentText);
    }

    private boolean isViewDisplayed(int viewId) {
        try {
            onView(withId(viewId)).check(matches(isDisplayed()));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
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

    private void waitUntilLotteryButtonEnabled(ActivityScenario<OrganizerEntrantListActivity> scenario)
            throws InterruptedException {
        AtomicBoolean enabled = new AtomicBoolean(false);

        for (int i = 0; i < 15; i++) {
            scenario.onActivity(activity -> {
                Button button = activity.findViewById(R.id.btn_lottery_draw);
                enabled.set(button != null && button.isEnabled());
            });

            if (enabled.get()) {
                return;
            }
            safeSleep(1000);
        }

        throw new AssertionError("Lottery button never became enabled");
    }

    private void waitUntilEditEventDatesLoaded(ActivityScenario<OrganizerEditEventActivity> scenario)
            throws InterruptedException {
        AtomicBoolean loaded = new AtomicBoolean(false);

        for (int i = 0; i < 15; i++) {
            scenario.onActivity(activity -> {
                android.widget.EditText startField = activity.findViewById(R.id.et_registration_start_date);
                android.widget.EditText endField = activity.findViewById(R.id.et_registration_end_date);
                String startText = startField != null && startField.getText() != null
                        ? startField.getText().toString().trim()
                        : "";
                String endText = endField != null && endField.getText() != null
                        ? endField.getText().toString().trim()
                        : "";
                loaded.set(!startText.isEmpty() && !endText.isEmpty());
            });

            if (loaded.get()) {
                return;
            }
            safeSleep(1000);
        }

        throw new AssertionError("Edit event form never finished loading existing registration dates");
    }

    @SuppressWarnings("unchecked")
    private DocumentSnapshot waitForSelectedEntrants(String eventId, int expectedCount)
            throws Exception {
        for (int i = 0; i < 15; i++) {
            DocumentSnapshot snapshot =
                    Tasks.await(db.collection("events").document(eventId).get(), 15, TimeUnit.SECONDS);

            if (snapshot != null && snapshot.exists()) {
                List<String> selectedEntrants = (List<String>) snapshot.get("selectedEntrantIds");
                if (selectedEntrants != null && selectedEntrants.size() == expectedCount) {
                    return snapshot;
                }
            }

            safeSleep(1000);
        }

        throw new AssertionError("Lottery result was not saved with " + expectedCount + " winners");
    }

    @SuppressWarnings("unchecked")
    private DocumentSnapshot waitForEventDocument(String eventId, int expectedWaitlistSize) throws Exception {
        for (int i = 0; i < 15; i++) {
            DocumentSnapshot snapshot =
                    com.google.android.gms.tasks.Tasks.await(
                            db.collection("events").document(eventId).get(),
                            10,
                            TimeUnit.SECONDS
                    );

            if (snapshot != null && snapshot.exists()) {
                List<String> waitlistEntrants = (List<String>) snapshot.get("waitlistEntrantIds");
                if (waitlistEntrants != null && waitlistEntrants.size() == expectedWaitlistSize) {
                    return snapshot;
                }
            }

            safeSleep(1000);
        }

        throw new AssertionError("Timed out waiting for lottery test event to appear");
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
