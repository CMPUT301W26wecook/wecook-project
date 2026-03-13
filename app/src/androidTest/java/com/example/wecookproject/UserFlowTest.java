package com.example.wecookproject;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;

import android.content.Intent;
import android.provider.Settings;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.example.wecookproject.model.Event;
import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@LargeTest
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
    public void test7_ProfileEditModeToggle() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.et_first_name)).check(matches(not(isEnabled())));
        onView(withId(R.id.btn_update)).perform(click());
        onView(withId(R.id.et_first_name)).check(matches(isEnabled()));
        onView(withId(R.id.btn_update)).check(matches(withText("Save Changes")));
    }

    @Test
    public void test8_ProfileUpdateSuccess() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.btn_update)).perform(click());
        onView(withId(R.id.et_last_name)).perform(clearText(), typeText("NewName"));
        onView(withId(R.id.btn_update)).perform(click());
        safeSleep(1000);
        onView(withId(R.id.et_last_name)).check(matches(withText("NewName")));
    }

    @Test
    public void test9_ProfileNotificationToggle() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.iv_notifications)).perform(click());
        onView(withId(R.id.iv_notifications)).check(matches(isDisplayed()));
    }

    @Test
    public void test10_ProfileAutoLoginToggle() {
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
    public void test11_ProfileDeleteAccountDialogShow() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.btn_delete)).perform(click());
        onView(withText("Delete Account")).check(matches(isDisplayed()));
        onView(withText("Cancel")).perform(click());
    }

    @Test
    public void test12_ProfileDeleteAccount_Cancel() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.btn_delete)).perform(click());
        onView(withText("Cancel")).perform(click());
        onView(withId(R.id.btn_delete)).check(matches(isDisplayed()));
    }

    @Test
    public void test13_ProfileNavigationToEvents() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.nav_events)).perform(click());
        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));
    }


    @Test
    public void test19_BottomNav_ScanToast() {
        prepareTestUser();
        ActivityScenario.launch(UserEventActivity.class);
        onView(withId(R.id.nav_scan)).perform(click());

        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));
    }



    private void prepareTestUser() {
        CountDownLatch latch = new CountDownLatch(1);
        Map<String, Object> user = new HashMap<>();
        user.put("firstName", "Integration");
        user.put("lastName", "Tester");
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
        eventData.put("maxWaitlist", (long) max);
        eventData.put("waitlistEntrantIds", entrants);
        eventData.put("currentWaitlistCount", (long) entrants.size());

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events").document(testEventId).set(eventData).addOnCompleteListener(t -> latch.countDown());
        try { latch.await(3, TimeUnit.SECONDS); } catch (Exception ignored) {}
    }

    private void safeSleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}