package com.example.wecookproject;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.example.wecookproject.model.Event;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class OrganizerEditEventActivityTest {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String eventId;

    @Before
    public void setUp() throws InterruptedException {
        eventId = "edit-test-" + UUID.randomUUID();

        Event event = new Event(
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

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events")
                .document(eventId)
                .set(event)
                .addOnCompleteListener(task -> latch.countDown());
        assertTrue("Timed out creating test event", latch.await(5, TimeUnit.SECONDS));
    }

    @After
    public void tearDown() throws InterruptedException {
        if (eventId == null) {
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events")
                .document(eventId)
                .delete()
                .addOnCompleteListener(task -> latch.countDown());
        latch.await(5, TimeUnit.SECONDS);
    }

    @Test
    public void launchWithoutEventId_finishesActivity() {
        ActivityScenario<OrganizerEditEventActivity> scenario =
                ActivityScenario.launch(OrganizerEditEventActivity.class);

        AtomicBoolean destroyed = new AtomicBoolean(false);
        scenario.onActivity(activity -> destroyed.set(activity.isFinishing() || activity.isDestroyed()));

        assertTrue(destroyed.get());
        scenario.close();
    }

    @Test
    public void updateWithSingleValidField_updatesOnlyThatField() throws InterruptedException {
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
    public void invalidRegistrationPeriod_blocksUpdate() throws InterruptedException {
        Intent intent = new Intent(
                ApplicationProvider.getApplicationContext(),
                OrganizerEditEventActivity.class
        );
        intent.putExtra("eventId", eventId);

        ActivityScenario<OrganizerEditEventActivity> scenario = ActivityScenario.launch(intent);

        onView(withId(R.id.et_registration_period))
                .perform(replaceText("04-01-2026"), closeSoftKeyboard());
        onView(withId(R.id.btn_update_event)).perform(click());

        onView(withId(R.id.et_registration_period)).check(matches(isDisplayed()));

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

        assertTrue("Timed out reading event after invalid update", latch.await(5, TimeUnit.SECONDS));
        DocumentSnapshot snapshot = snapshotRef.get();
        assertTrue(snapshot != null && snapshot.exists());
        assertFalse("04-01-2026".equals(snapshot.getString("registrationPeriod")));

        scenario.close();
    }

    private void waitForFirestoreWrite() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
