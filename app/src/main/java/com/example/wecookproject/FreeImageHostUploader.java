package com.example.wecookproject;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;

import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Uploads poster images to Freeimage.host using its v1 upload API.
 *
 * <p>Important: reading an API key from Firestore avoids checking it into the APK source, but it
 * does not make the key secret if client devices can read that document. A server-side proxy is
 * still the proper long-term solution.</p>
 */
public final class FreeImageHostUploader {
    private static final String API_URL = "https://freeimage.host/api/1/upload";
    private static final String CONFIG_COLLECTION = "app_config";
    private static final String CONFIG_DOCUMENT = "freeimage_host";
    private static final String CONFIG_FIELD_API_KEY = "apiKey";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private FreeImageHostUploader() {
    }

    /**
     * Uploads the given image URI to Freeimage.host and returns the hosted direct image URL.
     *
     * @param context Android context used to open the image stream
     * @param db Firestore instance used to read the API key
     * @param imageUri selected local image uri
     * @param callback completion callback
     */
    public static void uploadPoster(Context context,
                                    FirebaseFirestore db,
                                    Uri imageUri,
                                    Callback callback) {
        if (imageUri == null) {
            callback.onFailure("Select a poster first");
            return;
        }

        db.collection(CONFIG_COLLECTION)
                .document(CONFIG_DOCUMENT)
                .get()
                .addOnSuccessListener(snapshot -> {
                    String apiKey = snapshot.getString(CONFIG_FIELD_API_KEY);
                    if (TextUtils.isEmpty(apiKey)) {
                        callback.onFailure("Poster upload API key is missing in Firestore");
                        return;
                    }
                    EXECUTOR.execute(() -> uploadPosterInternal(context, imageUri, apiKey, callback));
                })
                .addOnFailureListener(e -> callback.onFailure("Failed to read poster upload settings"));
    }

    private static void uploadPosterInternal(Context context,
                                             Uri imageUri,
                                             String apiKey,
                                             Callback callback) {
        HttpURLConnection connection = null;
        try {
            byte[] imageBytes = readImageBytes(context.getContentResolver(), imageUri);
            String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            String requestBody = buildRequestBody(apiKey, base64Image);

            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Accept", "application/json");

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBody.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }

            int responseCode = connection.getResponseCode();
            String responseBody = readResponseBody(connection, responseCode);
            int apiStatusCode = parseApiStatusCode(responseBody);
            String imageUrl = parseImageUrl(responseBody);
            if (responseCode >= 200 && responseCode < 300
                    && (apiStatusCode == 0 || apiStatusCode == 200)
                    && !TextUtils.isEmpty(imageUrl)) {
                String httpsUrl = imageUrl.replace("http://", "https://");
                postSuccess(callback, httpsUrl);
                return;
            }

            String failureMessage = parseFailureMessage(responseBody);
            if (TextUtils.isEmpty(failureMessage)) {
                failureMessage = "Failed to upload poster";
            }
            postFailure(callback, failureMessage + " (HTTP " + responseCode + ")");
        } catch (IOException e) {
            String message = e.getMessage();
            postFailure(callback, TextUtils.isEmpty(message)
                    ? "Failed to upload poster"
                    : "Failed to upload poster: " + message);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] readImageBytes(ContentResolver contentResolver, Uri imageUri) throws IOException {
        InputStream inputStream = contentResolver.openInputStream(imageUri);
        if (inputStream == null) {
            throw new IOException("Unable to open selected image");
        }

        try (InputStream closableInputStream = inputStream;
             BufferedInputStream bufferedInputStream = new BufferedInputStream(closableInputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int read;
            while ((read = bufferedInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private static String buildRequestBody(String apiKey, String base64Image) throws IOException {
        return "key=" + urlEncode(apiKey)
                + "&action=upload"
                + "&format=json"
                + "&source=" + urlEncode(base64Image);
    }

    private static String urlEncode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static String readResponseBody(HttpURLConnection connection, int responseCode) throws IOException {
        InputStream stream = responseCode >= 200 && responseCode < 400
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) {
            return "";
        }

        try (InputStream inputStream = new BufferedInputStream(stream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String parseImageUrl(String responseBody) {
        try {
            JSONObject jsonObject = new JSONObject(responseBody);
            JSONObject imageObject = jsonObject.optJSONObject("image");
            if (imageObject == null) {
                return null;
            }

            String directUrl = imageObject.optString("url", "");
            if (!TextUtils.isEmpty(directUrl)) {
                return directUrl;
            }
            return imageObject.optString("display_url", "");
        } catch (JSONException ignored) {
            return null;
        }
    }

    private static String parseFailureMessage(String responseBody) {
        try {
            JSONObject jsonObject = new JSONObject(responseBody);
            JSONObject errorObject = jsonObject.optJSONObject("error");
            if (errorObject != null) {
                return errorObject.optString("message", "");
            }
            JSONObject successObject = jsonObject.optJSONObject("success");
            if (successObject != null) {
                return successObject.optString("message", "");
            }
            return jsonObject.optString("status_txt", "");
        } catch (JSONException ignored) {
            return "";
        }
    }

    private static int parseApiStatusCode(String responseBody) {
        try {
            JSONObject jsonObject = new JSONObject(responseBody);
            return jsonObject.optInt("status_code", 0);
        } catch (JSONException ignored) {
            return 0;
        }
    }

    private static void postSuccess(Callback callback, String imageUrl) {
        MAIN_HANDLER.post(() -> callback.onSuccess(imageUrl));
    }

    private static void postFailure(Callback callback, String message) {
        MAIN_HANDLER.post(() -> callback.onFailure(message));
    }

    /**
     * Callback used to report poster upload completion.
     */
    public interface Callback {
        /**
         * Called when poster upload succeeds.
         *
         * @param imageUrl hosted poster url
         */
        void onSuccess(String imageUrl);

        /**
         * Called when poster upload fails.
         *
         * @param message user-displayable failure message
         */
        void onFailure(String message);
    }
}
