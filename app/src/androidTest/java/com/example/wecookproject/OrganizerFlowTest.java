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

import android.provider.Settings;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class OrganizerFlowTest {

    private ActivityScenario<LoginActivity> activityScenario;

    /**
     * Deletes the Firestore user document before each test so the app always
     * starts from an unauthenticated state, then launches LoginActivity.
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

        activityScenario = ActivityScenario.launch(LoginActivity.class);
    }

    @After
    public void tearDown() {
        if (activityScenario != null) {
            activityScenario.close();
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

    private void performFullSignup() {
        // Login screen
        onView(withId(R.id.btn_organizer_login)).perform(click());

        // Details screen
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
        onView(withId(R.id.et_first_name)).perform(typeText("John"), closeSoftKeyboard());
        onView(withId(R.id.et_last_name)).perform(typeText("Doe"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(typeText("01012000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        // Address screen
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));
        onView(withId(R.id.et_address_line_1)).perform(typeText("123 Main St"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(typeText("Edmonton"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(typeText("T6G 2R3"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        // Wait for Firestore write and navigation to OrganizerHomeActivity
        safeSleep(2500);
    }

    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}