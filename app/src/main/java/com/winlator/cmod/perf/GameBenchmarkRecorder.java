package com.winlator.cmod.perf;

import java.util.Arrays;

/** Collects a repeatable 60-second game-window frame-time sample without changing game settings. */
public final class GameBenchmarkRecorder {
    private final long durationNs;
    private final float[] frameTimesMs = new float[12000];
    private int frameTimeCount;
    private int frameCount;
    private int maximumBatteryTempC = -1;
    private int maximumThermalStatus = -1;
    private long startNs;
    private long endNs;
    private long lastFrameNs;
    private boolean running;
    private boolean finished;

    public GameBenchmarkRecorder(long durationMs) {
        durationNs = Math.max(5_000L, durationMs) * 1_000_000L;
    }

    public synchronized void start(long nowNs) {
        startNs = Math.max(1L, nowNs);
        endNs = 0L;
        lastFrameNs = 0L;
        frameTimeCount = 0;
        frameCount = 0;
        maximumBatteryTempC = -1;
        maximumThermalStatus = -1;
        running = true;
        finished = false;
    }

    public synchronized boolean onFrame(long nowNs, int batteryTempC, int thermalStatus) {
        if (!running || nowNs < startNs || nowNs - startNs > durationNs) return false;
        if (lastFrameNs > 0L) {
            long deltaNs = nowNs - lastFrameNs;
            if (deltaNs > 0L && deltaNs < 1_000_000_000L
                    && frameTimeCount < frameTimesMs.length) {
                frameTimesMs[frameTimeCount++] = deltaNs / 1_000_000f;
            }
        }
        lastFrameNs = nowNs;
        frameCount++;
        if (batteryTempC > maximumBatteryTempC) maximumBatteryTempC = batteryTempC;
        if (thermalStatus >= 0 && thermalStatus > maximumThermalStatus) {
            maximumThermalStatus = thermalStatus;
        }
        return true;
    }

    public synchronized void finish(long nowNs) {
        if (!running && finished) return;
        endNs = Math.max(startNs, Math.min(nowNs, startNs + durationNs));
        running = false;
        finished = true;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized String getReport() {
        if (!finished) return "Benchmark is still running.";
        long elapsedNs = Math.max(1L, endNs - startNs);
        float averageFps = frameCount * 1_000_000_000f / elapsedNs;
        float[] sorted = Arrays.copyOf(frameTimesMs, frameTimeCount);
        Arrays.sort(sorted);

        StringBuilder report = new StringBuilder();
        report.append(String.format(java.util.Locale.ROOT,
                "Duration: %.1f s\nFrames sampled: %d\nAverage update rate: %.1f FPS",
                elapsedNs / 1_000_000_000f, frameCount, averageFps));

        if (sorted.length > 0) {
            float median = percentile(sorted, 0.50d);
            float p95 = percentile(sorted, 0.95d);
            float p99 = percentile(sorted, 0.99d);
            report.append(String.format(java.util.Locale.ROOT,
                    "\nFrame time median: %.2f ms\nFrame time p95: %.2f ms\nFrame time p99: %.2f ms\nEstimated 1%% low: %.1f FPS",
                    median, p95, p99, 1000f / Math.max(0.1f, p99)));
        } else {
            report.append("\nNo frame-time distribution available.");
        }

        report.append("\nBattery temperature peak: ");
        report.append(maximumBatteryTempC >= 0 ? maximumBatteryTempC + " °C" : "unavailable");
        report.append("\nAndroid thermal status peak: ");
        report.append(thermalLabel(maximumThermalStatus));
        if (frameCount < 2) {
            report.append("\n\nNo game frame samples were recorded. Confirm that a game window is active and rendering.");
        }
        return report.toString();
    }

    private static float percentile(float[] sorted, double percentile) {
        if (sorted.length == 0) return 0f;
        int index = Math.max(0, Math.min(sorted.length - 1,
                (int) Math.ceil(sorted.length * percentile) - 1));
        return sorted[index];
    }

    private static String thermalLabel(int status) {
        switch (status) {
            case 0: return "None";
            case 1: return "Light";
            case 2: return "Moderate";
            case 3: return "Severe";
            case 4: return "Critical";
            case 5: return "Emergency";
            case 6: return "Shutdown";
            default: return "Unavailable";
        }
    }
}
