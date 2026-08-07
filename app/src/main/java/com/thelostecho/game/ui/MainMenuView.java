package com.thelostecho.game.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.Random;

/**
 * Title screen: game logo, drifting particle background and the main menu
 * buttons. Returns an action code per tap; the scene performs the action.
 */
public final class MainMenuView {

    public static final int ACTION_NONE = 0;
    public static final int ACTION_NEW_GAME = 1;
    public static final int ACTION_CONTINUE = 2;
    public static final int ACTION_SETTINGS = 3;
    public static final int ACTION_CREDITS = 4;
    public static final int ACTION_EXIT = 5;

    private static final int BUTTON_COUNT = 5;
    private static final int PARTICLE_COUNT = 46;

    private static final class Btn {
        final RectF rect = new RectF();
        String label;
        int action;
    }

    private static final class Star {
        float x;
        float y;
        float speed;
        float size;
        float phase;
    }

    private final Btn[] buttons;
    private final Star[] stars;
    private final Random rng = new Random();
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean hasContinue = false;
    private float animTimer = 0f;
    private float viewW = 1f;
    private float viewH = 1f;
    private float density = 1f;

    public MainMenuView() {
        buttons = new Btn[BUTTON_COUNT];
        for (int i = 0; i < BUTTON_COUNT; i++) {
            buttons[i] = new Btn();
        }
        stars = new Star[PARTICLE_COUNT];
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            stars[i] = new Star();
        }
        titlePaint.setColor(0xFF7FD8FF);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setFakeBoldText(true);
        subPaint.setColor(0xFFA8B8C8);
        subPaint.setTextAlign(Paint.Align.CENTER);
        btnPaint.setARGB(150, 30, 42, 60);
        btnTextPaint.setColor(0xFFE8F0FF);
        btnTextPaint.setTextAlign(Paint.Align.CENTER);
        starPaint.setARGB(200, 200, 230, 255);

        buttons[0].label = "NEW GAME";
        buttons[0].action = ACTION_NEW_GAME;
        buttons[1].label = "CONTINUE";
        buttons[1].action = ACTION_CONTINUE;
        buttons[2].label = "SETTINGS";
        buttons[2].action = ACTION_SETTINGS;
        buttons[3].label = "CREDITS";
        buttons[3].action = ACTION_CREDITS;
        buttons[4].label = "EXIT";
        buttons[4].action = ACTION_EXIT;
    }

    public void setHasContinue(boolean has) {
        hasContinue = has;
    }

    public void update(float delta) {
        animTimer += delta;
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            Star s = stars[i];
            s.y += s.speed * delta;
            s.x += (float) Math.sin(animTimer + s.phase) * 4f * delta;
            if (s.y > viewH + 4f) {
                s.y = -4f;
                s.x = rng.nextFloat() * viewW;
            }
        }
    }

    public int handleTap(float x, float y) {
        for (int i = 0; i < BUTTON_COUNT; i++) {
            if (buttons[i].rect.contains(x, y)) {
                return buttons[i].action;
            }
        }
        return ACTION_NONE;
    }

    public void draw(Canvas canvas, float w, float h, float density) {
        this.viewW = w;
        this.viewH = h;
        this.density = density;
        float d = density;

        canvas.drawColor(0xFF05070F);

        // Starfield.
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            Star s = stars[i];
            float a = 120f + 60f * (float) Math.sin(animTimer * 2f + s.phase);
            starPaint.setAlpha((int) a);
            canvas.drawCircle(s.x, s.y, s.size, starPaint);
        }

        // Title.
        float titleY = h * 0.24f;
        titlePaint.setTextSize(64f * d);
        canvas.drawText("THE LOST ECHO", w * 0.5f, titleY, titlePaint);
        subPaint.setTextSize(17f * d);
        canvas.drawText("a narrative-stealth sonar game", w * 0.5f, titleY + 34f * d, subPaint);

        // Buttons.
        float btnW = 260f * d;
        float btnH = 54f * d;
        float startY = h * 0.42f;
        float gap = 66f * d;
        btnTextPaint.setTextSize(19f * d);
        for (int i = 0; i < BUTTON_COUNT; i++) {
            Btn b = buttons[i];
            if (i == 1 && !hasContinue) {
                continue; // skip CONTINUE when no save exists
            }
            float cx = w * 0.5f - btnW * 0.5f;
            b.rect.set(cx, startY, cx + btnW, startY + btnH);
            canvas.drawRoundRect(b.rect, 10f * d, 10f * d, btnPaint);
            canvas.drawText(b.label, w * 0.5f, startY + btnH * 0.65f, btnTextPaint);
            startY += gap;
        }
    }

    public void dispose() {
    }
}
