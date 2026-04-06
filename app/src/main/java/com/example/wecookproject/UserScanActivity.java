package com.example.wecookproject;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity for scanning QR codes using the device camera or by selecting an image from the album.
 */
public class UserScanActivity extends AppCompatActivity {
    private static final int PERMISSION_CAMERA_REQUEST = 101;
    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private BottomNavigationView bottomNav;
    private Button btnSelectPhoto;

    /**
     * Launcher for selecting an image from the device's album.
     */
    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    scanImageFromUri(uri);
                }
            }
    );

    /**
     * Initializes the activity, sets up the camera preview, and bottom navigation.
     * 
     * @param savedInstanceState Saved state of the activity.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_scan);

        previewView = findViewById(R.id.previewView);
        bottomNav = findViewById(R.id.bottom_nav);
        btnSelectPhoto = findViewById(R.id.btn_select_photo);
        cameraExecutor = Executors.newSingleThreadExecutor();

        setupBottomNav();

        btnSelectPhoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA_REQUEST);
        }
    }

    /**
     * Configures entrant bottom-navigation actions.
     */
    private void setupBottomNav() {
        bottomNav.setSelectedItemId(R.id.nav_scan);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_scan) {
                return true;
            } else if (itemId == R.id.nav_events) {
                Intent intent = new Intent(UserScanActivity.this, UserEventActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
                return true;
            } else if (itemId == R.id.nav_history) {
                Intent intent = new Intent(UserScanActivity.this, UserHistoryActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
                return true;
            } else if (itemId == R.id.nav_profile) {
                Intent intent = new Intent(UserScanActivity.this, UserProfileActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
                return true;
            }

            return false;
        });
    }

    /**
     * Initializes the CameraX process camera provider and binds use cases.
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (Exception e) {
                Log.e("UserScanActivity", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Binds the camera preview and image analysis use cases to the activity lifecycle.
     * 
     * @param cameraProvider The ProcessCameraProvider instance.
     */
    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, new QrCodeAnalyzer(result -> {
            runOnUiThread(() -> {
                handleScanResult(result);
            });
        }));

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e("UserScanActivity", "Use case binding failed", e);
        }
    }

    /**
     * Scans an image selected from the album for a QR code.
     * 
     * @param uri The URI of the selected image.
     */
    private void scanImageFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                return;
            }

            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = new MultiFormatReader().decode(binaryBitmap);

            handleScanResult(result.getText());
        } catch (Exception e) {
            Log.e("UserScanActivity", "Error scanning image from album", e);
            Toast.makeText(this, "No QR code found in the selected image", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Handles the result of a successful QR code scan.
     * If valid, navigates to UserEventDetailsActivity.
     * 
     * @param result The decoded text from the QR code.
     */
    private void handleScanResult(String result) {
        if (result != null && result.startsWith("https://wecook.app/event/")) {
            String eventId = result.substring("https://wecook.app/event/".length());
            if (!eventId.isEmpty()) {
                Intent intent = new Intent(this, UserEventDetailsActivity.class);
                intent.putExtra("eventId", eventId);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Invalid event link", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Handles the result of camera permission requests.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CAMERA_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    /**
     * Shuts down the camera executor when the activity is destroyed.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
