package com.example.wecookproject;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;

import java.util.Map;

/**
 * Captures user detail inputs during signup.
 *
 * <p>This screen validates role-specific required fields and forwards valid
 * data to the address step.</p>
 */
public class SignupDetailsActivity extends AppCompatActivity {
    /**
     * Initializes views, input formatting, and navigation actions.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup_details);

        ImageView backButton = findViewById(R.id.iv_back);
        Button continueButton = findViewById(R.id.btn_continue);
        EditText etFirstName = findViewById(R.id.et_first_name);
        EditText etLastName = findViewById(R.id.et_last_name);
        EditText etBirthday = findViewById(R.id.et_birthday);
        EditText etEmail = findViewById(R.id.et_email);
        EditText etPhoneNumber = findViewById(R.id.et_phone_number);
        TextInputLayout tilFirstName = findViewById(R.id.til_first_name);
        TextInputLayout tilLastName = findViewById(R.id.til_last_name);
        TextInputLayout tilBirthday = findViewById(R.id.til_birthday);

        backButton.setOnClickListener(v -> finish());
        setupBirthdayFormatting(etBirthday);
        TextInputLayout tilEmail = findViewById(R.id.til_email);
        TextInputLayout tilPhoneNumber = findViewById(R.id.til_phone_number);
        configureEntrantOnlyFields(tilEmail, tilPhoneNumber);
        continueButton.setOnClickListener(v -> handleContinue(
                etFirstName,
                etLastName,
                etBirthday,
                etEmail,
                etPhoneNumber,
                tilFirstName,
                tilLastName,
                tilBirthday,
                tilEmail,
                tilPhoneNumber
        ));
    }

    /**
     * Shows entrant-only fields and hides them for organizer signup.
     *
     * @param emailLayout email container
     * @param phoneNumberLayout phone number container
     */
    private void configureEntrantOnlyFields(TextInputLayout emailLayout, TextInputLayout phoneNumberLayout) {
        String clickedRole = getIntent().getStringExtra("clickedRole");
        if ("ORGANIZER".equals(clickedRole)) {
            emailLayout.setVisibility(View.GONE);
            phoneNumberLayout.setVisibility(View.GONE);
        }
    }

    /**
     * Adds birthday auto-formatting in {@code MM/DD/YYYY} format.
     *
     * @param birthdayInput birthday input field
     */
    private void setupBirthdayFormatting(EditText birthdayInput) {
        birthdayInput.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;

            /**
             * No-op callback required by {@link TextWatcher}.
             *
             * @param s current text
             * @param start changed start index
             * @param count changed length
             * @param after replacement length
             */
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            /**
             * No-op callback required by {@link TextWatcher}.
             *
             * @param s current text
             * @param start changed start index
             * @param before replaced length
             * @param count inserted length
             */
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            /**
             * Normalizes user-entered digits into birthday format.
             *
             * @param s editable text to normalize
             */
            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                // Strip all non-digit characters to work with raw digits only
                String digits = s.toString().replaceAll("[^\\d]", "");

                // Limit to 8 digits (MMDDYYYY)
                if (digits.length() > 8) {
                    digits = digits.substring(0, 8);
                }

                // Build the formatted string: MM/DD/YYYY
                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < digits.length(); i++) {
                    if (i == 2 || i == 4) {
                        formatted.append('/');
                    }
                    formatted.append(digits.charAt(i));
                }

                s.replace(0, s.length(), formatted.toString());

                isFormatting = false;
            }
        });
    }

    /**
     * Validates detail fields and proceeds to the address step.
     *
     * @param firstNameInput first-name field
     * @param lastNameInput last-name field
     * @param birthdayInput birthday field
     * @param emailInput email field
     * @param phoneNumberInput phone-number field
     */
    private void handleContinue(EditText firstNameInput,
                                EditText lastNameInput,
                                EditText birthdayInput,
                                EditText emailInput,
                                EditText phoneNumberInput,
                                TextInputLayout firstNameLayout,
                                TextInputLayout lastNameLayout,
                                TextInputLayout birthdayLayout,
                                TextInputLayout emailLayout,
                                TextInputLayout phoneNumberLayout) {
        String firstName = firstNameInput.getText().toString().trim();
        String lastName = lastNameInput.getText().toString().trim();
        String birthday = birthdayInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String phoneNumber = phoneNumberInput.getText().toString().trim();

        String clickedRole = getIntent().getStringExtra("clickedRole");
        boolean organizer = "ORGANIZER".equals(clickedRole);
        clearError(firstNameLayout);
        clearError(lastNameLayout);
        clearError(birthdayLayout);
        clearError(emailLayout);
        clearError(phoneNumberLayout);

        Map<String, String> errors = UserInputValidator.validateSignupDetails(
                organizer,
                firstName,
                lastName,
                birthday,
                email,
                phoneNumber
        );
        if (!errors.isEmpty()) {
            applyError(firstNameLayout, errors.get(UserInputValidator.FIELD_FIRST_NAME));
            applyError(lastNameLayout, errors.get(UserInputValidator.FIELD_LAST_NAME));
            applyError(birthdayLayout, errors.get(UserInputValidator.FIELD_BIRTHDAY));
            applyError(emailLayout, errors.get(UserInputValidator.FIELD_EMAIL));
            applyError(phoneNumberLayout, errors.get(UserInputValidator.FIELD_PHONE_NUMBER));
            Toast.makeText(this, errors.values().iterator().next(), Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(SignupDetailsActivity.this, SignupAddressActivity.class);
        intent.putExtra("firstName", firstName);
        intent.putExtra("lastName", lastName);
        intent.putExtra("birthday", birthday);
        intent.putExtra("email", email);
        intent.putExtra("phoneNumber", phoneNumber);
        if (getIntent().hasExtra("clickedRole")) {
            intent.putExtra("clickedRole", clickedRole);
        }
        startActivity(intent);
    }

    private void clearError(TextInputLayout layout) {
        layout.setError(null);
    }

    private void applyError(TextInputLayout layout, String error) {
        if (error != null && !error.trim().isEmpty()) {
            layout.setError(error);
        }
    }
}
