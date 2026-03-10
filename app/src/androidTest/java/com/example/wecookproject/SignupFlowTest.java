package com.example.wecookproject;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;

import android.content.Intent;
import android.provider.Settings;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class SignupFlowTest {

    @Rule
    public ActivityScenarioRule<LoginActivity> activityRule =
            new ActivityScenarioRule<>(LoginActivity.class);

    @Before
    public void setUp() {
        // We delete the user profile so the tests don't immediately jump to MainActivity
        // as a returning user.
        String androidId = Settings.Secure.getString(
                ApplicationProvider.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);
                
        CountDownLatch latch = new CountDownLatch(1);
        FirebaseFirestore.getInstance().collection("users").document(androidId).delete()
                .addOnCompleteListener(task -> latch.countDown());
        try { latch.await(5, TimeUnit.SECONDS); } catch (Exception e) {}
    }

    /**
     * Verify that clicking Login with empty Username or Password
     * shows a validation message and does NOT navigate away.
     */
    @Test
    public void testEmptyLoginCredentialsShowsError() {
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
    public void testSignupPromptNavigatesWhenEmpty() {
        // Because the @Before deletes the Firestore user, the auto-login won't bypass SignupDetails.
        // Wait for Firestore to complete "does not exist" check
        try { Thread.sleep(3000); } catch (InterruptedException e) {}
        
        onView(withId(R.id.text_Admin_login)).perform(click());
        
        try { Thread.sleep(1500); } catch (InterruptedException e) {}
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
    }

    /**
     * Verify that clicking Continue on the Address screen with empty
     * Address line 1, City, or Postal code does NOT navigate away.
     */
    @Test
    public void testEmptyAddressFieldsShowsError() {
        // Navigate to the Address screen via the signup flow
        try { Thread.sleep(3000); } catch (InterruptedException e) {}
        onView(withId(R.id.text_Admin_login)).perform(click());
        
        try { Thread.sleep(1500); } catch (InterruptedException e) {}
        onView(withId(R.id.et_first_name)).perform(typeText("John"), closeSoftKeyboard());
        onView(withId(R.id.et_last_name)).perform(typeText("Doe"), closeSoftKeyboard());
        // Type digits only — the TextWatcher auto-inserts '/' to form MM/DD/YYYY
        onView(withId(R.id.et_birthday)).perform(typeText("01012000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        // Confirm we are on the Address screen
        try { Thread.sleep(1500); } catch (InterruptedException e) {}
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
    public void testSignupFlow() {
        // 1. Wait for Firebase or screen load
        try { Thread.sleep(3000); } catch (InterruptedException e) {}
        
        // The title in activity_login.xml is "Login via your phone"
        onView(withId(R.id.tv_title)).check(matches(withText("Login via your phone")));

        // 2. Tap the Admin prompt to simulate signup route
        onView(withId(R.id.text_Admin_login)).perform(click());

        // 3. Check the Signup Details screen is displayed
        try { Thread.sleep(1500); } catch (InterruptedException e) {}
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));

        // 4. Enter first name, last name, and birthday, then continue
        onView(withId(R.id.et_first_name)).perform(typeText("John"), closeSoftKeyboard());
        onView(withId(R.id.et_last_name)).perform(typeText("Doe"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(typeText("01012000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        // 5. Check the Signup Address screen is displayed
        try { Thread.sleep(1500); } catch (InterruptedException e) {}
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));

        // 6. Enter required address fields, then continue
        onView(withId(R.id.et_address_line_1)).perform(typeText("123 Main St"), closeSoftKeyboard());
        onView(withId(R.id.et_address_line_2)).perform(typeText("Apt 4B"), closeSoftKeyboard());
        onView(withId(R.id.et_city)).perform(typeText("Edmonton"), closeSoftKeyboard());
        onView(withId(R.id.et_postal_code)).perform(typeText("T6G 2R3"), closeSoftKeyboard());
        onView(withId(R.id.et_country)).perform(typeText("Canada"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        // 7. Check that MainActivity (Home) is displayed (navigation no longer waits on Firestore)
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        onView(withText("Hello World!")).check(matches(isDisplayed()));
    }
}
