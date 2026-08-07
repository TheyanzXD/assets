package com.thelostecho.game.graphics;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * Expanding sonar pulses. Waves are pooled (no allocation in gameplay), each
 * with a growing radius and a fixed 0.8s lifetime. A single precomputed
 * RadialGradient shader is reused for every wave; per-wave position/scale is
 * applied through a reused Matrix. Waves reflect off walls, spawning secondary
 * pulses with reduced amplitude.
 */
public final class SonarWaveRenderer {

    public static final int MAX_WAVES = 24;
    public static final float MAX_RADIUS = 900f;
    public static final float LIFETIME = 0.8f;
    private static final int RING_COUNT = 3;

    private static final class Wave {
        float x;
        float y;
        float timeAlive;
        float amplitude;
        boolean active;
        boolean reveal;
        int ringStart;
    }

    private final Wave[] waves;
    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix gradientMatrix = new Matrix();
    private final RadialGradient gradient;
    private int waveCount = 0;

    public SonarWaveRenderer() {
        waves = new Wave[MAX_WAVES];
        for (int i = 0; i < MAX_WAVES; i++) {
            waves[i] = new Wave();
            waves[i].ringStart = i * RING_COUNT;
        }
        int[] colors = new int[]{
                0x00FFFFFF,
                0x40A0E8FF,
                0x0010B0E0
        };
        float[] stops = new float[]{0f, 0.55f, 1f};
        gradient = new RadialGradient(0f, 0f, MAX_RADIUS, colors, stops,
                Shader.TileMode.CLAMP);
        gradientPaint.setShader(gradient);
        gradientPaint.setStyle(Paint.Style.FILL);
        ringPaint.setARGB(200, 140, 230, 255);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);
    }

    public void spawnWave(float x, float y, float amplitude, boolean reveal) {
        Wave w = obtain();
        if (w == null) {
            return;
        }
        w.x = x;
        w.y = y;
        w.timeAlive = 0f;
        w.amplitude = Math.max(0.1f, amplitude);
        w.active = true;
        w.reveal = reveal;
    }

    /** Secondary pulse produced by a wall reflection. */
    public void spawnReflection(float x, float y, float amplitude) {
        spawnWave(x, y, amplitude * 0.45f, false);
    }

    private Wave obtain() {
        for (int i = 0; i < MAX_WAVES; i++) {
            if (!waves[i].active) {
                return waves[i];
            }
        }
        return null;
    }

    public void update(float delta, TileMapRenderer map) {
        waveCount = 0;
        for (int i = 0; i < MAX_WAVES; i++) {
            Wave w = waves[i];
            if (!w.active) {
                continue;
            }
            waveCount++;
            w.timeAlive += delta;
            if (w.timeAlive >= LIFETIME) {
                w.active = false;
                continue;
            }
            // Reflection check: when the expanding edge reaches a wall tile,
            // spawn a smaller pulse at the contact point.
            if (map != null) {
                float radius = currentRadius(w);
                int samples = 8;
                for (int s = 0; s < samples; s++) {
                    float ang = (float) (s * 2f * Math.PI / samples);
                    float sx = w.x + (float) Math.cos(ang) * radius;
                    float sy = w.y + (float) Math.sin(ang) * radius;
                    float prev = radius - w.amplitude * delta * 60f;
                    float px = w.x + (float) Math.cos(ang) * prev;
                    float py = w.y + (float) Math.sin(ang) * prev;
                    if (!map.isWalkablePixel(sx, sy) && map.isWalkablePixel(px, py)) {
                        spawnReflection(sx, sy, w.amplitude * 0.45f);
                    }
                }
            }
        }
    }

    public float currentRadius(int index) {
        Wave w = waves[index];
        float t = Math.min(1f, w.timeAlive / LIFETIME);
        return t * MAX_RADIUS * w.amplitude;
    }

    public float currentRadius(Wave w) {
        float t = Math.min(1f, w.timeAlive / LIFETIME);
        return t * MAX_RADIUS * w.amplitude;
    }

    /** World-space drawing (under the scene's world transform). */
    public void draw(Canvas canvas) {
        for (int i = 0; i < MAX_WAVES; i++) {
            Wave w = waves[i];
            if (!w.active) {
                continue;
            }
            float t = Math.min(1f, w.timeAlive / LIFETIME);
            float alpha = 255f * (1f - t);
            float radius = currentRadius(w);
            if (radius < 1f) {
                continue;
            }
            // Map the shared gradient onto this wave's circle.
            gradientMatrix.setScale(radius / MAX_RADIUS, radius / MAX_RADIUS);
            gradientMatrix.postTranslate(w.x, w.y);
            gradient.setLocalMatrix(gradientMatrix);
            gradientPaint.setAlpha((int) alpha);
            canvas.drawCircle(w.x, w.y, radius, gradientPaint);

            // Concentric rings.
            ringPaint.setAlpha((int) (alpha * 0.8f));
            for (int r = 1; r <= RING_COUNT; r++) {
                float rr = radius * (1f - (r - 1) * 0.1f);
                canvas.drawCircle(w.x, w.y, rr, ringPaint);
            }
        }
    }

    public void clear() {
        for (int i = 0; i < MAX_WAVES; i++) {
            waves[i].active = false;
        }
        waveCount = 0;
    }

    public int getWaveCount() {
        return waveCount;
    }

    public float getWaveX(int i) {
        return i >= 0 && i < MAX_WAVES ? waves[i].x : 0f;
    }

    public float getWaveY(int i) {
        return i >= 0 && i < MAX_WAVES ? waves[i].y : 0f;
    }

    public float getWaveRadius(int i) {
        return i >= 0 && i < MAX_WAVES ? currentRadius(waves[i]) : 0f;
    }

    public void dispose() {
        clear();
    }
}
