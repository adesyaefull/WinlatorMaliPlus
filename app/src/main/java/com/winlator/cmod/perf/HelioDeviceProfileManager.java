package com.winlator.cmod.perf;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import com.winlator.cmod.core.GPUInformation;

import java.util.Locale;

/** Conservative runtime profiles for MediaTek Helio G99/G100-class devices. */
public final class HelioDeviceProfileManager {
    public static final String PREF_ENABLED = "helio_profile_enabled";
    public static final String PREF_PROFILE = "helio_profile_mode";
    public static final String PROFILE_STABLE = "stable";
    public static final String PROFILE_BALANCED = "balanced";
    public static final String PROFILE_PERFORMANCE = "performance";

    private HelioDeviceProfileManager() {}

    public static final class DeviceInfo {
        public final boolean supported;
        public final String chipsetLabel;
        public final String socModel;
        public final String gpuRenderer;

        private DeviceInfo(boolean supported, String chipsetLabel,
                           String socModel, String gpuRenderer) {
            this.supported = supported;
            this.chipsetLabel = chipsetLabel;
            this.socModel = socModel;
            this.gpuRenderer = gpuRenderer;
        }
    }

    public static DeviceInfo detect(Context context) {
        String socManufacturer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? safe(Build.SOC_MANUFACTURER) : "";
        String socModel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? safe(Build.SOC_MODEL) : "";
        String hardware = safe(Build.HARDWARE);
        String board = safe(Build.BOARD);
        String fingerprint = safe(Build.FINGERPRINT);
        String device = safe(Build.DEVICE);
        String model = safe(Build.MODEL);
        String renderer = "";
        try {
            if (context != null) renderer = safe(GPUInformation.getRenderer("System", context));
        } catch (Throwable ignored) {
            // Renderer metadata may be unavailable until EGL initializes.
        }

        String signature = (socManufacturer + " " + socModel + " " + hardware + " "
                + board + " " + fingerprint + " " + device + " " + model + " " + renderer)
                .toUpperCase(Locale.ROOT);
        boolean explicit = signature.contains("HELIO G99") || signature.contains("HELIO G100");
        boolean mediaTek = signature.contains("MEDIATEK") || signature.contains("MTK")
                || signature.contains("MT6789") || signature.contains("MT8781");
        boolean maliG57 = signature.contains("MALI-G57");
        boolean supported = explicit || (mediaTek && (maliG57
                || signature.contains("MT6789") || signature.contains("MT8781")));

        String label;
        if (explicit) {
            label = signature.contains("HELIO G100") ? "MediaTek Helio G100" : "MediaTek Helio G99";
        } else if (supported && maliG57) {
            label = "MediaTek + Mali-G57 (G99/G100 profile)";
        } else if (supported) {
            label = "MediaTek G99/G100-compatible platform";
        } else if (mediaTek) {
            label = "MediaTek device (GPU not confirmed)";
        } else {
            label = "Target chipset not detected";
        }
        return new DeviceInfo(supported, label, socModel.isEmpty() ? hardware : socModel,
                renderer.isEmpty() ? "Not reported by system" : renderer);
    }

    public static boolean isSupportedDevice(Context context) {
        return detect(context).supported;
    }

    public static boolean isProfileEnabled(Context context, SharedPreferences preferences) {
        return context != null && preferences != null && isSupportedDevice(context)
                && preferences.getBoolean(PREF_ENABLED, true);
    }

    public static String getSelectedProfile(SharedPreferences preferences) {
        String profile = preferences == null ? PROFILE_STABLE
                : preferences.getString(PREF_PROFILE, PROFILE_STABLE);
        if (PROFILE_BALANCED.equals(profile) || PROFILE_PERFORMANCE.equals(profile)) return profile;
        return PROFILE_STABLE;
    }

    public static boolean shouldUseSustainedPerformance(Context context, SharedPreferences preferences) {
        boolean legacy = preferences != null && preferences.getBoolean(
                PerformanceManager.PREF_SUSTAINED_PERFORMANCE, false);
        if (!isProfileEnabled(context, preferences)) return legacy;
        return PROFILE_STABLE.equals(getSelectedProfile(preferences));
    }

    public static boolean isBigCoreProfileSelected(Context context, SharedPreferences preferences) {
        if (isProfileEnabled(context, preferences)) {
            return PROFILE_PERFORMANCE.equals(getSelectedProfile(preferences));
        }
        return preferences != null && preferences.getBoolean(
                PerformanceManager.PREF_PREFER_BIG_CORES, false);
    }

    public static boolean shouldPreferBigCores(Context context, SharedPreferences preferences) {
        boolean requested = isBigCoreProfileSelected(context, preferences);
        return requested && !isThermalPressure(context);
    }

    /** True at Android's MODERATE thermal status or above; false when unavailable. */
    public static boolean isThermalPressure(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.getCurrentThermalStatus()
                    >= PowerManager.THERMAL_STATUS_MODERATE;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String[] getProfileIds() {
        return new String[] { PROFILE_STABLE, PROFILE_BALANCED, PROFILE_PERFORMANCE };
    }

    public static String[] getProfileLabels() {
        return new String[] { "Stable / sustained", "Balanced", "Performance / big cores" };
    }

    public static String getProfileDescription(String profile) {
        if (PROFILE_PERFORMANCE.equals(profile)) {
            return "Uses the existing big-core affinity option for the guest process. This can help CPU-bound games but may increase heat and battery use.";
        }
        if (PROFILE_BALANCED.equals(profile)) {
            return "Keeps the standard Android Game Mode and guest-thread priority controls without requesting sustained mode or forcing big-core affinity.";
        }
        return "Requests Android sustained-performance mode where supported and avoids forcing emulation threads onto big cores. Intended for steadier longer sessions.";
    }

    public static String getThermalStatusLabel(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "Unavailable";
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return "Unavailable";
            switch (pm.getCurrentThermalStatus()) {
                case PowerManager.THERMAL_STATUS_NONE: return "Normal";
                case PowerManager.THERMAL_STATUS_LIGHT: return "Light";
                case PowerManager.THERMAL_STATUS_MODERATE: return "Moderate";
                case PowerManager.THERMAL_STATUS_SEVERE: return "Severe";
                case PowerManager.THERMAL_STATUS_CRITICAL: return "Critical";
                case PowerManager.THERMAL_STATUS_EMERGENCY: return "Emergency";
                case PowerManager.THERMAL_STATUS_SHUTDOWN: return "Shutdown";
                default: return "Unknown";
            }
        } catch (Throwable ignored) {
            return "Unavailable";
        }
    }

    /** Battery temperature only; this is not a CPU/GPU junction-temperature sensor. */
    public static String getBatteryTemperatureLabel(Context context) {
        if (context == null) return "Battery temperature unavailable";
        try {
            Intent battery = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return "Battery temperature unavailable";
            int tenthsC = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            if (tenthsC < 0) return "Battery temperature unavailable";
            return String.format(Locale.ROOT, "Battery %.1f°C", tenthsC / 10.0f);
        } catch (Throwable ignored) {
            return "Battery temperature unavailable";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
