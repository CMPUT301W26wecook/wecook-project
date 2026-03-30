package com.example.wecookproject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

public class UserInputValidatorTest {

    @Test
    public void entrantSignupRejectsInvalidBirthdayPhoneAndEmail() {
        Map<String, String> errors = UserInputValidator.validateSignupDetails(
                false,
                "Jane",
                "",
                "13/40/2020",
                "not-an-email",
                "12345"
        );

        assertEquals("Enter a valid birthday", errors.get(UserInputValidator.FIELD_BIRTHDAY));
        assertEquals("Enter a valid email", errors.get(UserInputValidator.FIELD_EMAIL));
        assertEquals("Enter a valid phone number", errors.get(UserInputValidator.FIELD_PHONE_NUMBER));
    }

    @Test
    public void organizerSignupRequiresLastName() {
        Map<String, String> errors = UserInputValidator.validateSignupDetails(
                true,
                "Alice",
                "",
                "01/15/1990",
                "",
                ""
        );

        assertEquals("Last name is required", errors.get(UserInputValidator.FIELD_LAST_NAME));
        assertFalse(errors.containsKey(UserInputValidator.FIELD_PHONE_NUMBER));
    }

    @Test
    public void addressValidationRejectsInvalidPostalCode() {
        Map<String, String> errors = UserInputValidator.validateSignupAddress(
                "123 Main St",
                "Edmonton",
                "12345",
                "Canada"
        );

        assertEquals("Use a valid postal code", errors.get(UserInputValidator.FIELD_POSTAL_CODE));
    }

    @Test
    public void entrantProfileValidationRequiresCoreFields() {
        Map<String, String> errors = UserInputValidator.validateEntrantProfile(
                "",
                "",
                "",
                "",
                "",
                "",
                ""
        );

        assertTrue(errors.containsKey(UserInputValidator.FIELD_FIRST_NAME));
        assertTrue(errors.containsKey(UserInputValidator.FIELD_BIRTHDAY));
        assertTrue(errors.containsKey(UserInputValidator.FIELD_ADDRESS_LINE_1));
        assertTrue(errors.containsKey(UserInputValidator.FIELD_CITY));
        assertTrue(errors.containsKey(UserInputValidator.FIELD_POSTAL_CODE));
    }

    @Test
    public void validEntrantProfilePassesValidation() {
        Map<String, String> errors = UserInputValidator.validateEntrantProfile(
                "John",
                "12/31/1999",
                "john@example.com",
                "123 Main St",
                "Edmonton",
                "T6G 2R3",
                "Canada"
        );

        assertTrue(errors.isEmpty());
    }
}
