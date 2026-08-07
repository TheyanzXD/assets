package com.thelostecho.game.entities;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.thelostecho.game.ai.AIPathfinding;
import com.thelostecho.game.ai.HearingLogic;
import com.thelostecho.game.core.InputManager;
import com.thelostecho.game.graphics.TileMapRenderer;
import com.thelostecho.game.managers.InventoryManager;

import java.util.List;

/**
 * Playable character "Raka". Supports two control schemes (virtual joystick and
 * touch-to-move with pathfinding), a stamina system for Sonar Sense, stealth
 * (hiding reduces detection by 70%), smooth rotation, footstep SFX based on the
 * surface tile, and respawning at the last checkpoint with brief invincibility.
 */
public final class Player extends GameObject {

    public static final float MAX_STAMINA = 100f;
    public static final float STAMINA_REGEN = 5f;
    public static final float SONAR_COST = 20f;
    public static final float SNEAK_DRAIN = 0.5f;
    public static final float SONAR_COOLDOWN = 1.0f;
    public static final float WALK_SPEED = 215f;
    public static final float SNEAK_SPEED = 95f;
    public static final float HIT_RADIUS = 10f;

    private static final float SOFT_JOY_THRESHOLD = 0.55f;
    private static final float RUN_THRESHOLD = 0.85f;

    private final InputManager input;
    private final TileMapRenderer map;
    private final InventoryManager inventory;
    private final AIPathfinding pathfinding;
    private final List<android.graphics.Point> pathBuffer = new java.util.ArrayList<android.graphics.Point>();

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float stamina = MAX_STAMINA;
    private float sonarCooldown = 0f;
    private float rotation = 0f;
    private float targetRotation = 0f;
    private float invincibleTimer = 0f;
    private float footstepTimer = 0f;
    private float animTimer = 0f;

    private float checkpointX = 200f;
    private float checkpointY = 200f;

    private float moveTargetX;
    private float moveTargetY;
    private boolean hasMoveTarget = false;
    private float repathTimer = 0f;
    private int pathIndex = 0;

    private boolean pulseFired = false;
    private boolean interactRequested = false;
    private boolean hidden = false;
    private boolean sneaking = false;

    public Player(InputManager input, TileMapRenderer map, InventoryManager inventory) {
        this.input = input;
        this.map = map;
        this.inventory = inventory;
        this.pathfinding = new AIPathfinding(map);
        width = 20f;
        height = 20f;
        x = 200f;
        y = 200f;
        bodyPaint.setARGB(255, 46, 110, 110);
        detailPaint.setARGB(255, 225, 240, 235);
        glowPaint.setARGB(160, 80, 220, 255);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(2.5f);
    }

    @Override
    public void update(float delta) {
        if (sonarCooldown > 0f) {
            sonarCooldown -= delta;
        }
        if (invincibleTimer > 0f) {
            invincibleTimer -= delta;
        }
        animTimer += delta;

        // --- Stamina -----------------------------------------------------
        if (stamina < MAX_STAMINA) {
            stamina = Math.min(MAX_STAMINA, stamina + STAMINA_REGEN * delta);
        }
        sneaking = false;

        // --- Movement input ----------------------------------------------
        float moveX = 0f;
        float moveY = 0f;
        boolean joystickUsed = false;

        if (input.isJoystickActive()) {
            float jx = input.getJoystickX();
            float jy = input.getJoystickY();
            float mag = (float) Math.sqrt(jx * jx + jy * jy);
            if (mag > 0.12f) {
                joystickUsed = true;
                moveX = jx;
                moveY = jy;
                if (mag < SOFT_JOY_THRESHOLD) {
                    sneaking = true;
                }
            }
        }
        if (!joystickUsed && hasMoveTarget) {
            if (!followPath(delta)) {
                hasMoveTarget = false;
            }
        }

        // --- Sneak stamina drain ------------------------------------------
        if (sneaking) {
            stamina = Math.max(0f, stamina - SNEAK_DRAIN * delta);
        }

        // --- Integrate velocity (collision resolved per axis) -------------
        float speed = sneaking ? SNEAK_SPEED : WALK_SPEED;
        vx = moveX * speed;
        vy = moveY * speed;

        if (moveX != 0f || moveY != 0f) {
            targetRotation = (float) Math.atan2(moveY, moveX);
            if (joystickUsed && input.isJoystickActive()) {
                float mag = (float) Math.sqrt(moveX * moveX + moveY * moveY);
                if (mag > 0.9f) {
                    sneaking = false;
                }
            }
        }
        // Smooth rotation toward the movement direction.
        float diff = targetRotation - rotation;
        while (diff > (float) Math.PI) {
            diff -= 2f * (float) Math.PI;
        }
        while (diff < -(float) Math.PI) {
            diff += 2f * (float) Math.PI;
        }
        rotation += diff * Math.min(1f, delta * 12f);

        float dx = vx * delta;
        float dy = vy * delta;
        float nx = x + dx;
        float ny = y + dy;
        if (map.isWalkableRect(nx - width * 0.5f, y - height * 0.5f,
                nx + width * 0.5f, y + height * 0.5f)) {
            x = nx;
        }
        if (map.isWalkableRect(x - width * 0.5f, ny - height * 0.5f,
                x + width * 0.5f, ny + height * 0.5f)) {
            y = ny;
        }
        clampToMap();

        // --- Hidden state --------------------------------------------------
        hidden = map.isShadowPixel(x, y);

        // --- Footsteps -----------------------------------------------------
        boolean moving = moveX != 0f || moveY != 0f || (hasMoveTarget && !joystickUsed);
        if (moving) {
            float interval = sneaking ? 0.55f : 0.32f;
            footstepTimer -= delta;
            if (footstepTimer <= 0f) {
                footstepTimer = interval;
                float fx = x + (float) Math.cos(rotation) * 14f;
                float fy = y + (float) Math.sin(rotation) * 14f;
                footstepRequested = true;
                footstepX = fx;
                footstepY = fy;
                footstepSurface = map.getSurfaceAt(x, y);
                footstepSneak = sneaking;
            }
        } else {
            footstepTimer = 0f;
        }

        // --- Sonar Sense -----------------------------------------------------
        if (input.consumeButton(InputManager.BTN_SONAR) && sonarCooldown <= 0f
                && stamina >= SONAR_COST) {
            stamina -= SONAR_COST;
            sonarCooldown = SONAR_COOLDOWN;
            pulseFired = true;
        }

        // --- Interact ----------------------------------------------------------
        if (input.consumeButton(InputManager.BTN_INTERACT)) {
            interactRequested = true;
        }
    }

    private boolean followPath(float delta) {
        if (repathTimer <= 0f) {
            repathTimer = 0.5f;
            List<android.graphics.Point> found = pathfinding.findPath(x, y, moveTargetX, moveTargetY);
            pathBuffer.clear();
            pathBuffer.addAll(found);
            pathIndex = 0;
        } else {
            repathTimer -= delta;
        }
        if (pathBuffer.isEmpty()) {
            return false;
        }
        while (pathIndex < pathBuffer.size()) {
            android.graphics.Point p = pathBuffer.get(pathIndex);
            float tx = p.x * TileMapRenderer.TILE + TileMapRenderer.TILE * 0.5f;
            float ty = p.y * TileMapRenderer.TILE + TileMapRenderer.TILE * 0.5f;
            float dx = tx - x;
            float dy = ty - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < 8f) {
                pathIndex++;
                continue;
            }
            float speed = sneaking ? SNEAK_SPEED : WALK_SPEED;
            float step = speed * delta;
            if (step >= d) {
                x = tx;
                y = ty;
                pathIndex++;
                continue;
            }
            vx = dx / d * speed;
            vy = dy / d * speed;
            float nx = x + vx * delta;
            float ny = y + vy * delta;
            if (map.isWalkableRect(nx - width * 0.5f, y - height * 0.5f,
                    nx + width * 0.5f, y + height * 0.5f)) {
                x = nx;
            }
            if (map.isWalkableRect(x - width * 0.5f, ny - height * 0.5f,
                    x + width * 0.5f, ny + height * 0.5f)) {
                y = ny;
            }
            targetRotation = (float) Math.atan2(vy, vx);
            return true;
        }
        return false;
    }

    private void clampToMap() {
        float half = TileMapRenderer.TILE * 0.5f;
        if (x < half) {
            x = half;
        }
        if (y < half) {
            y = half;
        }
        if (x > map.getMapWidthPx() - half) {
            x = map.getMapWidthPx() - half;
        }
        if (y > map.getMapHeightPx() - half) {
            y = map.getMapHeightPx() - half;
        }
    }

    // --- Public API for the scene / AI / HUD --------------------------------

    /** True on the frame the player fired a Sonar Sense pulse. */
    public boolean consumePulseFired() {
        boolean f = pulseFired;
        pulseFired = false;
        return f;
    }

    public boolean consumeInteractRequest() {
        boolean r = interactRequested;
        interactRequested = false;
        return r;
    }

    // Footstep request consumed by SceneManager (plays via AudioManager).
    public boolean footstepRequested;
    public float footstepX;
    public float footstepY;
    public int footstepSurface;
    public boolean footstepSneak;

    public boolean consumeFootstepRequest() {
        boolean f = footstepRequested;
        footstepRequested = false;
        return f;
    }

    public void setMoveTarget(float tx, float ty) {
        hasMoveTarget = true;
        moveTargetX = tx;
        moveTargetY = ty;
        repathTimer = 0f;
    }

    public void clearMoveTarget() {
        hasMoveTarget = false;
    }

    public boolean hasMoveTarget() {
        return hasMoveTarget;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public boolean isHidden() {
        return hidden;
    }

    public float getStamina() {
        return stamina;
    }

    public void setStamina(float value) {
        stamina = Math.max(0f, Math.min(MAX_STAMINA, value));
    }

    public float getSonarCooldownRemaining() {
        return sonarCooldown > 0f ? sonarCooldown : 0f;
    }

    public boolean isSonarReady() {
        return sonarCooldown <= 0f && stamina >= SONAR_COST;
    }

    public float getRotation() {
        return rotation;
    }

    public boolean isVulnerable() {
        return invincibleTimer <= 0f;
    }

    public float getHitRadius() {
        return HIT_RADIUS;
    }

    public void grantInvincibility(float seconds) {
        invincibleTimer = Math.max(invincibleTimer, seconds);
    }

    public void setCheckpoint(float cx, float cy) {
        checkpointX = cx;
        checkpointY = cy;
    }

    public float getCheckpointX() {
        return checkpointX;
    }

    public float getCheckpointY() {
        return checkpointY;
    }

    public void respawn() {
        x = checkpointX;
        y = checkpointY;
        vx = 0f;
        vy = 0f;
        hasMoveTarget = false;
        stamina = MAX_STAMINA;
        sonarCooldown = 0f;
        invincibleTimer = 1.5f;
        active = true;
    }

    public void teleport(float nx, float ny) {
        x = nx;
        y = ny;
        hasMoveTarget = false;
    }

    /** HearingLogic movement mode used by AI. */
    public int getMovementMode() {
        if (sneaking) {
            return HearingLogic.MODE_SNEAK;
        }
        if (input.isJoystickActive()) {
            float mag = (float) Math.sqrt(input.getJoystickX() * input.getJoystickX()
                    + input.getJoystickY() * input.getJoystickY());
            if (mag > RUN_THRESHOLD) {
                return HearingLogic.MODE_RUN;
            }
            if (mag > 0.12f) {
                return HearingLogic.MODE_WALK;
            }
        } else if (hasMoveTarget) {
            return HearingLogic.MODE_RUN;
        }
        return HearingLogic.MODE_IDLE;
    }

    @Override
    public void draw(Canvas canvas) {
        float bob = (float) Math.sin(animTimer * 10f) * (sneaking ? 1.5f : 3f);
        float cy = y + bob;
        float r = HIT_RADIUS;

        // Invincibility flicker after respawn.
        if (invincibleTimer > 0f && ((int) (animTimer * 10f)) % 2 == 0) {
            canvas.drawCircle(x, cy, r + 3f, glowPaint);
        }

        // Shadow silhouette.
        canvas.drawOval(x - r, y + r * 0.7f, x + r, y + r + 4f, shadowPaint());

        // Body.
        canvas.drawCircle(x, cy, r, bodyPaint);
        // Direction indicator (visor).
        float dx = (float) Math.cos(rotation);
        float dy = (float) Math.sin(rotation);
        canvas.drawLine(x + dx * 3f, cy + dy * 3f,
                x + dx * (r + 5f), cy + dy * (r + 5f), detailPaint);
        // Second visor line for a "sonar array" look.
        float px = -dy;
        float py = dx;
        canvas.drawLine(x + px * 4f + dx * 2f, cy + py * 4f + dy * 2f,
                x + px * 7f + dx * 2f, cy + py * 7f + dy * 2f, detailPaint);

        // Sneak pose: lower hat line.
        if (sneaking) {
            canvas.drawLine(x - r + 3f, cy - r + 2f, x + r - 3f, cy - r + 2f, detailPaint);
        }
    }

    private Paint cachedShadow;

    private Paint shadowPaint() {
        if (cachedShadow == null) {
            cachedShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            cachedShadow.setARGB(70, 0, 0, 0);
        }
        return cachedShadow;
    }

    @Override
    public void reset() {
        super.reset();
        stamina = MAX_STAMINA;
        sonarCooldown = 0f;
        invincibleTimer = 0f;
        hasMoveTarget = false;
        hidden = false;
        sneaking = false;
    }
}
