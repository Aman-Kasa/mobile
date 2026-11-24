package com.burdew.app.ui.requests;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.burdew.app.config.AppConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Receipt photos live in app-private storage (files/receipts/), never inside SQLite.
 * After capture they are downscaled and re-compressed so cheap phones don't fill up.
 */
public final class ReceiptPhotos {
    private ReceiptPhotos() {}

    public static File newFile(Context c, String transactionId) {
        File dir = new File(c.getFilesDir(), AppConfig.RECEIPTS_DIR);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, transactionId + "_" + System.currentTimeMillis() + ".jpg");
    }

    public static Uri uriFor(Context c, File f) {
        return FileProvider.getUriForFile(c, c.getPackageName() + ".files", f);
    }

    /** Downscale to AppConfig.RECEIPT_MAX_EDGE_PX, apply EXIF rotation, re-encode as JPEG. Off main thread. */
    public static boolean compress(File f) {
        if (!f.exists() || f.length() == 0) return false;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
        int edge = Math.max(bounds.outWidth, bounds.outHeight);
        if (edge <= 0) return false;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 1;
        while (edge / (o.inSampleSize * 2) >= AppConfig.RECEIPT_MAX_EDGE_PX) o.inSampleSize *= 2;
        Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (bmp == null) return false;
        try {
            int rotation = 0;
            switch (new ExifInterface(f.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                case ExifInterface.ORIENTATION_ROTATE_90: rotation = 90; break;
                case ExifInterface.ORIENTATION_ROTATE_180: rotation = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: rotation = 270; break;
            }
            float scale = Math.min(1f, (float) AppConfig.RECEIPT_MAX_EDGE_PX / Math.max(bmp.getWidth(), bmp.getHeight()));
            Matrix m = new Matrix();
            m.postScale(scale, scale);
            m.postRotate(rotation);
            Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                out.compress(Bitmap.CompressFormat.JPEG, AppConfig.RECEIPT_JPEG_QUALITY, fos);
            }
            if (out != bmp) out.recycle();
            bmp.recycle();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
