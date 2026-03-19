package com.example.wecookproject.model;

import java.util.HashMap;
import java.util.Map;

/**
 * This represents a user within the Event Lottery System.
 */
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
    private String phoneNumber;
    private boolean profileCompleted;
    private String role;

    /**
     * Default constructor required for Firebase.
     */
    public User() {
    }

    /**
     * Constructs a new User with all profile and system fields.
     *
     * @param addressLine1      The first line of the address.
     * @param addressLine2      The second line of the address.
     * @param androidId         The unique device identifier.
     * @param birthday          The user's birthday.
     * @param city              The user's city.
     * @param country           The user's country.
     * @param firstName         The user's first name.
     * @param lastName          The user's last name.
     * @param postalCode        The user's postal code.
     * @param profileCompleted  True if the profile is fully filled out.
     * @param role              The user role (e.g., "entrant").
     */
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
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public boolean isProfileCompleted() { return profileCompleted; }
    public void setProfileCompleted(boolean profileCompleted) { this.profileCompleted = profileCompleted; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    /**
     * This get the full name of the user.
     * @return A string containing "FirstName LastName", or just FirstName if LastName is missing.
     */
    public String getName() {
        if (firstName == null) return "";
        if (lastName == null) return firstName;
        return firstName + " " + lastName;
    }

    /**
     * Resets the user's profile information.
     */
    public void clearProfile() {
        this.firstName = "Deleted";
        this.lastName = "User";
        this.birthday = "";
        this.addressLine1 = "";
        this.addressLine2 = "";
        this.city = "";
        this.postalCode = "";
        this.country = "";
        this.phoneNumber = "";
        this.profileCompleted = false;
    }

    /**
     * Converts the user object into a Map for easy serialization to Firebase Firestore.
     * @return A map containing all persistent user fields.
     */
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
        userMap.put("phoneNumber", phoneNumber);
        userMap.put("profileCompleted", profileCompleted);
        userMap.put("role", role);
        return userMap;
    }
}
