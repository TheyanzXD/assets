package com.thelostecho.game.entities;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.thelostecho.game.managers.InventoryManager;

/**
 * A door / locked terminal gate. Requires a keycard of a specific colour
 * (red / blue / yellow) stored in the inventory; shows a lock icon when the
 * player approaches without the right card. Double doors block the tile map at
 * creation and open permanently once unlocked.
 */
public final class DoorTerminal extends InteractiveProp {

    public static final String KEY_RED = "keycard_red";
    public static final String KEY_BLUE = "keycard_blue";
    public static final String KEY_YELLOW = "keycard_yellow";

    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String requiredKey = KEY_RED;
    private String doorId = "";
    private float openAmount = 0f;
    private boolean playerNear = false;

    public DoorTerminal() {
        super(Kind.DOOR_TERMINAL);
        iconPaint.setARGB(255, 230, 230, 240);
    }

    public void init(float x, float y, String doorId, String requiredKey) {
        super.init(x, y, true);
        this.doorId = doorId;
        this.requiredKey = requiredKey;
        openAmount = 0f;
    }

    public String getDoorId() {
        return doorId;
    }

    public String getRequiredKey() {
        return requiredKey;
    }

    public void setPlayerNear(boolean near) {
        playerNear = near;
    }

    @Override
    public boolean interact(InventoryManager inventory) {
        if (!locked) {
            return false;
        }
        if (inventory.hasItem(requiredKey)) {
            unlock();
            inventory.removeItem(requiredKey);
            return true;
        }
        return false;
    }

    public float getOpenAmount() {
        return openAmount;
    }

    @Override
    public void update(float delta) {
        super.update(delta);
        if (!locked && openAmount < 1f) {
            openAmount = Math.min(1f, openAmount + delta * 1.5f);
        }
    }

    @Override
    public void update(float delta, Player player) {
        super.update(delta, player);
        float dx = player != null ? player.x - x : 0f;
        float dy = player != null ? player.y - y : 0f;
        playerNear = player != null && (dx * dx + dy * dy) < 80f * 80f;
        if (!locked && openAmount < 1f) {
            openAmount = Math.min(1f, openAmount + delta * 1.5f);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (!active) {
            return;
        }
        float hw = width * 0.5f;
        float hh = height * 0.5f;
        float shrink = openAmount * 14f;
        canvas.drawRect(x - hw + shrink, y - hh, x + hw - shrink, y + hh, bodyPaint);
        // Lock icon.
        if (locked && playerNear) {
            float lx = x + hw * 0.35f;
            float ly = y - hh * 0.2f;
            canvas.drawCircle(lx, ly, 6f, iconPaint);
            canvas.drawRect(lx - 5f, ly, lx + 5f, ly + 12f, iconPaint);
            int keyColor;
            if (KEY_RED.equals(requiredKey)) {
                keyColor = 0xFFFF5252;
            } else if (KEY_BLUE.equals(requiredKey)) {
                keyColor = 0xFF4FC3F7;
            } else {
                keyColor = 0xFFFFEE58;
            }
            iconPaint.setColor(keyColor);
            canvas.drawCircle(lx, ly, 4f, iconPaint);
        }
    }

    @Override
    public void reset() {
        super.reset();
        openAmount = 0f;
        playerNear = false;
    }
}
