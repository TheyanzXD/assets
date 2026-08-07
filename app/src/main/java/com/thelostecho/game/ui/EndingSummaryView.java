package com.thelostecho.game.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.thelostecho.game.story.ChoiceEventHandler;

/**
 * End-of-game summary. Shows the unlocked ending, the play statistics (play
 * time, alerts triggered, items found) and offers replay / main menu.
 */
public final class EndingSummaryView {

    public static final int ACTION_NONE = 0;
    public static final int ACTION_REPLAY = 1;
    public static final int ACTION_MAIN_MENU = 2;

    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint statPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF btnRect = new RectF();

    private int endingChoice = 0;
    private long playTimeSeconds = 0L;
    private int alertsTriggered = 0;
    private int itemsFound = 0;

    public void setData(int endingChoice, long playTimeSeconds, int alerts, int items) {
        this.endingChoice = endingChoice;
        this.playTimeSeconds = playTimeSeconds;
        this.alertsTriggered = alerts;
        this.itemsFound = items;
    }

    public int handleTap(float x, float y) {
        if (btnRect.contains(x, y)) {
            return ACTION_REPLAY;
        }
        // Lower half tap returns to the menu.
        if (y > btnRect.bottom + 30f) {
            return ACTION_MAIN_MENU;
        }
        return ACTION_NONE;
    }

    public void draw(Canvas canvas, float w, float h, float density) {
        float d = density;
        canvas.drawColor(0xFF04060C);

        accentPaint.setColor(endingChoice == ChoiceEventHandler.CHOICE_SAVE_PARENTS
                ? 0xFF7FD8FF : 0xFFFFD070);
        titlePaint.setColor(accentPaint.getColor());
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setFakeBoldText(true);
        titlePaint.setTextSize(30f * d);
        canvas.drawText(ChoiceEventHandler.endingTitle(endingChoice), w * 0.5f, 90f * d, titlePaint);

        textPaint.setColor(0xFFD8E0EC);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(16f * d);
        String body = ChoiceEventHandler.endingBody(endingChoice);
        float y = 150f * d;
        float maxWidth = w - 80f * d;
        String remaining = body;
        while (remaining.length() > 0 && y < h * 0.55f) {
            int cut = textPaint.breakText(remaining, true, maxWidth, null);
            if (cut <= 0) {
                cut = 1;
            }
            canvas.drawText(remaining, 0, cut, w * 0.5f, y, textPaint);
            remaining = remaining.substring(cut);
            y += 26f * d;
        }

        statPaint.setColor(0xFFA0B8D0);
        statPaint.setTextAlign(Paint.Align.CENTER);
        statPaint.setTextSize(16f * d);
        long mins = playTimeSeconds / 60L;
        long secs = playTimeSeconds % 60L;
        canvas.drawText("Play time: " + mins + "m " + secs + "s", w * 0.5f, h * 0.62f, statPaint);
        canvas.drawText("Alerts triggered: " + alertsTriggered, w * 0.5f, h * 0.65f, statPaint);
        canvas.drawText("Items found: " + itemsFound, w * 0.5f, h * 0.68f, statPaint);

        // Replay button.
        float btnW = 250f * d;
        float btnH = 54f * d;
        float bx = w * 0.5f - btnW * 0.5f;
        float by = h * 0.78f;
        btnRect.set(bx, by, bx + btnW, by + btnH);
        canvas.drawRoundRect(btnRect, 10f * d, 10f * d, btnPaint);
        btnTextPaint.setColor(0xFFE8F0FF);
        btnTextPaint.setTextAlign(Paint.Align.CENTER);
        btnTextPaint.setTextSize(19f * d);
        canvas.drawText("PLAY AGAIN", w * 0.5f, by + btnH * 0.65f, btnTextPaint);

        statPaint.setTextSize(13f * d);
        canvas.drawText("tap below the button for the main menu", w * 0.5f, by + btnH + 26f * d, statPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    public void dispose() {
    }
}
