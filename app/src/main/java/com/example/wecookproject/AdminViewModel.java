package com.example.wecookproject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.wecookproject.model.Event;
import com.example.wecookproject.model.User;

public class AdminViewModel extends ViewModel {
    private final MutableLiveData<Object> selectedItem = new MutableLiveData<>();

    public void selectItem(Object item) {
        selectedItem.setValue(item);
    }

    public LiveData<Object> getSelectedItem() {
        return selectedItem;
    }

    public void selectUser(User user) {
        selectItem(user);
    }

    public void selectEvent(Event event) {
        selectItem(event);
    }

    public LiveData<User> getSelectedUser() {
        MutableLiveData<User> userLiveData = new MutableLiveData<>();
        selectedItem.observeForever(item -> {
            if (item instanceof User) {
                userLiveData.setValue((User) item);
            }
        });
        return userLiveData;
    }

    public LiveData<Event> getSelectedEvent() {
        MutableLiveData<Event> eventLiveData = new MutableLiveData<>();
        selectedItem.observeForever(item -> {
            if (item instanceof Event) {
                eventLiveData.setValue((Event) item);
            }
        });
        return eventLiveData;
    }
}
