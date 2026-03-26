package com.example.wecookproject;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView adapter for rendering organizer waitlist entrants with selection and per-row actions.
 */
public class OrganizerWaitlistAdapter extends RecyclerView.Adapter<OrganizerWaitlistAdapter.WaitlistViewHolder> {
    private final List<OrganizerWaitlistItem> items = new ArrayList<>();
    private final Set<String> selectedEntrantIds = new HashSet<>();
    private String expandedActionEntrantId;
    private final OnDeleteClickListener onDeleteClickListener;
    private OnSelectionChangedListener onSelectionChangedListener;

    public interface OnDeleteClickListener {
        void onDelete(OrganizerWaitlistItem item);
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    public OrganizerWaitlistAdapter(OnDeleteClickListener onDeleteClickListener) {
        this.onDeleteClickListener = onDeleteClickListener;
    }

    @NonNull
    @Override
    public WaitlistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_organizer_waitlist_entrant, parent, false);
        return new WaitlistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WaitlistViewHolder holder, int position) {
        OrganizerWaitlistItem item = items.get(position);
        String entrantId = item.getEntrantId();
        boolean isExpanded = entrantId.equals(expandedActionEntrantId);

        holder.tvName.setText(item.getDisplayName());
        holder.tvName.setPaintFlags(holder.tvName.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

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

        holder.mainRow.setBackgroundResource(isExpanded
                ? R.drawable.bg_selection_row_active
                : R.drawable.bg_selection_row);
        holder.deleteAction.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

        holder.menuButton.setOnClickListener(v -> {
            if (entrantId.equals(expandedActionEntrantId)) {
                expandedActionEntrantId = null;
            } else {
                expandedActionEntrantId = entrantId;
            }
            notifyDataSetChanged();
        });

        holder.deleteAction.setOnClickListener(v -> {
            expandedActionEntrantId = null;
            if (onDeleteClickListener != null) {
                onDeleteClickListener.onDelete(item);
            }
        });
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
        if (expandedActionEntrantId != null && !validIds.contains(expandedActionEntrantId)) {
            expandedActionEntrantId = null;
        }

        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public List<String> getCurrentEntrantIds() {
        List<String> ids = new ArrayList<>();
        for (OrganizerWaitlistItem item : items) {
            ids.add(item.getEntrantId());
        }
        return ids;
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

    static class WaitlistViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox checkBox;
        private final TextView tvName;
        private final TextView deleteAction;
        private final ImageButton menuButton;
        private final LinearLayout mainRow;

        WaitlistViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.cb_waitlist_selected);
            tvName = itemView.findViewById(R.id.tv_entrant_name);
            deleteAction = itemView.findViewById(R.id.tv_delete_action);
            menuButton = itemView.findViewById(R.id.btn_item_menu);
            mainRow = itemView.findViewById(R.id.layout_row_main);
        }
    }
}
