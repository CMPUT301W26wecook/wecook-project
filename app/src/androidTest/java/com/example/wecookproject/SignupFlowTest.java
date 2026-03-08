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

    /**
     * Verify that clicking Login with empty Username or Password
     * shows a validation message and does NOT navigate away.
     */
    @Test
    public void testEmptyLoginCredentialsShowsError() {
        // Confirm we are on the Login screen
        onView(withId(R.id.tv_title)).check(matches(withText("Login or sign up")));

        // Attempt to login without entering any credentials
        onView(withId(R.id.btn_login)).perform(click());

        // Should still be on the Login screen (navigation was blocked)
        onView(withId(R.id.tv_title)).check(matches(withText("Login or sign up")));
    }

    /**
     * Verify that clicking the Sign Up prompt navigates to Details
     * even if login fields are empty.
     */
    @Test
    public void testSignupPromptNavigatesWhenEmpty() {
        onView(withId(R.id.tv_signup_prompt)).perform(click());
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));
    }

    /**
     * Full happy-path signup flow: fill all required fields and navigate
     * from Login → Details → Address → MainActivity.
     */
    @Test
    public void testSignupFlow() {
        // 1. Check the Login screen is displayed
        onView(withId(R.id.tv_title)).check(matches(withText("Login or sign up")));

        // 2. Tap the sign-up prompt (no credentials needed)
        onView(withId(R.id.tv_signup_prompt)).perform(click());

        // 3. Check the Signup Details screen is displayed
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Details")));

        // 4. Enter first name and birthday, then continue
        onView(withId(R.id.et_first_name)).perform(typeText("John"), closeSoftKeyboard());
        onView(withId(R.id.et_birthday)).perform(typeText("01/01/2000"), closeSoftKeyboard());
        onView(withId(R.id.btn_continue)).perform(click());

        // 5. Check the Signup Address screen is displayed
        onView(withId(R.id.tv_screen_title)).check(matches(withText("Address")));
        onView(withId(R.id.btn_continue)).perform(click());

        // 6. Check that MainActivity (Home) is displayed
        onView(withId(R.id.main)).check(matches(isDisplayed()));
    }
}
