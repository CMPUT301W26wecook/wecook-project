package com.example.wecookproject;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Safe readers for loosely-typed Firestore fields.
 */
public final class FirestoreFieldUtils {

    private FirestoreFieldUtils() {
    }

    /**
     * Reads a field as a list of strings and returns a defensive copy.
     *
     * @param snapshot source document
     * @param field field name
     * @return string list (never {@code null})
     */
    public static List<String> getStringList(DocumentSnapshot snapshot, String field) {
        Object raw = snapshot.get(field);
        if (!(raw instanceof List<?>)) {
            return new ArrayList<>();
        }

        List<String> values = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (item instanceof String) {
                values.add((String) item);
            }
        }
        return values;
    }
}
