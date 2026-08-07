package com.thelostecho.game.entities;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.thelostecho.game.graphics.TileMapRenderer;

/**
 * Turret projectile. Pooled by SceneManager: deactivated on wall impact,
 * world-bounds exit, or player contact (which triggers game over).
 */
public final class Projectile extends GameObject {

    public static final float SPEED = 420f;
    public static final float RADIUS = 5f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float angle;
    private boolean hitPlayer;

    public Projectile() {
        width = RADIUS * 2f;
        height = RADIUS * 2f;
        paint.setARGB(255, 255, 120, 60);
    }

    public void init(float x, float y, float angle) {
        this.x = x;
        this.y = y;
        this.angle = angle;
        vx = (float) Math.cos(angle) * SPEED;
        vy = (float) Math.sin(angle) * SPEED;
        hitPlayer = false;
        active = true;
    }

    public void update(TileMapRenderer map, float delta, Player player) {
        if (!active) {
            return;
        }
        float nx = x + vx * delta;
        float ny = y + vy * delta;
        if (map.isWalkablePixel(nx, y) && map.isWalkablePixel(x, ny)) {
            x = nx;
            y = ny;
        } else {
            active = false;
            return;
        }
        float half = TileMapRenderer.TILE * 0.5f;
        if (x < -half || y < -half || x > map.getMapWidthPx() + half
                || y > map.getMapHeightPx() + half) {
            active = false;
            return;
        }
        if (player != null && player.isActive() && player.isVulnerable()) {
            float dx = player.x - x;
            float dy = player.y - y;
            float rr = RADIUS + player.getHitRadius();
            if (dx * dx + dy * dy <= rr * rr) {
                hitPlayer = true;
                active = false;
            }
        }
    }

    /** True when this projectile struck the player (game over condition). */
    public boolean consumeHitPlayer() {
        boolean h = hitPlayer;
        hitPlayer = false;
        return h;
    }

    @Override
    public void update(float delta) {
        // World update is done through update(map, delta, player).
    }

    @Override
    public void draw(Canvas canvas) {
        if (!active) {
            return;
        }
        float r = RADIUS;
        canvas.drawCircle(x, y, r, paint);
        float tx = x + (float) Math.cos(angle) * 14f;
        float ty = y + (float) Math.sin(angle) * 14f;
        canvas.drawLine(x, y, tx, ty, paint);
    }

    @Override
    public void reset() {
        super.reset();
        hitPlayer = false;
    }
}
