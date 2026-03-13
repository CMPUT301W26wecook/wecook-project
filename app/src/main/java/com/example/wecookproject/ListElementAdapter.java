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

import java.util.ArrayList;
import java.util.List;

public class ListElementAdapter<T> extends RecyclerView.Adapter<ListElementAdapter.ListElementViewHolder> {

    public static abstract class OnMenuActionListener<T> {
        public void onShowDetail(T item) {}
        public void onDelete(T item, int position) {}
    }

    private final List<T> itemList;
    private final List<Boolean> selectedList;
    private OnMenuActionListener<T> menuActionListener;
    private final AdminViewModel viewModel;
    private boolean showDetailOption = true;
    private boolean showDeleteOption = true;

    public ListElementAdapter(List<T> itemList, AdminViewModel viewModel) {
        this.itemList = itemList;
        this.viewModel = viewModel;
        this.selectedList = new ArrayList<>();
        for (int i = 0; i < itemList.size(); i++) {
            selectedList.add(false);
        }
    }

    public void setOnMenuActionListener(OnMenuActionListener<T> listener) {
        this.menuActionListener = listener;
    }

    public void setShowDetailOption(boolean showDetailOption) {
        this.showDetailOption = showDetailOption;
    }

    public void setShowDeleteOption(boolean showDeleteOption) {
        this.showDeleteOption = showDeleteOption;
    }

    @NonNull
    @Override
    public ListElementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_list_element, parent, false);
        return new ListElementViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ListElementViewHolder holder, int position) {
        T item = itemList.get(position);
        
        String displayName = "";
        if (item instanceof User) {
            displayName = ((User) item).getName();
        } else if (item instanceof Event) {
            displayName = ((Event) item).getEventName();
        }
        
        holder.tvElementName.setText(displayName);

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

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public List<Boolean> getSelectedList() {
        return selectedList;
    }

    public static class ListElementViewHolder extends RecyclerView.ViewHolder {
        TextView tvElementName;
        CheckBox cbSelectElement;
        ImageButton btnElementMenu;

        public ListElementViewHolder(@NonNull View itemView) {
            super(itemView);
            tvElementName = itemView.findViewById(R.id.tv_element_name);
            cbSelectElement = itemView.findViewById(R.id.cb_select_element);
            btnElementMenu = itemView.findViewById(R.id.btn_element_menu);
        }
    }
}
