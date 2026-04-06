package com.example.wecookproject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wecookproject.model.Event;
import com.example.wecookproject.model.User;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generic adapter for Admin lists (Users/Events). Supports checkbox selection and action menus.
 *
 * @param <T> The type of the items in the list (e.g., User or Event).
 */
public class ListElementAdapter<T> extends RecyclerView.Adapter<ListElementAdapter.ListElementViewHolder> {

    /**
     * Handles menu actions (Show Detail, Delete).
     * @param <T> The type of the item being acted upon.
     */
    public static abstract class OnMenuActionListener<T> {
        /**
         * Called when the "Show Detail" action is selected.
         * @param item The selected item.
         */
        public void onShowDetail(T item) {}
        
        /**
         * Called when the "Delete" action is selected.
         * @param item The selected item.
         * @param position The position of the item in the list.
         */
        public void onDelete(T item, int position) {}
    }

    private final List<T> itemList;
    private final List<Boolean> selectedList;
    private OnMenuActionListener<T> menuActionListener;
    private final AdminViewModel viewModel;
    private boolean showDetailOption = true;
    private boolean showDeleteOption = true;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());

    /**
     * Constructs a new ListElementAdapter.
     * 
     * @param itemList The list of items to be displayed.
     * @param viewModel The AdminViewModel for managing shared state.
     */
    public ListElementAdapter(List<T> itemList, AdminViewModel viewModel) {
        this.itemList = itemList;
        this.viewModel = viewModel;
        this.selectedList = new ArrayList<>();
        for (int i = 0; i < itemList.size(); i++) {
            selectedList.add(false);
        }
    }

    /**
     * Sets the listener for menu actions.
     * @param listener The listener to set.
     */
    public void setOnMenuActionListener(OnMenuActionListener<T> listener) {
        this.menuActionListener = listener;
    }

    /**
     * Enables or disables the "Show Detail" option in the popup menu.
     * @param showDetailOption True to show the option, false to hide it.
     */
    public void setShowDetailOption(boolean showDetailOption) {
        this.showDetailOption = showDetailOption;
    }

    /**
     * Enables or disables the "Delete" option in the popup menu.
     * @param showDeleteOption True to show the option, false to hide it.
     */
    public void setShowDeleteOption(boolean showDeleteOption) {
        this.showDeleteOption = showDeleteOption;
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
    public ListElementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_list_element, parent, false);
        return new ListElementViewHolder(view);
    }

    /**
     * Binds the data to the ViewHolder at the specified position.
     * Sets display name, checkbox state, and menu click listeners.
     *
     * @param holder The ViewHolder to update.
     * @param position The position of the item in the list.
     */
    @Override
    public void onBindViewHolder(@NonNull ListElementViewHolder holder, int position) {
        T item = itemList.get(position);
        
        String displayName = "";
        String detailMessage = "";
        if (item instanceof User) {
            displayName = ((User) item).getName();
        } else if (item instanceof Event) {
            Event event = (Event) item;
            displayName = event.getEventName();

            holder.tvDetailMessage.setTypeface(android.graphics.Typeface.MONOSPACE);
            String start = event.getRegistrationStartDate() != null ? dateFormat.format(event.getRegistrationStartDate()) : "N/A";
            String end = event.getRegistrationEndDate() != null ? dateFormat.format(event.getRegistrationEndDate()) : "N/A";
            detailMessage = String.format("From: %s\n" +
                                          "To:   %s", start, end);
        }
        
        holder.tvElementName.setText(displayName);
        holder.tvDetailMessage.setText(detailMessage);
        holder.tvDetailMessage.setVisibility(detailMessage.isEmpty() ? View.GONE : View.VISIBLE);

        holder.cbSelectElement.setOnCheckedChangeListener(null);
        holder.cbSelectElement.setChecked(selectedList.get(holder.getBindingAdapterPosition()));

        holder.cbSelectElement.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                selectedList.set(currentPos, isChecked);
            }
        });

        holder.btnElementMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            if (showDetailOption) {
                popup.getMenu().add("Show Detail");
            }
            if (showDeleteOption) {
                popup.getMenu().add("Delete");
            }
            
            popup.setOnMenuItemClickListener(menuItem -> {
                if (menuActionListener != null) {
                    if (menuItem.getTitle().equals("Show Detail")) {
                        if (viewModel != null) {
                            viewModel.selectItem(item);
                        }
                        menuActionListener.onShowDetail(item);
                    } else if (menuItem.getTitle().equals("Delete")) {
                        menuActionListener.onDelete(item, holder.getBindingAdapterPosition());
                    }
                }
                return true;
            });
            popup.show();
        });

        holder.itemView.setOnClickListener(null);
    }

    /**
     * Returns the total number of items in the data set.
     *
     * @return The number of items in the list.
     */
    @Override
    public int getItemCount() {
        return itemList.size();
    }

    /**
     * Returns the list representing the selection status of items.
     *
     * @return A list of Booleans.
     */
    public List<Boolean> getSelectedList() {
        return selectedList;
    }

    /**
     * ViewHolder class for the ListElementAdapter.
     * Holds references to the UI components for each list item.
     */
    public static class ListElementViewHolder extends RecyclerView.ViewHolder {
        TextView tvElementName;
        TextView tvDetailMessage;
        CheckBox cbSelectElement;
        ImageButton btnElementMenu;

        /**
         * Constructs a new ViewHolder.
         * @param itemView The view representing an individual list item.
         */
        public ListElementViewHolder(@NonNull View itemView) {
            super(itemView);
            tvElementName = itemView.findViewById(R.id.tv_element_name);
            tvDetailMessage = itemView.findViewById(R.id.tv_element_detail);
            cbSelectElement = itemView.findViewById(R.id.cb_select_element);
            btnElementMenu = itemView.findViewById(R.id.btn_element_menu);
        }
    }
}
