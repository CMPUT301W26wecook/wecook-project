package com.example.wecookproject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wecookproject.model.Event;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectableEventAdapter extends RecyclerView.Adapter<SelectableEventAdapter.EventViewHolder> {

    private final List<Event> eventList;
    private final Set<String> selectedEventIds = new HashSet<>();

    public SelectableEventAdapter(List<Event> eventList) {
        this.eventList = eventList;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = eventList.get(position);
        String eventId = event.getEventId();

        holder.tvEventName.setText(event.getEventName());
        holder.tvEventStatus.setText("Upcoming");

        boolean isSelected = eventId != null && selectedEventIds.contains(eventId);
        holder.cbSelectEvent.setOnCheckedChangeListener(null);
        holder.cbSelectEvent.setChecked(isSelected);

        View.OnClickListener toggleSelection = v -> {
            if (eventId == null || eventId.isEmpty()) {
                return;
            }

            boolean newSelectedState = !holder.cbSelectEvent.isChecked();
            holder.cbSelectEvent.setChecked(newSelectedState);
            updateSelection(eventId, newSelectedState);
        };

        holder.itemView.setOnClickListener(toggleSelection);
        holder.cbSelectEvent.setOnCheckedChangeListener((buttonView, checked) -> {
            if (eventId == null || eventId.isEmpty()) {
                return;
            }
            updateSelection(eventId, checked);
        });
    }

    @Override
    public int getItemCount() {
        return eventList.size();
    }

    public List<String> getSelectedEventIds() {
        return new ArrayList<>(selectedEventIds);
    }

    public void clearSelection() {
        selectedEventIds.clear();
        notifyDataSetChanged();
    }

    private void updateSelection(String eventId, boolean selected) {
        if (selected) {
            selectedEventIds.add(eventId);
        } else {
            selectedEventIds.remove(eventId);
        }
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvEventName;
        private final TextView tvEventStatus;
        private final CheckBox cbSelectEvent;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEventName = itemView.findViewById(R.id.tv_event_name);
            tvEventStatus = itemView.findViewById(R.id.tv_event_status);
            cbSelectEvent = itemView.findViewById(R.id.cb_select_event);
        }
    }
}

