package com.thelostecho.game.entities;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.thelostecho.game.managers.InventoryManager;

/**
 * World item that is auto-picked up when the player walks within 40 units.
 * Each item carries an InventoryManager id (keycard_red, usb_drive, ...).
 */
public final class CollectibleItem extends GameObject {

    public static final float PICKUP_RADIUS = 40f;
    public static final float BOBBING_AMPLITUDE = 5f;

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final InventoryManager inventory;
    private String itemId = "";
    private int color;
    private float baseY;
    private float bobPhase;
    private boolean pickedUp;
    private boolean pickupFlag;

    public CollectibleItem(InventoryManager inventory) {
        this.inventory = inventory;
        width = 18f;
        height = 18f;
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(3f);
    }

    public void init(float x, float y, String itemId, int color) {
        this.x = x;
        this.y = y;
        this.baseY = y;
        this.itemId = itemId;
        this.color = color;
        this.bodyPaint.setColor(color);
        this.glowPaint.setColor(color);
        this.bobPhase = (x * 0.01f + y * 0.017f) % 6.2831f;
        this.pickedUp = false;
        this.pickupFlag = false;
        this.active = true;
    }

    public void update(float delta, Player player) {
        if (!active) {
            return;
        }
        bobPhase += delta * 3f;
        y = baseY + (float) Math.sin(bobPhase) * BOBBING_AMPLITUDE;

        if (player != null && player.isActive()) {
            float dx = player.x - x;
            float dy = player.y - y;
            if (dx * dx + dy * dy <= PICKUP_RADIUS * PICKUP_RADIUS) {
                inventory.addItem(itemId);
                pickedUp = true;
                pickupFlag = true;
                active = false;
            }
        }
    }

    /** True exactly once when the player collected this item. */
    public boolean consumePickupFlag() {
        boolean f = pickupFlag;
        pickupFlag = false;
        return f;
    }

    public String getItemId() {
        return itemId;
    }

    public boolean wasPickedUp() {
        return pickedUp;
    }

    @Override
    public void update(float delta) {
    }

    @Override
    public void draw(Canvas canvas) {
        if (!active) {
            return;
        }
        canvas.drawCircle(x, y, 11f, glowPaint);
        canvas.drawCircle(x, y, 8f, bodyPaint);
        canvas.drawLine(x - 4f, y, x + 4f, y, bodyPaint);
        canvas.drawLine(x, y - 4f, x, y + 4f, bodyPaint);
    }

    @Override
    public void reset() {
        super.reset();
        pickedUp = false;
        pickupFlag = false;
        itemId = "";
    }
}
