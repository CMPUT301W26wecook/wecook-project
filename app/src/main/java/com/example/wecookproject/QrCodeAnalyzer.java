package com.example.wecookproject;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;

/**
 * Analyzer class for processing camera frames and searching for QR codes using ZXing.
 */
public class QrCodeAnalyzer implements ImageAnalysis.Analyzer {
    private final ScanCallback callback;
    private final MultiFormatReader reader;
    private boolean isScanning = true;

    /**
     * Interface for communicating scan results back to the activity.
     */
    public interface ScanCallback {
        void onScan(String result);
    }

    public QrCodeAnalyzer(ScanCallback callback) {
        this.callback = callback;
        this.reader = new MultiFormatReader();
    }

    /**
     * Analyzes each image frame from the camera.
     *
     * @param image The image proxy from CameraX.
     */
    @Override
    public void analyze(@NonNull ImageProxy image) {
        if (!isScanning) {
            image.close();
            return;
        }

        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        int width = image.getWidth();
        int height = image.getHeight();

        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                data, width, height, 0, 0, width, height, false);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        try {
            Result result = reader.decodeWithState(bitmap);
            isScanning = false;
            callback.onScan(result.getText());
        } catch (Exception e) {
            // No QR code found in this frame
        } finally {
            image.close();
        }
    }
}
