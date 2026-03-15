package com.example.wecookproject;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public final class QrCodeUtils {
    private static final String PROMO_BASE_URL = "https://wecook.app/event/";

    private QrCodeUtils() {
    }

    public static String buildPromotionalEventLink(String eventId) {
        return PROMO_BASE_URL + eventId;
    }

    public static Bitmap generateQrBitmap(String content, int sizePx) throws WriterException {
        BitMatrix bitMatrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < sizePx; x++) {
            for (int y = 0; y < sizePx; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }
}
