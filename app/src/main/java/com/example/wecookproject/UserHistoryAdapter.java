package com.example.wecookproject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class UserHistoryAdapter extends RecyclerView.Adapter<UserHistoryAdapter.UserHistoryViewHolder> {
    public interface Listener {
        void onHistoryClicked(UserHistoryItem item);
        void onDeleteClicked(UserHistoryItem item);
    }

    private final List<UserHistoryItem> items;
    private final Listener listener;

    public UserHistoryAdapter(List<UserHistoryItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserHistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_history_event, parent, false);
        return new UserHistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserHistoryViewHolder holder, int position) {
        UserHistoryItem item = items.get(position);
        holder.tvEventName.setText(item.getEventName());
        holder.tvMeta.setText(item.getLocation() + " • " + UserEventUiUtils.formatDateRange(item.getRegistrationStartDate(), item.getRegistrationEndDate()));
        UserEventUiUtils.applyStatusChip(holder.tvStatus, item.getStatus(), false);
        PosterLoader.loadInto(holder.ivPoster, item.getPosterPath());

        holder.itemView.setOnClickListener(v -> listener.onHistoryClicked(item));
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClicked(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class UserHistoryViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivPoster;
        private final TextView tvEventName;
        private final TextView tvMeta;
        private final TextView tvStatus;
        private final ImageButton btnDelete;

        UserHistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPoster = itemView.findViewById(R.id.iv_history_poster);
            tvEventName = itemView.findViewById(R.id.tv_history_event_name);
            tvMeta = itemView.findViewById(R.id.tv_history_meta);
            tvStatus = itemView.findViewById(R.id.tv_history_status);
            btnDelete = itemView.findViewById(R.id.btn_delete_history);
        }
    }
}
