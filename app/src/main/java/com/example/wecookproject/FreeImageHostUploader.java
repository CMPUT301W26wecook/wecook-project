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
     * Uploads the given image URI to Freeimage.host and returns hosted poster metadata.
     *
     * @param context Android context used to open the image stream
     * @param db Firestore instance used to read the API key
     * @param imageUri selected local image uri
     * @param callback completion callback
     */
    public static void uploadPoster(Context context,
                                    FirebaseFirestore db,
                                    Uri imageUri,
                                    UploadCallback callback) {
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
                                             UploadCallback callback) {
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
            UploadResult uploadResult = parseUploadResult(responseBody);
            String imageUrl = uploadResult.getImageUrl();
            if (responseCode >= 200 && responseCode < 300
                    && (apiStatusCode == 0 || apiStatusCode == 200)
                    && !TextUtils.isEmpty(imageUrl)) {
                postSuccess(callback, uploadResult.withHttpsUrls());
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

    private static UploadResult parseUploadResult(String responseBody) {
        try {
            JSONObject jsonObject = new JSONObject(responseBody);
            JSONObject imageObject = jsonObject.optJSONObject("image");
            if (imageObject == null) {
                return UploadResult.empty();
            }

            String directUrl = imageObject.optString("url", "");
            String imageUrl = !TextUtils.isEmpty(directUrl)
                    ? directUrl
                    : imageObject.optString("display_url", "");

            String deleteUrl = firstNonBlank(
                    imageObject.optString("delete_url", ""),
                    imageObject.optString("url_delete", ""),
                    jsonObject.optString("delete_url", ""),
                    jsonObject.optString("url_delete", "")
            );

            String viewerUrl = firstNonBlank(
                    imageObject.optString("url_viewer", ""),
                    jsonObject.optString("url_viewer", "")
            );

            return new UploadResult(imageUrl, deleteUrl, viewerUrl);
        } catch (JSONException ignored) {
            return UploadResult.empty();
        }
    }

    /**
     * Deletes a previously uploaded poster using a hosted deletion URL when available.
     *
     * @param deleteUrl hosted deletion URL
     * @param callback completion callback
     */
    public static void deletePoster(String deleteUrl, DeletionCallback callback) {
        if (TextUtils.isEmpty(deleteUrl)) {
            postDeletionSuccess(callback);
            return;
        }

        EXECUTOR.execute(() -> deletePosterInternal(deleteUrl.trim(), callback));
    }

    private static void deletePosterInternal(String deleteUrl, DeletionCallback callback) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(deleteUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json,text/html,*/*");

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 400) {
                postDeletionSuccess(callback);
                return;
            }

            postDeletionFailure(callback, "Failed to delete poster");
        } catch (IOException e) {
            String message = e.getMessage();
            postDeletionFailure(callback, TextUtils.isEmpty(message)
                    ? "Failed to delete poster"
                    : "Failed to delete poster: " + message);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
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

    private static void postSuccess(UploadCallback callback, UploadResult uploadResult) {
        MAIN_HANDLER.post(() -> callback.onSuccess(uploadResult));
    }

    private static void postFailure(UploadCallback callback, String message) {
        MAIN_HANDLER.post(() -> callback.onFailure(message));
    }

    private static void postDeletionSuccess(DeletionCallback callback) {
        if (callback == null) {
            return;
        }
        MAIN_HANDLER.post(callback::onSuccess);
    }

    private static void postDeletionFailure(DeletionCallback callback, String message) {
        if (callback == null) {
            return;
        }
        MAIN_HANDLER.post(() -> callback.onFailure(message));
    }

    /**
     * Immutable upload result containing hosted poster metadata.
     */
    public static final class UploadResult {
        private final String imageUrl;
        private final String deleteUrl;
        private final String viewerUrl;

        private UploadResult(String imageUrl, String deleteUrl, String viewerUrl) {
            this.imageUrl = imageUrl;
            this.deleteUrl = deleteUrl;
            this.viewerUrl = viewerUrl;
        }

        public static UploadResult empty() {
            return new UploadResult("", "", "");
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public String getDeleteUrl() {
            return deleteUrl;
        }

        public String getViewerUrl() {
            return viewerUrl;
        }

        public UploadResult withHttpsUrls() {
            return new UploadResult(
                    toHttps(imageUrl),
                    toHttps(deleteUrl),
                    toHttps(viewerUrl)
            );
        }

        private static String toHttps(String value) {
            return TextUtils.isEmpty(value) ? "" : value.replace("http://", "https://");
        }
    }

    /**
     * Callback used to report poster upload completion.
     */
    public interface UploadCallback {
        /**
         * Called when poster upload succeeds.
         *
         * @param uploadResult hosted poster metadata
         */
        void onSuccess(UploadResult uploadResult);

        /**
         * Called when poster upload fails.
         *
         * @param message user-displayable failure message
         */
        void onFailure(String message);
    }

    /**
     * Callback used to report poster deletion completion.
     */
    public interface DeletionCallback {
        /**
         * Called when poster deletion succeeds or is unnecessary.
         */
        void onSuccess();

        /**
         * Called when hosted poster deletion fails.
         *
         * @param message user-displayable failure message
         */
        void onFailure(String message);
    }
}
