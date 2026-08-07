package com.thelostecho.game.graphics;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Fade-to-black / fade-in transition used when changing maps or entering a
 * cutscene. The scene calls start() once; the renderer walks FADING_OUT ->
 * HOLDING -> FADING_IN and exposes a consumed completion flag so the scene can
 * swap the active map exactly at the darkest point.
 */
public final class MapTransitionEffect {

    public enum Phase { IDLE, FADING_OUT, HOLDING, FADING_IN }

    private final Paint fadePaint = new Paint();

    private Phase phase = Phase.IDLE;
    private float timer = 0f;
    private float fadeOutDuration = 0.6f;
    private float holdDuration = 0.2f;
    private float fadeInDuration = 0.6f;
    private boolean completionFlag = false;

    public void start(float fadeOut, float hold, float fadeIn) {
        fadeOutDuration = fadeOut;
        holdDuration = hold;
        fadeInDuration = fadeIn;
        timer = 0f;
        phase = Phase.FADING_OUT;
        completionFlag = false;
    }

    public void update(float delta) {
        if (phase == Phase.IDLE) {
            return;
        }
        timer += delta;
        switch (phase) {
            case FADING_OUT:
                if (timer >= fadeOutDuration) {
                    timer = 0f;
                    phase = Phase.HOLDING;
                }
                break;
            case HOLDING:
                if (timer >= holdDuration) {
                    timer = 0f;
                    phase = Phase.FADING_IN;
                    completionFlag = true; // darkest point reached
                }
                break;
            case FADING_IN:
                if (timer >= fadeInDuration) {
                    phase = Phase.IDLE;
                    timer = 0f;
                }
                break;
            default:
                break;
        }
    }

    /** True exactly once when the screen is fully black (map swap point). */
    public boolean consumeCompletion() {
        boolean c = completionFlag;
        completionFlag = false;
        return c;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isActive() {
        return phase != Phase.IDLE;
    }

    /** Draws a full-screen black overlay with the current fade alpha. */
    public void draw(Canvas canvas) {
        float alpha;
        switch (phase) {
            case FADING_OUT:
                alpha = timer / fadeOutDuration;
                break;
            case HOLDING:
                alpha = 1f;
                break;
            case FADING_IN:
                alpha = 1f - timer / fadeInDuration;
                break;
            case IDLE:
            default:
                return;
        }
        fadePaint.setARGB((int) (255f * alpha), 0, 0, 0);
        canvas.drawRect(0f, 0f, canvas.getWidth(), canvas.getHeight(), fadePaint);
    }

    public void reset() {
        phase = Phase.IDLE;
        completionFlag = false;
    }
}
