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
import static org.junit.Assert.assertTrue;

import android.content.Intent;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

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
    public void test8_ProfileNotificationToggle() {
        prepareTestUser();
        ActivityScenario.launch(UserProfileActivity.class);
        onView(withId(R.id.iv_notifications)).perform(click());
        onView(withId(R.id.iv_notifications)).check(matches(isDisplayed()));
    }

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
        eventData.put("description", "Test event description");
        eventData.put("maxWaitlist", (long) max);
        eventData.put("waitlistEntrantIds", entrants);
        eventData.put("currentWaitlistCount", (long) entrants.size());

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events").document(testEventId).set(eventData).addOnCompleteListener(t -> latch.countDown());
        try { latch.await(3, TimeUnit.SECONDS); } catch (Exception ignored) {}
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
}
