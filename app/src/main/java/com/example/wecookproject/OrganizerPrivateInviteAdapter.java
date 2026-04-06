package com.example.wecookproject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView adapter for organizer private waitlist invites.
 */
public class OrganizerPrivateInviteAdapter extends RecyclerView.Adapter<OrganizerPrivateInviteAdapter.ViewHolder> {
    private final List<OrganizerWaitlistItem> items = new ArrayList<>();
    private final Set<String> selectedEntrantIds = new HashSet<>();
    private OnSelectionChangedListener onSelectionChangedListener;

    interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_organizer_private_invite_entrant, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OrganizerWaitlistItem item = items.get(position);
        String entrantId = item.getEntrantId();
        holder.tvName.setText(item.getDisplayName());
        holder.tvSubtitle.setText(item.getSubtitle());
        holder.tvEntrantId.setText(entrantId);

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(selectedEntrantIds.contains(entrantId));
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedEntrantIds.add(entrantId);
            } else {
                selectedEntrantIds.remove(entrantId);
            }
            notifySelectionChanged();
        });

        holder.itemView.setOnClickListener(v -> holder.checkBox.performClick());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void submitList(List<OrganizerWaitlistItem> newItems) {
        Set<String> validIds = new HashSet<>();
        for (OrganizerWaitlistItem item : newItems) {
            validIds.add(item.getEntrantId());
        }
        selectedEntrantIds.retainAll(validIds);
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public List<String> getSelectedEntrantIds() {
        return new ArrayList<>(selectedEntrantIds);
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.onSelectionChangedListener = listener;
    }

    private void notifySelectionChanged() {
        if (onSelectionChangedListener != null) {
            onSelectionChangedListener.onSelectionChanged(selectedEntrantIds.size());
        }
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox checkBox;
        private final TextView tvName;
        private final TextView tvSubtitle;
        private final TextView tvEntrantId;

        private ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.cb_private_invite_selected);
            tvName = itemView.findViewById(R.id.tv_private_invite_name);
            tvSubtitle = itemView.findViewById(R.id.tv_private_invite_subtitle);
            tvEntrantId = itemView.findViewById(R.id.tv_private_invite_entrant_id);
        }
    }
}
