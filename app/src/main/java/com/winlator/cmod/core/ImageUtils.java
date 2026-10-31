package com.winlator.cmod.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.IntRange;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public abstract class ImageUtils {
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height <= 0 || width <= 0 || reqHeight <= 0 || reqWidth <= 0) return inSampleSize;
        while (height / inSampleSize > reqHeight || width / inSampleSize > reqWidth) {
            if (inSampleSize > Integer.MAX_VALUE / 2) break;
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int maxSize) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height <= 0 || width <= 0 || maxSize <= 0) return inSampleSize;
        while (Math.max(width / inSampleSize, height / inSampleSize) > maxSize) {
            if (inSampleSize > Integer.MAX_VALUE / 2) break;
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    public static Bitmap getBitmapFromUri(Context context, Uri uri, BitmapFactory.Options options) {
        if (uri == null) return null;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                Log.e("ImageUtils", "Unable to open image URI: " + uri);
                return null;
            }
            return options != null
                    ? BitmapFactory.decodeStream(input, null, options)
                    : BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            Log.e("ImageUtils", "Unable to decode image URI: " + uri, e);
            return null;
        }
    }

    public static Bitmap getBitmapFromUri(Context context, Uri uri) {
        return getBitmapFromUri(context, uri, null);
    }

    public static Bitmap getBitmapFromUri(Context context, Uri uri, int maxSize) {
        if (uri == null) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                Log.e("ImageUtils", "Unable to open image URI: " + uri);
                return null;
            }
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, options);
        } catch (IOException e) {
            Log.e("ImageUtils", "Unable to read image bounds: " + uri, e);
            return null;
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            Log.e("ImageUtils", "Image has invalid dimensions: " + uri);
            return null;
        }
        options.inSampleSize = calculateInSampleSize(options, maxSize);
        options.inJustDecodeBounds = false;
        return getBitmapFromUri(context, uri, options);
    }

    public static boolean save(Bitmap bitmap, File output, Bitmap.CompressFormat compressFormat, @IntRange(from = 0, to = 100) int quality) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(output);
            return bitmap.compress(compressFormat, quality, fos);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                if (fos != null) {
                    fos.flush();
                    fos.close();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean isPNGData(java.nio.ByteBuffer data) {
        int position = data.position();
        if (Byte.toUnsignedInt(data.get(position + 0)) != 137 || 
            data.get(position + 1) != 80 || 
            data.get(position + 2) != 78 || 
            data.get(position + 3) != 71) {
            return false;
        }
        return true;
    }
}
