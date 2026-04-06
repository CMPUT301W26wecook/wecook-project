package com.example.wecookproject;

import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.wecookproject.model.Event;
import com.example.wecookproject.model.EventComment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.zxing.WriterException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

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
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration eventListener;
    private ListenerRegistration commentsListener;
    private SwitchMaterial geolocationSwitch;
    private Event currentEvent;
    private String organizerId;
    private String organizerDisplayName;
    private TextView tvOrganizerLabel;
    private EditText etComment;
    private Button btnPostComment;
    private Button btnInviteEntrants;
    private TextView tvCommentsEmpty;
    private LinearLayout commentsContainer;

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
        organizerId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        
        TextView tvEventNameBig = findViewById(R.id.tv_event_name);
        TextView tvEventLocation = findViewById(R.id.tv_event_location);
        TextView tvEventNameDetail = findViewById(R.id.tv_event_name_detail);
        TextView tvEventDates = findViewById(R.id.tv_event_dates);
        ImageView ivEventPoster = findViewById(R.id.iv_event_poster);
        tvOrganizerLabel = findViewById(R.id.tv_organizer_label);
        TextView tvWaitlistLabel = findViewById(R.id.tv_waitlist_label);
        TextView tvCapacityLabel = findViewById(R.id.tv_capacity_label);
        TextView tvEventVisibility = findViewById(R.id.tv_event_visibility);
        TextView tvEventDescription = findViewById(R.id.tv_event_description);
        geolocationSwitch = findViewById(R.id.switch_geolocation);
        geolocationSwitch.setEnabled(false);
        geolocationSwitch.setClickable(false);
        etComment = findViewById(R.id.et_organizer_comment);
        btnPostComment = findViewById(R.id.btn_post_organizer_comment);
        btnInviteEntrants = findViewById(R.id.btn_invite_entrants);
        tvCommentsEmpty = findViewById(R.id.tv_comments_empty);
        commentsContainer = findViewById(R.id.layout_comments_container);
        TextView tvAvailability = findViewById(R.id.tv_event_availability);

        loadOrganizerProfile();

        if (eventId != null) {
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
                                PosterLoader.loadInto(ivEventPoster, event.getPosterPath());
                                
                                // Format registration dates
                                String registrationDateText = "TBD";
                                if (event.getRegistrationStartDate() != null && event.getRegistrationEndDate() != null) {
                                    registrationDateText = UserEventUiUtils.formatRegistrationDate(event.getRegistrationStartDate())
                                            + " to "
                                            + UserEventUiUtils.formatRegistrationDate(event.getRegistrationEndDate());
                                } else if (event.getRegistrationStartDate() != null) {
                                    registrationDateText = "From " + UserEventUiUtils.formatRegistrationDate(event.getRegistrationStartDate());
                                } else if (event.getRegistrationEndDate() != null) {
                                    registrationDateText = "Until " + UserEventUiUtils.formatRegistrationDate(event.getRegistrationEndDate());
                                }
                                if (event.getEventTime() != null) {
                                    registrationDateText = registrationDateText + "\nEvent time: "
                                            + UserEventUiUtils.formatEventDate(event.getEventTime());
                                }
                                tvEventDates.setText(registrationDateText);
                                
                                updateOrganizerLabel();
                                int waitlistCount = FirestoreFieldUtils
                                        .getStringList(documentSnapshot, "waitlistEntrantIds")
                                        .size();
                                tvWaitlistLabel.setText("Waitlist: " + waitlistCount + "/" + event.getMaxWaitlist());
                                List<String> acceptedEntrantIds = FirestoreFieldUtils.getStringList(documentSnapshot, "acceptedEntrantIds");
                                int acceptedCount = acceptedEntrantIds.size();
                                tvCapacityLabel.setText("Capacity: " + acceptedCount + "/" + event.getCapacity());
                                String visibilityLabel = Event.VISIBILITY_PRIVATE.equals(event.getVisibilityTag())
                                        ? "Private"
                                        : "Public";
                                tvEventVisibility.setText("Visibility: " + visibilityLabel);
                                btnInviteEntrants.setVisibility(Event.VISIBILITY_PRIVATE.equals(event.getVisibilityTag())
                                        ? View.VISIBLE
                                        : View.GONE);
                                geolocationSwitch.setChecked(event.isGeolocationRequired());
                                
                                String description = event.getDescription() == null
                                        ? ""
                                        : event.getDescription().trim();
                                tvEventDescription.setText(description);

                                // 可用性标签逻辑
                                int waitlistCount = event.getCurrentWaitlistCount();
                                int maxWaitlist = event.getMaxWaitlist();
                                List<String> acceptedEntrantIds = FirestoreFieldUtils.getStringList(documentSnapshot, "acceptedEntrantIds");
                                int finalCount = acceptedEntrantIds != null ? acceptedEntrantIds.size() : 0;
                                int capacity = event.getCapacity();
                                boolean available = (waitlistCount < maxWaitlist) && (finalCount < capacity);
                                if (available) {
                                    tvAvailability.setText("可用性：可报名");
                                    tvAvailability.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                                } else {
                                    tvAvailability.setText("可用性：不可报名");
                                    tvAvailability.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                                }
                            }
                        } else {
                            // Event was deleted or doesn't exist
                            Toast.makeText(this, "Event no longer exists", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
            observeComments(eventId);
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

        findViewById(R.id.btn_view_enrolled_entrants).setOnClickListener(v -> {
            if (eventId != null) {
                Intent intent = new Intent(this, OrganizerEnrolledEntrantsActivity.class);
                intent.putExtra("eventId", eventId);
                startActivity(intent);
            } else {
                Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
            }
        });

        btnInviteEntrants.setOnClickListener(v -> {
            if (eventId == null || eventId.trim().isEmpty()) {
                Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentEvent == null || !Event.VISIBILITY_PRIVATE.equals(currentEvent.getVisibilityTag())) {
                Toast.makeText(this, "Private invites are available only for private events", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, OrganizerPrivateWaitlistInviteActivity.class);
            intent.putExtra("eventId", eventId);
            startActivity(intent);
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
        btnPostComment.setOnClickListener(v -> submitComment(eventId));
    }

    private void loadOrganizerProfile() {
        db.collection("users")
                .document(organizerId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    organizerDisplayName = UserDocumentUtils.buildDisplayName(snapshot, "Organizer");
                    updateOrganizerLabel();
                })
                .addOnFailureListener(e -> {
                    organizerDisplayName = "Organizer";
                    updateOrganizerLabel();
                });
    }

    private void updateOrganizerLabel() {
        if (tvOrganizerLabel == null) {
            return;
        }
        String labelName = organizerDisplayName == null || organizerDisplayName.trim().isEmpty()
                ? "Organizer"
                : organizerDisplayName;
        tvOrganizerLabel.setText("Organizer: " + labelName);
    }

    private void observeComments(String eventId) {
        if (commentsListener != null) {
            commentsListener.remove();
        }
        commentsListener = db.collection("events")
                .document(eventId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Failed to load comments", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<EventComment> comments = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot snapshot : snapshots.getDocuments()) {
                            EventComment comment = snapshot.toObject(EventComment.class);
                            if (comment == null) {
                                continue;
                            }
                            comment.setCommentId(snapshot.getId());
                            comments.add(comment);
                        }
                    }
                    renderComments(comments, eventId);
                });
    }

    private void submitComment(String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "No event ID provided", Toast.LENGTH_SHORT).show();
            return;
        }

        String commentText = etComment.getText() == null ? "" : etComment.getText().toString().trim();
        if (commentText.isEmpty()) {
            etComment.setError("Comment cannot be empty");
            return;
        }

        btnPostComment.setEnabled(false);
        DocumentReference commentReference = db.collection("events")
                .document(eventId)
                .collection("comments")
                .document();

        Map<String, Object> commentData = new HashMap<>();
        commentData.put("commentId", commentReference.getId());
        commentData.put("eventId", eventId);
        commentData.put("authorId", organizerId);
        commentData.put("authorName", organizerDisplayName == null || organizerDisplayName.trim().isEmpty()
                ? "Organizer"
                : organizerDisplayName);
        commentData.put("authorRole", "organizer");
        commentData.put("commentText", commentText);
        commentData.put("createdAt", FieldValue.serverTimestamp());

        commentReference.set(commentData)
                .addOnSuccessListener(unused -> {
                    etComment.setText("");
                    btnPostComment.setEnabled(true);
                    Toast.makeText(this, "Comment posted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    btnPostComment.setEnabled(true);
                    Toast.makeText(this, "Failed to post comment", Toast.LENGTH_SHORT).show();
                });
    }

    private void renderComments(List<EventComment> comments, String eventId) {
        commentsContainer.removeAllViews();
        boolean hasComments = comments != null && !comments.isEmpty();
        tvCommentsEmpty.setVisibility(hasComments ? View.GONE : View.VISIBLE);
        if (!hasComments) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (EventComment comment : comments) {
            View itemView = inflater.inflate(R.layout.item_organizer_event_comment, commentsContainer, false);
            TextView tvAuthor = itemView.findViewById(R.id.tv_comment_author);
            TextView tvAuthorTag = itemView.findViewById(R.id.tv_comment_author_tag);
            TextView tvCreatedAt = itemView.findViewById(R.id.tv_comment_created_at);
            TextView tvText = itemView.findViewById(R.id.tv_comment_text);
            TextView btnDelete = itemView.findViewById(R.id.btn_delete_comment);

            tvAuthor.setText(getDisplayAuthorName(comment));
            tvCreatedAt.setText(formatCommentDate(comment.getCreatedAt()));
            tvText.setText(comment.getCommentText() == null ? "" : comment.getCommentText());

            boolean organizerComment = "organizer".equalsIgnoreCase(comment.getAuthorRole());
            tvAuthorTag.setVisibility(organizerComment ? View.VISIBLE : View.GONE);

            boolean entrantComment = "entrant".equalsIgnoreCase(comment.getAuthorRole());
            btnDelete.setVisibility(entrantComment ? View.VISIBLE : View.GONE);
            btnDelete.setOnClickListener(v -> deleteComment(eventId, comment.getCommentId()));

            commentsContainer.addView(itemView);
        }
    }

    private void deleteComment(String eventId, String commentId) {
        if (eventId == null || eventId.trim().isEmpty() || commentId == null || commentId.trim().isEmpty()) {
            Toast.makeText(this, "Comment unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        db.collection("events")
                .document(eventId)
                .collection("comments")
                .document(commentId)
                .delete()
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Comment deleted", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to delete comment", Toast.LENGTH_SHORT).show());
    }

    private String getDisplayAuthorName(EventComment comment) {
        if (comment == null) {
            return "";
        }
        String authorName = comment.getAuthorName();
        if (authorName != null && !authorName.trim().isEmpty()) {
            return authorName.trim();
        }
        String authorId = comment.getAuthorId();
        return authorId == null ? "Unknown user" : authorId;
    }

    private String formatCommentDate(com.google.firebase.Timestamp timestamp) {
        if (timestamp == null) {
            return "Just now";
        }
        return UserEventUiUtils.formatEventTimestamp(timestamp);
    }

    private String getSafeTrimmedString(DocumentSnapshot snapshot, String fieldName) {
        if (snapshot == null) {
            return "";
        }
        String value = snapshot.getString(fieldName);
        return value == null ? "" : value.trim();
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
        if (commentsListener != null) {
            commentsListener.remove();
        }
    }
}
