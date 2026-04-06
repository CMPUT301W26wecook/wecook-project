package com.example.wecookproject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wecookproject.model.User;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RecyclerView adapter for administrative notification logs.
 * Displays sender and recipient information along with the notification content.
 */
public class AdminNotificationAdapter extends RecyclerView.Adapter<AdminNotificationAdapter.ViewHolder> {

    private final List<UserNotificationItem> items;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Map<String, String> userNameCache = new HashMap<>();

    /**
     * Constructs a new AdminNotificationAdapter.
     * @param items The list of notification log items to display.
     */
    public AdminNotificationAdapter(List<UserNotificationItem> items) {
        this.items = items;
    }

    /**
     * Inflates the item layout and creates a new ViewHolder.
     *
     * @param parent Parent view group.
     * @param viewType View type of the new View.
     * @return A new ListElementViewHolder.
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_notification_list_element, parent, false);
        return new ViewHolder(view);
    }

    /**
     * Binds the data to the ViewHolder at the specified position.
     * Sets display title, location, timestamp, sender, recipient, and message.
     *
     * @param holder The ViewHolder to update.
     * @param position The position of the item in the list.
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserNotificationItem item = items.get(position);
        holder.tvTitle.setText(item.getEventName());
        holder.tvLocation.setText(item.getLocation().isEmpty() ? "Location unavailable" : item.getLocation());
        holder.tvMessage.setText(item.getMessage());
        holder.tvTimestamp.setText(item.getFormattedTime());

        loadUserName(item.getSenderId(), "Sender: ", holder.tvSender);
        loadUserName(item.getRecipientId(), "Recipient: ", holder.tvRecipient);
    }

    /**
     * Loads the user's name from Firestore and updates the cache.
     * 
     * @param userId   The ID of the user to look up.
     * @param prefix   Text prefix (e.g., "Sender: ").
     * @param textView The TextView to update.
     */
    private void loadUserName(String userId, String prefix, TextView textView) {
        if (userId == null || userId.isEmpty()) {
            textView.setText(prefix + userId);
            return;
        }

        if (userNameCache.containsKey(userId)) {
            textView.setText(prefix + userNameCache.get(userId));
        } else {
            db.collection("users").document(userId).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    User user = doc.toObject(User.class);
                    if (user != null) {
                        String name = user.getName();
                        userNameCache.put(userId, name);
                        notifyDataSetChanged();
                    }
                } else {
                    userNameCache.put(userId, "Deleted User (" + userId + ")");
                    notifyDataSetChanged();
                }
            });
        }
    }

    /**
     * Returns the total number of items in the data set.
     *
     * @return The number of items in the list.
     */
    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * ViewHolder class for the AdminNotificationAdapter.
     * Holds references to the UI components for each list item.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvLocation;
        private final TextView tvMessage;
        private final TextView tvTimestamp;
        private final TextView tvSender;
        private final TextView tvRecipient;

        /**
         * Constructs a new ViewHolder.
         * @param itemView The view representing an individual list item.
         */
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_notification_title);
            tvLocation = itemView.findViewById(R.id.tv_notification_location);
            tvMessage = itemView.findViewById(R.id.tv_notification_message);
            tvTimestamp = itemView.findViewById(R.id.tv_notification_timestamp);
            tvSender = itemView.findViewById(R.id.tv_notification_sender);
            tvRecipient = itemView.findViewById(R.id.tv_notification_recipient);
        }
    }
}
