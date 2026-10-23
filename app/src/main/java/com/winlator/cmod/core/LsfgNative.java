package com.winlator.cmod.core;

import android.content.Context;

import java.io.File;

public final class LsfgNative {
    public static final int STATUS_OK = 0;
    public static final int STATUS_NOT_INSTALLED = 1;
    public static final int STATUS_CACHE_UNUSABLE = 6;

    static {
        System.loadLibrary("vulkan_renderer");
    }

    private LsfgNative() {}

    private static native int nativeValidateDll(String dllPath);
    private static native int nativeDllVariant(String dllPath, boolean preferFp16);
    private static native int nativeBuildCache(String dllPath, String cachePath, boolean preferFp16);
    private static native boolean nativeCacheMatchesSource(String cachePath, String dllPath);
    private static native int nativeCacheVariant(String cachePath);
    private static native String nativeStatusName(int status);
    private static native String nativeVariantName(int variant);

    public static File dllFile(Context context) {
        return new File(context.getFilesDir(), "lsfg-vk/Lossless.dll");
    }

    public static File cacheFile(Context context) {
        return new File(context.getFilesDir(), "lsfg-vk/lsfg-native.spvcache");
    }

    public static boolean isDllAvailable(Context context) {
        return dllFile(context).isFile();
    }

    public static int validateDll(Context context) {
        return validateDll(dllFile(context));
    }

    public static int validateDll(File dll) {
        return dll.isFile() ? nativeValidateDll(dll.getAbsolutePath()) : STATUS_NOT_INSTALLED;
    }

    public static int ensureCache(Context context, boolean preferFp16) {
        File dll = dllFile(context);
        if (!dll.isFile()) return STATUS_NOT_INSTALLED;
        File cache = cacheFile(context);
        if (cache.isFile() && nativeCacheMatchesSource(cache.getAbsolutePath(), dll.getAbsolutePath())) {
            return STATUS_OK;
        }
        File parent = cache.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory())) {
            return STATUS_CACHE_UNUSABLE;
        }
        return nativeBuildCache(dll.getAbsolutePath(), cache.getAbsolutePath(), preferFp16);
    }

    public static boolean isCacheValid(Context context) {
        File dll = dllFile(context);
        File cache = cacheFile(context);
        return dll.isFile() && cache.isFile()
                && nativeCacheMatchesSource(cache.getAbsolutePath(), dll.getAbsolutePath());
    }

    public static String explain(int status) {
        return nativeStatusName(status);
    }
}
