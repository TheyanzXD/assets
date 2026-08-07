package com.thelostecho.game.entities;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.thelostecho.game.managers.InventoryManager;

/**
 * Base for interactive environment objects: pushable crates, security consoles
 * and door terminals. Crates block movement and vision and can be pushed; door
 * terminals require the correct keycard; consoles act as story/quest props.
 */
public class InteractiveProp extends GameObject {

    public enum Kind { CRATE, CONSOLE, DOOR_TERMINAL, TERMINAL }

    protected final Kind kind;
    protected final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    protected boolean locked = true;
    protected boolean unlockedFlag = false;
    protected float pushTimer = 0f;

    public InteractiveProp(Kind kind) {
        this.kind = kind;
        if (kind == Kind.CRATE) {
            width = 30f;
            height = 30f;
            bodyPaint.setARGB(255, 96, 84, 60);
            accentPaint.setARGB(255, 60, 52, 38);
        } else if (kind == Kind.CONSOLE) {
            width = 40f;
            height = 26f;
            bodyPaint.setARGB(255, 70, 80, 100);
            accentPaint.setARGB(255, 0, 220, 190);
        } else {
            width = 44f;
            height = 60f;
            bodyPaint.setARGB(255, 70, 90, 110);
            accentPaint.setARGB(255, 255, 200, 60);
        }
    }

    public void init(float x, float y) {
        this.x = x;
        this.y = y;
        active = true;
    }

    public void init(float x, float y, boolean locked) {
        init(x, y);
        this.locked = locked;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isLocked() {
        return locked;
    }

    public void unlock() {
        if (locked) {
            locked = false;
            unlockedFlag = true;
        }
    }

    /** Consumed by the scene to trigger the unlock sound/animation. */
    public boolean consumeUnlockedFlag() {
        boolean f = unlockedFlag;
        unlockedFlag = false;
        return f;
    }

    /** Attempts to interact; returns true when something happened. */
    public boolean interact(InventoryManager inventory) {
        return false;
    }

    public void update(float delta, Player player) {
        if (kind == Kind.CRATE && pushTimer > 0f) {
            pushTimer -= delta;
        }
    }

    @Override
    public void update(float delta) {
    }

    @Override
    public void draw(Canvas canvas) {
        if (!active) {
            return;
        }
        float hw = width * 0.5f;
        float hh = height * 0.5f;
        canvas.drawRect(x - hw, y - hh, x + hw, y + hh, bodyPaint);
        if (kind == Kind.CRATE) {
            canvas.drawLine(x - hw, y - hh, x + hw, y + hh, accentPaint);
            canvas.drawLine(x + hw, y - hh, x - hw, y + hh, accentPaint);
        } else if (kind == Kind.CONSOLE) {
            canvas.drawRect(x - hw + 6f, y - hh + 6f, x + hw - 6f, y + hh - 6f, accentPaint);
        } else if (kind == Kind.TERMINAL) {
            canvas.drawRect(x - hw + 8f, y - hh + 8f, x + hw - 8f, y + hh - 8f, accentPaint);
        }
        // Door terminals show a lock state.
        if (kind == Kind.DOOR_TERMINAL) {
            if (locked) {
                canvas.drawCircle(x, y + 16f, 5f, accentPaint);
            } else {
                canvas.drawCircle(x, y + 16f, 5f, unlockedPaint());
            }
        }
    }

    private Paint cachedUnlocked;

    private Paint unlockedPaint() {
        if (cachedUnlocked == null) {
            cachedUnlocked = new Paint(Paint.ANTI_ALIAS_FLAG);
            cachedUnlocked.setARGB(255, 60, 255, 140);
        }
        return cachedUnlocked;
    }

    @Override
    public void reset() {
        super.reset();
        locked = true;
        unlockedFlag = false;
        pushTimer = 0f;
    }
}
