package com.example.wecookproject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shared validation helpers for signup and profile forms.
 */
public final class UserInputValidator {

    public static final String FIELD_FIRST_NAME = "firstName";
    public static final String FIELD_LAST_NAME = "lastName";
    public static final String FIELD_BIRTHDAY = "birthday";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_PHONE_NUMBER = "phoneNumber";
    public static final String FIELD_ADDRESS_LINE_1 = "addressLine1";
    public static final String FIELD_CITY = "city";
    public static final String FIELD_POSTAL_CODE = "postalCode";
    public static final String FIELD_COUNTRY = "country";

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CANADIAN_POSTAL_PATTERN =
            Pattern.compile("^[A-Za-z]\\d[A-Za-z][ -]?\\d[A-Za-z]\\d$");

    private UserInputValidator() {
    }

    public static Map<String, String> validateSignupDetails(boolean limitedProfile,
                                                            String firstName,
                                                            String lastName,
                                                            String birthday,
                                                            String email,
                                                            String phoneNumber) {
        LinkedHashMap<String, String> errors = new LinkedHashMap<>();
        requireNonBlank(errors, FIELD_FIRST_NAME, firstName, "First name is required");
        if (limitedProfile) {
            requireNonBlank(errors, FIELD_LAST_NAME, lastName, "Last name is required");
        }
        validateBirthday(errors, birthday, true);
        validateEmail(errors, email, false);
        if (!limitedProfile) {
            validatePhoneNumber(errors, phoneNumber, true);
        }
        return errors;
    }

    public static Map<String, String> validateSignupAddress(String addressLine1,
                                                            String city,
                                                            String postalCode,
                                                            String country) {
        LinkedHashMap<String, String> errors = new LinkedHashMap<>();
        requireNonBlank(errors, FIELD_ADDRESS_LINE_1, addressLine1, "Address line 1 is required");
        requireNonBlank(errors, FIELD_CITY, city, "City is required");
        validatePostalCode(errors, postalCode, true);
        validateCountry(errors, country);
        return errors;
    }

    public static Map<String, String> validateEntrantProfile(String firstName,
                                                             String birthday,
                                                             String email,
                                                             String addressLine1,
                                                             String city,
                                                             String postalCode,
                                                             String country) {
        LinkedHashMap<String, String> errors = new LinkedHashMap<>();
        requireNonBlank(errors, FIELD_FIRST_NAME, firstName, "First name is required");
        validateBirthday(errors, birthday, true);
        validateEmail(errors, email, false);
        requireNonBlank(errors, FIELD_ADDRESS_LINE_1, addressLine1, "Address is required");
        requireNonBlank(errors, FIELD_CITY, city, "City is required");
        validatePostalCode(errors, postalCode, true);
        validateCountry(errors, country);
        return errors;
    }

    public static Map<String, String> validateOrganizerProfile(String firstName,
                                                               String lastName,
                                                               String birthday,
                                                               String addressLine1,
                                                               String city,
                                                               String postalCode,
                                                               String country) {
        LinkedHashMap<String, String> errors = new LinkedHashMap<>();
        requireNonBlank(errors, FIELD_FIRST_NAME, firstName, "First name is required");
        requireNonBlank(errors, FIELD_LAST_NAME, lastName, "Last name is required");
        validateBirthday(errors, birthday, true);
        requireNonBlank(errors, FIELD_ADDRESS_LINE_1, addressLine1, "Address is required");
        requireNonBlank(errors, FIELD_CITY, city, "City is required");
        validatePostalCode(errors, postalCode, true);
        validateCountry(errors, country);
        return errors;
    }

    private static void requireNonBlank(Map<String, String> errors,
                                        String field,
                                        String value,
                                        String message) {
        if (errors.containsKey(field)) {
            return;
        }
        if (value == null || value.trim().isEmpty()) {
            errors.put(field, message);
        }
    }

    private static void validateBirthday(Map<String, String> errors, String birthday, boolean required) {
        if (birthday == null || birthday.trim().isEmpty()) {
            if (required) {
                errors.put(FIELD_BIRTHDAY, "Birthday is required");
            }
            return;
        }

        String normalizedBirthday = birthday.trim();
        if (!normalizedBirthday.matches("\\d{2}/\\d{2}/\\d{4}")) {
            errors.put(FIELD_BIRTHDAY, "Use MM/DD/YYYY");
            return;
        }

        SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
        format.setLenient(false);
        try {
            Date parsedDate = format.parse(normalizedBirthday);
            if (parsedDate == null || !normalizedBirthday.equals(format.format(parsedDate))) {
                errors.put(FIELD_BIRTHDAY, "Enter a valid birthday");
                return;
            }
            if (parsedDate.after(new Date())) {
                errors.put(FIELD_BIRTHDAY, "Birthday cannot be in the future");
            }
        } catch (ParseException e) {
            errors.put(FIELD_BIRTHDAY, "Enter a valid birthday");
        }
    }

    private static void validateEmail(Map<String, String> errors, String email, boolean required) {
        if (email == null || email.trim().isEmpty()) {
            if (required) {
                errors.put(FIELD_EMAIL, "Email is required");
            }
            return;
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            errors.put(FIELD_EMAIL, "Enter a valid email");
        }
    }

    private static void validatePhoneNumber(Map<String, String> errors, String phoneNumber, boolean required) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            if (required) {
                errors.put(FIELD_PHONE_NUMBER, "Phone number is required");
            }
            return;
        }

        String digitsOnly = phoneNumber.replaceAll("\\D", "");
        if (digitsOnly.length() < 10 || digitsOnly.length() > 15) {
            errors.put(FIELD_PHONE_NUMBER, "Enter a valid phone number");
        }
    }

    private static void validatePostalCode(Map<String, String> errors, String postalCode, boolean required) {
        if (postalCode == null || postalCode.trim().isEmpty()) {
            if (required) {
                errors.put(FIELD_POSTAL_CODE, "Postal code is required");
            }
            return;
        }

        String normalizedPostalCode = postalCode.trim();
        if (!CANADIAN_POSTAL_PATTERN.matcher(normalizedPostalCode).matches()) {
            errors.put(FIELD_POSTAL_CODE, "Use a valid postal code");
        }
    }

    private static void validateCountry(Map<String, String> errors, String country) {
        if (country == null || country.trim().isEmpty()) {
            return;
        }
        if (country.trim().length() < 2) {
            errors.put(FIELD_COUNTRY, "Enter a valid country");
        }
    }
}
