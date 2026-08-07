package com.thelostecho.game.core;

/**
 * Tracks per-second FPS and frame-time stats. Fully allocation-free: values are
 * written into a reusable float[] for the HUD to display.
 */
public final class PerformanceMonitor {

    public static final int STAT_INDEX_FPS = 0;
    public static final int STAT_INDEX_AVG_FRAME_MS = 1;
    public static final int STAT_INDEX_WORST_FRAME_MS = 2;
    public static final int STAT_COUNT = 3;

    private final float[] stats = new float[STAT_COUNT];
    private long lastFrameNanos = 0L;
    private float smoothedFps = 0f;
    private long avgWindowStart = 0L;
    private int framesInWindow = 0;
    private long frameMsSum = 0L;
    private long worstFrameMs = 0L;

    public PerformanceMonitor() {
        for (int i = 0; i < STAT_COUNT; i++) {
            stats[i] = 0f;
        }
    }

    /**
     * Called by GameThread once per second. Sub-sample tracking happens through
     * tick(), which accumulates frame durations.
     */
    public void reportFps(float fps) {
        if (fps < 0f) {
            fps = 0f;
        }
        smoothedFps = smoothedFps <= 0f ? fps : smoothedFps * 0.85f + fps * 0.15f;
    }

    /** Must be called once per rendered frame with the frame's duration in ms. */
    public void tickFrame(float frameMs) {
        framesInWindow++;
        frameMsSum += (long) frameMs;
        if (frameMs > worstFrameMs) {
            worstFrameMs = (long) frameMs;
        }
        long now = System.nanoTime();
        if (avgWindowStart == 0L) {
            avgWindowStart = now;
        }
        long windowMs = (now - avgWindowStart) / 1000000L;
        if (windowMs >= 1000L && framesInWindow > 0) {
            stats[STAT_INDEX_AVG_FRAME_MS] = (float) frameMsSum / framesInWindow;
            stats[STAT_INDEX_WORST_FRAME_MS] = worstFrameMs;
            frameMsSum = 0L;
            framesInWindow = 0;
            worstFrameMs = 0L;
            avgWindowStart = now;
        }
        lastFrameNanos = now;
        stats[STAT_INDEX_FPS] = smoothedFps;
    }

    /** Returns the monitored stats into the given (reused) array. */
    public void getStats(float[] out) {
        if (out.length < STAT_COUNT) {
            return;
        }
        out[STAT_INDEX_FPS] = smoothedFps;
        out[STAT_INDEX_AVG_FRAME_MS] = stats[STAT_INDEX_AVG_FRAME_MS];
        out[STAT_INDEX_WORST_FRAME_MS] = stats[STAT_INDEX_WORST_FRAME_MS];
    }

    public long getLastFrameNanos() {
        return lastFrameNanos;
    }
}
