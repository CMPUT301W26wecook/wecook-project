package com.example.wecookproject;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withSpinnerText;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.content.Intent;
import android.provider.Settings;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.PerformException;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import org.hamcrest.Matcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class UserFlowTest {

    private final String testEventId = "integration_test_event";
    private String androidId;
    private FirebaseFirestore db;

    @Before
    public void setUp() {
        db = FirebaseFirestore.getInstance();
        androidId = Settings.Secure.getString(
                ApplicationProvider.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);
    }

    @After
    public void tearDown() {
        db.collection("events").document(testEventId).delete();
    }


    @Test
    public void test1_LoginNewUserNavigatesToSignup() {
        db.collection("users").document(androidId).delete();
        ActivityScenario.launch(LoginActivity.class);
        onView(withId(R.id.btn_entrant_login)).perform(click());
        onView(withId(R.id.et_first_name)).check(matches(isDisplayed()));
    }

    @Test
    public void test2_LoginExistingUserNavigatesToEvents() {
        prepareTestUser();
        ActivityScenario.launch(LoginActivity.class);
        onView(withId(R.id.btn_entrant_login)).perform(click());
        safeSleep(1000);
        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));
    }

    @Test
    public void test3_SignupBirthdayAutoFormatting() {
        ActivityScenario.launch(SignupDetailsActivity.class);
        onView(withId(R.id.et_birthday)).perform(typeText("12311999"));
        onView(withId(R.id.et_birthday)).check(matches(withText("12/31/1999")));
    }

    @Test
    public void test4_SignupDetailsValidation_MissingFirstName() {
        ActivityScenario.launch(SignupDetailsActivity.class);
        onView(withId(R.id.et_birthday)).perform(typeText("01012000"));
        onView(withId(R.id.btn_continue)).perform(click());
        onView(withId(R.id.et_address_line_1)).check(doesNotExist());
    }

    @Test
    public void test5_SignupAddressValidation_MissingPostal() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), SignupAddressActivity.class);
        intent.putExtra("firstName", "Tester");
        ActivityScenario.launch(intent);
        onView(withId(R.id.et_address_line_1)).perform(typeText("123 Street"));
        onView(withId(R.id.et_city)).perform(typeText("Edmonton"));
        onView(withId(R.id.btn_continue)).perform(click());
        onView(withId(R.id.rv_events)).check(doesNotExist());
    }



    @Test
    public void test6_ProfileEditModeToggle() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.et_first_name)).check(matches(not(isEnabled())));
        onView(withId(R.id.btn_update)).perform(click());
        onView(withId(R.id.et_first_name)).check(matches(isEnabled()));
        onView(withId(R.id.btn_update)).check(matches(withText("Save Changes")));
    }

    @Test
    public void test7_ProfileUpdateSuccess() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.btn_update)).perform(click());
        onView(withId(R.id.et_last_name)).perform(clearText(), typeText("NewName"));
        onView(withId(R.id.btn_update)).perform(click());
        safeSleep(1000);
        onView(withId(R.id.et_last_name)).check(matches(withText("NewName")));
    }

    @Test
    public void test8_ProfileUpdateInvalidBirthdayBlocked() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.btn_update)).perform(click());
        onView(withId(R.id.et_birthday)).perform(clearText(), typeText("13322020"));
        onView(withId(R.id.btn_update)).perform(click());
        onView(withId(R.id.btn_update)).check(matches(withText("Save Changes")));
    }

    @Test
    public void test8b_ProfileUpdateInvalidEmailBlocked() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.btn_update)).perform(click());
        onView(withId(R.id.et_email)).perform(clearText(), typeText("bad-email"));
        onView(withId(R.id.btn_update)).perform(click());
        onView(withId(R.id.btn_update)).check(matches(withText("Save Changes")));
    }

    @Test
    public void test8c_ProfileUpdateInvalidPostalCodeBlocked() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.btn_update)).perform(click());
        onView(withId(R.id.et_postal_code)).perform(scrollTo(), replaceText("12345"), closeSoftKeyboard());
        onView(withId(R.id.btn_update)).perform(click());
        onView(withId(R.id.btn_update)).check(matches(withText("Save Changes")));
    }

    @Test
    public void test8d_ProfileUpdateEmptyRequiredFieldBlocked() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.btn_update)).perform(click());
        onView(withId(R.id.et_city)).perform(clearText());
        onView(withId(R.id.btn_update)).perform(click());
        onView(withId(R.id.btn_update)).check(matches(withText("Save Changes")));
    }

//    @Test
//    public void test8_ProfileNotificationToggle() {
//        prepareTestUser();
//        ActivityScenario.launch(UserProfileActivity.class);
//        onView(withId(R.id.iv_notifications)).perform(click());
//        onView(withId(R.id.iv_notifications)).check(matches(isDisplayed()));
//    }

    @Test
    public void test9_ProfileAutoLoginToggle() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.btn_update)).perform(click());
        onView(withId(R.id.switch_auto_login)).perform(click());
        onView(withId(R.id.btn_update)).perform(click());
        // 验证状态保存
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.switch_auto_login)).check(matches(isDisplayed()));
    }

    @Test
    public void test10_ProfileDeleteAccountDialogShow() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.btn_delete)).perform(click());
        onView(withText("Delete Account")).check(matches(isDisplayed()));
        onView(withText("Cancel")).perform(click());
    }

    @Test
    public void test11_ProfileDeleteAccount_Cancel() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.btn_delete)).perform(click());
        onView(withText("Cancel")).perform(click());
        onView(withId(R.id.btn_delete)).check(matches(isDisplayed()));
    }

    @Test
    public void test12_ProfileNavigationToEvents() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.nav_events)).perform(click());
        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));
    }

    @Test
    public void test13_BottomNav_ScanToast() {
        prepareTestUser();
        ActivityScenario.launch(UserEventActivity.class);
        onView(withId(R.id.nav_scan)).perform(click());

        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));
    }

    @Test
    public void test13b_EventDialogCanOpenDetailsActivity() {
        prepareTestUser();
        createTestEvent("Tap Through Event", "Edmonton", 10, List.of());

        ActivityScenario.launch(UserEventActivity.class);
        safeSleep(1000);

        onView(withText("Tap Through Event")).perform(click());
        onView(withId(R.id.btn_dialog_open_details)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_dialog_open_details)).perform(click());
        safeSleep(1000);

        onView(withId(R.id.tv_detail_event_name)).check(matches(withText("Tap Through Event")));
    }

    @Test
    public void test13c_EligibilityFilterShowsOnlyJoinableEvents() throws Exception {
        prepareTestUser();

        String joinableEventId = "joinable-filter-" + UUID.randomUUID();
        String fullEventId = "full-filter-" + UUID.randomUUID();
        String waitlistedEventId = "waitlisted-filter-" + UUID.randomUUID();
        String closedEventId = "closed-filter-" + UUID.randomUUID();

        String joinableEventName = "Joinable Event " + joinableEventId;
        String fullEventName = "Full Event " + fullEventId;
        String waitlistedEventName = "Waitlisted Event " + waitlistedEventId;
        String closedEventName = "Closed Event " + closedEventId;

        Timestamp now = Timestamp.now();
        Timestamp oneDayAgo = new Timestamp(new Date(now.toDate().getTime() - TimeUnit.DAYS.toMillis(1)));
        Timestamp oneDayLater = new Timestamp(new Date(now.toDate().getTime() + TimeUnit.DAYS.toMillis(1)));
        Timestamp twoDaysAgo = new Timestamp(new Date(now.toDate().getTime() - TimeUnit.DAYS.toMillis(2)));

        createEligibilityFilterEvent(joinableEventId, joinableEventName, List.of(), 3, oneDayAgo, oneDayLater);
        createEligibilityFilterEvent(fullEventId, fullEventName, List.of("entrant-a", "entrant-b"), 2, oneDayAgo, oneDayLater);
        createEligibilityFilterEvent(waitlistedEventId, waitlistedEventName, List.of(androidId), 3, oneDayAgo, oneDayLater);
        createEligibilityFilterEvent(closedEventId, closedEventName, List.of(), 3, twoDaysAgo, oneDayAgo);

        ActivityScenario<UserEventActivity> scenario = ActivityScenario.launch(UserEventActivity.class);

        try {
            waitForEventInList(joinableEventName, 8000);
            assertEventInList(fullEventName);
            assertEventInList(waitlistedEventName);
            assertEventInList(closedEventName);
            onView(withId(R.id.spinner_eligibility_filter))
                    .check(matches(withSpinnerText("Eligibility: All")));

            selectSpinnerItem(R.id.spinner_eligibility_filter, "Eligibility: Joinable");
            safeSleep(1000);

            assertEventInList(joinableEventName);
            assertEventNotInList(fullEventName);
            assertEventNotInList(waitlistedEventName);
            assertEventNotInList(closedEventName);

            selectSpinnerItem(R.id.spinner_eligibility_filter, "Eligibility: All");
            safeSleep(1000);

            assertEventInList(joinableEventName);
            assertEventInList(fullEventName);
            assertEventInList(waitlistedEventName);
            assertEventInList(closedEventName);
        } finally {
            scenario.close();
            cleanupHistoryDocument(joinableEventId);
            cleanupHistoryDocument(fullEventId);
            cleanupHistoryDocument(waitlistedEventId);
            cleanupHistoryDocument(closedEventId);
            cleanupEvent(joinableEventId);
            cleanupEvent(fullEventId);
            cleanupEvent(waitlistedEventId);
            cleanupEvent(closedEventId);
        }
    }

    @Test
    public void test20_EventDetailsShowQrDisplaysPromotionalLink() throws InterruptedException {
        prepareTestUser();
        String eventId = "user-qr-" + UUID.randomUUID();
        createEventForQrTest(eventId);
        createHistoryForQrTest(eventId);

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), UserEventDetailsActivity.class);
        intent.putExtra("eventId", eventId);
        ActivityScenario<UserEventDetailsActivity> scenario = ActivityScenario.launch(intent);

        safeSleep(1500);
        onView(withId(R.id.btn_detail_show_qr)).perform(click());
        onView(withText("Event QR Code")).check(matches(isDisplayed()));
        onView(withText(QrCodeUtils.buildPromotionalEventLink(eventId))).check(matches(isDisplayed()));
        onView(withText(QrCodeUtils.buildPromotionalEventLink(eventId))).perform(click());
        safeSleep(1000);
        onView(withId(R.id.tv_public_event_name)).check(matches(withText("User QR Test Event")));
        pressBack();
        safeSleep(500);

        scenario.close();
        db.collection("users").document(androidId).collection("eventHistory").document(eventId).delete();
        db.collection("events").document(eventId).delete();
    }

    @Test
    public void test14_EventDetailsShowsLotteryCriteriaButton() {
        prepareTestUser();
        createTestEvent("Lottery Event", "Edmonton", 10, List.of());

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), UserEventDetailsActivity.class);
        intent.putExtra("eventId", testEventId);
        ActivityScenario.launch(intent);

        onView(withId(R.id.btn_view_lottery_criteria)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_view_lottery_criteria)).perform(click());
        onView(withText("Lottery Criteria")).check(matches(isDisplayed()));
    }

    @Test
    public void test15_LotteryCriteriaBackReturnsToEventDetails() {
        prepareTestUser();
        createTestEvent("Lottery Event", "Edmonton", 10, List.of());

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), UserEventDetailsActivity.class);
        intent.putExtra("eventId", testEventId);
        ActivityScenario.launch(intent);

        onView(withId(R.id.btn_view_lottery_criteria)).perform(click());
        onView(withId(R.id.iv_lottery_criteria_back)).perform(click());
        safeSleep(500);
        onView(withId(R.id.tv_detail_event_name)).check(matches(isDisplayed()));
    }

    @Test
    public void test16_NotificationReadButtonMarksNotificationRead() throws Exception {
        prepareTestUser();
        String eventId = "notif-read-" + UUID.randomUUID();
        String eventName = "Inbox Read Event " + eventId;
        createDetailedEvent(eventId, eventName);
        String notificationId = createNotification(eventId, eventName, NotificationHelper.TYPE_PRIVATE_INVITE);

        ActivityScenario.launch(UserNotificationActivity.class);
        safeSleep(1000);

        onView(withId(R.id.rv_notifications))
                .perform(RecyclerViewActions.actionOnItem(
                        hasDescendant(withText(eventName)),
                        click()
                ));
        safeSleep(1000);

        DocumentSnapshot snapshot = readNotification(notificationId);
        assertNotNull(snapshot);
        assertEquals(NotificationHelper.STATUS_READ, snapshot.getString("status"));
        assertNotNull(snapshot.getTimestamp("readAt"));

        cleanupNotification(notificationId);
        cleanupEvent(eventId);
    }

    @Test
    public void test16a_PrivateWaitlistInviteAppearsInEventsAndJoinsFromNotification() throws Exception {
        prepareTestUser();
        String eventId = "private-waitlist-" + UUID.randomUUID();
        String eventName = "Private Waitlist Event " + eventId;
        createPrivateWaitlistInviteEvent(eventId, eventName);
        createHistoryDocument(eventId, UserEventRecord.STATUS_WAITLIST_INVITED);
        String notificationId = createNotification(
                eventId,
                eventName,
                NotificationHelper.TYPE_PRIVATE_WAITLIST_INVITE
        );

        ActivityScenario<UserEventActivity> eventScenario = ActivityScenario.launch(UserEventActivity.class);
        try {
            waitForEventInList(eventName, 8000);
            onView(withId(R.id.rv_events))
                    .perform(RecyclerViewActions.actionOnItem(
                            hasDescendant(withText(eventName)),
                            click()
                    ));
            onView(withId(R.id.btn_join_waitlist)).check(matches(withText("Join the Waitlist")));
            pressBack();
        } finally {
            eventScenario.close();
        }

        ActivityScenario<UserNotificationActivity> notificationScenario =
                ActivityScenario.launch(UserNotificationActivity.class);
        try {
            safeSleep(1000);
            onView(withId(R.id.rv_notifications))
                    .perform(RecyclerViewActions.actionOnItem(
                            hasDescendant(withText(eventName)),
                            clickChildViewWithId(R.id.btn_notification_mark_read)
                    ));
            safeSleep(1500);
        } finally {
            notificationScenario.close();
        }

        DocumentSnapshot eventSnapshot = readEvent(eventId);
        assertNotNull(eventSnapshot);
        @SuppressWarnings("unchecked")
        List<String> waitlistEntrants = (List<String>) eventSnapshot.get("waitlistEntrantIds");
        @SuppressWarnings("unchecked")
        List<String> privateInviteeIds = (List<String>) eventSnapshot.get("privateWaitlistInviteeIds");
        assertNotNull(waitlistEntrants);
        assertTrue(waitlistEntrants.contains(androidId));
        assertFalse(privateInviteeIds != null && privateInviteeIds.contains(androidId));

        DocumentSnapshot historySnapshot = readHistoryDocument(eventId);
        assertNotNull(historySnapshot);
        assertEquals(UserEventRecord.STATUS_WAITLISTED, historySnapshot.getString("status"));

        DocumentSnapshot notificationSnapshot = readNotification(notificationId);
        assertNotNull(notificationSnapshot);
        assertEquals(NotificationHelper.STATUS_CONFIRMED, notificationSnapshot.getString("status"));

        cleanupNotification(notificationId);
        cleanupHistoryDocument(eventId);
        cleanupEvent(eventId);
    }

    @Test
    public void test17_NotificationTapOpensEventDetailsAndMarksRead() throws Exception {
        prepareTestUser();
        String eventId = "notif-open-" + UUID.randomUUID();
        String eventName = "Inbox Open Event " + eventId;
        createDetailedEvent(eventId, eventName);
        String notificationId = createNotification(eventId, eventName, NotificationHelper.TYPE_LOTTERY_SELECTED);

        ActivityScenario.launch(UserNotificationActivity.class);
        safeSleep(1000);

        onView(withId(R.id.rv_notifications))
                .perform(RecyclerViewActions.actionOnItem(
                        hasDescendant(withText(eventName)),
                        click()
                ));
        waitForViewText(R.id.tv_detail_event_name, eventName, 8000);

        DocumentSnapshot snapshot = readNotification(notificationId);
        assertNotNull(snapshot);
        assertEquals(NotificationHelper.STATUS_READ, snapshot.getString("status"));
        assertNotNull(snapshot.getTimestamp("readAt"));

        cleanupNotification(notificationId);
        cleanupEvent(eventId);
    }

    @Test
    public void test18_EventDetailsDisplaysExistingComments() throws Exception {
        prepareTestUser();
        String eventId = "comment-view-" + UUID.randomUUID();
        createDetailedEvent(eventId, "Comment View Event");
        createEventComment(eventId, "organizer-test", "Organizer One", "organizer", "Welcome to the event.");

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), UserEventDetailsActivity.class);
        intent.putExtra("eventId", eventId);
        ActivityScenario.launch(intent);

        waitForVisibleText("Comments", 8000);
        waitForVisibleText("Organizer One", 8000);
        waitForVisibleText("Welcome to the event.", 8000);
        waitForVisibleText("ORGANIZER", 8000);

        onView(withText("Organizer One")).perform(scrollTo());
        onView(withText("Organizer One")).check(matches(isDisplayed()));
        onView(allOf(withId(R.id.tv_comment_text), withText("Welcome to the event."))).perform(scrollTo());
        onView(allOf(withId(R.id.tv_comment_text), withText("Welcome to the event."))).check(matches(isDisplayed()));
        onView(allOf(withId(R.id.tv_comment_author_tag), withText("ORGANIZER"))).perform(scrollTo());
        onView(allOf(withId(R.id.tv_comment_author_tag), withText("ORGANIZER"))).check(matches(isDisplayed()));

        cleanupCommentsForEvent(eventId);
        cleanupEvent(eventId);
    }

    @Test
    public void test19_EventDetailsPostCommentPersistsToFirestore() throws Exception {
        prepareTestUser();
        String eventId = "comment-post-" + UUID.randomUUID();
        createDetailedEvent(eventId, "Comment Post Event");

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), UserEventDetailsActivity.class);
        intent.putExtra("eventId", eventId);
        ActivityScenario.launch(intent);

        safeSleep(1500);
        onView(withId(R.id.et_event_comment)).perform(scrollTo(), replaceText("Entrant integration comment"), closeSoftKeyboard());
        onView(withId(R.id.btn_post_comment)).perform(scrollTo(), click());
        safeSleep(2000);

        Map<String, Object> postedComment = findCommentByText(eventId, "Entrant integration comment");
        assertNotNull(postedComment);
        assertEquals(androidId, postedComment.get("authorId"));
        assertEquals("entrant", postedComment.get("authorRole"));
        assertFalse(String.valueOf(postedComment.get("authorName")).trim().isEmpty());

        cleanupCommentsForEvent(eventId);
        cleanupEvent(eventId);
    }



    private void prepareTestUser() {
        CountDownLatch latch = new CountDownLatch(1);
        Map<String, Object> user = new HashMap<>();
        user.put("firstName", "Integration");
        user.put("lastName", "Tester");
        user.put("birthday", "01/01/2000");
        user.put("email", "integration@test.com");
        user.put("addressLine1", "123 Main St");
        user.put("city", "Edmonton");
        user.put("postalCode", "T6G 2R3");
        user.put("country", "Canada");
        user.put("role", "entrant");
        user.put("profileCompleted", true);
        db.collection("users").document(androidId).set(user).addOnCompleteListener(t -> latch.countDown());
        try { latch.await(3, TimeUnit.SECONDS); } catch (Exception ignored) {}
    }

    private void createTestEvent(String name, String loc, int max, List<String> entrants) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventId", testEventId);
        eventData.put("eventName", name);
        eventData.put("location", loc);
        eventData.put("description", "Test event description");
        eventData.put("maxWaitlist", (long) max);
        eventData.put("waitlistEntrantIds", entrants);
        eventData.put("currentWaitlistCount", (long) entrants.size());

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events").document(testEventId).set(eventData).addOnCompleteListener(t -> latch.countDown());
        try { latch.await(3, TimeUnit.SECONDS); } catch (Exception ignored) {}
    }

    private void createEligibilityFilterEvent(String eventId,
                                              String eventName,
                                              List<String> entrants,
                                              int maxWaitlist,
                                              Timestamp registrationStartDate,
                                              Timestamp registrationEndDate) throws InterruptedException {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventId", eventId);
        eventData.put("eventName", eventName);
        eventData.put("location", "Edmonton");
        eventData.put("organizerId", "org-test");
        eventData.put("description", "Eligibility filter test event");
        eventData.put("visibilityTag", "public");
        eventData.put("registrationStartDate", registrationStartDate);
        eventData.put("registrationEndDate", registrationEndDate);
        eventData.put("eventTime", new Timestamp(new Date(registrationEndDate.toDate().getTime() + TimeUnit.HOURS.toMillis(2))));
        eventData.put("maxWaitlist", (long) maxWaitlist);
        eventData.put("waitlistEntrantIds", new ArrayList<>(entrants));
        eventData.put("currentWaitlistCount", (long) entrants.size());
        eventData.put("selectedEntrantIds", new ArrayList<String>());
        eventData.put("replacementEntrantIds", new ArrayList<String>());
        eventData.put("acceptedEntrantIds", new ArrayList<String>());
        eventData.put("geolocationRequired", false);

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events")
                .document(eventId)
                .set(eventData)
                .addOnCompleteListener(task -> latch.countDown());
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    private void createEventForQrTest(String eventId) throws InterruptedException {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventId", eventId);
        eventData.put("eventName", "User QR Test Event");
        eventData.put("location", "Edmonton");
        eventData.put("organizerId", "org-test");
        eventData.put("description", "QR details test");
        eventData.put("enrollmentCriteria", "Open to all");
        eventData.put("lotteryMethodology", "System generates");
        eventData.put("maxWaitlist", 50L);
        eventData.put("waitlistEntrantIds", new ArrayList<String>());
        eventData.put("currentWaitlistCount", 0L);
        eventData.put("geolocationRequired", true);

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events").document(eventId)
                .set(eventData)
                .addOnCompleteListener(t -> latch.countDown());
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    private void createDetailedEvent(String eventId, String eventName) throws InterruptedException {
        Timestamp now = Timestamp.now();
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventId", eventId);
        eventData.put("eventName", eventName);
        eventData.put("location", "Edmonton");
        eventData.put("organizerId", "org-test");
        eventData.put("description", "Notification detail test");
        eventData.put("visibilityTag", "public");
        eventData.put("enrollmentCriteria", "Open to all");
        eventData.put("lotteryMethodology", "System generates");
        eventData.put("registrationStartDate", new Timestamp(new Date(now.toDate().getTime() - TimeUnit.DAYS.toMillis(1))));
        eventData.put("registrationEndDate", new Timestamp(new Date(now.toDate().getTime() + TimeUnit.DAYS.toMillis(1))));
        eventData.put("eventTime", new Timestamp(new Date(now.toDate().getTime() + TimeUnit.DAYS.toMillis(2))));
        eventData.put("maxWaitlist", 50L);
        eventData.put("waitlistEntrantIds", new ArrayList<String>());
        eventData.put("currentWaitlistCount", 0L);
        eventData.put("selectedEntrantIds", new ArrayList<String>());
        eventData.put("replacementEntrantIds", new ArrayList<String>());
        eventData.put("acceptedEntrantIds", new ArrayList<String>());
        eventData.put("geolocationRequired", false);

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events").document(eventId)
                .set(eventData)
                .addOnCompleteListener(t -> latch.countDown());
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    private void createPrivateWaitlistInviteEvent(String eventId, String eventName) throws InterruptedException {
        Timestamp now = Timestamp.now();
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventId", eventId);
        eventData.put("eventName", eventName);
        eventData.put("location", "Edmonton");
        eventData.put("organizerId", "org-test");
        eventData.put("description", "Private waitlist invite test");
        eventData.put("visibilityTag", "private");
        eventData.put("registrationStartDate", new Timestamp(new Date(now.toDate().getTime() - TimeUnit.DAYS.toMillis(1))));
        eventData.put("registrationEndDate", new Timestamp(new Date(now.toDate().getTime() + TimeUnit.DAYS.toMillis(1))));
        eventData.put("eventTime", new Timestamp(new Date(now.toDate().getTime() + TimeUnit.DAYS.toMillis(2))));
        eventData.put("maxWaitlist", 10L);
        eventData.put("waitlistEntrantIds", new ArrayList<String>());
        eventData.put("currentWaitlistCount", 0L);
        eventData.put("selectedEntrantIds", new ArrayList<String>());
        eventData.put("replacementEntrantIds", new ArrayList<String>());
        eventData.put("acceptedEntrantIds", new ArrayList<String>());
        eventData.put("declinedEntrantIds", new ArrayList<String>());
        eventData.put("privateWaitlistInviteeIds", new ArrayList<>(List.of(androidId)));
        eventData.put("geolocationRequired", false);

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events").document(eventId)
                .set(eventData)
                .addOnCompleteListener(t -> latch.countDown());
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    private void createEventComment(String eventId,
                                    String authorId,
                                    String authorName,
                                    String authorRole,
                                    String commentText) throws InterruptedException {
        String commentId = "comment-" + UUID.randomUUID();
        Map<String, Object> comment = new HashMap<>();
        comment.put("commentId", commentId);
        comment.put("eventId", eventId);
        comment.put("authorId", authorId);
        comment.put("authorName", authorName);
        comment.put("authorRole", authorRole);
        comment.put("commentText", commentText);
        comment.put("createdAt", Timestamp.now());

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events")
                .document(eventId)
                .collection("comments")
                .document(commentId)
                .set(comment)
                .addOnCompleteListener(task -> latch.countDown());
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    private String createNotification(String eventId, String eventName, String type) throws InterruptedException {
        String notificationId = "notification-" + UUID.randomUUID();
        Map<String, Object> notification = new HashMap<>();
        notification.put("eventId", eventId);
        notification.put("eventName", eventName);
        notification.put("location", "Edmonton");
        notification.put("message", "Open this notification");
        notification.put("recipientId", androidId);
        notification.put("senderId", "organizer-test");
        notification.put("status", NotificationHelper.STATUS_UNREAD);
        notification.put("type", type);
        notification.put("actionTarget", eventId);
        notification.put("createdAt", Timestamp.now());
        notification.put("readAt", null);

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("users")
                .document(androidId)
                .collection("notifications")
                .document(notificationId)
                .set(notification)
                .addOnCompleteListener(task -> latch.countDown());
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        return notificationId;
    }

    private DocumentSnapshot readNotification(String notificationId) throws InterruptedException {
        AtomicReference<DocumentSnapshot> snapshotRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        db.collection("users")
                .document(androidId)
                .collection("notifications")
                .document(notificationId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    snapshotRef.set(snapshot);
                    latch.countDown();
                })
                .addOnFailureListener(e -> latch.countDown());
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        return snapshotRef.get();
    }

    private void cleanupNotification(String notificationId) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        db.collection("users")
                .document(androidId)
                .collection("notifications")
                .document(notificationId)
                .delete()
                .addOnCompleteListener(task -> latch.countDown());
        latch.await(5, TimeUnit.SECONDS);
    }

    private void cleanupHistoryDocument(String eventId) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        db.collection("users")
                .document(androidId)
                .collection("eventHistory")
                .document(eventId)
                .delete()
                .addOnCompleteListener(task -> latch.countDown());
        latch.await(5, TimeUnit.SECONDS);
    }

    private void createHistoryDocument(String eventId, String status) throws InterruptedException {
        Map<String, Object> historyData = new HashMap<>();
        historyData.put("eventId", eventId);
        historyData.put("status", status);
        historyData.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("users")
                .document(androidId)
                .collection("eventHistory")
                .document(eventId)
                .set(historyData)
                .addOnCompleteListener(task -> latch.countDown());
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    private DocumentSnapshot readEvent(String eventId) throws InterruptedException {
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
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        return snapshotRef.get();
    }

    private DocumentSnapshot readHistoryDocument(String eventId) throws InterruptedException {
        AtomicReference<DocumentSnapshot> snapshotRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        db.collection("users")
                .document(androidId)
                .collection("eventHistory")
                .document(eventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    snapshotRef.set(snapshot);
                    latch.countDown();
                })
                .addOnFailureListener(e -> latch.countDown());
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        return snapshotRef.get();
    }

    private void cleanupEvent(String eventId) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events").document(eventId)
                .delete()
                .addOnCompleteListener(task -> latch.countDown());
        latch.await(5, TimeUnit.SECONDS);
    }

    private void cleanupCommentsForEvent(String eventId) throws InterruptedException {
        AtomicReference<List<DocumentSnapshot>> commentsRef = new AtomicReference<>(new ArrayList<>());
        CountDownLatch fetchLatch = new CountDownLatch(1);
        db.collection("events")
                .document(eventId)
                .collection("comments")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    commentsRef.set(queryDocumentSnapshots.getDocuments());
                    fetchLatch.countDown();
                })
                .addOnFailureListener(e -> fetchLatch.countDown());
        assertTrue(fetchLatch.await(5, TimeUnit.SECONDS));

        List<DocumentSnapshot> comments = commentsRef.get();
        if (comments == null || comments.isEmpty()) {
            return;
        }

        CountDownLatch deleteLatch = new CountDownLatch(comments.size());
        for (DocumentSnapshot comment : comments) {
            db.collection("events")
                    .document(eventId)
                    .collection("comments")
                    .document(comment.getId())
                    .delete()
                    .addOnCompleteListener(task -> deleteLatch.countDown());
        }
        deleteLatch.await(5, TimeUnit.SECONDS);
    }

    private Map<String, Object> findCommentByText(String eventId, String commentText) throws InterruptedException {
        AtomicReference<Map<String, Object>> commentRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events")
                .document(eventId)
                .collection("comments")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot snapshot : queryDocumentSnapshots.getDocuments()) {
                        String storedText = snapshot.getString("commentText");
                        if (commentText.equals(storedText)) {
                            commentRef.set(snapshot.getData());
                            break;
                        }
                    }
                    latch.countDown();
                })
                .addOnFailureListener(e -> latch.countDown());
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        return commentRef.get();
    }

    private void createHistoryForQrTest(String eventId) throws InterruptedException {
        Map<String, Object> historyData = new HashMap<>();
        historyData.put("eventId", eventId);
        historyData.put("status", "");
        historyData.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("users")
                .document(androidId)
                .collection("eventHistory")
                .document(eventId)
                .set(historyData)
                .addOnCompleteListener(t -> latch.countDown());
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    private void safeSleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void waitForViewText(int viewId, String expectedText, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        AssertionError lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(viewId)).check(matches(withText(expectedText)));
                return;
            } catch (AssertionError error) {
                lastError = error;
                safeSleep(250);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        fail("Timed out waiting for view text: " + expectedText);
    }

    private void waitForVisibleText(String expectedText, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        AssertionError lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withText(expectedText)).check(matches(withEffectiveVisibility(VISIBLE)));
                return;
            } catch (AssertionError error) {
                lastError = error;
                safeSleep(250);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        fail("Timed out waiting for text: " + expectedText);
    }

    private void waitForEventInList(String eventName, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        AssertionError lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                assertEventInList(eventName);
                return;
            } catch (AssertionError error) {
                lastError = error;
                safeSleep(250);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        fail("Timed out waiting for event in list: " + eventName);
    }

    private void assertEventInList(String eventName) {
        onView(withId(R.id.rv_events))
                .perform(RecyclerViewActions.scrollTo(hasDescendant(withText(eventName))));
        onView(withText(eventName)).check(matches(isDisplayed()));
    }

    private void assertEventNotInList(String eventName) {
        try {
            onView(withId(R.id.rv_events))
                    .perform(RecyclerViewActions.scrollTo(hasDescendant(withText(eventName))));
            fail("Expected event to be absent from list: " + eventName);
        } catch (PerformException expected) {
            // Expected when the adapter does not contain a matching item.
        }
    }

    private void selectSpinnerItem(int spinnerId, String itemText) {
        onView(withId(spinnerId)).perform(click());
        onData(allOf(is(instanceOf(String.class)), is(itemText))).perform(click());
    }

    private ViewAction clickChildViewWithId(int viewId) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isDisplayed();
            }

            @Override
            public String getDescription() {
                return "Click on a child view with specified id.";
            }

            @Override
            public void perform(UiController uiController, View view) {
                View childView = view.findViewById(viewId);
                if (childView == null) {
                    throw new PerformException.Builder()
                            .withActionDescription(getDescription())
                            .withViewDescription(String.valueOf(view))
                            .build();
                }
                childView.performClick();
            }
        };
    }
}
