package com.example.wecookproject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.wecookproject.model.Event;
import com.example.wecookproject.model.User;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AdminEventDetailFragment extends Fragment {

    private AdminViewModel viewModel;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();;
    private Event currentEvent;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fargment_admin_event_detail, container, false);

        TextView tvAvatar = view.findViewById(R.id.tv_event_avatar);
        TextView tvNameHeader = view.findViewById(R.id.tv_event_name_header);
        TextView tvLocation = view.findViewById(R.id.tv_event_location);
        ImageView ivPoster = view.findViewById(R.id.iv_event_poster);
        TextView tvNameLabel = view.findViewById(R.id.tv_event_name_label);
        TextView tvDate = view.findViewById(R.id.tv_event_date);
        TextView tvOrganizerName = view.findViewById(R.id.tv_organizer_name);
        TextView tvWaitlistStatus = view.findViewById(R.id.tv_waitlist_status);
        TextView tvDetails = view.findViewById(R.id.tv_event_details);
        Button btnDeletePoster = view.findViewById(R.id.btn_delete_poster);
        Button btnDeleteEvent = view.findViewById(R.id.btn_delete_event);

        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());

        viewModel.getSelectedEvent().observe(getViewLifecycleOwner(), event -> {
            if (event != null) {
                currentEvent = event;
                tvNameHeader.setText(event.getEventName());
                tvNameLabel.setText(event.getEventName());
                tvLocation.setText(event.getLocation());
                
                String startDate = event.getRegistrationStartDate() != null ? sdf.format(event.getRegistrationStartDate()) : "N/A";
                String endDate = event.getRegistrationEndDate() != null ? sdf.format(event.getRegistrationEndDate()) : "N/A";
                tvDate.setText(String.format("%s - %s", startDate, endDate));
                
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
                db.collection("events").document(currentEvent.getEventId())
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(getContext(), "Event deleted", Toast.LENGTH_SHORT).show();
                            getParentFragmentManager().popBackStack();
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to delete event", Toast.LENGTH_SHORT).show());
            }
        });

        view.findViewById(R.id.btn_event_menu).setOnClickListener(v -> {
            // To show the QR code, implement in future
            Toast.makeText(getContext(), "QR code not implemented", Toast.LENGTH_SHORT).show();
        });

        return view;
    }
}
