package com.example.wecookproject.model;

import java.util.HashMap;
import java.util.Map;

public class User {
    private String firstName;
    private String lastName;
    private String birthday;
    private String address1;
    private String address2;
    private String city;
    private String postalCode;
    private String country;
    private String androidId;
    private String role;

    public User() {

    }

    public User(String firstName, String lastName, String birthday, String address1, String address2, String city, String postalCode, String country, String androidId, String role) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthday = birthday;
        this.address1 = address1;
        this.address2 = address2;
        this.city = city;
        this.postalCode = postalCode;
        this.country = country;
        this.androidId = androidId;
        this.role = role;
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }

    public String getAddress1() { return address1; }
    public void setAddress1(String addressLine1) { this.address1 = addressLine1; }

    public String getAddress2() { return address2; }
    public void setAddress2(String addressLine2) { this.address2 = addressLine2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getAndroidId() { return androidId; }
    public void setAndroidId(String androidId) { this.androidId = androidId; }

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
        this.address1 = "";
        this.address2 = "";
        this.city = "";
        this.postalCode = "";
        this.country = "";
    }

    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("firstName", firstName);
        userMap.put("lastName", lastName);
        userMap.put("birthday", birthday);
        userMap.put("address1", address1);
        userMap.put("address2", address2);
        userMap.put("city", city);
        userMap.put("postalCode", postalCode);
        userMap.put("country", country);
        userMap.put("androidId", androidId);
        userMap.put("role", role);
        return userMap;
    }
}
