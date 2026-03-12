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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.provider.Settings;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.example.wecookproject.model.Event;
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
    private final List<String> extraEventIds = new ArrayList<>();

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

        deleteEventIfExists(eventId);
        for (String extraId : extraEventIds) {
            deleteEventIfExists(extraId);
        }
        extraEventIds.clear();
    }

    @Test
    public void test1_OrganizerLoginWithoutExistingUserRoutesToSignup() {
        onView(withId(R.id.btn_organizer_login)).perform(click());
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
    }

    @Test
    public void test2_OrganizerProfileMandatoryNamesBlockUpdate() {
        performFullSignup();

        ActivityScenario<OrganizerProfileActivity> profileScenario =
                ActivityScenario.launch(OrganizerProfileActivity.class);

        onView(withId(R.id.btn_update_info)).perform(click());
        onView(withId(R.id.tv_organizer_info_title)).check(matches(withText("Organizer Info")));

        profileScenario.close();
    }

    @Test
    public void test3_CreateEventWithoutNameIsBlocked() {
        performFullSignup();

        ActivityScenario<OrganizerCreateEventActivity> createScenario =
                ActivityScenario.launch(OrganizerCreateEventActivity.class);

        onView(withId(R.id.btn_create_event)).perform(click());
        onView(withId(R.id.btn_create_event)).check(matches(isDisplayed()));

        createScenario.close();
    }

    @Test
    public void test4_BottomNavSwitchesBetweenTabs() {
        performFullSignup();

        ActivityScenario<OrganizerHomeActivity> homeScenario =
                ActivityScenario.launch(OrganizerHomeActivity.class);

        onView(withId(R.id.nav_create_events)).perform(click());
        safeSleep(500);
        onView(withId(R.id.btn_create_event)).check(matches(isDisplayed()));

        onView(withId(R.id.nav_profile)).perform(click());
        safeSleep(500);
        onView(withId(R.id.tv_organizer_info_title)).check(matches(withText("Organizer Info")));

        onView(withId(R.id.nav_events)).perform(click());
        safeSleep(500);
        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));

        homeScenario.close();
    }

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

    @Test
    public void test6_NotificationScreenIsReachableAndShowsHintField() {
        performFullSignup();

        ActivityScenario<OrganizerNotificationActivity> notifScenario =
                ActivityScenario.launch(OrganizerNotificationActivity.class);

        onView(withId(R.id.et_notification_message)).check(matches(isDisplayed()));

        notifScenario.close();
    }

    @Test
    public void test7_CreateEventAndVerifyInList() {
        performFullSignup();

        ActivityScenario<OrganizerHomeActivity> homeScenario =
                ActivityScenario.launch(OrganizerHomeActivity.class);

        onView(withId(R.id.nav_create_events)).perform(click());
        safeSleep(1000);

        String testEventName = "Espresso Test Event";
        onView(withId(R.id.et_event_name)).perform(typeText(testEventName), closeSoftKeyboard());
        onView(withId(R.id.et_registration_period)).perform(typeText("2026-04-01 to 2026-04-10"), closeSoftKeyboard());
        onView(withId(R.id.et_max_waitlist)).perform(typeText("50"), closeSoftKeyboard());

        onView(withId(R.id.rb_open_to_all)).perform(click());
        onView(withId(R.id.rb_system_generates)).perform(click());

        onView(withId(R.id.btn_create_event)).perform(click());
        safeSleep(2500);

        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));

        homeScenario.close();
    }

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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), OrganizerEventDetailsActivity.class);
        intent.putExtra("eventId", "mockEventId");
        ActivityScenario<OrganizerEventDetailsActivity> detailsScenario =
                ActivityScenario.launch(intent);

        safeSleep(1500);

        onView(withId(R.id.tv_event_name_detail)).check(matches(isDisplayed()));
        onView(withId(R.id.tv_event_dates)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_edit_event)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_view_waitlist)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_registration_map)).check(matches(isDisplayed()));

        detailsScenario.close();
        FirebaseFirestore.getInstance().collection("events").document("mockEventId").delete();
    }

    @Test
    public void test9_EditEventLaunchWithoutIdFinishesActivity() {
        ActivityScenario<OrganizerEditEventActivity> scenario =
                ActivityScenario.launch(OrganizerEditEventActivity.class);

        assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.getState());
        scenario.close();
    }

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

    @Test
    public void test11_LotteryDrawWithoutInputDoesNotWriteSelection() throws InterruptedException {
        String lotteryEventId = createLotteryEvent(Arrays.asList("u1", "u2", "u3", "u4"));

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), OrganizerEntrantListActivity.class);
        intent.putExtra("eventId", lotteryEventId);
        ActivityScenario<OrganizerEntrantListActivity> scenario = ActivityScenario.launch(intent);

        safeSleep(1500);
        onView(withId(R.id.btn_lottery_draw)).perform(click());
        safeSleep(1500);

        DocumentSnapshot snapshot = readEventSnapshot(lotteryEventId);
        assertNotNull(snapshot);
        List<String> selected = getStringList(snapshot, "selectedEntrantIds");
        assertTrue(selected.isEmpty());

        scenario.close();
    }

    @Test
    public void test12_LotteryDrawWithValidInputStoresExactNumberOfWinners() throws InterruptedException {
        List<String> waitlist = Arrays.asList("u1", "u2", "u3", "u4", "u5");
        String lotteryEventId = createLotteryEvent(waitlist);

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), OrganizerEntrantListActivity.class);
        intent.putExtra("eventId", lotteryEventId);
        ActivityScenario<OrganizerEntrantListActivity> scenario = ActivityScenario.launch(intent);

        safeSleep(1500);
        onView(withId(R.id.et_lottery_count)).perform(replaceText("2"), closeSoftKeyboard());
        onView(withId(R.id.btn_lottery_draw)).perform(click());
        waitForFirestoreWrite();

        DocumentSnapshot snapshot = readEventSnapshot(lotteryEventId);
        assertNotNull(snapshot);

        List<String> selected = getStringList(snapshot, "selectedEntrantIds");
        assertEquals(2, selected.size());
        assertEquals(Long.valueOf(2), snapshot.getLong("lotteryCount"));

        Set<String> unique = new HashSet<>(selected);
        assertEquals(selected.size(), unique.size());
        for (String selectedId : selected) {
            assertTrue(waitlist.contains(selectedId));
        }

        scenario.close();
    }

    private String createLotteryEvent(List<String> waitlistEntrantIds) throws InterruptedException {
        String lotteryEventId = "lottery-test-" + UUID.randomUUID();
        extraEventIds.add(lotteryEventId);

        Event lotteryEvent = new Event(
                lotteryEventId,
                "organizer-test",
                "Lottery Test Event",
                "2026-04-01",
                "Open to all",
                25,
                waitlistEntrantIds.size(),
                "System generates",
                false,
                "Edmonton",
                "Lottery description"
        );
        lotteryEvent.setWaitlistEntrantIds(new ArrayList<>(waitlistEntrantIds));
        lotteryEvent.setSelectedEntrantIds(new ArrayList<>());
        lotteryEvent.setLotteryCount(0);

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events")
                .document(lotteryEventId)
                .set(lotteryEvent)
                .addOnCompleteListener(task -> latch.countDown());
        assertTrue("Timed out creating lottery test event", latch.await(5, TimeUnit.SECONDS));

        return lotteryEventId;
    }

    private DocumentSnapshot readEventSnapshot(String targetEventId) throws InterruptedException {
        AtomicReference<DocumentSnapshot> snapshotRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events")
                .document(targetEventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    snapshotRef.set(snapshot);
                    latch.countDown();
                })
                .addOnFailureListener(e -> latch.countDown());

        assertTrue("Timed out reading event snapshot", latch.await(5, TimeUnit.SECONDS));
        return snapshotRef.get();
    }

    private List<String> getStringList(DocumentSnapshot snapshot, String fieldName) {
        List<String> result = new ArrayList<>();
        Object raw = snapshot.get(fieldName);
        if (raw instanceof List<?>) {
            for (Object item : (List<?>) raw) {
                if (item instanceof String) {
                    result.add((String) item);
                }
            }
        }
        return result;
    }

    private void deleteEventIfExists(String targetEventId) {
        if (targetEventId == null) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events").document(targetEventId)
                .delete()
                .addOnCompleteListener(task -> latch.countDown());
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void performFullSignup() {
        onView(withId(R.id.btn_organizer_login)).perform(click());

        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
        onView(withId(R.id.et_first_name)).perform(replaceText("John"), closeSoftKeyboard());
        onView(withId(R.id.et_last_name)).perform(replaceText("Doe"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(replaceText("01/01/2000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        safeSleep(3000);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));
        onView(withId(R.id.et_address_line_1)).perform(replaceText("123 Main St"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(replaceText("Edmonton"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(replaceText("T6G 2R3"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

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
