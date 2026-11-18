package com.winlator.cmod.perf;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Conservative, opt-in FPS pacing controller.
 * Only changes the application's frame cap; it never changes CPU/GPU clocks or Android thermal policy.
 */
public final class AdaptiveFramePacingController {
    public static final int NO_CHANGE = -1;
    private static final long SAMPLE_WINDOW_NS = 2_500_000_000L;
    private static final int[] CANDIDATE_CAPS = {30, 45, 60};

    private final int originalFpsLimit;
    private final int[] levels;
    private final float[] frameTimesMs = new float[1024];
    private int currentIndex;
    private int frameTimeCount;
    private int frameCount;
    private int poorWindows;
    private int stableWindows;
    private long sampleStartNs;
    private long lastFrameNs;

    public AdaptiveFramePacingController(int originalFpsLimit, int maximumTargetFps) {
        this.originalFpsLimit = Math.max(0, originalFpsLimit);
        int maximum = Math.max(1, maximumTargetFps);
        ArrayList<Integer> caps = new ArrayList<>();
        for (int candidate : CANDIDATE_CAPS) {
            if (candidate < maximum) caps.add(candidate);
        }
        caps.add(maximum);
        levels = new int[caps.size()];
        for (int i = 0; i < caps.size(); i++) levels[i] = caps.get(i);
        currentIndex = levels.length - 1;
    }

    public synchronized boolean isEnabled() {
        return levels.length > 1;
    }

    public synchronized int getOriginalFpsLimit() {
        return originalFpsLimit;
    }

    public synchronized int getCurrentTargetFps() {
        return levels[currentIndex];
    }

    /**
     * thermalStatus follows PowerManager: 0=none, 1=light, 2=moderate, 3+=severe; -1=unavailable.
     * Returns an effective XServer FPS limit when a step is made, otherwise NO_CHANGE.
     */
    public synchronized int onFrame(long nowNs, int thermalStatus) {
        if (nowNs <= 0L) return NO_CHANGE;
        if (sampleStartNs == 0L) sampleStartNs = nowNs;

        if (lastFrameNs > 0L) {
            long deltaNs = nowNs - lastFrameNs;
            if (deltaNs > 0L && deltaNs < 1_000_000_000L
                    && frameTimeCount < frameTimesMs.length) {
                frameTimesMs[frameTimeCount++] = deltaNs / 1_000_000f;
            }
        }
        lastFrameNs = nowNs;
        frameCount++;

        long elapsedNs = nowNs - sampleStartNs;
        if (elapsedNs < SAMPLE_WINDOW_NS) return NO_CHANGE;

        float observedFps = frameCount * 1_000_000_000f / elapsedNs;
        float p95FrameTime = percentile95();
        int targetFps = levels[currentIndex];
        float frameBudgetMs = 1000f / Math.max(1, targetFps);
        boolean thermalPressure = thermalStatus >= 2;
        boolean poorPacing = observedFps < targetFps * 0.82f
                || (p95FrameTime > 0f && p95FrameTime > frameBudgetMs * 1.65f);

        sampleStartNs = nowNs;
        frameCount = 0;
        frameTimeCount = 0;

        if (thermalPressure || poorPacing) {
            poorWindows++;
            stableWindows = 0;
            if (thermalPressure || poorWindows >= 2) {
                poorWindows = 0;
                if (currentIndex > 0) {
                    currentIndex--;
                    return getAppliedLimit();
                }
            }
            return NO_CHANGE;
        }

        boolean thermalClear = thermalStatus < 0 || thermalStatus < 2;
        boolean stablePacing = observedFps >= targetFps * 0.92f
                && (p95FrameTime <= 0f || p95FrameTime <= frameBudgetMs * 1.35f);
        if (thermalClear && stablePacing) {
            stableWindows++;
            poorWindows = 0;
            if (stableWindows >= 6 && currentIndex < levels.length - 1) {
                stableWindows = 0;
                currentIndex++;
                return getAppliedLimit();
            }
        } else {
            stableWindows = 0;
            poorWindows = 0;
        }
        return NO_CHANGE;
    }

    private float percentile95() {
        if (frameTimeCount == 0) return 0f;
        float[] sorted = Arrays.copyOf(frameTimesMs, frameTimeCount);
        Arrays.sort(sorted);
        int index = Math.max(0, Math.min(sorted.length - 1,
                (int) Math.ceil(sorted.length * 0.95d) - 1));
        return sorted[index];
    }

    /** The user's original cap is restored when the controller reaches its highest tier. */
    private int getAppliedLimit() {
        return currentIndex == levels.length - 1
                ? originalFpsLimit : levels[currentIndex];
    }
}
