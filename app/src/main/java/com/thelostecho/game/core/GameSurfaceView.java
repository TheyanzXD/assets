package com.thelostecho.game.core;

import android.content.Context;
import android.graphics.Canvas;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * The single game surface. Owns the input pipeline, the performance monitor and
 * the game loop, and bridges Surface lifecycle to the scene. Rendering happens
 * on a software canvas (LAYER_TYPE_SOFTWARE) for consistent results across
 * devices; all coordinates are density-scaled world units.
 */
public final class GameSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private final Context appContext;
    private final InputManager input;
    private final PerformanceMonitor monitor;

    private SceneManager scene;
    private GameThread gameThread;
    private boolean surfaceReady = false;
    private volatile boolean paused = false;

    public GameSurfaceView(Context context) {
        super(context);
        appContext = context.getApplicationContext();
        setFocusable(true);
        setFocusableInTouchMode(true);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        getHolder().addCallback(this);

        input = new InputManager();
        monitor = new PerformanceMonitor();

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float initialW = Math.max(1f, metrics.widthPixels);
        float initialH = Math.max(1f, metrics.heightPixels);
        float density = Math.max(0.5f, metrics.density);
        input.updateLayout(initialW, initialH, density);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (scene == null) {
            scene = new SceneManager(appContext, this, input, monitor);
        }
        scene.onSurfaceCreated();
        surfaceReady = true;
        if (gameThread == null) {
            gameThread = new GameThread(holder, scene, monitor);
        }
        gameThread.startLoop();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (scene != null) {
            float density = Math.max(0.5f, getContext().getResources().getDisplayMetrics().density);
            input.updateLayout(width, height, density);
            scene.onSurfaceChanged(width, height);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        if (gameThread != null) {
            gameThread.stopLoop();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (input != null) {
            input.process(event);
        }
        return true;
    }

    /** Frame-time statistics for the debug HUD overlay. */
    public void getStats(float[] out) {
        if (monitor != null) {
            monitor.getStats(out);
        }
    }

    public InputManager getInput() {
        return input;
    }

    public void onResumeGame() {
        paused = false;
        if (gameThread != null) {
            gameThread.resumeLoop();
        }
        if (scene != null) {
            scene.onResume();
        }
    }

    public void onPauseGame() {
        paused = true;
        if (gameThread != null) {
            gameThread.pauseLoop();
        }
        if (scene != null) {
            scene.onPause();
        }
    }

    public void onDestroyGame() {
        if (gameThread != null) {
            gameThread.stopLoop();
            gameThread = null;
        }
        if (scene != null) {
            scene.dispose();
            scene = null;
        }
        surfaceReady = false;
    }

    public boolean isPaused() {
        return paused;
    }
}
