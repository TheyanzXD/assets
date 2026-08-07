package com.thelostecho.game.entities;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Frequency puzzle. The player gets a Walkman that can play three tones
 * (LOW / MID / HIGH). Playing the correct sequence lights up glowing runes;
 * an incorrect tone resets the sequence. Solving it can unlock a door or
 * disable the turret attached to this puzzle.
 */
public final class AcousticPuzzle extends GameObject {

    public static final int TONE_LOW = 0;
    public static final int TONE_MID = 1;
    public static final int TONE_HIGH = 2;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint runePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint solvedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int[] sequence = new int[]{0, 1, 2, 1};
    private int[] inputHistory = new int[8];
    private int inputCount = 0;
    private boolean solved = false;
    private boolean solveFlag = false;
    private int boundTurretIndex = -1;
    private String boundDoorId = "";
    private float animTimer = 0f;

    public AcousticPuzzle() {
        width = 48f;
        height = 36f;
        basePaint.setARGB(255, 60, 70, 90);
        runePaint.setARGB(255, 0, 220, 190);
        solvedPaint.setARGB(255, 40, 255, 140);
    }

    public void init(float x, float y, int[] seq) {
        this.x = x;
        this.y = y;
        this.sequence = seq;
        inputCount = 0;
        solved = false;
        solveFlag = false;
        active = true;
    }

    /** Bind the effects of this puzzle to a turret index and/or a door id. */
    public void bind(int turretIndex, String doorId) {
        boundTurretIndex = turretIndex;
        boundDoorId = doorId;
    }

    /** Called by the Walkman UI when the player plays a tone. */
    public void pressTone(int tone) {
        if (solved) {
            return;
        }
        if (inputCount < inputHistory.length) {
            inputHistory[inputCount] = tone;
            inputCount++;
        }
        if (!matchesSoFar()) {
            inputCount = 0;
        } else if (inputCount == sequence.length) {
            solved = true;
            solveFlag = true;
        }
    }

    private boolean matchesSoFar() {
        for (int i = 0; i < inputCount; i++) {
            if (inputHistory[i] != sequence[i]) {
                return false;
            }
        }
        return true;
    }

    public boolean consumeSolveFlag() {
        boolean f = solveFlag;
        solveFlag = false;
        return f;
    }

    public boolean isSolved() {
        return solved;
    }

    public int getProgress() {
        return inputCount;
    }

    public int getLength() {
        return sequence.length;
    }

    public int getToneAt(int i) {
        return i >= 0 && i < sequence.length ? sequence[i] : -1;
    }

    public int getBoundTurretIndex() {
        return boundTurretIndex;
    }

    public String getBoundDoorId() {
        return boundDoorId;
    }

    @Override
    public void update(float delta) {
        animTimer += delta;
    }

    @Override
    public void draw(Canvas canvas) {
        if (!active) {
            return;
        }
        float pulse = 1f + 0.06f * (float) Math.sin(animTimer * 3f);
        canvas.drawRoundRect(x - width * 0.5f, y - height * 0.5f,
                x + width * 0.5f, y + height * 0.5f, 8f, 8f, basePaint);
        // Runes: lit up to the current progress.
        int n = sequence.length;
        float spacing = width / (n + 1);
        for (int i = 0; i < n; i++) {
            float rx = x - width * 0.5f + spacing * (i + 1);
            boolean lit = solved || i < inputCount;
            float rr = (lit ? 5f : 3f) * pulse;
            canvas.drawCircle(rx, y, rr, solved ? solvedPaint : runePaint);
        }
        if (solved) {
            canvas.drawLine(x - width * 0.5f + 8f, y,
                    x + width * 0.5f - 8f, y, solvedPaint);
        }
    }

    @Override
    public void reset() {
        super.reset();
        inputCount = 0;
        solved = false;
        solveFlag = false;
    }
}
