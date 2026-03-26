package com.example.wecookproject;

import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.wecookproject.model.Event;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.zxing.WriterException;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Activity for organizers to view an event's details, observe live updates, and navigate to
 * related management flows such as editing the event or reviewing its waitlist. Within the app it
 * acts as the UI controller for the organizer event-details screen, binding Firestore snapshot
 * data directly to the view layer.
 *
 * Outstanding issues:
 * - Some actions are incomplete or placeholder-driven, including the registration map button and
 *   the QR-code flow and will be implemented in part 4.
 * - Presentation and Firestore listener logic are handled directly in the Activity, which tightly
 *   couples UI and data updates instead of separating them through a repository or ViewModel-style
 *   layer.
 */
public class OrganizerEventDetailsActivity extends AppCompatActivity {
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
    
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration eventListener;
    private SwitchMaterial geolocationSwitch;
    private boolean suppressSwitchCallback;
    private Event currentEvent;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_TIME_PATTERN, Locale.getDefault());

    /**
     * Initializes event detail rendering and related navigation actions.
     *
     * @param savedInstanceState previously saved state, or {@code null}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_event_details);

        String eventId = getIntent().getStringExtra("eventId");
        
        TextView tvEventNameBig = findViewById(R.id.tv_event_name);
        TextView tvEventLocation = findViewById(R.id.tv_event_location);
        TextView tvEventNameDetail = findViewById(R.id.tv_event_name_detail);
        TextView tvEventDates = findViewById(R.id.tv_event_dates);
        TextView tvOrganizerLabel = findViewById(R.id.tv_organizer_label);
        TextView tvWaitlistLabel = findViewById(R.id.tv_waitlist_label);
        TextView tvCapacityLabel = findViewById(R.id.tv_capacity_label);
        TextView tvEventVisibility = findViewById(R.id.tv_event_visibility);
        TextView tvEventDescription = findViewById(R.id.tv_event_description);
        geolocationSwitch = findViewById(R.id.switch_geolocation);

        if (eventId != null) {
            geolocationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressSwitchCallback) {
                    return;
                }
                db.collection("events")
                        .document(eventId)
                        .update("geolocationRequired", isChecked)
                        .addOnFailureListener(e -> {
                            suppressSwitchCallback = true;
                            buttonView.setChecked(!isChecked);
                            suppressSwitchCallback = false;
                            Toast.makeText(this, "Failed to update geolocation requirement", Toast.LENGTH_SHORT).show();
                        });
            });

            // Use addSnapshotListener for real-time updates
            eventListener = db.collection("events").document(eventId)
                    .addSnapshotListener((documentSnapshot, error) -> {
                        if (error != null) {
                            Toast.makeText(this, "Failed to load event details: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        
                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            Event event = documentSnapshot.toObject(Event.class);
                            if (event != null) {
                                currentEvent = event;
                                tvEventNameBig.setText(event.getEventName());
                                tvEventLocation.setText(event.getLocation());
                                tvEventNameDetail.setText(event.getEventName());
                                
                                // Format registration dates
                                String registrationDateText = "TBD";
                                if (event.getRegistrationStartDate() != null && event.getRegistrationEndDate() != null) {
                                    registrationDateText = dateFormat.format(event.getRegistrationStartDate()) + " to " + dateFormat.format(event.getRegistrationEndDate());
                                } else if (event.getRegistrationStartDate() != null) {
                                    registrationDateText = "From " + dateFormat.format(event.getRegistrationStartDate());
                                } else if (event.getRegistrationEndDate() != null) {
                                    registrationDateText = "Until " + dateFormat.format(event.getRegistrationEndDate());
                                }
                                tvEventDates.setText(registrationDateText);
                                
                                tvOrganizerLabel.setText("Organizer: " + event.getOrganizerId().substring(0, Math.min(event.getOrganizerId().length(), 5)) + "...");
                                tvWaitlistLabel.setText("Waitlist: " + event.getCurrentWaitlistCount() + "/" + event.getMaxWaitlist());
                                List<String> acceptedEntrantIds = FirestoreFieldUtils.getStringList(documentSnapshot, "acceptedEntrantIds");
                                int acceptedCount = acceptedEntrantIds.size();
                                tvCapacityLabel.setText("Capacity: " + acceptedCount + "/" + event.getCapacity());
                                String visibilityLabel = Event.VISIBILITY_PRIVATE.equals(event.getVisibilityTag())
                                        ? "Private"
                                        : "Public";
                                tvEventVisibility.setText("Visibility: " + visibilityLabel);
                                suppressSwitchCallback = true;
                                geolocationSwitch.setChecked(event.isGeolocationRequired());
                                suppressSwitchCallback = false;
                                
                                String description = event.getDescription() == null
                                        ? ""
                                        : event.getDescription().trim();
                                tvEventDescription.setText(description);
                            }
                        } else {
                            // Event was deleted or doesn't exist
                            Toast.makeText(this, "Event no longer exists", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
        } else {
            Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
            finish();
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        // Deselect if not exactly a main tab, or keep highlighting "events"
        bottomNav.setSelectedItemId(R.id.nav_events);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_create_events) {
                startActivity(new Intent(this, OrganizerCreateEventActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, OrganizerProfileActivity.class));
                return true;
            } else if (id == R.id.nav_events) {
                Intent intent = new Intent(this, OrganizerHomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            }
            return true;
        });
        
        findViewById(R.id.btn_edit_event).setOnClickListener(v -> {
             if (eventId != null) {
                 Intent intent = new Intent(this, OrganizerEditEventActivity.class);
                 intent.putExtra("eventId", eventId);
                 startActivity(intent);
             } else {
                 Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
             }
        });
        
        findViewById(R.id.btn_view_waitlist).setOnClickListener(v -> {
             if (eventId != null) {
                 Intent intent = new Intent(this, OrganizerEntrantListActivity.class);
                 intent.putExtra("eventId", eventId);
                 startActivity(intent);
             } else {
                 Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
             }
        });
        
        findViewById(R.id.btn_registration_map).setOnClickListener(v -> {
             if (eventId != null) {
                 Intent intent = new Intent(this, OrganizerEventMapActivity.class);
                 intent.putExtra("eventId", eventId);
                 startActivity(intent);
             } else {
                 Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
             }
        });

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        findViewById(R.id.btn_show_qr).setOnClickListener(v -> {
            if (eventId == null || eventId.trim().isEmpty()) {
                Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentEvent != null && Event.VISIBILITY_PRIVATE.equals(currentEvent.getVisibilityTag())) {
                Toast.makeText(this, "Private events cannot generate promotional QR codes", Toast.LENGTH_SHORT).show();
                return;
            }
            String resolvedEventId = eventId;
            if ((resolvedEventId == null || resolvedEventId.trim().isEmpty()) && currentEvent != null) {
                resolvedEventId = currentEvent.getEventId();
            }
            if (resolvedEventId == null || resolvedEventId.trim().isEmpty()) {
                Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
                return;
            }

            String qrPayload;
            if (currentEvent != null
                    && currentEvent.getQrCodePath() != null
                    && !currentEvent.getQrCodePath().trim().isEmpty()) {
                qrPayload = currentEvent.getQrCodePath().trim();
            } else {
                qrPayload = ensureQrPathExists(db, resolvedEventId, currentEvent);
                if (qrPayload == null || qrPayload.trim().isEmpty()) {
                    Toast.makeText(this, "Unable to generate promotional QR code", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            showQrDialog(qrPayload);
        });
    }

    private String ensureQrPathExists(FirebaseFirestore db, String eventDocId, Event event) {
        if (event == null) {
            return null;
        }
        if (event != null && Event.VISIBILITY_PRIVATE.equals(event.getVisibilityTag())) {
            return null;
        }
        String existing = event.getQrCodePath();
        if (existing != null && !existing.trim().isEmpty()) {
            return existing.trim();
        }
        String resolvedEventId = eventDocId;
        if (resolvedEventId == null || resolvedEventId.trim().isEmpty()) {
            resolvedEventId = event.getEventId();
        }
        if (resolvedEventId == null || resolvedEventId.trim().isEmpty()) {
            return null;
        }
        String generatedLink = QrCodeUtils.buildPromotionalEventLink(resolvedEventId);
        if (currentEvent != null) {
            currentEvent.setQrCodePath(generatedLink);
        }
        db.collection("events")
                .document(resolvedEventId)
                .update("qrCodePath", generatedLink);
        return generatedLink;
    }

    private void showQrDialog(String payload) {
        try {
            int qrSize = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    280,
                    getResources().getDisplayMetrics()
            );
            Bitmap qrBitmap = QrCodeUtils.generateQrBitmap(payload, qrSize);
            ImageView qrImage = new ImageView(this);
            qrImage.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            qrImage.setAdjustViewBounds(true);
            qrImage.setImageBitmap(qrBitmap);

            TextView linkView = new TextView(this);
            linkView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            linkView.setText(payload);
            linkView.setTextColor(Color.BLUE);
            linkView.setPaintFlags(linkView.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            linkView.setOnClickListener(v -> {
                Intent openIntent = new Intent(this, PublicEventLandingActivity.class);
                openIntent.setData(Uri.parse(payload));
                startActivity(openIntent);
            });
            linkView.setOnLongClickListener(v -> {
                ClipboardManager clipboard =
                        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Event link", payload));
                    Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            int topPadding = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
            linkView.setPadding(0, topPadding, 0, 0);

            LinearLayout dialogContent = new LinearLayout(this);
            dialogContent.setOrientation(LinearLayout.VERTICAL);
            dialogContent.setGravity(Gravity.CENTER_HORIZONTAL);
            int padding = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
            dialogContent.setPadding(padding, padding, padding, padding);
            dialogContent.addView(qrImage);
            dialogContent.addView(linkView);

            new AlertDialog.Builder(this)
                    .setTitle("Promotional QR Code")
                    .setView(dialogContent)
                    .setPositiveButton("Close", null)
                    .show();
        } catch (WriterException e) {
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Removes snapshot listeners to avoid leaks.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detach the listener to prevent memory leaks
        if (eventListener != null) {
            eventListener.remove();
        }
    }
}
