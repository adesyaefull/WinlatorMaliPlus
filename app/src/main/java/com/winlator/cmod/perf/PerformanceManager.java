package com.winlator.cmod.perf;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import androidx.preference.PreferenceManager;

/**
 * Unified Performance Management Engine for Winlator Mali.
 * Coordinates all non-root and system performance enhancements.
 */
public class PerformanceManager {
    private static final String TAG = "PerformanceManager";

    public static final String PREF_GAME_MODE_SIGNAL = "perf_game_mode_signal";
    public static final String PREF_THREAD_PRIORITY_BOOST = "perf_thread_priority_boost";
    public static final String PREF_PREFER_BIG_CORES = "perf_prefer_big_cores";
    public static final String PREF_SUSTAINED_PERFORMANCE = "perf_sustained_performance";
    public static final String PREF_SAMSUNG_PERF_BOOST = "perf_samsung_boost";
    public static final String PREF_ROOT_PERFORMANCE_PROFILE = "perf_root_profile";
    public static final String PREF_ROOT_CPU_GOVERNOR = "perf_root_cpu_governor";
    public static final String PREF_ROOT_GPU_GOVERNOR = "perf_root_gpu_governor";
    public static final String PREF_ROOT_IO_SCHEDULER = "perf_root_io_scheduler";

    private static SamsungSPerfDriver samsungDriver = null;
    private static int currentGuestPid = -1;
    private static boolean isSessionActive = false;
    private static boolean bigCoreAffinityApplied = false;
    private static PowerManager thermalPowerManager;
    private static PowerManager.OnThermalStatusChangedListener thermalStatusListener;

    public static void init(Context context) {
        if (samsungDriver == null && context != null && SamsungSPerfDriver.isSamsungDevice()) {
            samsungDriver = new SamsungSPerfDriver(context);
        }
    }

    public static void onGameStart(Activity activity, int guestRootPid, SharedPreferences prefs) {
        if (activity == null) return;
        currentGuestPid = guestRootPid;
        isSessionActive = true;

        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        }

        init(activity);

        // 1. Android 13+ Game Mode Signal
        if (prefs.getBoolean(PREF_GAME_MODE_SIGNAL, true)) {
            GameModeSignal.enterGameplay(activity);
        }

        // 2. Sustained Performance Mode
        setSustainedPerformance(activity,
                HelioDeviceProfileManager.shouldUseSustainedPerformance(activity, prefs));

        // 3. Thread Priority Boost
        if (prefs.getBoolean(PREF_THREAD_PRIORITY_BOOST, true) && guestRootPid > 0) {
            PerfPriority.boost(guestRootPid);
        }

        // 4. Prefer Big Cores
        if (HelioDeviceProfileManager.shouldPreferBigCores(activity, prefs) && guestRootPid > 0) {
            CpuTopology.applyBigCoreAffinity(guestRootPid);
        }

        // 5. Samsung Galaxy Performance SDK Boost
        if (prefs.getBoolean(PREF_SAMSUNG_PERF_BOOST, true) && samsungDriver != null && samsungDriver.isSupported()) {
            samsungDriver.startBoost();
        }
        applyRootPerformanceProfile(activity, prefs);
        applyGuestAffinity(activity, prefs, guestRootPid);
        registerThermalStatusListener(activity);

        Log.d(TAG, "Performance profile applied on game start (pid=" + guestRootPid + ")");
    }

    public static void onGameResume(Activity activity, int guestRootPid) {
        if (activity == null) return;
        if (guestRootPid > 0) currentGuestPid = guestRootPid;
        isSessionActive = true;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);

        if (prefs.getBoolean(PREF_GAME_MODE_SIGNAL, true)) {
            GameModeSignal.enterGameplay(activity);
        }

        setSustainedPerformance(activity,
                HelioDeviceProfileManager.shouldUseSustainedPerformance(activity, prefs));

        if (prefs.getBoolean(PREF_THREAD_PRIORITY_BOOST, true) && currentGuestPid > 0) {
            PerfPriority.boost(currentGuestPid);
        }

        if (HelioDeviceProfileManager.shouldPreferBigCores(activity, prefs) && currentGuestPid > 0) {
            CpuTopology.applyBigCoreAffinity(currentGuestPid);
        }

        if (prefs.getBoolean(PREF_SAMSUNG_PERF_BOOST, true) && samsungDriver != null && samsungDriver.isSupported()) {
            samsungDriver.startBoost();
        }
        applyRootPerformanceProfile(activity, prefs);
        applyGuestAffinity(activity, prefs, currentGuestPid);
        registerThermalStatusListener(activity);
    }

    public static void onGamePause(Activity activity) {
        if (activity == null) return;
        unregisterThermalStatusListener();
        restoreGuestAffinity(currentGuestPid);
        GameModeSignal.exitGameplay(activity);
        setSustainedPerformance(activity, false);
        if (samsungDriver != null && samsungDriver.isSupported()) {
            samsungDriver.stopBoost();
        }
        RootPerformanceManager.restore(activity, error -> {
            if (error != null) {
                android.widget.Toast.makeText(activity.getApplicationContext(),
                        "Could not restore CPU governor: " + error,
                        android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    public static void onGameStop(Activity activity) {
        if (activity == null) return;
        isSessionActive = false;
        unregisterThermalStatusListener();
        restoreGuestAffinity(currentGuestPid);
        GameModeSignal.exitGameplay(activity);
        setSustainedPerformance(activity, false);
        PerfPriority.restore();
        if (samsungDriver != null && samsungDriver.isSupported()) {
            samsungDriver.stopBoost();
        }
        RootPerformanceManager.restore(activity, error -> {
            if (error != null) {
                android.widget.Toast.makeText(activity.getApplicationContext(),
                        "Could not restore CPU governor: " + error,
                        android.widget.Toast.LENGTH_LONG).show();
            }
        });
        currentGuestPid = -1;
        Log.d(TAG, "Performance profile restored on game stop");
    }

    public static void applySettingsLive(Activity activity, SharedPreferences prefs) {
        if (activity == null || !isSessionActive) return;
        if (prefs == null) prefs = PreferenceManager.getDefaultSharedPreferences(activity);

        if (prefs.getBoolean(PREF_GAME_MODE_SIGNAL, true)) {
            GameModeSignal.enterGameplay(activity);
        } else {
            GameModeSignal.exitGameplay(activity);
        }

        setSustainedPerformance(activity,
                HelioDeviceProfileManager.shouldUseSustainedPerformance(activity, prefs));
        if (prefs.getBoolean(PREF_ROOT_PERFORMANCE_PROFILE, false)) {
            applyRootPerformanceProfile(activity, prefs);
        } else {
            RootPerformanceManager.restore(activity, null);
        }

        if (prefs.getBoolean(PREF_THREAD_PRIORITY_BOOST, true)) {
            if (currentGuestPid > 0) PerfPriority.boost(currentGuestPid);
        } else {
            PerfPriority.restore();
        }

        applyGuestAffinity(activity, prefs, currentGuestPid);

        if (samsungDriver != null && samsungDriver.isSupported()) {
            if (prefs.getBoolean(PREF_SAMSUNG_PERF_BOOST, true)) {
                samsungDriver.startBoost();
            } else {
                samsungDriver.stopBoost();
            }
        }
    }

    public static void updateGuestPid(Activity activity, int pid) {
        if (pid <= 0) return;
        currentGuestPid = pid;
        if (activity != null && isSessionActive) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            if (prefs.getBoolean(PREF_THREAD_PRIORITY_BOOST, true)) {
                PerfPriority.boost(pid);
            }
            applyGuestAffinity(activity, prefs, pid);
        }
    }

    public static boolean isSamsungSupported() {
        return samsungDriver != null && samsungDriver.isSupported();
    }

    private static void applyGuestAffinity(Activity activity, SharedPreferences prefs, int pid) {
        if (pid <= 0 || activity == null || prefs == null) return;
        if (HelioDeviceProfileManager.shouldPreferBigCores(activity, prefs)) {
            int updated = CpuTopology.applyBigCoreAffinity(pid);
            if (updated > 0) bigCoreAffinityApplied = true;
        } else if (bigCoreAffinityApplied) {
            CpuTopology.applyAllCoreAffinity(pid);
            bigCoreAffinityApplied = false;
        }
    }

    private static void restoreGuestAffinity(int pid) {
        if (!bigCoreAffinityApplied) return;
        if (pid > 0) CpuTopology.applyAllCoreAffinity(pid);
        bigCoreAffinityApplied = false;
    }

    private static void registerThermalStatusListener(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        unregisterThermalStatusListener();
        try {
            PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            PowerManager.OnThermalStatusChangedListener listener = status -> {
                if (!isSessionActive || currentGuestPid <= 0 || !bigCoreAffinityApplied) return;
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                if (!HelioDeviceProfileManager.isBigCoreProfileSelected(activity, prefs)) {
                    restoreGuestAffinity(currentGuestPid);
                    return;
                }
                if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
                    restoreGuestAffinity(currentGuestPid);
                    Log.i(TAG, "Thermal guard restored guest affinity to all online cores");
                } else if (HelioDeviceProfileManager.shouldPreferBigCores(activity, prefs)) {
                    int updated = CpuTopology.applyBigCoreAffinity(currentGuestPid);
                    if (updated > 0) bigCoreAffinityApplied = true;
                }
            };
            pm.addThermalStatusListener(activity.getMainExecutor(), listener);
            thermalPowerManager = pm;
            thermalStatusListener = listener;
        } catch (Throwable error) {
            Log.w(TAG, "Thermal status listener unavailable: " + error.getMessage());
        }
    }

    private static void unregisterThermalStatusListener() {
        if (thermalPowerManager == null || thermalStatusListener == null) return;
        try {
            thermalPowerManager.removeThermalStatusListener(thermalStatusListener);
        } catch (Throwable error) {
            Log.w(TAG, "Could not unregister thermal listener: " + error.getMessage());
        } finally {
            thermalPowerManager = null;
            thermalStatusListener = null;
        }
    }

    private static void applyRootPerformanceProfile(Activity activity, SharedPreferences prefs) {
        if (!prefs.getBoolean(PREF_ROOT_PERFORMANCE_PROFILE, false)) return;
        String cpuGovernor = prefs.getString(PREF_ROOT_CPU_GOVERNOR, "performance");
        String gpuGovernor = prefs.getString(PREF_ROOT_GPU_GOVERNOR, "");
        String ioScheduler = prefs.getString(PREF_ROOT_IO_SCHEDULER, "");
        RootPerformanceManager.applyPerformanceProfile(
                activity, cpuGovernor, gpuGovernor, ioScheduler, error -> {
            if (error != null) {
                android.widget.Toast.makeText(activity.getApplicationContext(),
                        "Could not apply root performance profile: " + error,
                        android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void setSustainedPerformance(Activity activity, boolean enable) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        try {
            PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isSustainedPerformanceModeSupported()) {
                activity.getWindow().setSustainedPerformanceMode(enable);
                Log.d(TAG, "Sustained performance mode set to " + enable);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to set sustained performance mode: " + t.getMessage());
        }
    }
}
