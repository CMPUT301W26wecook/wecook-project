package com.example.wecookproject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight async image loader used for event posters.
 */
public final class PosterLoader {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /**
     * Utility class constructor.
     */
    private PosterLoader() {
    }

    /**
     * Loads a remote poster image into an {@link ImageView}.
     *
     * <p>If the path is empty or invalid, the placeholder remains.</p>
     *
     * @param imageView target image view
     * @param posterPath poster URL/path
     */
    public static void loadInto(ImageView imageView, String posterPath) {
        imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        imageView.setTag(posterPath);

        if (posterPath == null || posterPath.trim().isEmpty()) {
            return;
        }

        if (!posterPath.startsWith("http://") && !posterPath.startsWith("https://")) {
            return;
        }

        EXECUTOR.execute(() -> {
            try (InputStream inputStream = new URL(posterPath).openStream()) {
                byte[] imageBytes = readAllBytes(inputStream);
                Bitmap bitmap = decodeSampledBitmap(imageBytes, imageView.getWidth(), imageView.getHeight());
                if (bitmap == null) {
                    return;
                }
                MAIN_HANDLER.post(() -> {
                    Object tag = imageView.getTag();
                    if (tag instanceof String && posterPath.equals(tag)) {
                        imageView.setPadding(0, 0, 0, 0);
                        imageView.setAdjustViewBounds(true);
                        imageView.setImageBitmap(bitmap);
                    }
                });
            } catch (Exception ignored) {
                // Keep placeholder if loading fails.
            }
        });
    }

    private static byte[] readAllBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private static Bitmap decodeSampledBitmap(byte[] imageBytes, int requestedWidth, int requestedHeight) {
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, boundsOptions);

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                requestedWidth,
                requestedHeight
        );
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, decodeOptions);
    }

    private static int calculateInSampleSize(int width, int height, int requestedWidth, int requestedHeight) {
        if (requestedWidth <= 0 || requestedHeight <= 0) {
            return 1;
        }

        int inSampleSize = 1;
        int halfWidth = width / 2;
        int halfHeight = height / 2;
        while ((halfWidth / inSampleSize) >= requestedWidth && (halfHeight / inSampleSize) >= requestedHeight) {
            inSampleSize *= 2;
        }
        return Math.max(inSampleSize, 1);
    }
}
