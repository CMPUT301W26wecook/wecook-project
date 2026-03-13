package com.example.wecookproject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wecookproject.model.Organizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectableOrganizerAdapter extends RecyclerView.Adapter<SelectableOrganizerAdapter.OrganizerViewHolder> {

    private final List<Organizer> organizerList;
    private final Set<String> selectedOrganizerIds = new HashSet<>();

    public SelectableOrganizerAdapter(List<Organizer> organizerList) {
        this.organizerList = organizerList;
    }

    @NonNull
    @Override
    public OrganizerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_organizer, parent, false);
        return new OrganizerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrganizerViewHolder holder, int position) {
        Organizer organizer = organizerList.get(position);
        String organizerId = organizer.getAndroidId();

        holder.tvOrganizerName.setText(organizer.getFullName());
        holder.tvOrganizerId.setText("Organizer ID: " + organizer.getDisplayId());
        holder.tvOrganizerLocation.setText("Location: " + organizer.getDisplayLocation());
        holder.tvEventCount.setText("Events: " + organizer.getEventCount() + " managed");
        holder.tvOrganizerStatus.setText(organizer.getStatus());

        // Set status color based on profile completion
        if (organizer.isProfileCompleted()) {
            holder.tvOrganizerStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE8F5E8));  // Light green
            holder.tvOrganizerStatus.setTextColor(0xFF2E7D32);  // Green
        } else {
            holder.tvOrganizerStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFEBEE));  // Light red
            holder.tvOrganizerStatus.setTextColor(0xFFD32F2F);  // Red
        }

        boolean isSelected = organizerId != null && selectedOrganizerIds.contains(organizerId);
        holder.cbSelectOrganizer.setOnCheckedChangeListener(null);
        holder.cbSelectOrganizer.setChecked(isSelected);

        View.OnClickListener toggleSelection = v -> {
            if (organizerId == null || organizerId.isEmpty()) {
                return;
            }

            boolean newSelectedState = !holder.cbSelectOrganizer.isChecked();
            holder.cbSelectOrganizer.setChecked(newSelectedState);
            updateSelection(organizerId, newSelectedState);
        };

        holder.itemView.setOnClickListener(toggleSelection);
        holder.cbSelectOrganizer.setOnCheckedChangeListener((buttonView, checked) -> {
            if (organizerId == null || organizerId.isEmpty()) {
                return;
            }
            updateSelection(organizerId, checked);
        });
    }

    @Override
    public int getItemCount() {
        return organizerList.size();
    }

    public List<String> getSelectedOrganizerIds() {
        return new ArrayList<>(selectedOrganizerIds);
    }

    public void clearSelection() {
        selectedOrganizerIds.clear();
        notifyDataSetChanged();
    }

    private void updateSelection(String organizerId, boolean selected) {
        if (selected) {
            selectedOrganizerIds.add(organizerId);
        } else {
            selectedOrganizerIds.remove(organizerId);
        }
    }

    static class OrganizerViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvOrganizerName;
        private final TextView tvOrganizerId;
        private final TextView tvOrganizerLocation;
        private final TextView tvEventCount;
        private final TextView tvOrganizerStatus;
        private final CheckBox cbSelectOrganizer;

        OrganizerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrganizerName = itemView.findViewById(R.id.tv_organizer_name);
            tvOrganizerId = itemView.findViewById(R.id.tv_organizer_id);
            tvOrganizerLocation = itemView.findViewById(R.id.tv_organizer_location);
            tvEventCount = itemView.findViewById(R.id.tv_event_count);
            tvOrganizerStatus = itemView.findViewById(R.id.tv_organizer_status);
            cbSelectOrganizer = itemView.findViewById(R.id.cb_select_organizer);
        }
    }
}

