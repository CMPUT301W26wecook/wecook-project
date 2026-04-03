package com.example.wecookproject;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class OrganizerInvitedEntrantAdapter extends RecyclerView.Adapter<OrganizerInvitedEntrantAdapter.InvitedViewHolder> {
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_PENDING = "pending";

    private final List<OrganizerInvitedEntrantItem> items = new ArrayList<>();
    private final OnDeleteClickListener onDeleteClickListener;

    public interface OnDeleteClickListener {
        void onDelete(OrganizerInvitedEntrantItem item);
    }

    public OrganizerInvitedEntrantAdapter(OnDeleteClickListener onDeleteClickListener) {
        this.onDeleteClickListener = onDeleteClickListener;
    }

    @NonNull
    @Override
    public InvitedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_organizer_invited_entrant, parent, false);
        return new InvitedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull InvitedViewHolder holder, int position) {
        OrganizerInvitedEntrantItem item = items.get(position);
        holder.checkBox.setChecked(item.isSelected());
        holder.name.setText(item.getDisplayName());
        holder.name.setPaintFlags(holder.name.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        if (STATUS_ACCEPTED.equals(item.getStatus())) {
            holder.status.setVisibility(View.VISIBLE);
            holder.status.setText("Accepted");
            holder.status.setBackgroundResource(R.drawable.bg_status_accepted);
        } else if (STATUS_CONFIRMED.equals(item.getStatus())) {
            holder.status.setVisibility(View.VISIBLE);
            holder.status.setText("Confirmed");
            holder.status.setBackgroundResource(R.drawable.bg_status_accepted);
        } else if (STATUS_CANCELLED.equals(item.getStatus())) {
            holder.status.setVisibility(View.VISIBLE);
            holder.status.setText("Cancelled");
            holder.status.setBackgroundResource(R.drawable.bg_status_cancelled);
        } else if (STATUS_PENDING.equals(item.getStatus())) {
            holder.status.setVisibility(View.VISIBLE);
            holder.status.setText("Invited");
            holder.status.setBackgroundResource(R.drawable.bg_pill_purple);
        } else {
            holder.status.setVisibility(View.GONE);
        }

        holder.menuButton.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(v.getContext(), v);
            popupMenu.getMenu().add("Delete");
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                if (onDeleteClickListener != null) {
                    onDeleteClickListener.onDelete(item);
                }
                return true;
            });
            popupMenu.show();
        });

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> item.setSelected(isChecked));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void submitList(List<OrganizerInvitedEntrantItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public static class InvitedViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkBox;
        final TextView name;
        final TextView status;
        final ImageButton menuButton;

        public InvitedViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.cb_invited_selected);
            name = itemView.findViewById(R.id.tv_invited_name);
            status = itemView.findViewById(R.id.tv_invited_status);
            menuButton = itemView.findViewById(R.id.btn_item_menu);
        }
    }
}
