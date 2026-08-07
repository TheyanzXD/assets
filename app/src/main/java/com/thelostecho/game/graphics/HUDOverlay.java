package com.thelostecho.game.graphics;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import com.thelostecho.game.core.InputManager;
import com.thelostecho.game.entities.Player;
import com.thelostecho.game.managers.GameStateManager;
import com.thelostecho.game.managers.InventoryManager;
import com.thelostecho.game.managers.QuestManager;

/**
 * All on-screen UI: virtual joystick, action buttons, stamina bar, sonar
 * cooldown ring, detection glow, quest tracker and the inventory panel. Draws
 * in screen space using the same button rectangles owned by InputManager.
 */
public final class HUDOverlay {

    public static final int DETECTION_NONE = 0;
    public static final int DETECTION_SUSPICIOUS = 1;
    public static final int DETECTION_ALERT = 2;

    private final Paint joystickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint joystickKnobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF buttonRect = new RectF();
    private final RectF arcRect = new RectF();
    private final RectF barRect = new RectF();
    private final RectF panelRect = new RectF();
    private final RectF itemRect = new RectF();

    private final float[] joystickVec = new float[2];
    private LinearGradient staminaGradient;
    private float gradientW = 0f;
    private float gradientH = 0f;

    private int detectionLevel = DETECTION_NONE;
    private boolean inventoryOpen = false;
    private boolean debugFps = false;

    private String staminaText = "";
    private String cooldownText = "";
    private String fpsText = "";

    public HUDOverlay() {
        joystickPaint.setARGB(90, 255, 255, 255);
        joystickPaint.setStyle(Paint.Style.STROKE);
        joystickKnobPaint.setARGB(140, 200, 240, 255);
        joystickKnobPaint.setStyle(Paint.Style.STROKE);
        buttonPaint.setARGB(110, 40, 50, 70);
        buttonActivePaint.setARGB(170, 90, 180, 220);
        iconPaint.setARGB(255, 230, 240, 250);
        barBackPaint.setARGB(120, 10, 12, 20);
        textPaint.setARGB(255, 235, 240, 250);
        textPaint.setTextSize(15f);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(8f);
        panelPaint.setARGB(215, 16, 18, 30);
    }

    public void setDetectionLevel(int level) {
        detectionLevel = level;
    }

    public int getDetectionLevel() {
        return detectionLevel;
    }

    public void setInventoryOpen(boolean open) {
        inventoryOpen = open;
    }

    public boolean isInventoryOpen() {
        return inventoryOpen;
    }

    public void setDebugFps(boolean on) {
        debugFps = on;
    }

    public void update(float delta, Player player, GameStateManager gsm) {
        if (player != null) {
            int staminaInt = (int) player.getStamina();
            String s = String.valueOf(staminaInt);
            if (!s.equals(staminaText)) {
                staminaText = s;
            }
            int cd = (int) (player.getSonarCooldownRemaining() * 100f);
            String c = String.valueOf(cd);
            if (!c.equals(cooldownText)) {
                cooldownText = c;
            }
        }
    }

    /** Screen-space drawing; call after world rendering. */
    public void draw(Canvas canvas, InputManager input, Player player,
                     QuestManager quests, GameStateManager gsm, float fps) {
        if (input == null) {
            return;
        }
        float density = input.getDensity();
        float w = canvas.getWidth();
        float h = canvas.getHeight();

        drawJoystick(canvas, input, density);
        drawButtons(canvas, input, player, density);
        drawStaminaBar(canvas, player, w, density);
        drawDetectionGlow(canvas, w, h);
        drawQuestTracker(canvas, quests, density);
        if (inventoryOpen) {
            drawInventory(canvas, input, w, h, density);
        }
        if (debugFps) {
            fpsText = "FPS " + (int) fps;
            textPaint.setColor(0xFF80FF80);
            canvas.drawText(fpsText, 8f, 18f * density, textPaint);
        }
        if (gsm != null && gsm.isPaused()) {
            textPaint.setColor(0xFFFFFFFF);
            canvas.drawText("PAUSED", w * 0.5f - 40f, h * 0.3f, textPaint);
        }
    }

    private void drawJoystick(Canvas canvas, InputManager input, float d) {
        if (!input.isDpadScheme()) {
            return;
        }
        float originX = input.getJoystickOriginX();
        float originY = input.getJoystickOriginY();
        float radius = input.getJoystickRadius();
        joystickPaint.setStrokeWidth(4f * d);
        joystickKnobPaint.setStrokeWidth(4f * d);
        if (input.isJoystickActive()) {
            joystickPaint.setARGB(120, 255, 255, 255);
        } else {
            joystickPaint.setARGB(70, 255, 255, 255);
        }
        canvas.drawCircle(originX, originY, radius, joystickPaint);
        float knobX = originX + input.getJoystickX() * radius * 0.6f;
        float knobY = originY + input.getJoystickY() * radius * 0.6f;
        canvas.drawCircle(knobX, knobY, radius * 0.42f, joystickKnobPaint);
    }

    private void drawButtons(Canvas canvas, InputManager input, Player player, float d) {
        boolean sonarDown = input.isButtonDown(InputManager.BTN_SONAR);
        drawButtonBase(canvas, input, InputManager.BTN_SONAR, sonarDown);
        // Sonar cooldown ring.
        if (player != null) {
            input.getButtonRect(InputManager.BTN_SONAR, buttonRect);
            float cx = buttonRect.centerX();
            float cy = buttonRect.centerY();
            float r = buttonRect.width() * 0.5f + 4f * d;
            arcRect.set(cx - r, cy - r, cx + r, cy + r);
            float cd = player.getSonarCooldownRemaining() / Player.SONAR_COOLDOWN;
            float sweep = (1f - cd) * 360f;
            iconPaint.setColor(0xFF80FFD0);
            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeWidth(4f * d);
            canvas.drawArc(arcRect, -90f, sweep, false, iconPaint);
            iconPaint.setStyle(Paint.Style.FILL);
        }
        drawButtonBase(canvas, input, InputManager.BTN_INTERACT,
                input.isButtonDown(InputManager.BTN_INTERACT));
        drawButtonBase(canvas, input, InputManager.BTN_WALKMAN,
                input.isButtonDown(InputManager.BTN_WALKMAN));
        drawButtonBase(canvas, input, InputManager.BTN_INVENTORY,
                input.isButtonDown(InputManager.BTN_INVENTORY) || inventoryOpen);
        drawButtonBase(canvas, input, InputManager.BTN_PAUSE,
                input.isButtonDown(InputManager.BTN_PAUSE));

        // Icons.
        drawSonarIcon(canvas, input, d);
        drawInteractIcon(canvas, input, d);
        drawWalkmanIcon(canvas, input, d);
        drawInventoryIcon(canvas, input, d);
        drawPauseIcon(canvas, input, d);
    }

    private void drawButtonBase(Canvas canvas, InputManager input, int btn, boolean active) {
        input.getButtonRect(btn, buttonRect);
        canvas.drawCircle(buttonRect.centerX(), buttonRect.centerY(),
                buttonRect.width() * 0.5f, active ? buttonActivePaint : buttonPaint);
    }

    private void drawSonarIcon(Canvas canvas, InputManager input, float d) {
        input.getButtonRect(InputManager.BTN_SONAR, buttonRect);
        float cx = buttonRect.centerX();
        float cy = buttonRect.centerY();
        iconPaint.setColor(0xFFA0E8FF);
        canvas.drawCircle(cx, cy, 14f * d, iconPaint);
        canvas.drawCircle(cx, cy, 7f * d, iconPaint);
    }

    private void drawInteractIcon(Canvas canvas, InputManager input, float d) {
        input.getButtonRect(InputManager.BTN_INTERACT, buttonRect);
        float cx = buttonRect.centerX();
        float cy = buttonRect.centerY();
        iconPaint.setColor(0xFFFFFFFF);
        canvas.drawRect(cx - 8f * d, cy - 6f * d, cx + 8f * d, cy + 8f * d, iconPaint);
    }

    private void drawWalkmanIcon(Canvas canvas, InputManager input, float d) {
        input.getButtonRect(InputManager.BTN_WALKMAN, buttonRect);
        float cx = buttonRect.centerX();
        float cy = buttonRect.centerY();
        iconPaint.setColor(0xFFFFE082);
        canvas.drawCircle(cx - 7f * d, cy, 5f * d, iconPaint);
        canvas.drawCircle(cx + 7f * d, cy, 5f * d, iconPaint);
        canvas.drawLine(cx - 2f * d, cy + 5f * d, cx + 2f * d, cy - 5f * d, iconPaint);
    }

    private void drawInventoryIcon(Canvas canvas, InputManager input, float d) {
        input.getButtonRect(InputManager.BTN_INVENTORY, buttonRect);
        float cx = buttonRect.centerX();
        float cy = buttonRect.centerY();
        iconPaint.setColor(0xFFC5E0FF);
        canvas.drawRoundRect(cx - 8f * d, cy - 10f * d, cx + 8f * d,
                cy + 8f * d, 3f * d, 3f * d, iconPaint);
        canvas.drawRect(cx - 4f * d, cy - 14f * d, cx + 4f * d, cy - 8f * d, iconPaint);
    }

    private void drawPauseIcon(Canvas canvas, InputManager input, float d) {
        input.getButtonRect(InputManager.BTN_PAUSE, buttonRect);
        float cx = buttonRect.centerX();
        float cy = buttonRect.centerY();
        iconPaint.setColor(0xFFDDDDFF);
        canvas.drawRect(cx - 7f * d, cy - 9f * d, cx - 2f * d, cy + 9f * d, iconPaint);
        canvas.drawRect(cx + 2f * d, cy - 9f * d, cx + 7f * d, cy + 9f * d, iconPaint);
    }

    private void drawStaminaBar(Canvas canvas, Player player, float w, float d) {
        if (player == null) {
            return;
        }
        float barW = 220f * d;
        float barH = 12f * d;
        float x = w * 0.5f - barW * 0.5f;
        float y = 40f * d;
        barRect.set(x, y, x + barW, y + barH);
        canvas.drawRoundRect(barRect, barH * 0.5f, barH * 0.5f, barBackPaint);

        float ratio = Math.max(0f, Math.min(1f, player.getStamina() / Player.MAX_STAMINA));
        if (staminaGradient == null || gradientW != barW || gradientH != barH) {
            gradientW = barW;
            gradientH = barH;
            staminaGradient = new LinearGradient(0f, 0f, barW, 0f,
                    new int[]{0xFFFF5050, 0xFFFFD050, 0xFF50FF90},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        }
        barFillPaint.setShader(staminaGradient);
        float fillW = barW * ratio;
        if (fillW > 2f) {
            canvas.drawRoundRect(x, y, x + fillW, y + barH, barH * 0.5f, barH * 0.5f, barFillPaint);
        }
        textPaint.setColor(0xFFE8FFF0);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(staminaText, w * 0.5f, y + barH + 16f * d, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawDetectionGlow(Canvas canvas, float w, float h) {
        if (detectionLevel == DETECTION_NONE) {
            return;
        }
        int alpha;
        if (detectionLevel == DETECTION_ALERT) {
            alpha = (int) (120 + 70 * (0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 160.0)));
        } else {
            alpha = 55;
        }
        glowPaint.setARGB(alpha, 255, 40, 40);
        canvas.drawRect(6f, 6f, w - 6f, h - 6f, glowPaint);
    }

    private void drawQuestTracker(Canvas canvas, QuestManager quests, float d) {
        if (quests == null) {
            return;
        }
        java.util.List<QuestManager.Quest> active = quests.getActiveQuests();
        float y = 90f * d;
        textPaint.setColor(0xFFE0E8FF);
        textPaint.setTextSize(14f * d);
        for (int i = 0; i < active.size(); i++) {
            QuestManager.Quest q = active.get(i);
            canvas.drawText("> " + q.getTitle(), 12f * d, y, textPaint);
            y += 18f * d;
            canvas.drawText("   " + q.getProgressText(), 12f * d, y, textPaint);
            y += 20f * d;
        }
        textPaint.setTextSize(15f);
    }

    private void drawInventory(Canvas canvas, InputManager input, float w, float h, float d) {
        panelRect.set(w * 0.2f, h * 0.18f, w * 0.8f, h * 0.82f);
        canvas.drawRoundRect(panelRect, 12f * d, 12f * d, panelPaint);
        textPaint.setColor(0xFFCCCCEE);
        textPaint.setTextSize(18f * d);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("INVENTORY", panelRect.centerX(), panelRect.top + 30f * d, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);

        InventoryManager inv = InventoryManager.getInstance();
        java.util.List<String> items = inv.getItems();
        float cell = 46f * d;
        float startX = panelRect.left + 20f * d;
        float startY = panelRect.top + 50f * d;
        int perRow = (int) ((panelRect.width() - 40f * d) / cell);
        if (perRow < 1) {
            perRow = 1;
        }
        textPaint.setTextSize(12f * d);
        for (int i = 0; i < items.size(); i++) {
            int row = i / perRow;
            int col = i % perRow;
            float ix = startX + col * cell;
            float iy = startY + row * cell;
            itemRect.set(ix, iy, ix + cell * 0.85f, iy + cell * 0.85f);
            canvas.drawRoundRect(itemRect, 6f * d, 6f * d, buttonActivePaint);
            InventoryManager.ItemDef def = inv.getDef(items.get(i));
            String name = def != null ? def.name : items.get(i);
            canvas.drawText(name, ix + 4f * d, iy + cell * 0.55f, textPaint);
            if (iy > panelRect.bottom - cell) {
                break;
            }
        }
    }

    public void dispose() {
        staminaGradient = null;
    }
}
