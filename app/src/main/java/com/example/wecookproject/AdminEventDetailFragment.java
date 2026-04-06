package com.example.wecookproject;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.wecookproject.model.Event;
import com.example.wecookproject.model.EventComment;
import com.example.wecookproject.model.User;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.zxing.WriterException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A Fragment that displays the details of a specific event for administrative purposes.
 */
public class AdminEventDetailFragment extends Fragment {
    private AdminViewModel viewModel;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private Event currentEvent;
    private ListenerRegistration commentsListener;

    private CardView cvEventDetails;
    private LinearLayout llCommentsView;
    private LinearLayout commentsContainer;
    private TextView tvCommentsEmpty;

    /**
     * Initializes the fragment and retrieves the shared AdminViewModel.
     * 
     * @param savedInstanceState    Saved state of the fragment
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);
    }

    /**
     * Show event detail UI and handles Admin interactions for deleting posters and events.
     * 
     * @param inflater           Parent view to which the fragment's UI should be attached.
     * @param container          Parent view for the fragment's UI.
     * @param savedInstanceState Saved state of the fragment.
     * @return The View for the Event Detail UI.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fargment_admin_event_detail, container, false);

        cvEventDetails = view.findViewById(R.id.cv_event_details);
        llCommentsView = view.findViewById(R.id.ll_comments_view);
        commentsContainer = view.findViewById(R.id.layout_comments_container);
        tvCommentsEmpty = view.findViewById(R.id.tv_comments_empty);
        ImageView ivCommentsBack = view.findViewById(R.id.iv_comments_back);

        TextView tvAvatar = view.findViewById(R.id.tv_event_avatar);
        TextView tvNameHeader = view.findViewById(R.id.tv_event_name_header);
        TextView tvLocation = view.findViewById(R.id.tv_event_location);
        ImageView ivPoster = view.findViewById(R.id.iv_event_poster);
        TextView tvNameLabel = view.findViewById(R.id.tv_event_name_label);
        TextView tvStartDate = view.findViewById(R.id.tv_event_start_date);
        TextView tvEndDate = view.findViewById(R.id.tv_event_end_date);
        TextView tvOrganizerName = view.findViewById(R.id.tv_organizer_name);
        TextView tvWaitlistStatus = view.findViewById(R.id.tv_waitlist_status);
        TextView tvDetails = view.findViewById(R.id.tv_event_details);
        Button btnDeletePoster = view.findViewById(R.id.btn_delete_poster);
        Button btnDeleteEvent = view.findViewById(R.id.btn_delete_event);
        ImageButton btnEventMenu = view.findViewById(R.id.btn_event_menu);

        viewModel.getSelectedEvent().observe(getViewLifecycleOwner(), event -> {
            if (event != null) {
                currentEvent = event;
                tvNameHeader.setText(event.getEventName());
                tvNameLabel.setText(event.getEventName());
                tvLocation.setText(event.getLocation());
                
                String startDate = event.getRegistrationStartDate() != null
                        ? UserEventUiUtils.formatRegistrationDate(event.getRegistrationStartDate())
                        : "N/A";
                String endDate = event.getRegistrationEndDate() != null
                        ? UserEventUiUtils.formatRegistrationDate(event.getRegistrationEndDate())
                        : "N/A";
                
                tvStartDate.setText("Start: " + startDate);
                tvEndDate.setText("End: " + endDate);
                
                tvWaitlistStatus.setText(String.format(Locale.getDefault(), "Waitlist: %d/%d", event.getCurrentWaitlistCount(), event.getMaxWaitlist()));
                tvDetails.setText(event.getDescription());

                if (event.getEventName() != null && !event.getEventName().isEmpty()) {
                    tvAvatar.setText(event.getEventName().substring(0, 1).toUpperCase());
                }

                PosterLoader.loadInto(ivPoster, event.getPosterPath());

                // Fetch organizer name
                if (event.getOrganizerId() != null) {
                    db.collection("users").document(event.getOrganizerId()).get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    User organizer = documentSnapshot.toObject(User.class);
                                    if (organizer != null) {
                                        tvOrganizerName.setText(organizer.getName());
                                    }
                                }
                            });
                }
            }
        });

        btnDeletePoster.setOnClickListener(v -> {
            if (currentEvent != null && currentEvent.getEventId() != null) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("posterPath", null);
                db.collection("events").document(currentEvent.getEventId())
                        .update(updates)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(getContext(), "Poster deleted", Toast.LENGTH_SHORT).show();
                            ivPoster.setImageResource(android.R.drawable.ic_menu_gallery);
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to delete poster", Toast.LENGTH_SHORT).show());
            }
        });

        btnDeleteEvent.setOnClickListener(v -> {
            if (currentEvent != null && currentEvent.getEventId() != null) {
                String eventId = currentEvent.getEventId();
                db.collection("events").document(eventId).collection("comments")
                        .get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                                doc.getReference().delete();
                            }
                            db.collection("events").document(eventId)
                                    .delete()
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(getContext(), "Event and comments deleted", Toast.LENGTH_SHORT).show();
                                        getParentFragmentManager().popBackStack();
                                    })
                                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to delete event", Toast.LENGTH_SHORT).show());
                        })
                        .addOnFailureListener(e -> {
                            Log.e("AdminEventDetail", "Failed to delete comments for event: " + eventId, e);
                            db.collection("events").document(eventId).delete()
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(getContext(), "Event deleted", Toast.LENGTH_SHORT).show();
                                        getParentFragmentManager().popBackStack();
                                    });
                        });
            }
        });

        btnEventMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), v);
            popup.getMenu().add("Show QR Code");
            popup.getMenu().add("Show Comments");
            
            popup.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();
                if ("Show QR Code".equals(title)) {
                    handleShowQrCode();
                } else if ("Show Comments".equals(title)) {
                    handleShowComments();
                }
                return true;
            });
            popup.show();
        });

        ivCommentsBack.setOnClickListener(v -> {
            llCommentsView.setVisibility(View.GONE);
            cvEventDetails.setVisibility(View.VISIBLE);
            stopObservingComments();
        });

        return view;
    }

    /**
     * Toggles visibility to show the comments view and starts observing real-time comment data.
     */
    private void handleShowComments() {
        if (currentEvent == null || currentEvent.getEventId() == null) {
            Toast.makeText(getContext(), "No event selected", Toast.LENGTH_SHORT).show();
            return;
        }

        cvEventDetails.setVisibility(View.GONE);
        llCommentsView.setVisibility(View.VISIBLE);
        startObservingComments(currentEvent.getEventId());
    }

    /**
     * Subscribes to Firestore snapshot listener for the event's comments collection.
     * 
     * @param eventId The unique ID of the event whose comments are being observed.
     */
    private void startObservingComments(String eventId) {
        stopObservingComments();
        commentsListener = db.collection("events")
                .document(eventId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e("AdminComments", "Listen failed", error);
                        return;
                    }

                    if (snapshots != null) {
                        renderComments(snapshots.getDocuments());
                    }
                });
    }

    /**
     * Unsubscribes from the real-time Firestore comments listener if it exists.
     */
    private void stopObservingComments() {
        if (commentsListener != null) {
            commentsListener.remove();
            commentsListener = null;
        }
    }

    /**
     * Dynamically inflates and populates the comments container with views for each comment document.
     * 
     * @param documents List of Firestore document snapshots representing event comments.
     */
    private void renderComments(List<DocumentSnapshot> documents) {
        commentsContainer.removeAllViews();
        if (documents.isEmpty()) {
            tvCommentsEmpty.setVisibility(View.VISIBLE);
            return;
        }

        tvCommentsEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (DocumentSnapshot doc : documents) {
            EventComment comment = doc.toObject(EventComment.class);
            if (comment == null) continue;
            comment.setCommentId(doc.getId());

            View itemView = inflater.inflate(R.layout.item_admin_event_comment, commentsContainer, false);
            TextView tvAuthor = itemView.findViewById(R.id.tv_comment_author);
            TextView tvOrganizerTag = itemView.findViewById(R.id.tv_comment_organizer_tag);
            TextView tvEntrantTag = itemView.findViewById(R.id.tv_comment_entrant_tag);
            TextView tvCreatedAt = itemView.findViewById(R.id.tv_comment_created_at);
            TextView tvText = itemView.findViewById(R.id.tv_comment_text);
            TextView btnDelete = itemView.findViewById(R.id.btn_delete_comment);

            tvAuthor.setText(comment.getAuthorName() != null ? comment.getAuthorName() : comment.getAuthorId());
            if (comment.getCreatedAt() != null) {
                tvCreatedAt.setText(UserEventUiUtils.formatEventTimestamp(comment.getCreatedAt()));
            }
            tvText.setText(comment.getCommentText());

            if ("organizer".equalsIgnoreCase(comment.getAuthorRole())) {
                tvOrganizerTag.setVisibility(View.VISIBLE);
                tvEntrantTag.setVisibility(View.GONE);
            } else if ("entrant".equalsIgnoreCase(comment.getAuthorRole())) {
                tvEntrantTag.setVisibility(View.VISIBLE);
                tvOrganizerTag.setVisibility(View.GONE);
            } else {
                tvOrganizerTag.setVisibility(View.GONE);
                tvEntrantTag.setVisibility(View.GONE);
            }

            // Administrators can delete any comment
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> deleteComment(comment.getCommentId()));

            commentsContainer.addView(itemView);
        }
    }

    /**
     * Deletes a specific comment from the Firestore database.
     * 
     * @param commentId The unique ID of the comment to be removed.
     */
    private void deleteComment(String commentId) {
        if (currentEvent == null || currentEvent.getEventId() == null || commentId == null) return;

        db.collection("events")
                .document(currentEvent.getEventId())
                .collection("comments")
                .document(commentId)
                .delete()
                .addOnSuccessListener(unused -> Toast.makeText(getContext(), "Comment deleted", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to delete comment", Toast.LENGTH_SHORT).show());
    }

    /**
     * Validates event state and initiates the QR code display dialog logic.
     */
    private void handleShowQrCode() {
        if (currentEvent == null) {
            Toast.makeText(getContext(), "No event selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String eventId = currentEvent.getEventId();
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(getContext(), "Event ID is missing", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Event.VISIBILITY_PRIVATE.equals(currentEvent.getVisibilityTag())) {
            Toast.makeText(getContext(), "Private events do not have promotional QR codes", Toast.LENGTH_SHORT).show();
            return;
        }

        String qrPayload = currentEvent.getQrCodePath();
        if (qrPayload == null || qrPayload.trim().isEmpty()) {
            Toast.makeText(getContext(), "QR code is missing for this event", Toast.LENGTH_SHORT).show();
            return;
        }

        showQrDialog(qrPayload);
    }

    /**
     * Generates a QR code bitmap from the given payload and displays it in an AlertDialog.
     * 
     * @param payload The content/link to be encoded into the QR code.
     */
    private void showQrDialog(String payload) {
        try {
            int qrSize = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    280,
                    getResources().getDisplayMetrics()
            );
            Bitmap qrBitmap = QrCodeUtils.generateQrBitmap(payload, qrSize);
            
            ImageView qrImage = new ImageView(getContext());
            qrImage.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            qrImage.setAdjustViewBounds(true);
            qrImage.setImageBitmap(qrBitmap);

            TextView linkView = new TextView(getContext());
            linkView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            linkView.setText(payload);
            linkView.setTextColor(Color.BLUE);
            linkView.setPaintFlags(linkView.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            
            linkView.setOnClickListener(v -> {
                Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setData(Uri.parse(payload));
                startActivity(openIntent);
            });

            linkView.setOnLongClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Event link", payload));
                    Toast.makeText(getContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show();
                }
                return true;
            });

            int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
            LinearLayout dialogContent = new LinearLayout(getContext());
            dialogContent.setOrientation(LinearLayout.VERTICAL);
            dialogContent.setGravity(Gravity.CENTER_HORIZONTAL);
            dialogContent.setPadding(padding, padding, padding, padding);
            dialogContent.addView(qrImage);
            dialogContent.addView(linkView);

            new AlertDialog.Builder(requireContext())
                    .setTitle("Event Promotional QR Code")
                    .setView(dialogContent)
                    .setPositiveButton("Close", null)
                    .show();

        } catch (WriterException e) {
            Toast.makeText(getContext(), "Failed to generate QR code bitmap", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Cleans up resources, specifically stopping the Firestore comments listener.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopObservingComments();
    }
}
