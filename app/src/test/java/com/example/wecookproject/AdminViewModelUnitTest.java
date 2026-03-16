package com.example.wecookproject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.wecookproject.model.Event;
import com.example.wecookproject.model.User;

import org.junit.Rule;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class AdminViewModelUnitTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Test
    public void selectUserShouldUpdateUserLiveDataAndClearEventLiveData() throws InterruptedException {
        AdminViewModel viewModel = new AdminViewModel();
        User user = new User("Addr1", "", "id1", "1990-01-01", "Edmonton", "Canada", "Alice", "Admin", "T6G", true, "entrant");

        viewModel.selectUser(user);

        User selectedUser = getOrAwaitValue(viewModel.getSelectedUser());
        Event selectedEvent = getOrAwaitValue(viewModel.getSelectedEvent());

        assertNotNull(selectedUser);
        assertEquals("id1", selectedUser.getAndroidId());
        assertNull(selectedEvent);
    }

    @Test
    public void selectEventShouldUpdateEventLiveDataAndClearUserLiveData() throws InterruptedException {
        AdminViewModel viewModel = new AdminViewModel();
        Event event = new Event(
                "event1",
                "org1",
                "Demo Event",
                new Date(),
                new Date(System.currentTimeMillis() + 1000),
                100,
                20,
                false,
                "Edmonton",
                "Test event"
        );

        viewModel.selectEvent(event);

        Event selectedEvent = getOrAwaitValue(viewModel.getSelectedEvent());
        User selectedUser = getOrAwaitValue(viewModel.getSelectedUser());

        assertNotNull(selectedEvent);
        assertEquals("event1", selectedEvent.getEventId());
        assertNull(selectedUser);
    }

    @Test
    public void selectItemWithUnknownTypeShouldReturnNullForBothMappings() throws InterruptedException {
        AdminViewModel viewModel = new AdminViewModel();

        viewModel.selectItem("not supported");

        User selectedUser = getOrAwaitValue(viewModel.getSelectedUser());
        Event selectedEvent = getOrAwaitValue(viewModel.getSelectedEvent());

        assertNull(selectedUser);
        assertNull(selectedEvent);
    }

    private static <T> T getOrAwaitValue(androidx.lifecycle.LiveData<T> liveData) throws InterruptedException {
        AtomicReference<T> data = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        androidx.lifecycle.Observer<T> observer = new androidx.lifecycle.Observer<T>() {
            @Override
            public void onChanged(T value) {
                data.set(value);
                latch.countDown();
                liveData.removeObserver(this);
            }
        };

        liveData.observeForever(observer);

        if (!latch.await(2, TimeUnit.SECONDS)) {
            liveData.removeObserver(observer);
            throw new AssertionError("LiveData value was never set.");
        }

        return data.get();
    }
}
