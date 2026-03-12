package com.example.wecookproject;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.provider.Settings;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

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
public class AdminFlowTest {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ActivityScenario<LoginActivity> activityScenario;
    private String androidId;
    private String createdEventId;
    private String createdEventName;

    @Before
    public void setUp() {
        androidId = Settings.Secure.getString(
                ApplicationProvider.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);

        deleteUserDocument(androidId);
        createdEventId = null;
        createdEventName = null;

        activityScenario = ActivityScenario.launch(LoginActivity.class);
    }

    @After
    public void tearDown() {
        if (activityScenario != null) {
            activityScenario.close();
        }

        deleteUserDocument(androidId);

        if (createdEventId != null) {
            CountDownLatch latch = new CountDownLatch(1);
            db.collection("events").document(createdEventId)
                    .delete()
                    .addOnCompleteListener(task -> latch.countDown());
            awaitLatch(latch, 5, "Timed out deleting created event");
        }
    }

    @Test
    public void test1_AdminLoginRoutesToSignupDetails() {
        onView(withId(R.id.text_Admin_login)).perform(click());
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
    }

    @Test
    public void test2_AdminDeletesOrganizerCreatedEvent() {
        performOrganizerSignup();
        createdEventName = "Admin Delete Test " + UUID.randomUUID();

        ActivityScenario<OrganizerCreateEventActivity> createScenario =
                ActivityScenario.launch(OrganizerCreateEventActivity.class);

        onView(withId(R.id.et_event_name)).perform(replaceText(createdEventName), closeSoftKeyboard());
        onView(withId(R.id.et_registration_period)).perform(replaceText("2026-05-01 to 2026-05-10"), closeSoftKeyboard());
        onView(withId(R.id.et_max_waitlist)).perform(replaceText("25"), closeSoftKeyboard());
        onView(withId(R.id.rb_open_to_all)).perform(click());
        onView(withId(R.id.rb_system_generates)).perform(click());
        onView(withId(R.id.btn_create_event)).perform(click());

        safeSleep(2500);
        createScenario.close();

        createdEventId = findEventIdByName(createdEventName);
        assertNotNull("Expected created event to exist in Firestore", createdEventId);

        ActivityScenario<AdminEventActivity> adminScenario =
                ActivityScenario.launch(AdminEventActivity.class);

        safeSleep(2000);
        onView(withText(createdEventName)).check(matches(isDisplayed()));
        onView(withText(createdEventName)).perform(click());
        onView(withId(R.id.deleteSelectedEventsButton)).perform(click());
        onView(withText("Delete")).perform(click());

        safeSleep(2500);
        assertFalse("Expected admin delete to remove the event", eventExists(createdEventId));

        adminScenario.close();
        createdEventId = null;
    }

    private void performOrganizerSignup() {
        onView(withId(R.id.btn_organizer_login)).perform(click());

        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
        onView(withId(R.id.et_first_name)).perform(replaceText("Admin"), closeSoftKeyboard());
        onView(withId(R.id.et_last_name)).perform(replaceText("Flow"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(replaceText("01/01/2000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        safeSleep(2000);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));
        onView(withId(R.id.et_address_line_1)).perform(replaceText("123 Test Ave"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(replaceText("Edmonton"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(replaceText("T6G 2R3"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        safeSleep(3000);
        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));
    }

    private String findEventIdByName(String eventName) {
        AtomicReference<String> eventIdRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        db.collection("events")
                .whereEqualTo("eventName", eventName)
                .whereEqualTo("organizerId", androidId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        eventIdRef.set(querySnapshot.getDocuments().get(0).getId());
                    }
                    latch.countDown();
                })
                .addOnFailureListener(e -> latch.countDown());

        assertTrue("Timed out loading created event", awaitLatch(latch, 5, null));
        return eventIdRef.get();
    }

    private boolean eventExists(String eventId) {
        AtomicReference<DocumentSnapshot> snapshotRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        db.collection("events").document(eventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    snapshotRef.set(snapshot);
                    latch.countDown();
                })
                .addOnFailureListener(e -> latch.countDown());

        assertTrue("Timed out checking deleted event", awaitLatch(latch, 5, null));
        DocumentSnapshot snapshot = snapshotRef.get();
        return snapshot != null && snapshot.exists();
    }

    private void deleteUserDocument(String userId) {
        CountDownLatch latch = new CountDownLatch(1);
        db.collection("users").document(userId)
                .delete()
                .addOnCompleteListener(task -> latch.countDown());
        awaitLatch(latch, 5, "Timed out deleting test user");
    }

    private boolean awaitLatch(CountDownLatch latch, int seconds, String timeoutMessage) {
        try {
            boolean success = latch.await(seconds, TimeUnit.SECONDS);
            if (!success && timeoutMessage != null) {
                System.err.println(timeoutMessage);
            }
            return success;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
}
