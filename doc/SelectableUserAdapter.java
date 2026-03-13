package com.example.wecookproject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wecookproject.model.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectableUserAdapter extends RecyclerView.Adapter<SelectableUserAdapter.UserViewHolder> {

    private final List<User> userList;
    private final Set<String> selectedUserIds = new HashSet<>();

    public SelectableUserAdapter(List<User> userList) {
        this.userList = userList;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = userList.get(position);
        String userId = user.getAndroidId();

        holder.tvUserName.setText(user.getFullName());
        holder.tvUserId.setText("User ID: " + user.getDisplayId());
        holder.tvUserLocation.setText("Location: " + user.getDisplayLocation());
        holder.tvUserStatus.setText(user.getStatus());

        // Set status color based on profile completion
        if (user.isProfileCompleted()) {
            holder.tvUserStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE3F2FD));  // Light blue
            holder.tvUserStatus.setTextColor(0xFF1976D2);  // Blue
        } else {
            holder.tvUserStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFEBEE));  // Light red
            holder.tvUserStatus.setTextColor(0xFFD32F2F);  // Red
        }

        boolean isSelected = userId != null && selectedUserIds.contains(userId);
        holder.cbSelectUser.setOnCheckedChangeListener(null);
        holder.cbSelectUser.setChecked(isSelected);

        View.OnClickListener toggleSelection = v -> {
            if (userId == null || userId.isEmpty()) {
                return;
            }

            boolean newSelectedState = !holder.cbSelectUser.isChecked();
            holder.cbSelectUser.setChecked(newSelectedState);
            updateSelection(userId, newSelectedState);
        };

        holder.itemView.setOnClickListener(toggleSelection);
        holder.cbSelectUser.setOnCheckedChangeListener((buttonView, checked) -> {
            if (userId == null || userId.isEmpty()) {
                return;
            }
            updateSelection(userId, checked);
        });
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public List<String> getSelectedUserIds() {
        return new ArrayList<>(selectedUserIds);
    }

    public void clearSelection() {
        selectedUserIds.clear();
        notifyDataSetChanged();
    }

    private void updateSelection(String userId, boolean selected) {
        if (selected) {
            selectedUserIds.add(userId);
        } else {
            selectedUserIds.remove(userId);
        }
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvUserName;
        private final TextView tvUserId;
        private final TextView tvUserLocation;
        private final TextView tvUserStatus;
        private final CheckBox cbSelectUser;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tv_user_name);
            tvUserId = itemView.findViewById(R.id.tv_user_id);
            tvUserLocation = itemView.findViewById(R.id.tv_user_location);
            tvUserStatus = itemView.findViewById(R.id.tv_user_status);
            cbSelectUser = itemView.findViewById(R.id.cb_select_user);
        }
    }
}

