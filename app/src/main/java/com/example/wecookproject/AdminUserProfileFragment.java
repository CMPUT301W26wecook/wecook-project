package com.example.wecookproject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.wecookproject.model.User;
import com.google.firebase.firestore.FirebaseFirestore;

public class AdminUserProfileFragment extends Fragment {
    
    private User user;
    private AdminViewModel viewModel;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_user_profile, container, false);

        TextView tvFirstName = view.findViewById(R.id.tv_first_name);
        TextView tvLastName = view.findViewById(R.id.tv_last_name);
        TextView tvDob = view.findViewById(R.id.tv_dob);
        TextView tvAddress1 = view.findViewById(R.id.tv_address1);
        TextView tvAddress2 = view.findViewById(R.id.tv_address2);
        TextView tvCity = view.findViewById(R.id.tv_city);
        TextView tvPostalCode = view.findViewById(R.id.tv_postal_code);
        TextView tvCountry = view.findViewById(R.id.tv_country);

        viewModel.getSelectedUser().observe(getViewLifecycleOwner(), selectedUser -> {
            if (selectedUser != null) {
                this.user = selectedUser;
                tvFirstName.setText("First Name: " + (user.getFirstName() != null ? user.getFirstName() : ""));
                tvLastName.setText("Last Name: " + (user.getLastName() != null ? user.getLastName() : ""));
                tvDob.setText("Birthday: " + (user.getBirthday() != null ? user.getBirthday() : ""));
                tvAddress1.setText("Address Line 1: " + (user.getAddressLine1() != null ? user.getAddressLine1() : ""));
                tvAddress2.setText("Address Line 2: " + (user.getAddressLine2() != null ? user.getAddressLine2() : ""));
                tvCity.setText("City: " + (user.getCity() != null ? user.getCity() : ""));
                tvPostalCode.setText("Postal Code: " + (user.getPostalCode() != null ? user.getPostalCode() : ""));
                tvCountry.setText("Country: " + (user.getCountry() != null ? user.getCountry() : ""));
            }
        });

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            getParentFragmentManager().popBackStack();
        });
        
        view.findViewById(R.id.btn_delete_account).setOnClickListener(v -> {
            if (user != null) {
                user.clearProfile();

                tvFirstName.setText("First Name: Deleted");
                tvLastName.setText("Last Name: User");
                tvDob.setText("Birthday: ");
                tvAddress1.setText("Address Line 1: ");
                tvAddress2.setText("Address Line 2: ");
                tvCity.setText("City: ");
                tvPostalCode.setText("Postal Code: ");
                tvCountry.setText("Country: ");

                db.collection("users").document(user.getAndroidId())
                        .set(user.toFirestoreMap())
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(getContext(), "User profile info cleared", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Error clearing profile", Toast.LENGTH_SHORT).show());
            }
        });

        return view;
    }
}
