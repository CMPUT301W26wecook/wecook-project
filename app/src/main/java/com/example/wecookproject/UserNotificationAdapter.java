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
    interface NotificationActionListener {
        void onNotificationOpened(UserNotificationItem item);
        void onMarkReadClicked(UserNotificationItem item);
    }

    private final List<UserNotificationItem> items;
    private final NotificationActionListener actionListener;

    public UserNotificationAdapter(List<UserNotificationItem> items, NotificationActionListener actionListener) {
        this.items = items;
        this.actionListener = actionListener;
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
        holder.tvType.setText(formatType(item.getType()));
        holder.tvStatus.setText(formatStatus(item));

        boolean actionEnabled;
        String actionText;
        if (item.requiresConfirmation()) {
            actionEnabled = !item.isConfirmed();
            actionText = item.isConfirmed() ? "Confirmed" : "Confirm";
        } else {
            actionEnabled = item.isUnread();
            actionText = "Read";
        }
        holder.btnRead.setText(actionText);
        holder.btnRead.setEnabled(actionEnabled);
        holder.btnRead.setAlpha(actionEnabled ? 1f : 0.5f);

        holder.itemView.setOnClickListener(v -> actionListener.onNotificationOpened(item));
        holder.btnRead.setOnClickListener(v -> actionListener.onMarkReadClicked(item));
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
        private final TextView tvType;
        private final TextView btnRead;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_notification_title);
            tvLocation = itemView.findViewById(R.id.tv_notification_location);
            tvMessage = itemView.findViewById(R.id.tv_notification_message);
            tvTimestamp = itemView.findViewById(R.id.tv_notification_timestamp);
            tvStatus = itemView.findViewById(R.id.tv_notification_status);
            tvType = itemView.findViewById(R.id.tv_notification_type);
            btnRead = itemView.findViewById(R.id.btn_notification_mark_read);
        }
    }

    private static String formatType(String type) {
        if (NotificationHelper.TYPE_PRIVATE_INVITE.equals(type)) {
            return "Invitation";
        }
        if (NotificationHelper.TYPE_LOTTERY_SELECTED.equals(type)) {
            return "Lottery";
        }
        if (NotificationHelper.TYPE_LOTTERY_NOT_SELECTED.equals(type)) {
            return "Not Selected";
        }
        if (NotificationHelper.TYPE_REPLACEMENT_SELECTED.equals(type)) {
            return "Replacement";
        }
        return "Update";
    }

    private static String formatStatus(UserNotificationItem item) {
        if (item.isConfirmed()) {
            return "Confirmed";
        }
        if (item.isUnread()) {
            return "Unread";
        }
        return "Read";
    }
}
