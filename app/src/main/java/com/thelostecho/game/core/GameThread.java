package com.thelostecho.game.core;

import android.graphics.Canvas;
import android.view.SurfaceHolder;

/**
 * The main game loop. Fixed timestep with accumulator (no spiral of death),
 * decoupled update/render, safe start/stop, pause/resume and adaptive FPS.
 */
public final class GameThread extends Thread {

    public static final int TARGET_FPS = 60;
    private static final float STEP = 1f / TARGET_FPS;
    private static final float MAX_FRAME_TIME = 0.25f;

    private final SurfaceHolder holder;
    private final SceneManager scene;
    private final PerformanceMonitor monitor;

    private final Object pauseLock = new Object();
    private final Object stepLock = new Object();

    private volatile boolean running = false;
    private boolean paused = false;

    // Adaptive frame skip: when the device can't keep up we drop render calls
    // (but never update calls) so the simulation stays deterministic.
    private boolean frameSkipEnabled = false;

    public GameThread(SurfaceHolder holder, SceneManager scene, PerformanceMonitor monitor) {
        super("TheLostEcho-GameThread");
        this.holder = holder;
        this.scene = scene;
        this.monitor = monitor;
        setDaemon(true);
    }

    @Override
    public void run() {
        long previousTime = System.nanoTime();
        float accumulator = 0f;
        int frameCount = 0;
        long fpsWindow = System.nanoTime();

        while (running) {
            long now = System.nanoTime();
            float elapsed = (now - previousTime) / 1000000000f;
            previousTime = now;
            if (elapsed > MAX_FRAME_TIME) {
                elapsed = MAX_FRAME_TIME;
            }

            synchronized (pauseLock) {
                while (paused && running) {
                    try {
                        pauseLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            if (!running) {
                break;
            }

            // Synchronized update section so the render call can never read
            // half-updated world state.
            synchronized (stepLock) {
                accumulator += elapsed;
                int updatesThisFrame = 0;
                while (accumulator >= STEP) {
                    scene.update(STEP);
                    accumulator -= STEP;
                    updatesThisFrame++;
                    if (updatesThisFrame >= 5) {
                        accumulator = 0f; // drop backlog, avoid spiral of death
                        break;
                    }
                }
                float interpolation = accumulator / STEP;
                render(interpolation, frameSkipEnabled && elapsed < STEP);
            }

            frameCount++;
            long now2 = System.nanoTime();
            if (now2 - fpsWindow >= 1000000000L) {
                float fps = frameCount * 1000000000f / (float) (now2 - fpsWindow);
                monitor.reportFps(fps);
                // Adaptive FPS: if consistently below 30, start skipping renders.
                frameSkipEnabled = fps < 30f;
                frameCount = 0;
                fpsWindow = now2;
            }
        }
    }

    private void render(float interpolation, boolean skip) {
        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas != null) {
                if (!skip) {
                    scene.render(canvas);
                }
            }
        } finally {
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }

    /** Starts the loop. No-op if already running. */
    public void startLoop() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
        if (!running) {
            running = true;
            if (!isAlive()) {
                start();
            }
        }
    }

    /** Safe stop: interrupts and joins the thread. */
    public void stopLoop() {
        running = false;
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
        if (isAlive()) {
            interrupt();
            try {
                join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Pauses the loop (waits on pauseLock until resumed). */
    public void pauseLoop() {
        synchronized (pauseLock) {
            paused = true;
        }
    }

    /** Resumes the loop after pauseLoop(). */
    public void resumeLoop() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
    }

    public boolean isLoopPaused() {
        return paused;
    }
}
