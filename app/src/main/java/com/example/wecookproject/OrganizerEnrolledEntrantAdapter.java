package com.example.wecookproject;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class OrganizerEnrolledEntrantAdapter extends RecyclerView.Adapter<OrganizerEnrolledEntrantAdapter.Holder> {
    private final List<OrganizerEnrolledEntrantItem> items = new ArrayList<>();

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_organizer_enrolled_entrant, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        OrganizerEnrolledEntrantItem item = items.get(position);
        holder.name.setText(item.getDisplayName());
        holder.contact.setText(buildContactLine(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void submitList(List<OrganizerEnrolledEntrantItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    private static String buildContactLine(OrganizerEnrolledEntrantItem item) {
        List<String> parts = new ArrayList<>();
        if (!item.getPhoneNumber().isEmpty()) {
            parts.add(item.getPhoneNumber());
        }
        if (!item.getEmail().isEmpty()) {
            parts.add(item.getEmail());
        }
        if (parts.isEmpty()) {
            return "Contact not on file";
        }
        return TextUtils.join(" · ", parts);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView contact;

        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_enrolled_name);
            contact = itemView.findViewById(R.id.tv_enrolled_contact);
        }
    }
}
