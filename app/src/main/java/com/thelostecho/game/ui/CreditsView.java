package com.thelostecho.game.ui;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Scrollable credits. The text drifts upward automatically; tapping anywhere
 * returns to the main menu.
 */
public final class CreditsView {

    public static final int ACTION_NONE = 0;
    public static final int ACTION_BACK = 1;

    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final String[] LINES = {
            "THE LOST ECHO",
            "",
            "a story by Raka",
            "",
            "written & designed in the slums of Aethelgard",
            "",
            "ENGINE",
            "Android Native - Java",
            "SurfaceView + Canvas",
            "",
            "AUDIO",
            "synthesized on device",
            "",
            "With thanks to Meera, Old Juno and Warden Kess",
            "who still remember what the city sounded like",
            "before the drones learned to listen.",
            "",
            "FREQUENCIES LOST AND FOUND",
            "",
            "(tap to return)"
    };

    private float scroll = 0f;
    private float viewH = 1f;
    private float density = 1f;

    public int handleTap(float x, float y) {
        return ACTION_BACK;
    }

    public void update(float delta) {
        scroll += delta * 40f;
    }

    public void reset() {
        scroll = 0f;
    }

    public void draw(Canvas canvas, float w, float h, float density) {
        this.viewH = h;
        this.density = density;
        float d = density;
        canvas.drawColor(0xFF04060C);

        titlePaint.setColor(0xFF7FD8FF);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setFakeBoldText(true);
        titlePaint.setTextSize(30f * d);
        textPaint.setColor(0xFFC8D8E8);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(17f * d);

        float y = h - scroll * d;
        for (int i = 0; i < LINES.length; i++) {
            String line = LINES[i];
            boolean isTitle = i == 0 || (line.equals(line.toUpperCase()) && line.length() > 4);
            if (isTitle) {
                canvas.drawText(line, w * 0.5f, y, titlePaint);
            } else {
                canvas.drawText(line, w * 0.5f, y, textPaint);
            }
            y += (line.isEmpty() ? 20f : 26f) * d;
        }
        if (y < 0f) {
            scroll = 0f;
        }

        hintPaint.setColor(0xFF607080);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setTextSize(13f * d);
        canvas.drawText("tap to return", w * 0.5f, h - 20f * d, hintPaint);
    }

    public void dispose() {
    }
}
