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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * A Fragment that displays the profile of a specific User for administrative purposes.
 */
public class AdminUserProfileFragment extends Fragment {
    
    private User user;
    private AdminViewModel viewModel;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Initializes the fragment and retrieves the shared AdminViewModel.
     * 
     * @param savedInstanceState Saved state of the fragment
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);
    }

    /**
     * Show User Profile UI and handles Admin interactions for deleting the account.
     *
     * @param inflater           Parent view to which the fragment's UI should be attached.
     * @param container          Parent view for the fragment's UI.
     * @param savedInstanceState Saved state of the fragment.
     * @return The View for the User Profile UI.
     */
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
                removeRoleFromUser(user.getAndroidId(), UserDocumentUtils.ROLE_ENTRANT, "User account deleted");
            }
        });

        return view;
    }

    /**
     * Removes a specific role from a user document in Firestore.
     * If the user only has the specified role, the entire document is deleted.
     * Otherwise, only the specified role is removed from the roles map.
     * After operation, it navigates back to AdminUserFragment.
     *
     * @param userId         The unique Android ID of the user.
     * @param role           The role string to remove (e.g., "entrant").
     * @param successMessage Message to display in a Toast upon successful deletion.
     */
    private void removeRoleFromUser(String userId, String role, String successMessage) {
        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        getParentFragmentManager().popBackStack();
                        return;
                    }

                    if (UserDocumentUtils.getRoleCount(snapshot) <= 1) {
                        db.collection("users")
                                .document(userId)
                                .delete()
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(getContext(), successMessage, Toast.LENGTH_SHORT).show();
                                    getParentFragmentManager().popBackStack();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(getContext(), "Error deleting account", Toast.LENGTH_SHORT).show());
                        return;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("roles." + role, FieldValue.delete());
                    if (UserDocumentUtils.ROLE_ENTRANT.equals(UserDocumentUtils.getSafeTrimmedString(snapshot, "role"))
                            && UserDocumentUtils.hasRole(snapshot, UserDocumentUtils.ROLE_ORGANIZER)) {
                        updates.put("role", UserDocumentUtils.ROLE_ORGANIZER);
                    }
                    db.collection("users")
                            .document(userId)
                            .update(updates)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(getContext(), successMessage, Toast.LENGTH_SHORT).show();
                                getParentFragmentManager().popBackStack();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(getContext(), "Error deleting account", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error deleting account", Toast.LENGTH_SHORT).show());
    }
}
