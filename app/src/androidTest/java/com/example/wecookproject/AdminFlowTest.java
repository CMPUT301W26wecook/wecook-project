package com.example.wecookproject;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class AdminFlowTest {
    @Rule
    public ActivityScenarioRule<LoginActivity> activityRule = new ActivityScenarioRule<>(LoginActivity.class);

    @Before
    public void setUp() {
        //setupTestData();
    }

    @After
    public void tearDown() {
        //cleanupTestData();
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
        onView(withId(R.id.tv_address2)).check(matches(isDisplayed())); // Check visibility if it might be empty
        onView(withId(R.id.tv_city)).check(matches(withText(containsString("Calgary"))));
        onView(withId(R.id.tv_postal_code)).check(matches(withText(containsString("T2P 2M5"))));
        onView(withId(R.id.tv_country)).check(matches(withText(containsString("Canada"))));
    }

    private void cleanupTestData() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Delete Test Organizers
        for (int i = 1; i <= 3; i++) {
            db.collection("users").document("test_org_" + i).delete();
        }

        // Delete Test Entrants
        for (int i = 1; i <= 5; i++) {
            db.collection("users").document("test_user_" + i).delete();
        }

        // Delete Test Events
        for (int i = 1; i <= 3; i++) {
            db.collection("events").document("test_event_" + i).delete();
        }
    }

    private void setupTestData() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        for (int i = 1; i <= 3; i++) {
            String id = "test_org_" + i;
            User org = new User(
                    "Organizer" + i,
                    "Test",
                    "1980-01-01",
                    "Org St " + i,
                    "",
                    "Edmonton",
                    "T6G 2R3",
                    "Canada",
                    id,
                    "organizer"
            );
            db.collection("users").document(id).set(org.toFirestoreMap());
        }

        for (int i = 1; i <= 5; i++) {
            String id = "test_user_" + i;
            User user = new User(
                    "Entrant" + i,
                    "Test",
                    "1995-05-05",
                    "User Ave " + i,
                    "",
                    "Calgary",
                    "T2P 2M5",
                    "Canada",
                    id,
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
                    "Open to all",
                    50,
                    10,
                    "System generates",
                    false,
                    "Sample Location",
                    "This is a sample description for test event " + i
            );
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
