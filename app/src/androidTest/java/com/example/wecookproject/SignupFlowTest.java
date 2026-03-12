package com.example.wecookproject;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
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

/**
 * Instrumented tests for the full signup flow:
 *   LoginActivity → SignupDetailsActivity → SignupAddressActivity → destination
 *
 * Entrant/Admin → UserEventActivity  (shows "Events" title via tv_events_title)
 * Organizer     → OrganizerHomeActivity (shows "Create Events" bottom-nav item)
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SignupFlowTest {

    private ActivityScenario<LoginActivity> activityScenario;

    @Before
    public void setUp() {
        deleteUserDocument();
        activityScenario = ActivityScenario.launch(LoginActivity.class);
    }

    @After
    public void tearDown() {
        if (activityScenario != null) {
            activityScenario.close();
        }
        deleteUserDocument();
    }

    /** Removes the current device's user document so the app routes to signup. */
    private void deleteUserDocument() {
        String androidId = Settings.Secure.getString(
                ApplicationProvider.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);
        CountDownLatch latch = new CountDownLatch(1);
        FirebaseFirestore.getInstance().collection("users").document(androidId).delete()
                .addOnCompleteListener(task -> latch.countDown());
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ──────────────────────────────────────────────
    //  Login screen
    // ──────────────────────────────────────────────

    /** Verify login screen shows all expected elements. */
    @Test
    public void test1_LoginScreenDisplayed() {
        onView(withId(R.id.tv_title)).check(matches(withText("Login via your phone")));
        onView(withId(R.id.btn_entrant_login)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_organizer_login)).check(matches(isDisplayed()));
        onView(withId(R.id.text_Admin_login)).check(matches(isDisplayed()));
    }

    /** Entrant button routes to signup details when user does not exist. */
    @Test
    public void test2_EntrantLoginNavigatesToSignup() {
        onView(withId(R.id.btn_entrant_login)).perform(click());
        safeSleep(2000);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
    }

    /** Organizer button routes to signup details when user does not exist. */
    @Test
    public void test3_OrganizerLoginNavigatesToSignup() {
        onView(withId(R.id.btn_organizer_login)).perform(click());
        safeSleep(2000);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
    }

    // ──────────────────────────────────────────────
    //  Details screen – validation
    // ──────────────────────────────────────────────

    /** Submitting details with all fields empty stays on the Details screen. */
    @Test
    public void test4_DetailsEmptyFieldsBlocksNavigation() {
        onView(withId(R.id.btn_entrant_login)).perform(click());
        safeSleep(2000);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));

        onView(withId(R.id.btn_continue)).perform(click());

        // Navigation blocked — still on Details
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
    }

    /** First name alone (no birthday) blocks navigation. */
    @Test
    public void test5_DetailsMissingBirthdayBlocksNavigation() {
        onView(withId(R.id.btn_entrant_login)).perform(click());
        safeSleep(2000);

        onView(withId(R.id.et_first_name)).perform(typeText("John"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
    }

    /** Birthday alone (no first name) blocks navigation. */
    @Test
    public void test6_DetailsMissingFirstNameBlocksNavigation() {
        onView(withId(R.id.btn_entrant_login)).perform(click());
        safeSleep(2000);

        onView(withId(R.id.et_birthday)).perform(typeText("01012000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
    }

    /** Organizer path requires last name; omitting it blocks navigation. */
    @Test
    public void test7_OrganizerMissingLastNameBlocksNavigation() {
        onView(withId(R.id.btn_organizer_login)).perform(click());
        safeSleep(2000);

        onView(withId(R.id.et_first_name)).perform(typeText("John"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(typeText("01012000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        // Organizer requires last name — still on Details
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
    }

    /** Birthday auto-formats typed digits into MM/DD/YYYY. */
    @Test
    public void test8_BirthdayAutoFormatting() {
        onView(withId(R.id.btn_entrant_login)).perform(click());
        safeSleep(2000);

        onView(withId(R.id.et_birthday)).perform(typeText("12251999"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).check(matches(withText("12/25/1999")));
    }

    // ──────────────────────────────────────────────
    //  Address screen – validation
    // ──────────────────────────────────────────────

    /** Submitting address with all fields empty stays on Address. */
    @Test
    public void test9_AddressAllEmptyBlocksNavigation() {
        navigateToAddressScreen();

        onView(withId(R.id.btn_continue)).perform(click());

        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));
    }

    /** Address with only address line 1 (no city, no postal code) blocks. */
    @Test
    public void testA_AddressMissingCityAndPostalBlocksNavigation() {
        navigateToAddressScreen();

        onView(withId(R.id.et_address_line_1)).perform(typeText("123 Main St"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));
    }

    /** Address with missing postal code blocks. */
    @Test
    public void testB_AddressMissingPostalCodeBlocksNavigation() {
        navigateToAddressScreen();

        onView(withId(R.id.et_address_line_1)).perform(typeText("123 Main St"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(typeText("Edmonton"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));
    }

    /** Address with missing city blocks. */
    @Test
    public void testC_AddressMissingCityBlocksNavigation() {
        navigateToAddressScreen();

        onView(withId(R.id.et_address_line_1)).perform(typeText("123 Main St"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(typeText("T6G 2R3"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));
    }

    /** Address with missing address line 1 blocks. */
    @Test
    public void testD_AddressMissingLine1BlocksNavigation() {
        navigateToAddressScreen();

        onView(withId(R.id.et_city)).perform(typeText("Edmonton"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(typeText("T6G 2R3"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));
    }

    // ──────────────────────────────────────────────
    //  Happy-path flows
    // ──────────────────────────────────────────────

    /**
     * Entrant signup – all fields filled.
     * Login → Details → Address → UserEventActivity ("Events" title).
     */
    @Test
    public void testE_EntrantSignupAllFields() {
        onView(withId(R.id.tv_title)).check(matches(withText("Login via your phone")));
        onView(withId(R.id.btn_entrant_login)).perform(click());
        safeSleep(2000);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));

        onView(withId(R.id.et_first_name)).perform(typeText("John"), closeSoftKeyboard());
        onView(withId(R.id.et_last_name)).perform(typeText("Doe"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(typeText("01012000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));

        onView(withId(R.id.et_address_line_1)).perform(typeText("123 Main St"), closeSoftKeyboard());
        onView(withId(R.id.et_address_line_2)).perform(typeText("Apt 4B"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(typeText("Edmonton"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(typeText("T6G 2R3"), closeSoftKeyboard());
        onView(withId(R.id.et_country)).perform(typeText("Canada"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        safeSleep(3000);
        onView(withId(R.id.tv_events_title)).check(matches(withText("Events")));
    }

    /**
     * Entrant signup – only required fields (no last name, address line 2, country).
     */
    @Test
    public void testF_EntrantSignupMinimalFields() {
        onView(withId(R.id.btn_entrant_login)).perform(click());
        safeSleep(2000);

        onView(withId(R.id.et_first_name)).perform(typeText("Jane"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(typeText("05051995"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());
        safeSleep(1500);

        onView(withId(R.id.et_address_line_1)).perform(typeText("456 Elm Ave"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(typeText("Calgary"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(typeText("T2P 1J9"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        safeSleep(3000);
        onView(withId(R.id.tv_events_title)).check(matches(withText("Events")));
    }

    /**
     * Organizer signup – happy path.
     * Includes last name (required for organizer).
     * Login → Details → Address → OrganizerHomeActivity ("Create Events" bottom-nav item).
     */
    @Test
    public void testG_OrganizerSignupHappyPath() {
        onView(withId(R.id.btn_organizer_login)).perform(click());
        safeSleep(2000);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));

        onView(withId(R.id.et_first_name)).perform(typeText("Alice"), closeSoftKeyboard());
        onView(withId(R.id.et_last_name)).perform(typeText("Smith"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(typeText("03031990"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));

        onView(withId(R.id.et_address_line_1)).perform(typeText("789 Maple Dr"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(typeText("Vancouver"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(typeText("V6B 1A1"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        safeSleep(3000);
        // OrganizerHomeActivity has a bottom nav with "Create Events" item
        onView(withText("Create Events")).check(matches(isDisplayed()));
    }

    /**
     * Organizer signup – all optional fields included.
     */
    @Test
    public void testH_OrganizerSignupAllFields() {
        onView(withId(R.id.btn_organizer_login)).perform(click());
        safeSleep(2000);

        onView(withId(R.id.et_first_name)).perform(typeText("Bob"), closeSoftKeyboard());
        onView(withId(R.id.et_last_name)).perform(typeText("Jones"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(typeText("07071985"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());
        safeSleep(1500);

        onView(withId(R.id.et_address_line_1)).perform(typeText("101 Oak St"), closeSoftKeyboard());
        onView(withId(R.id.et_address_line_2)).perform(typeText("Suite 200"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(typeText("Toronto"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(typeText("M5V 2H1"), closeSoftKeyboard());
        onView(withId(R.id.et_country)).perform(typeText("Canada"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        safeSleep(3000);
        onView(withText("Create Events")).check(matches(isDisplayed()));
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    /**
     * Navigates from Login → Details (entrant) → Address screen.
     * Used by address-validation tests to avoid repeating boilerplate.
     */
    private void navigateToAddressScreen() {
        onView(withId(R.id.btn_entrant_login)).perform(click());
        safeSleep(2000);

        onView(withId(R.id.et_first_name)).perform(typeText("Test"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(typeText("06061990"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());
        safeSleep(1500);

        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));
    }

    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
