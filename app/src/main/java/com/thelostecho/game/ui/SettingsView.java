package com.thelostecho.game.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Options screen: SFX/music volume controls, control scheme toggle (virtual
 * joystick vs touch-to-move) and progress reset. Values are applied live
 * through AudioManager and persisted to SharedPreferences.
 */
public final class SettingsView {

    public static final int ACTION_NONE = 0;
    public static final int ACTION_BACK = 1;
    public static final int ACTION_SFX_UP = 2;
    public static final int ACTION_SFX_DOWN = 3;
    public static final int ACTION_MUSIC_UP = 4;
    public static final int ACTION_MUSIC_DOWN = 5;
    public static final int ACTION_TOGGLE_CONTROL = 6;
    public static final int ACTION_RESET = 7;

    private static final int BUTTON_COUNT = 8;

    private static final class Btn {
        final RectF rect = new RectF();
        String label;
        int action;
    }

    private final Btn[] buttons;
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

    private float sfxVolume = 0.9f;
    private float musicVolume = 0.7f;
    private boolean dpadScheme = true;
    private float density = 1f;

    public SettingsView() {
        buttons = new Btn[BUTTON_COUNT];
        for (int i = 0; i < BUTTON_COUNT; i++) {
            buttons[i] = new Btn();
        }
        titlePaint.setColor(0xFF7FD8FF);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setFakeBoldText(true);
        textPaint.setColor(0xFFB8C8D8);
        btnPaint.setARGB(150, 30, 42, 60);
        btnTextPaint.setColor(0xFFE8F0FF);
        btnTextPaint.setTextAlign(Paint.Align.CENTER);
        barPaint.setARGB(120, 10, 12, 20);
        barFillPaint.setARGB(220, 80, 190, 255);

        buttons[0].label = "SFX  +";
        buttons[0].action = ACTION_SFX_UP;
        buttons[1].label = "SFX  -";
        buttons[1].action = ACTION_SFX_DOWN;
        buttons[2].label = "MUSIC +";
        buttons[2].action = ACTION_MUSIC_UP;
        buttons[3].label = "MUSIC -";
        buttons[3].action = ACTION_MUSIC_DOWN;
        buttons[4].label = "TOGGLE CONTROL";
        buttons[4].action = ACTION_TOGGLE_CONTROL;
        buttons[5].label = "RESET PROGRESS";
        buttons[5].action = ACTION_RESET;
        buttons[6].label = "BACK";
        buttons[6].action = ACTION_BACK;
        buttons[7].label = "";
        buttons[7].action = ACTION_NONE;
    }

    public void setVolumes(float sfx, float music) {
        sfxVolume = sfx;
        musicVolume = music;
    }

    public void setDpadScheme(boolean dpad) {
        dpadScheme = dpad;
    }

    public boolean getDpadScheme() {
        return dpadScheme;
    }

    public void update(float delta) {
    }

    public int handleTap(float x, float y) {
        for (int i = 0; i < BUTTON_COUNT; i++) {
            if (buttons[i].action != ACTION_NONE && buttons[i].rect.contains(x, y)) {
                return buttons[i].action;
            }
        }
        return ACTION_NONE;
    }

    public void draw(Canvas canvas, float w, float h, float density) {
        this.density = density;
        float d = density;
        canvas.drawColor(0xFF05070F);

        titlePaint.setTextSize(34f * d);
        canvas.drawText("SETTINGS", w * 0.5f, 90f * d, titlePaint);
        textPaint.setTextSize(16f * d);

        // Volume bars.
        float barW = 260f * d;
        float barH = 14f * d;
        float barY = 150f * d;
        barRect.set(w * 0.5f - barW * 0.5f, barY, w * 0.5f + barW * 0.5f, barY + barH);
        canvas.drawRoundRect(barRect, 7f * d, 7f * d, barPaint);
        barRect.set(w * 0.5f - barW * 0.5f, barY,
                w * 0.5f - barW * 0.5f + barW * sfxVolume, barY + barH);
        canvas.drawRoundRect(barRect, 7f * d, 7f * d, barFillPaint);
        canvas.drawText("SFX VOLUME", w * 0.5f, barY - 12f * d, textPaint);

        float barY2 = 230f * d;
        barRect.set(w * 0.5f - barW * 0.5f, barY2, w * 0.5f + barW * 0.5f, barY2 + barH);
        canvas.drawRoundRect(barRect, 7f * d, 7f * d, barPaint);
        barRect.set(w * 0.5f - barW * 0.5f, barY2,
                w * 0.5f - barW * 0.5f + barW * musicVolume, barY2 + barH);
        canvas.drawRoundRect(barRect, 7f * d, 7f * d, barFillPaint);
        canvas.drawText("MUSIC VOLUME", w * 0.5f, barY2 - 12f * d, textPaint);

        // Buttons in a 2-column grid.
        float btnW = 200f * d;
        float btnH = 50f * d;
        float col1X = w * 0.5f - btnW - 10f * d;
        float col2X = w * 0.5f + 10f * d;
        float startY = 300f * d;
        btnTextPaint.setTextSize(17f * d);
        for (int i = 0; i < BUTTON_COUNT; i++) {
            Btn b = buttons[i];
            if (b.action == ACTION_NONE) {
                continue;
            }
            int column = i % 2;
            int row = i / 2;
            float bx = column == 0 ? col1X : col2X;
            float by = startY + row * (btnH + 14f * d);
            b.rect.set(bx, by, bx + btnW, by + btnH);
            canvas.drawRoundRect(b.rect, 9f * d, 9f * d, btnPaint);
            canvas.drawText(b.label, bx + btnW * 0.5f, by + btnH * 0.65f, btnTextPaint);
        }

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(15f * d);
        String controlMode = dpadScheme ? "Virtual Joystick" : "Touch-to-Move";
        canvas.drawText("Control: " + controlMode, w * 0.5f, h - 90f * d, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    public void dispose() {
    }
}
