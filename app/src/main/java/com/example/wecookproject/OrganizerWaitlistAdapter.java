package com.example.wecookproject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for rendering organizer waitlist entrants in a scrollable list. Within the
 * app it acts as the presentation adapter for the entrant-management flow, binding
 * {@link OrganizerWaitlistItem} data objects to the waitlist row layout.
 *
 * Outstanding issues:
 * - The adapter is display-only and does not yet expose row interactions for selection, bulk
 *   actions, or entrant-specific organizer actions.
 */
public class OrganizerWaitlistAdapter extends RecyclerView.Adapter<OrganizerWaitlistAdapter.WaitlistViewHolder> {
    private final List<OrganizerWaitlistItem> items = new ArrayList<>();

    /**
     * Inflates one waitlist row.
     *
     * @param parent parent RecyclerView
     * @param viewType view type id
     * @return created view holder
     */
    @NonNull
    @Override
    public WaitlistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_organizer_waitlist_entrant, parent, false);
        return new WaitlistViewHolder(view);
    }

    /**
     * Binds one waitlist row.
     *
     * @param holder row holder
     * @param position adapter position
     */
    @Override
    public void onBindViewHolder(@NonNull WaitlistViewHolder holder, int position) {
        OrganizerWaitlistItem item = items.get(position);
        holder.tvAvatar.setText(item.getAvatarLabel());
        holder.tvName.setText(item.getDisplayName());
        holder.tvSubtitle.setText(item.getSubtitle());
    }

    /**
     * @return number of rows
     */
    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * Replaces adapter data and refreshes list.
     *
     * @param newItems replacement items
     */
    public void submitList(List<OrganizerWaitlistItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    /**
     * ViewHolder for organizer waitlist rows.
     */
    static class WaitlistViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvAvatar;
        private final TextView tvName;
        private final TextView tvSubtitle;

        /**
         * Creates a view holder and binds subviews.
         *
         * @param itemView row root view
         */
        WaitlistViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tv_entrant_avatar);
            tvName = itemView.findViewById(R.id.tv_entrant_name);
            tvSubtitle = itemView.findViewById(R.id.tv_entrant_subtitle);
        }
    }
}
