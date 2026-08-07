package com.thelostecho.game.entities;

import android.graphics.Canvas;
import android.graphics.RectF;

/**
 * Base class for every dynamic world object. Position/velocity are kept in
 * density-independent world units. Each subclass provides its own pooled
 * drawing state so nothing is allocated during gameplay.
 */
public abstract class GameObject implements GameObject.Poolable {

    /** Minimal pooling contract shared by all pooled entities. */
    public interface Poolable {
        void reset();

        boolean isActive();

        void setActive(boolean active);
    }

    public float x;
    public float y;
    public float vx;
    public float vy;
    public float width;
    public float height;

    public boolean active = true;
    public boolean pooled = false;

    /** Reusable hitbox; never allocate inside update/draw. */
    protected final RectF hitbox = new RectF();

    public abstract void update(float delta);

    public abstract void draw(Canvas canvas);

    /** Releases GPU/audio resources. Called once when the object dies. */
    public void dispose() {
    }

    /** Centers the hitbox on the object. Returns the shared instance. */
    public RectF getHitbox() {
        hitbox.set(x - width * 0.5f, y - height * 0.5f,
                x + width * 0.5f, y + height * 0.5f);
        return hitbox;
    }

    @Override
    public void reset() {
        active = true;
        vx = 0f;
        vy = 0f;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }

    /** Distance (squared) to another object, in world units. */
    public float distSqTo(GameObject other) {
        float dx = other.x - x;
        float dy = other.y - y;
        return dx * dx + dy * dy;
    }

    public float distTo(float ox, float oy) {
        float dx = ox - x;
        float dy = oy - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
