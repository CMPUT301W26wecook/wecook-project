package com.example.wecookproject;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.example.wecookproject.model.Event;
import com.example.wecookproject.model.User;
import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.Date;
import java.util.concurrent.CountDownLatch;

@RunWith(AndroidJUnit4.class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AdminFlowTest {
    @Rule
    public ActivityScenarioRule<LoginActivity> activityRule = new ActivityScenarioRule<>(LoginActivity.class);

    @Before
    public void setUp() {
        setupTestData();
    }

    @After
    public void tearDown() {
        cleanupTestData();
    }

    @Test
    public void testBrowseUserList() {
        navigateToAdminMainMenu();

        onView(withId(R.id.rv_user_list)).check(matches(isDisplayed()));

        onView(withText("Entrant1 Test")).check(matches(isDisplayed()));
        onView(withText("Entrant5 Test")).check(matches(isDisplayed()));
    }

    @Test
    public void testBrowseUserProfile() {
        navigateToAdminMainMenu();

        onView(allOf(withId(R.id.btn_element_menu), hasSibling(withText("Entrant1 Test")))).perform(click());

        onView(withText("Show Detail")).perform(click());

        onView(withId(R.id.tv_first_name)).check(matches(withText(containsString("Entrant1"))));
        onView(withId(R.id.tv_last_name)).check(matches(withText(containsString("Test"))));
        onView(withId(R.id.tv_dob)).check(matches(withText(containsString("1995-05-05"))));
        onView(withId(R.id.tv_address1)).check(matches(withText(containsString("User Ave 1"))));
        onView(withId(R.id.tv_city)).check(matches(withText(containsString("Calgary"))));
        onView(withId(R.id.tv_postal_code)).check(matches(withText(containsString("T2P 2M5"))));
        onView(withId(R.id.tv_country)).check(matches(withText(containsString("Canada"))));
    }

    @Test
    public void testDeleteUserProfile() {
        navigateToAdminMainMenu();

        onView(allOf(withId(R.id.btn_element_menu), hasSibling(withText("Entrant1 Test")))).perform(click());
        onView(withText("Delete")).perform(click());
        safeSleep(1000);
        onView(withText("Entrant1 Test")).check(doesNotExist());

        onView(allOf(withId(R.id.btn_element_menu), hasSibling(withText("Entrant2 Test")))).perform(click());
        onView(withText("Show Detail")).perform(click());
        onView(withId(R.id.btn_delete_account)).perform(click());
        pressBack();
        safeSleep(1000);
        onView(withText("Entrant2 Test")).check(doesNotExist());

        onView(allOf(withId(R.id.cb_select_element), hasSibling(withText("Entrant3 Test")))).perform(click());
        onView(allOf(withId(R.id.cb_select_element), hasSibling(withText("Entrant4 Test")))).perform(click());
        onView(withId(R.id.btn_delete_selected)).perform(click());
        safeSleep(1000);
        onView(withText("Entrant3 Test")).check(doesNotExist());
        onView(withText("Entrant4 Test")).check(doesNotExist());
    }
    
    @Test
    public void testBrowseOrganizerList() {
        navigateToAdminMainMenu();

        onView(withId(R.id.nav_organizers)).perform(click());
        safeSleep(1000);

        onView(withId(R.id.rv_organizer_list)).check(matches(isDisplayed()));

        onView(withText("Organizer1 Test")).check(matches(isDisplayed()));
        onView(withText("Organizer3 Test")).check(matches(isDisplayed()));
    }

    @Test
    public void testDeleteOrganizer() {
        navigateToAdminMainMenu();

        onView(withId(R.id.nav_organizers)).perform(click());
        safeSleep(1000);
        onView(allOf(withId(R.id.btn_element_menu), hasSibling(withText("Organizer1 Test")))).perform(click());
        onView(withText("Delete")).perform(click());
        safeSleep(1000);
        onView(withText("Organizer1 Test")).check(doesNotExist());

        onView(allOf(withId(R.id.cb_select_element), hasSibling(withText("Organizer2 Test")))).perform(click());
        onView(allOf(withId(R.id.cb_select_element), hasSibling(withText("Organizer3 Test")))).perform(click());
        onView(withId(R.id.btn_delete_selected)).perform(click());
        safeSleep(1000);
        onView(withText("Organizer2 Test")).check(doesNotExist());
        onView(withText("Organizer3 Test")).check(doesNotExist());
    }

    @Test
    public void testBrowseEventList() {
        navigateToAdminMainMenu();

        onView(withId(R.id.nav_events)).perform(click());
        safeSleep(1000);

        onView(withId(R.id.rv_event_list)).check(matches(isDisplayed()));

        onView(withText("Test Event 1")).check(matches(isDisplayed()));
        onView(withText("Test Event 3")).check(matches(isDisplayed()));
    }

    @Test
    public void testBrowseEventDetail() {
        navigateToAdminMainMenu();

        onView(withId(R.id.nav_events)).perform(click());
        safeSleep(1000);

        onView(allOf(withId(R.id.btn_element_menu), hasSibling(withText("Test Event 1")))).perform(click());
        onView(withText("Show Detail")).perform(click());
        safeSleep(1000);

        onView(withId(R.id.tv_event_name_header)).check(matches(withText("Test Event 1")));
        onView(withId(R.id.tv_event_location)).check(matches(withText("Sample Location")));
    }

    @Test
    public void testDeleteEvent() {
        navigateToAdminMainMenu();

        onView(withId(R.id.nav_events)).perform(click());
        safeSleep(1000);

        onView(allOf(withId(R.id.btn_element_menu), hasSibling(withText("Test Event 1")))).perform(click());
        onView(withText("Show Detail")).perform(click());
        safeSleep(1000);
        onView(withId(R.id.btn_delete_event)).perform(click());
        safeSleep(1000);
        onView(withText("Test Event 1")).check(doesNotExist());

        onView(allOf(withId(R.id.cb_select_element), hasSibling(withText("Test Event 2")))).perform(click());
        onView(allOf(withId(R.id.cb_select_element), hasSibling(withText("Test Event 3")))).perform(click());
        onView(withId(R.id.btn_delete_selected)).perform(click());
        safeSleep(1000);
        onView(withText("Test Event 2")).check(doesNotExist());
        onView(withText("Test Event 3")).check(doesNotExist());
    }

    @Test
    public void testDeletePosterImage() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        navigateToAdminMainMenu();

        onView(withId(R.id.nav_events)).perform(click());
        safeSleep(1000);

        onView(allOf(withId(R.id.btn_element_menu), hasSibling(withText("Test Event 1")))).perform(click());
        onView(withText("Show Detail")).perform(click());
        safeSleep(1000);
        onView(withId(R.id.btn_delete_poster)).perform(click());
        safeSleep(1000);

        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events").document("test_event_1").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                String path = task.getResult().getString("posterPath");
                Assert.assertTrue("Poster path should be null or empty", path == null || path.isEmpty());
            } else {
                Assert.fail("Firestore fetch failed");
            }
            latch.countDown();
        });
    }

    private void cleanupTestData() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        for (int i = 1; i <= 3; i++) {
            db.collection("users").document("test_org_" + i).delete();
        }

        for (int i = 1; i <= 5; i++) {
            db.collection("users").document("test_user_" + i).delete();
        }

        for (int i = 1; i <= 3; i++) {
            db.collection("events").document("test_event_" + i).delete();
        }
    }

    private void setupTestData() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        for (int i = 1; i <= 3; i++) {
            String id = "test_org_" + i;
            User org = new User(
                    "Org St " + i,
                    "",
                    id,
                    "1980-01-01",
                    "Edmonton",
                    "Canada",
                    "Organizer" + i,
                    "Test",
                    "T6G 2R3",
                    true,
                    "organizer"
            );
            db.collection("users").document(id).set(org.toFirestoreMap());
        }

        for (int i = 1; i <= 5; i++) {
            String id = "test_user_" + i;
            User user = new User(
                    "User Ave " + i,
                    "",
                    id,
                    "1995-05-05",
                    "Calgary",
                    "Canada",
                    "Entrant" + i,
                    "Test",
                    "T2P 2M5",
                    true,
                    "entrant"
            );
            db.collection("users").document(id).set(user.toFirestoreMap());
        }

        for (int i = 1; i <= 3; i++) {
            String eventId = "test_event_" + i;
            Event event = new Event(
                    eventId,
                    "test_org_1",
                    "Test Event " + i,
                    new Date(),
                    new Date(System.currentTimeMillis() + 604800000L),
                    50,
                    10,
                    false,
                    "Sample Location",
                    "This is a sample description for test event " + i
            );
            event.setPosterPath("http://example.com/poster" + i + ".jpg");
            db.collection("events").document(eventId).set(event);
        }
    }

    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void navigateToAdminMainMenu(){
        onView(withId(R.id.text_Admin_login)).perform(click());
        safeSleep(1000);
        onView(withId(R.id.et_username)).perform(replaceText("admin"));
        onView(withId(R.id.et_password)).perform(replaceText("admin"));
        onView(withId(R.id.btn_login)).perform(click());
        safeSleep(1000);
    }
}
