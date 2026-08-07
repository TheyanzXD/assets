package com.thelostecho.game.graphics;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Pooled particle system. Footstep dust, sonar shimmer, muzzle flashes, door
 * sparks and drone explosion debris all share this emitter. A hard cap (500)
 * plus index-scan acquisition keeps memory bounded and GC-free.
 */
public final class ParticleSystem {

    public static final int MAX_PARTICLES = 500;

    private static final class Particle {
        boolean active;
        float x;
        float y;
        float vx;
        float vy;
        float life;
        float maxLife;
        float scale;
        int color;
        float gravity;
    }

    private final Particle[] particles;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final java.util.Random rng = new java.util.Random();

    // Stream emitter state.
    private float streamAccumulator = 0f;

    public ParticleSystem() {
        particles = new Particle[MAX_PARTICLES];
        for (int i = 0; i < MAX_PARTICLES; i++) {
            particles[i] = new Particle();
        }
    }

    private Particle obtain() {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (!particles[i].active) {
                return particles[i];
            }
        }
        return null;
    }

    private void spawn(Particle p, float x, float y, int color, float speed,
                       float life, float scale, float angle, float gravity) {
        p.active = true;
        p.x = x;
        p.y = y;
        p.vx = (float) Math.cos(angle) * speed;
        p.vy = (float) Math.sin(angle) * speed;
        p.life = life;
        p.maxLife = life;
        p.scale = scale;
        p.color = color;
        p.gravity = gravity;
    }

    /** Explosive emitter: particles radiate outward from a point. */
    public void burst(float x, float y, int color, int count, float speed, float life) {
        for (int i = 0; i < count; i++) {
            Particle p = obtain();
            if (p == null) {
                return;
            }
            float angle = rng.nextFloat() * (float) Math.PI * 2f;
            float sp = speed * (0.4f + rng.nextFloat() * 0.8f);
            float scale = 1.5f + rng.nextFloat() * 2.5f;
            spawn(p, x, y, color, sp, life * (0.6f + rng.nextFloat() * 0.7f),
                    scale, angle, 120f);
        }
    }

    /** Continuous emitter driven by a rate (particles per second). */
    public void stream(float x, float y, int color, float rate, float speed, float life) {
        streamAccumulator += rate / 60f;
        while (streamAccumulator >= 1f) {
            streamAccumulator -= 1f;
            Particle p = obtain();
            if (p == null) {
                return;
            }
            float angle = -rng.nextFloat() * (float) Math.PI;
            float sp = speed * (0.6f + rng.nextFloat() * 0.8f);
            spawn(p, x, y, color, sp, life, 1f + rng.nextFloat() * 2f, angle, 60f);
        }
    }

    public void update(float delta) {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            Particle p = particles[i];
            if (!p.active) {
                continue;
            }
            p.life -= delta;
            if (p.life <= 0f) {
                p.active = false;
                continue;
            }
            p.vy += p.gravity * delta;
            p.x += p.vx * delta;
            p.y += p.vy * delta;
        }
    }

    /** World-space drawing (under the scene's world transform). */
    public void draw(Canvas canvas) {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            Particle p = particles[i];
            if (!p.active) {
                continue;
            }
            float k = Math.max(0f, p.life / p.maxLife);
            int alpha = (int) (255f * k);
            int color = (p.color & 0x00FFFFFF) | (alpha << 24);
            paint.setColor(color);
            canvas.drawCircle(p.x, p.y, p.scale, paint);
        }
    }

    public void clear() {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            particles[i].active = false;
        }
        streamAccumulator = 0f;
    }

    public int getActiveCount() {
        int c = 0;
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (particles[i].active) {
                c++;
            }
        }
        return c;
    }
}
