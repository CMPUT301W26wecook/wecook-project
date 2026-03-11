package com.example.wecookproject;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.UUID;

public class OrganizerEditEventActivity extends AppCompatActivity {
    private String eventId;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private TextInputLayout tilEventName;
    private TextInputLayout tilRegistrationPeriod;
    private TextInputLayout tilMaxWaitlist;
    private TextInputEditText etEventName;
    private TextInputEditText etRegistrationPeriod;
    private TextInputEditText etMaxWaitlist;
    private RadioGroup rgEnrollmentCriteria;
    private RadioGroup rgLotteryMethod;
    private String originalPosterUrl;
    private String pendingPosterUrl;
    private boolean posterCommitted;
    private ActivityResultLauncher<String> posterPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_edit_event);
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        eventId = getIntent().getStringExtra("eventId");
        if (eventId == null) {
            Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tilEventName = findViewById(R.id.til_event_name);
        tilRegistrationPeriod = findViewById(R.id.til_registration_period);
        tilMaxWaitlist = findViewById(R.id.til_max_waitlist);
        etEventName = findViewById(R.id.et_event_name);
        etRegistrationPeriod = findViewById(R.id.et_registration_period);
        etMaxWaitlist = findViewById(R.id.et_max_waitlist);
        rgEnrollmentCriteria = findViewById(R.id.rg_enrollment_criteria);
        rgLotteryMethod = findViewById(R.id.rg_lottery_method);
        FrameLayout flPosterUpload = findViewById(R.id.fl_poster_upload);

        posterPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handlePosterSelection
        );

        loadCurrentPosterUrl();
        flPosterUpload.setOnClickListener(v -> posterPickerLauncher.launch("image/*"));

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_events) {
                startActivity(new Intent(this, OrganizerHomeActivity.class));
                return true;
            } else if (id == R.id.nav_create_events) {
                startActivity(new Intent(this, OrganizerCreateEventActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, OrganizerProfileActivity.class));
                return true;
            }
            return true;
        });

        findViewById(R.id.iv_back).setOnClickListener(v -> cancelAndExit());
        findViewById(R.id.btn_cancel).setOnClickListener(v -> cancelAndExit());
        findViewById(R.id.btn_update_event).setOnClickListener(v -> updateEvent());

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                cancelAndExit();
            }
        });
    }

    private void updateEvent() {
        tilEventName.setError(null);
        tilRegistrationPeriod.setError(null);
        tilMaxWaitlist.setError(null);

        String eventName = getTrimmedText(etEventName);
        String registrationPeriod = getTrimmedText(etRegistrationPeriod);
        String maxWaitlistText = getTrimmedText(etMaxWaitlist);

        boolean hasError = false;
        Map<String, Object> updates = new HashMap<>();

        if (!TextUtils.isEmpty(eventName)) {
            updates.put("eventName", eventName);
        }

        if (!TextUtils.isEmpty(registrationPeriod)) {
            updates.put("registrationPeriod", registrationPeriod);
        }

        if (!TextUtils.isEmpty(maxWaitlistText)) {
            try {
                int maxWaitlist = Integer.parseInt(maxWaitlistText);
                if (maxWaitlist <= 0) {
                    tilMaxWaitlist.setError("Maximum waitlist must be greater than 0");
                    hasError = true;
                } else {
                    updates.put("maxWaitlist", maxWaitlist);
                }
            } catch (NumberFormatException e) {
                tilMaxWaitlist.setError("Enter a valid number");
                hasError = true;
            }
        }

        String enrollmentCriteria = getEnrollmentCriteriaValue(rgEnrollmentCriteria.getCheckedRadioButtonId());
        if (enrollmentCriteria != null) {
            updates.put("enrollmentCriteria", enrollmentCriteria);
        }

        String lotteryMethodology = getLotteryMethodValue(rgLotteryMethod.getCheckedRadioButtonId());
        if (lotteryMethodology != null) {
            updates.put("lotteryMethodology", lotteryMethodology);
        }

        if (!TextUtils.isEmpty(pendingPosterUrl)) {
            updates.put("posterUrl", pendingPosterUrl);
        }

        if (hasError) {
            return;
        }

        if (updates.isEmpty()) {
            Toast.makeText(this, "Enter at least one valid field to update", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("events")
                .document(eventId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    if (!TextUtils.isEmpty(pendingPosterUrl)) {
                        String replacedPosterUrl = originalPosterUrl;
                        originalPosterUrl = pendingPosterUrl;
                        pendingPosterUrl = null;
                        posterCommitted = true;
                        deleteStorageFile(replacedPosterUrl);
                    }
                    Toast.makeText(this, "Event updated", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update event", Toast.LENGTH_SHORT).show());
    }

    private void handlePosterSelection(Uri imageUri) {
        if (imageUri == null) {
            return;
        }

        String mimeType = getContentResolver().getType(imageUri);
        if (!isValidPosterMimeType(mimeType)) {
            Toast.makeText(this, "Only JPG, JPEG, and PNG images are allowed", Toast.LENGTH_SHORT).show();
            return;
        }

        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (TextUtils.isEmpty(extension)) {
            extension = "jpg";
        }

        StorageReference posterRef = storage.getReference()
                .child("event_posters")
                .child(eventId)
                .child(UUID.randomUUID() + "." + extension);

        posterRef.putFile(imageUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return posterRef.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri -> {
                    String previousPendingPoster = pendingPosterUrl;
                    pendingPosterUrl = downloadUri.toString();
                    posterCommitted = false;
                    deleteStorageFile(previousPendingPoster);
                    Toast.makeText(this, "Poster uploaded. Tap Update Event to save it.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to upload poster", Toast.LENGTH_SHORT).show());
    }

    private boolean isValidPosterMimeType(String mimeType) {
        return "image/jpeg".equalsIgnoreCase(mimeType)
                || "image/jpg".equalsIgnoreCase(mimeType)
                || "image/png".equalsIgnoreCase(mimeType);
    }

    private void loadCurrentPosterUrl() {
        db.collection("events")
                .document(eventId)
                .get()
                .addOnSuccessListener(documentSnapshot -> originalPosterUrl = documentSnapshot.getString("posterUrl"));
    }

    private void cancelAndExit() {
        if (!posterCommitted && !TextUtils.isEmpty(pendingPosterUrl)) {
            deleteStorageFile(pendingPosterUrl);
            pendingPosterUrl = null;
        }
        finish();
    }

    private void deleteStorageFile(String fileUrl) {
        if (TextUtils.isEmpty(fileUrl)) {
            return;
        }

        try {
            storage.getReferenceFromUrl(fileUrl)
                    .delete()
                    .addOnFailureListener(e -> { });
        } catch (IllegalArgumentException ignored) {
        }
    }

    private String getTrimmedText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private String getEnrollmentCriteriaValue(int checkedId) {
        if (checkedId == R.id.rb_open_to_all) {
            return "Open to all";
        }
        if (checkedId == R.id.rb_by_invitation) {
            return "By invitation only";
        }
        if (checkedId == R.id.rb_age_restricted) {
            return "Age restricted (18+)";
        }
        return null;
    }

    private String getLotteryMethodValue(int checkedId) {
        if (checkedId == R.id.rb_organizer_picks) {
            return "Organizer picks";
        }
        if (checkedId == R.id.rb_system_generates) {
            return "System generates";
        }
        return null;
    }

}
