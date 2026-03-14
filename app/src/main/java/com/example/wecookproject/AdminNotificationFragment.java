package com.example.wecookproject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Not implemented
 */

public class AdminNotificationFragment extends Fragment {
    /**
     * Inflates the admin notification list fragment view.
     *
     * @param inflater layout inflater
     * @param container parent view container
     * @param savedInstanceState saved fragment state
     * @return inflated fragment root view
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_notification_list, container, false);
        return view;
    }
}
