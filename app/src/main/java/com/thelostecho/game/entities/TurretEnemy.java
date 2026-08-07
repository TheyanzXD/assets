package com.thelostecho.game.entities;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.thelostecho.game.ai.HearingLogic;
import com.thelostecho.game.ai.VisionConeLogic;
import com.thelostecho.game.graphics.TileMapRenderer;

/**
 * A stationary ceiling turret. It rotates 360 degrees but tracks slowly; if the
 * player is inside its line of sight for one full second it opens fire. It can
 * be permanently disabled by solving the AcousticPuzzle assigned to it.
 */
public final class TurretEnemy extends GameObject {

    public enum State { IDLE, SCANNING, FIRING }

    public static final float SIGHT_RANGE = 340f;
    public static final float TRACK_SPEED = 1.1f;
    private static final float LOCK_ON_TIME = 1.0f;
    private static final float HIDDEN_MULT = 0.3f;

    private final TileMapRenderer map;
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barrelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint disabledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private State state = State.IDLE;
    private float barrelAngle = 0f;
    private float lockTimer = 0f;
    private float scanDirection = 1f;
    private boolean disabled = false;
    private boolean fireRequested = false;
    private boolean disabledFlag = false;
    private float animTimer = 0f;

    public TurretEnemy(TileMapRenderer map) {
        this.map = map;
        width = 26f;
        height = 26f;
        basePaint.setARGB(255, 120, 120, 132);
        barrelPaint.setARGB(255, 70, 70, 84);
        eyePaint.setARGB(255, 255, 90, 70);
        disabledPaint.setARGB(255, 80, 80, 90);
    }

    public void init(float x, float y) {
        this.x = x;
        this.y = y;
        active = true;
        state = State.IDLE;
        lockTimer = 0f;
        barrelAngle = 0f;
    }

    public void update(float delta, float px, float py, int playerMode, boolean playerHidden) {
        if (!active) {
            return;
        }
        animTimer += delta;
        if (disabled) {
            state = State.IDLE;
            return;
        }

        float visionRange = SIGHT_RANGE * (playerHidden ? HIDDEN_MULT : 1f);
        boolean canSee = !playerHidden && VisionConeLogic.canSee(map, x, y,
                barrelAngle, (float) Math.PI * 0.5f, visionRange, px, py);
        boolean hears = HearingLogic.hears(x, y, px, py, 140f, playerMode);

        if (canSee) {
            lockTimer += delta;
            state = State.SCANNING;
            float target = (float) Math.atan2(py - y, px - x);
            rotateToward(target, delta);
            if (lockTimer >= LOCK_ON_TIME) {
                state = State.FIRING;
                if (barrelAligned(target)) {
                    fireRequested = true;
                    lockTimer = 0f;
                }
            }
        } else {
            lockTimer = 0f;
            state = hears ? State.SCANNING : State.IDLE;
            if (state == State.IDLE) {
                barrelAngle += delta * 0.6f * scanDirection;
                if (barrelAngle > (float) Math.PI * 0.5f
                        || barrelAngle < -(float) Math.PI * 0.5f) {
                    scanDirection *= -1f;
                }
            }
        }
    }

    private void rotateToward(float target, float delta) {
        float diff = target - barrelAngle;
        while (diff > (float) Math.PI) {
            diff -= 2f * (float) Math.PI;
        }
        while (diff < -(float) Math.PI) {
            diff += 2f * (float) Math.PI;
        }
        float step = TRACK_SPEED * delta;
        if (Math.abs(diff) <= step) {
            barrelAngle = target;
        } else {
            barrelAngle += Math.signum(diff) * step;
        }
    }

    private boolean barrelAligned(float target) {
        float diff = Math.abs(target - barrelAngle);
        while (diff > (float) Math.PI) {
            diff -= 2f * (float) Math.PI;
        }
        return diff < 0.1f;
    }

    /** True exactly once per firing volley; the scene spawns the projectile. */
    public boolean consumeFireRequest() {
        boolean f = fireRequested;
        fireRequested = false;
        return f;
    }

    /** Puzzle completion calls this; permanently silences the turret. */
    public void setDisabled(boolean d) {
        if (disabled != d) {
            disabled = d;
            disabledFlag = true;
        }
    }

    public boolean consumeDisabledFlag() {
        boolean f = disabledFlag;
        disabledFlag = false;
        return f;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public State getState() {
        return state;
    }

    public float getBarrelAngle() {
        return barrelAngle;
    }

    public float getSightRange() {
        return SIGHT_RANGE;
    }

    @Override
    public void draw(Canvas canvas) {
        if (!active) {
            return;
        }
        float r = width * 0.5f;
        canvas.drawCircle(x, y, r, disabled ? disabledPaint : basePaint);
        float bx = x + (float) Math.cos(barrelAngle) * r;
        float by = y + (float) Math.sin(barrelAngle) * r;
        canvas.drawLine(x, y, bx, by, barrelPaint);
        canvas.drawCircle(x, y, r * 0.3f, eyePaint);
        if (disabled) {
            canvas.drawLine(x - r * 0.5f, y - r * 0.5f, x + r * 0.5f, y + r * 0.5f, barrelPaint);
            canvas.drawLine(x - r * 0.5f, y + r * 0.5f, x + r * 0.5f, y - r * 0.5f, barrelPaint);
        }
    }

    @Override
    public void reset() {
        super.reset();
        state = State.IDLE;
        lockTimer = 0f;
        barrelAngle = 0f;
        disabled = false;
        fireRequested = false;
        disabledFlag = false;
    }
}
