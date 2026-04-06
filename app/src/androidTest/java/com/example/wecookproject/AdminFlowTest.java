package com.example.wecookproject;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.fail;

import android.view.View;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.example.wecookproject.model.Event;
import com.example.wecookproject.model.User;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class AdminFlowTest {

    private static final long FIRESTORE_TIMEOUT_SECONDS = 30;
    private static final int WAIT_RETRY_COUNT = 30;
    private static final long WAIT_INTERVAL_MS = 600;
    private static final long UI_SETTLE_MS = 350;
    private static final String RUN_ID = String.valueOf(System.currentTimeMillis());

    private static final String USER_1_ID = "adminflow_user_1_" + RUN_ID;
    private static final String USER_2_ID = "adminflow_user_2_" + RUN_ID;
    private static final String USER_3_ID = "adminflow_user_3_" + RUN_ID;
    private static final String USER_4_ID = "adminflow_user_4_" + RUN_ID;

    private static final String ORG_1_ID = "adminflow_org_1_" + RUN_ID;
    private static final String ORG_2_ID = "adminflow_org_2_" + RUN_ID;
    private static final String ORG_3_ID = "adminflow_org_3_" + RUN_ID;

    private static final String EVENT_1_ID = "adminflow_event_1_" + RUN_ID;
    private static final String EVENT_2_ID = "adminflow_event_2_" + RUN_ID;
    private static final String EVENT_3_ID = "adminflow_event_3_" + RUN_ID;

    private static final String USER_1_NAME = "AFEntrant1 " + RUN_ID;
    private static final String USER_2_NAME = "AFEntrant2 " + RUN_ID;

    private static final String ORG_1_NAME = "AFOrganizer1 " + RUN_ID;
    private static final String ORG_2_NAME = "AFOrganizer2 " + RUN_ID;
    private static final String ORG_3_NAME = "AFOrganizer3 " + RUN_ID;

    private static final String EVENT_1_NAME = "AF Event 1 " + RUN_ID;
    private static final String EVENT_2_NAME = "AF Event 2 " + RUN_ID;
    private static final String EVENT_3_NAME = "AF Event 3 " + RUN_ID;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final List<String> seededUserIds = Arrays.asList(
            USER_1_ID, USER_2_ID, USER_3_ID, USER_4_ID,
            ORG_1_ID, ORG_2_ID, ORG_3_ID
    );
    private final List<String> seededEventIds = Arrays.asList(EVENT_1_ID, EVENT_2_ID, EVENT_3_ID);

    @Rule
    public ActivityScenarioRule<LoginActivity> activityRule = new ActivityScenarioRule<>(LoginActivity.class);

    @Before
    public void setUp() {
        cleanupTestData();
        setupTestData();
    }

    @After
    public void tearDown() {
        cleanupTestData();
    }

    @Test
    public void adminStoryLoginAndNavigateTabs() {
        navigateToAdminMainMenu();

        waitUntilVisible(withId(R.id.rv_user_list));
        waitUntilVisible(withText(USER_1_NAME));

        onView(withId(R.id.nav_organizers)).perform(click());
        safeSleep(UI_SETTLE_MS);
        waitUntilVisible(withId(R.id.rv_organizer_list));
        waitUntilVisible(withText(ORG_1_NAME));

        onView(withId(R.id.nav_events)).perform(click());
        safeSleep(UI_SETTLE_MS);
        waitUntilVisible(withId(R.id.rv_event_list));
        waitUntilVisible(withText(EVENT_1_NAME));

        onView(withId(R.id.nav_notifications)).perform(click());
        safeSleep(UI_SETTLE_MS);
        waitUntilVisible(withId(R.id.rv_notification_list));
    }

    @Test
    public void adminStoryViewEntrantProfileDetails() {
        navigateToAdminMainMenu();
        waitUntilVisible(withText(USER_1_NAME));

        onView(allOf(withId(R.id.btn_element_menu), hasSibling(withText(USER_1_NAME)))).perform(click());
        waitUntilVisible(withText("Show Detail"));
        onView(withText("Show Detail")).perform(click());
        safeSleep(UI_SETTLE_MS);

        waitUntilVisible(withId(R.id.tv_first_name));
        onView(withId(R.id.tv_first_name)).check(matches(withText(containsString("AFEntrant1"))));
        onView(withId(R.id.tv_last_name)).check(matches(withText(containsString(RUN_ID))));
        onView(withId(R.id.tv_dob)).check(matches(withText(containsString("1995-05-05"))));
        onView(withId(R.id.tv_address1)).check(matches(withText(containsString("User Ave 1"))));
        onView(withId(R.id.tv_city)).check(matches(withText(containsString("Calgary"))));
        onView(withId(R.id.tv_postal_code)).check(matches(withText(containsString("T2P 2M5"))));
        onView(withId(R.id.tv_country)).check(matches(withText(containsString("Canada"))));
    }

    @Test
    public void adminStoryDeleteEntrantFromListAndDetail() {
        navigateToAdminMainMenu();
        waitUntilVisible(withText(USER_1_NAME));
        waitUntilVisible(withText(USER_2_NAME));

        onView(allOf(withId(R.id.btn_element_menu), hasSibling(withText(USER_1_NAME)))).perform(click());
        onView(withText("Delete")).perform(click());
        waitUntilUserDeleted(USER_1_ID);
        waitUntilGone(withText(USER_1_NAME));

        onView(allOf(withId(R.id.btn_element_menu), hasSibling(withText(USER_2_NAME)))).perform(click());
        waitUntilVisible(withText("Show Detail"));
        onView(withText("Show Detail")).perform(click());
        safeSleep(UI_SETTLE_MS);
        waitUntilVisible(withId(R.id.btn_delete_account));
        onView(withId(R.id.btn_delete_account)).perform(click());
        safeSleep(UI_SETTLE_MS);

        waitUntilUserDeleted(USER_2_ID);

        safeSleep(UI_SETTLE_MS);
        waitUntilVisible(withId(R.id.rv_user_list));
        waitUntilGone(withText(USER_2_NAME));
    }

    @Test
    public void adminStoryDeleteOrganizersSingleAndBulk() {
        navigateToAdminMainMenu();

        onView(withId(R.id.nav_organizers)).perform(click());
        safeSleep(UI_SETTLE_MS);
        waitUntilVisible(withId(R.id.rv_organizer_list));
        waitUntilVisible(withText(ORG_1_NAME));
        waitUntilVisible(withText(ORG_2_NAME));
        waitUntilVisible(withText(ORG_3_NAME));

        onView(allOf(withId(R.id.btn_element_menu), hasSibling(withText(ORG_1_NAME)))).perform(click());
        onView(withText("Delete")).perform(click());
        safeSleep(UI_SETTLE_MS);
        waitUntilUserDeleted(ORG_1_ID);
        waitUntilGone(withText(ORG_1_NAME));

        onView(allOf(withId(R.id.cb_select_element), hasSibling(withText(ORG_2_NAME)))).perform(click());
        onView(allOf(withId(R.id.cb_select_element), hasSibling(withText(ORG_3_NAME)))).perform(click());
        onView(withId(R.id.btn_delete_selected)).perform(click());
        safeSleep(UI_SETTLE_MS);

        waitUntilUserDeleted(ORG_2_ID);
        waitUntilUserDeleted(ORG_3_ID);
        waitUntilGone(withText(ORG_2_NAME));
        waitUntilGone(withText(ORG_3_NAME));
    }

    @Test
    public void adminStoryViewEventDetailAndDeletePoster() {
        navigateToAdminMainMenu();

        onView(withId(R.id.nav_events)).perform(click());
        safeSleep(UI_SETTLE_MS);
        waitUntilVisible(withText(EVENT_1_NAME));

        onView(allOf(withId(R.id.btn_element_menu), hasSibling(withText(EVENT_1_NAME)))).perform(click());
        waitUntilVisible(withText("Show Detail"));
        onView(withText("Show Detail")).perform(click());
        safeSleep(UI_SETTLE_MS);

        waitUntilVisible(withId(R.id.tv_event_name_header));
        onView(withId(R.id.tv_event_name_header)).check(matches(withText(EVENT_1_NAME)));
        onView(withId(R.id.tv_event_location)).check(matches(withText("Sample Location")));

        onView(withId(R.id.btn_delete_poster)).perform(click());
        safeSleep(UI_SETTLE_MS);
        waitUntilPosterCleared(EVENT_1_ID);
    }

    @Test
    public void adminStoryDeleteEventsSingleAndBulk() {
        navigateToAdminMainMenu();

        onView(withId(R.id.nav_events)).perform(click());
        safeSleep(UI_SETTLE_MS);
        waitUntilVisible(withText(EVENT_1_NAME));
        waitUntilVisible(withText(EVENT_2_NAME));
        waitUntilVisible(withText(EVENT_3_NAME));

        onView(allOf(withId(R.id.btn_element_menu), hasSibling(withText(EVENT_1_NAME)))).perform(click());
        onView(withText("Delete")).perform(click());
        safeSleep(UI_SETTLE_MS);
        waitUntilEventDeleted(EVENT_1_ID);
        waitUntilGone(withText(EVENT_1_NAME));

        onView(allOf(withId(R.id.cb_select_element), hasSibling(withText(EVENT_2_NAME)))).perform(click());
        onView(allOf(withId(R.id.cb_select_element), hasSibling(withText(EVENT_3_NAME)))).perform(click());
        onView(withId(R.id.btn_delete_selected)).perform(click());
        safeSleep(UI_SETTLE_MS);

        waitUntilEventDeleted(EVENT_2_ID);
        waitUntilEventDeleted(EVENT_3_ID);
        waitUntilGone(withText(EVENT_2_NAME));
        waitUntilGone(withText(EVENT_3_NAME));
    }

    private void cleanupTestData() {
        List<Task<Void>> tasks = new ArrayList<>();

        for (String userId : seededUserIds) {
            tasks.add(db.collection("users").document(userId).delete());
        }

        for (String eventId : seededEventIds) {
            tasks.add(db.collection("events").document(eventId).delete());
        }

        awaitTaskList(tasks, "cleanup");
    }

    private void setupTestData() {
        List<Task<Void>> tasks = new ArrayList<>();

        Map<String, Boolean> organizerRole = new HashMap<>();
        organizerRole.put("organizer", true);

        Map<String, Boolean> entrantRole = new HashMap<>();
        entrantRole.put("entrant", true);

        User organizer1 = new User(
                "Org St 1",
                "",
                ORG_1_ID,
                "1980-01-01",
                "Edmonton",
                "Canada",
                "AFOrganizer1",
                RUN_ID,
                "T6G 2R3",
                true,
                organizerRole
        );
        User organizer2 = new User(
                "Org St 2",
                "",
                ORG_2_ID,
                "1980-01-01",
                "Edmonton",
                "Canada",
                "AFOrganizer2",
                RUN_ID,
                "T6G 2R3",
                true,
                organizerRole
        );
        User organizer3 = new User(
                "Org St 3",
                "",
                ORG_3_ID,
                "1980-01-01",
                "Edmonton",
                "Canada",
                "AFOrganizer3",
                RUN_ID,
                "T6G 2R3",
                true,
                organizerRole
        );

        tasks.add(db.collection("users").document(ORG_1_ID).set(organizer1.toFirestoreMap()));
        tasks.add(db.collection("users").document(ORG_2_ID).set(organizer2.toFirestoreMap()));
        tasks.add(db.collection("users").document(ORG_3_ID).set(organizer3.toFirestoreMap()));

        User user1 = new User(
                "User Ave 1",
                "",
                USER_1_ID,
                "1995-05-05",
                "Calgary",
                "Canada",
                "AFEntrant1",
                RUN_ID,
                "T2P 2M5",
                true,
                entrantRole
        );
        User user2 = new User(
                "User Ave 2",
                "",
                USER_2_ID,
                "1995-05-05",
                "Calgary",
                "Canada",
                "AFEntrant2",
                RUN_ID,
                "T2P 2M5",
                true,
                entrantRole
        );
        User user3 = new User(
                "User Ave 3",
                "",
                USER_3_ID,
                "1995-05-05",
                "Calgary",
                "Canada",
                "AFEntrant3",
                RUN_ID,
                "T2P 2M5",
                true,
                entrantRole
        );
        User user4 = new User(
                "User Ave 4",
                "",
                USER_4_ID,
                "1995-05-05",
                "Calgary",
                "Canada",
                "AFEntrant4",
                RUN_ID,
                "T2P 2M5",
                true,
                entrantRole
        );

        tasks.add(db.collection("users").document(USER_1_ID).set(user1.toFirestoreMap()));
        tasks.add(db.collection("users").document(USER_2_ID).set(user2.toFirestoreMap()));
        tasks.add(db.collection("users").document(USER_3_ID).set(user3.toFirestoreMap()));
        tasks.add(db.collection("users").document(USER_4_ID).set(user4.toFirestoreMap()));

        Date now = new Date();
        Date weekLater = new Date(System.currentTimeMillis() + 604800000L);

        Event event1 = new Event(
                EVENT_1_ID,
                ORG_1_ID,
                EVENT_1_NAME,
                now,
                weekLater,
                50,
                10,
                false,
                "Sample Location",
                "Sample description 1"
        );
        event1.setPosterPath("http://example.com/poster1.jpg");

        Event event2 = new Event(
                EVENT_2_ID,
                ORG_1_ID,
                EVENT_2_NAME,
                now,
                weekLater,
                50,
                11,
                false,
                "Sample Location",
                "Sample description 2"
        );
        event2.setPosterPath("http://example.com/poster2.jpg");

        Event event3 = new Event(
                EVENT_3_ID,
                ORG_1_ID,
                EVENT_3_NAME,
                now,
                weekLater,
                50,
                12,
                false,
                "Sample Location",
                "Sample description 3"
        );
        event3.setPosterPath("http://example.com/poster3.jpg");

        tasks.add(db.collection("events").document(EVENT_1_ID).set(event1));
        tasks.add(db.collection("events").document(EVENT_2_ID).set(event2));
        tasks.add(db.collection("events").document(EVENT_3_ID).set(event3));

        awaitTaskList(tasks, "seed");
    }

    private void awaitTaskList(List<? extends Task<?>> tasks, String operation) {
        try {
            Tasks.await(Tasks.whenAllComplete(tasks), FIRESTORE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail("Firestore " + operation + " failed: " + e.getMessage());
        }
    }

    private void waitUntilPosterCleared(String eventId) {
        for (int i = 0; i < WAIT_RETRY_COUNT; i++) {
            try {
                String posterPath = Tasks.await(
                        db.collection("events").document(eventId).get(),
                        FIRESTORE_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                ).getString("posterPath");

                if (posterPath == null || posterPath.isEmpty()) {
                    return;
                }
            } catch (Exception ignored) {
            }
            safeSleep(WAIT_INTERVAL_MS);
        }
        fail("Poster path was not cleared in time.");
    }

    private void waitUntilUserDeleted(String userId) {
        for (int i = 0; i < WAIT_RETRY_COUNT; i++) {
            try {
                boolean exists = Tasks.await(
                        db.collection("users").document(userId).get(),
                        FIRESTORE_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                ).exists();

                if (!exists) {
                    return;
                }
            } catch (Exception ignored) {
            }
            safeSleep(WAIT_INTERVAL_MS);
        }
        fail("User was not deleted in time for userId=" + userId);
    }

    private void waitUntilEventDeleted(String eventId) {
        for (int i = 0; i < WAIT_RETRY_COUNT; i++) {
            try {
                boolean exists = Tasks.await(
                        db.collection("events").document(eventId).get(),
                        FIRESTORE_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                ).exists();

                if (!exists) {
                    return;
                }
            } catch (Exception ignored) {
            }
            safeSleep(WAIT_INTERVAL_MS);
        }
        fail("Event was not deleted in time for eventId=" + eventId);
    }

    private void waitUntilVisible(Matcher<View> matcher) {
        for (int i = 0; i < WAIT_RETRY_COUNT; i++) {
            try {
                onView(matcher).check(matches(isDisplayed()));
                return;
            } catch (Throwable ignored) {
            }
            safeSleep(WAIT_INTERVAL_MS);
        }
        onView(matcher).check(matches(isDisplayed()));
    }

    private void waitUntilGone(Matcher<View> matcher) {
        for (int i = 0; i < WAIT_RETRY_COUNT; i++) {
            try {
                onView(matcher).check(doesNotExist());
                return;
            } catch (Throwable ignored) {
            }
            safeSleep(WAIT_INTERVAL_MS);
        }
        onView(matcher).check(doesNotExist());
    }

    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void navigateToAdminMainMenu() {
        onView(withId(R.id.text_Admin_login)).perform(click());
        safeSleep(UI_SETTLE_MS);
        waitUntilVisible(withId(R.id.et_password));
        onView(withId(R.id.et_password)).perform(replaceText("admin"), closeSoftKeyboard());
        onView(withId(R.id.btn_login)).perform(click());
        safeSleep(UI_SETTLE_MS);
        waitUntilVisible(withId(R.id.bottom_nav));
    }
}
