package com.example.wecookproject;

import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wecookproject.model.Event;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PublicEventLandingActivity extends AppCompatActivity {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_public_event_landing);

        TextView tvEventName = findViewById(R.id.tv_public_event_name);
        TextView tvEventDescription = findViewById(R.id.tv_public_event_description);
        ImageView ivPoster = findViewById(R.id.iv_public_event_poster);

        String eventId = resolveEventIdFromIntent();
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "Invalid event link", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("events").document(eventId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    String visibilityTag = snapshot.getString("visibilityTag");
                    if (Event.VISIBILITY_PRIVATE.equalsIgnoreCase(visibilityTag)) {
                        Toast.makeText(this, "This event is private", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    String eventName = snapshot.getString("eventName");
                    String description = snapshot.getString("description");
                    Date eventTime = snapshot.getDate("eventTime");
                    String posterUrl = snapshot.getString("posterUrl");
                    if (posterUrl == null || posterUrl.trim().isEmpty()) {
                        posterUrl = snapshot.getString("posterPath");
                    }

                    tvEventName.setText(eventName == null || eventName.trim().isEmpty() ? "Event" : eventName);
                    String resolvedDescription = description == null || description.trim().isEmpty()
                            ? "Event description coming soon."
                            : description;
                    if (eventTime != null) {
                        resolvedDescription = "Event time: " + dateFormat.format(eventTime) + "\n\n" + resolvedDescription;
                    }
                    tvEventDescription.setText(resolvedDescription);
                    PosterLoader.loadInto(ivPoster, posterUrl);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show());
    }

    private String resolveEventIdFromIntent() {
        Uri data = getIntent().getData();
        if (data == null) {
            return null;
        }
        String queryEventId = data.getQueryParameter("eventId");
        if (queryEventId != null && !queryEventId.trim().isEmpty()) {
            return queryEventId.trim();
        }
        return data.getLastPathSegment();
    }
}
