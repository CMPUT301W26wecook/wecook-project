package com.example.wecookproject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wecookproject.model.Event;

import java.util.List;

/**
 * RecyclerView adapter for organizer event cards.
 */
public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private List<Event> eventList;
    private OnItemClickListener listener;

    /**
     * Callback for event row taps.
     */
    public interface OnItemClickListener {
        /**
         * Called when an event row is clicked.
         *
         * @param eventId selected event id
         */
        void onItemClick(String eventId);
    }

    /**
     * Creates an event adapter.
     *
     * @param eventList list of events to render
     * @param listener click callback
     */
    public EventAdapter(List<Event> eventList, OnItemClickListener listener) {
        this.eventList = eventList;
        this.listener = listener;
    }

    /**
     * Inflates one event row.
     *
     * @param parent parent RecyclerView
     * @param viewType view type id
     * @return created view holder
     */
    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_organizer_event, parent, false);
        return new EventViewHolder(view);
    }

    /**
     * Binds event data at position.
     *
     * @param holder row holder
     * @param position adapter position
     */
    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = eventList.get(position);
        holder.tvEventName.setText(event.getEventName());
        holder.tvEventVisibility.setText(
                Event.VISIBILITY_PRIVATE.equals(event.getVisibilityTag()) ? "Private" : "Public"
        );
        // For now, hardcode status to "Upcoming", as indicated in layout.
        holder.tvEventStatus.setText("Upcoming");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(event.getEventId());
            }
        });
    }

    /**
     * @return number of event rows
     */
    @Override
    public int getItemCount() {
        return eventList.size();
    }

    /**
     * ViewHolder for event rows.
     */
    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView tvEventName;
        TextView tvEventVisibility;
        TextView tvEventStatus;

        /**
         * Creates a view holder and binds row views.
         *
         * @param itemView row root view
         */
        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEventName = itemView.findViewById(R.id.tv_event_name);
            tvEventVisibility = itemView.findViewById(R.id.tv_event_visibility);
            tvEventStatus = itemView.findViewById(R.id.tv_event_status);
        }
    }
}
