package com.example.wecookproject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * RecyclerView adapter for entrant notifications.
 */
public class UserNotificationAdapter extends RecyclerView.Adapter<UserNotificationAdapter.ViewHolder> {
    private final List<UserNotificationItem> items;

    public UserNotificationAdapter(List<UserNotificationItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserNotificationItem item = items.get(position);
        holder.tvTitle.setText(item.getEventName());
        holder.tvLocation.setText(item.getLocation().isEmpty() ? "Location unavailable" : item.getLocation());
        holder.tvMessage.setText(item.getMessage());
        holder.tvTimestamp.setText(item.getFormattedTime());
        holder.tvStatus.setText(item.getStatus().equalsIgnoreCase("unread") ? "Unread" : "Read");
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvLocation;
        private final TextView tvMessage;
        private final TextView tvTimestamp;
        private final TextView tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_notification_title);
            tvLocation = itemView.findViewById(R.id.tv_notification_location);
            tvMessage = itemView.findViewById(R.id.tv_notification_message);
            tvTimestamp = itemView.findViewById(R.id.tv_notification_timestamp);
            tvStatus = itemView.findViewById(R.id.tv_notification_status);
        }
    }
}
