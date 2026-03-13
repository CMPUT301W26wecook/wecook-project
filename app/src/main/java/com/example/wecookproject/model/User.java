package com.example.wecookproject.model;

import java.util.HashMap;
import java.util.Map;

public class User {
    private String addressLine1;
    private String addressLine2;
    private String androidId;
    private String birthday;
    private String city;
    private String country;
    private String firstName;
    private String lastName;
    private String postalCode;
    private boolean profileCompleted;
    private String role;

    public User() {
    }

    public User(String addressLine1, String addressLine2, String androidId, String birthday, String city, String country, String firstName, String lastName, String postalCode, boolean profileCompleted, String role) {
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.androidId = androidId;
        this.birthday = birthday;
        this.city = city;
        this.country = country;
        this.firstName = firstName;
        this.lastName = lastName;
        this.postalCode = postalCode;
        this.profileCompleted = profileCompleted;
        this.role = role;
    }

    // Getters and Setters
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getAndroidId() { return androidId; }
    public void setAndroidId(String androidId) { this.androidId = androidId; }

    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public boolean isProfileCompleted() { return profileCompleted; }
    public void setProfileCompleted(boolean profileCompleted) { this.profileCompleted = profileCompleted; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getName() {
        if (firstName == null) return "";
        if (lastName == null) return firstName;
        return firstName + " " + lastName;
    }

    public void clearProfile() {
        this.firstName = "Deleted";
        this.lastName = "User";
        this.birthday = "";
        this.addressLine1 = "";
        this.addressLine2 = "";
        this.city = "";
        this.postalCode = "";
        this.country = "";
        this.profileCompleted = false;
    }

    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("addressLine1", addressLine1);
        userMap.put("addressLine2", addressLine2);
        userMap.put("androidId", androidId);
        userMap.put("birthday", birthday);
        userMap.put("city", city);
        userMap.put("country", country);
        userMap.put("firstName", firstName);
        userMap.put("lastName", lastName);
        userMap.put("postalCode", postalCode);
        userMap.put("profileCompleted", profileCompleted);
        userMap.put("role", role);
        return userMap;
    }
}
