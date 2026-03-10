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

@RunWith(AndroidJUnit4.class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SignupFlowTest {

    private ActivityScenario<LoginActivity> activityScenario;

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
     * Verify that clicking Login with empty Username or Password
     * shows a validation message and does NOT navigate away.
     */
    @Test
    public void test1_EmptyLoginCredentialsShowsError() {
        // Confirm we are on the Login screen
        // onView(withId(R.id.tv_title)).check(matches(withText("Login via your phone")));

        // Attempt to login without entering any credentials
        // onView(withId(R.id.btn_organizer_login)).perform(click());

        // Note: With Device ID login, there are no credentials.
        // It automatically routes to SignupDetailsActivity or MainActivity.
        // This test is kept empty/modified as the old credential logic is removed.
    }

    /**
     * Verify that clicking the Sign Up prompt navigates to Details
     * even if login fields are empty.
     */
    @Test
    public void test2_SignupPromptNavigatesWhenEmpty() {
        // Because the @Before deletes the Firestore user, the auto-login won't bypass SignupDetails.
        // Wait for Firestore to complete "does not exist" check
        
        onView(withId(R.id.text_Admin_login)).perform(click());
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
    }

    /**
     * Verify that clicking Continue on the Address screen with empty
     * Address line 1, City, or Postal code does NOT navigate away.
     */
    @Test
    public void test3_EmptyAddressFieldsShowsError() {
        // Navigate to the Address screen via the signup flow
        onView(withId(R.id.text_Admin_login)).perform(click());
        safeSleep(1500);
        onView(withId(R.id.et_first_name)).perform(typeText("John"), closeSoftKeyboard());
        onView(withId(R.id.et_last_name)).perform(typeText("Doe"), closeSoftKeyboard());
        // Type digits only — the TextWatcher auto-inserts '/' to form MM/DD/YYYY
        onView(withId(R.id.et_birthday)).perform(typeText("01012000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        // Confirm we are on the Address screen
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));

        // Attempt to continue without filling any address fields
        onView(withId(R.id.btn_continue)).perform(click());

        // Should still be on the Address screen (navigation was blocked)
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));
    }

    /**
     * Full happy-path signup flow: fill all required fields and navigate
     * from Login → Details → Address → MainActivity.
     */
    @Test
    public void test4_SignupFlow() {
        // 1. Wait for Firebase or screen load
        
        // The title in activity_login.xml is "Login via your phone"
        onView(withId(R.id.tv_title)).check(matches(withText("Login via your phone")));

        // 2. Tap the Admin prompt to simulate signup route
        onView(withId(R.id.text_Admin_login)).perform(click());

        // 3. Check the Signup Details screen is displayed
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));

        // 4. Enter first name, last name, and birthday, then continue
        onView(withId(R.id.et_first_name)).perform(typeText("John"), closeSoftKeyboard());
        onView(withId(R.id.et_last_name)).perform(typeText("Doe"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(typeText("01012000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        // 5. Check the Signup Address screen is displayed
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));

        // 6. Enter required address fields, then continue
        onView(withId(R.id.et_address_line_1)).perform(typeText("123 Main St"), closeSoftKeyboard());
        onView(withId(R.id.et_address_line_2)).perform(typeText("Apt 4B"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(typeText("Edmonton"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(typeText("T6G 2R3"), closeSoftKeyboard());
        onView(withId(R.id.et_country)).perform(typeText("Canada"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        // 7. Check that MainActivity (Home) is displayed (navigation no longer waits on Firestore)
        safeSleep(2000);
        onView(withText("Hello World!")).check(matches(isDisplayed()));
    }

    /**
     * Signup flow with: first name, birthday, address line 1, postal code, city
     */
    @Test
    public void test5_SignupFlowPartialFields1() {
        onView(withId(R.id.tv_title)).check(matches(withText("Login via your phone")));
        onView(withId(R.id.text_Admin_login)).perform(click());
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));

        onView(withId(R.id.et_first_name)).perform(typeText("John"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(typeText("01012000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));

        onView(withId(R.id.et_address_line_1)).perform(typeText("123 Main St"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(typeText("T6G 2R3"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(typeText("Edmonton"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        safeSleep(2000);
        onView(withText("Hello World!")).check(matches(isDisplayed()));
    }

    /**
     * Signup flow with: first name, birthday, address line 1, address line 2, postal code, city
     */
    @Test
    public void test6_SignupFlowPartialFields2() {
        onView(withId(R.id.tv_title)).check(matches(withText("Login via your phone")));
        onView(withId(R.id.text_Admin_login)).perform(click());
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));

        onView(withId(R.id.et_first_name)).perform(typeText("Jane"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(typeText("02022000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));

        onView(withId(R.id.et_address_line_1)).perform(typeText("456 Elm St"), closeSoftKeyboard());
        onView(withId(R.id.et_address_line_2)).perform(typeText("Suite 100"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(typeText("T2P 1J9"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(typeText("Calgary"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        safeSleep(2000);
        onView(withText("Hello World!")).check(matches(isDisplayed()));
    }

    /**
     * Signup flow with: first name, birthday, address line 1, postal code, city, country
     */
    @Test
    public void test7_SignupFlowPartialFields3() {
        onView(withId(R.id.tv_title)).check(matches(withText("Login via your phone")));
        onView(withId(R.id.text_Admin_login)).perform(click());
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));

        onView(withId(R.id.et_first_name)).perform(typeText("Alice"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(typeText("03032000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));

        onView(withId(R.id.et_address_line_1)).perform(typeText("789 Maple St"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(typeText("V6B 1A1"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(typeText("Vancouver"), closeSoftKeyboard());
        onView(withId(R.id.et_country)).perform(typeText("Canada"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        safeSleep(2000);
        onView(withText("Hello World!")).check(matches(isDisplayed()));
    }

    /**
     * Signup flow with: first name, birthday, address line 1, address line 2, postal code, city, country
     */
    @Test
    public void test8_SignupFlowPartialFields4() {
        onView(withId(R.id.tv_title)).check(matches(withText("Login via your phone")));
        onView(withId(R.id.text_Admin_login)).perform(click());
        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));

        onView(withId(R.id.et_first_name)).perform(typeText("Bob"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(typeText("04042000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        safeSleep(1500);
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));

        onView(withId(R.id.et_address_line_1)).perform(typeText("101 Oak St"), closeSoftKeyboard());
        onView(withId(R.id.et_address_line_2)).perform(typeText("Apt 2"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(typeText("M5V 2H1"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(typeText("Toronto"), closeSoftKeyboard());
        onView(withId(R.id.et_country)).perform(typeText("Canada"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        safeSleep(2000);
        onView(withText("Hello World!")).check(matches(isDisplayed()));
    }

    // Helper to sleep without cluttering tests with try/catch boilerplate
    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
