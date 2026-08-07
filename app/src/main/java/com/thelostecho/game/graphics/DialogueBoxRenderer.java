package com.thelostecho.game.graphics;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Bottom dialogue box with a typewriter effect (character by character), the
 * speaker's name, a blinking next-page indicator, and double-tap to skip.
 * Text is advanced through DialogueManager by the scene.
 */
public final class DialogueBoxRenderer {

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    private String speaker = "";
    private String fullText = "";
    private String shownText = "";
    private float typeTimer = 0f;
    private float animTimer = 0f;
    private boolean busy = false;
    private boolean pageAdvance = false;

    private static final float CHARS_PER_SECOND = 45f;

    public DialogueBoxRenderer() {
        boxPaint.setARGB(220, 12, 14, 24);
        namePaint.setARGB(255, 255, 210, 120);
        textPaint.setARGB(255, 235, 240, 250);
        indicatorPaint.setARGB(255, 255, 255, 255);
    }

    /** Sets a new line. Resets the typewriter. */
    public void setLine(String speakerName, String text) {
        speaker = speakerName != null ? speakerName : "";
        fullText = text != null ? text : "";
        typeTimer = 0f;
        shownText = "";
        busy = true;
        pageAdvance = false;
    }

    public void update(float delta) {
        animTimer += delta;
        if (!busy) {
            return;
        }
        if (shownText.length() < fullText.length()) {
            typeTimer += delta;
            int chars = (int) (typeTimer * CHARS_PER_SECOND);
            if (chars > fullText.length()) {
                chars = fullText.length();
            }
            if (chars > shownText.length()) {
                shownText = fullText.substring(0, chars);
            }
        } else {
            // Fully typed; next tap advances/skips the page.
            pageAdvance = true;
        }
    }

    /** Double-tap skip / single tap on the box advances. */
    public void tap() {
        if (!busy) {
            return;
        }
        if (shownText.length() < fullText.length()) {
            shownText = fullText;
        } else {
            pageAdvance = true;
        }
    }

    public boolean consumePageAdvance() {
        boolean a = pageAdvance;
        pageAdvance = false;
        return a;
    }

    public boolean isBusy() {
        return busy;
    }

    /** Copies the box rect used by the last draw() call into the caller's rect. */
    public void getLastBox(RectF out) {
        out.set(box);
    }

    public void finish() {
        busy = false;
        shownText = "";
        fullText = "";
        speaker = "";
        pageAdvance = false;
    }

    public void draw(Canvas canvas, float w, float h, float density) {
        if (!busy) {
            return;
        }
        float d = density;
        float pad = 16f * d;
        float boxH = 110f * d;
        box.set(pad, h - boxH - pad, w - pad, h - pad);
        canvas.drawRoundRect(box, 12f * d, 12f * d, boxPaint);

        textPaint.setTextSize(17f * d);
        namePaint.setTextSize(15f * d);
        canvas.drawText(speaker, box.left + 14f * d, box.top + 22f * d, namePaint);

        // Wrap the shown text (simple greedy wrapping on the box width).
        float maxWidth = box.width() - 28f * d;
        String remaining = shownText;
        float lineY = box.top + 52f * d;
        textPaint.setTextSize(17f * d);
        while (remaining.length() > 0 && lineY < box.bottom - 20f * d) {
            int cut = textPaint.breakText(remaining, true, maxWidth, null);
            if (cut <= 0) {
                cut = 1;
            }
            canvas.drawText(remaining, 0, cut, box.left + 14f * d, lineY, textPaint);
            remaining = remaining.substring(cut);
            lineY += 22f * d;
        }

        // Blinking triangle indicator when the line is fully typed.
        if (shownText.length() >= fullText.length()
                && ((int) (animTimer * 2f)) % 2 == 0) {
            canvas.drawText(">", box.right - 24f * d, box.bottom - 12f * d,
                    indicatorPaint);
        }
    }

    public void dispose() {
        finish();
    }
}
