package com.thelostecho.game.graphics;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Post-effect that visually "wobbles" the screen when a sonar pulse passes
 * over it. Since the world is drawn procedurally we simulate the distortion
 * with translucent offset rings rather than a framebuffer sample (cheap, no
 * GPU readback). Optional; off by default in the developer settings.
 */
public final class DistortionEffect {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean enabled = false;

    private float timer = 0f;
    private float duration = 0f;
    private float x = 0f;
    private float y = 0f;
    private float radius = 0f;

    public DistortionEffect() {
        ringPaint.setStyle(Paint.Style.STROKE);
    }

    public void setEnabled(boolean on) {
        enabled = on;
        if (!on) {
            timer = 0f;
            duration = 0f;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Triggers a distortion pulse centred on the given world point. */
    public void trigger(float wx, float wy, float worldRadius) {
        if (!enabled) {
            return;
        }
        timer = 0f;
        duration = 0.45f;
        x = wx;
        y = wy;
        radius = worldRadius;
    }

    public void update(float delta) {
        if (timer < duration) {
            timer += delta;
        }
    }

    /** World-space drawing (under the scene's world transform). */
    public void draw(Canvas canvas) {
        if (!enabled || timer >= duration) {
            return;
        }
        float k = timer / duration;
        float r = radius * (0.3f + k * 0.7f);
        int alpha = (int) (120f * (1f - k));
        ringPaint.setARGB(alpha, 180, 230, 255);
        ringPaint.setStrokeWidth(3f + 8f * (1f - k));
        canvas.drawCircle(x, y, r, ringPaint);
        canvas.drawCircle(x, y, r * 0.6f, ringPaint);
    }

    public void dispose() {
    }
}
