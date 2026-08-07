package com.thelostecho.game.entities;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.thelostecho.game.managers.DialogueManager;

/**
 * A friendly character with a portrait, a name and a dialogue tree entry point.
 * Some NPCs also give quests: interacting triggers DialogueManager, which can
 * in turn start quests via QuestManager.
 */
public final class NPC extends GameObject {

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private String npcName = "";
    private String dialogueId = "";
    private int color;
    private float animTimer = 0f;
    private boolean talking = false;
    private boolean questTriggered = false;
    private float interactHintTimer = 0f;

    public NPC() {
        width = 20f;
        height = 20f;
        accentPaint.setARGB(255, 255, 255, 255);
    }

    public void init(float x, float y, String name, String dialogueId, int color) {
        this.x = x;
        this.y = y;
        this.npcName = name;
        this.dialogueId = dialogueId;
        this.color = color;
        this.bodyPaint.setColor(color);
        this.headPaint.setColor(color);
        animTimer = 0f;
        talking = false;
        questTriggered = false;
        active = true;
    }

    public void update(float delta) {
        animTimer += delta;
        if (interactHintTimer > 0f) {
            interactHintTimer -= delta;
        }
    }

    /** Player pressed interact near this NPC: open their dialogue tree. */
    public boolean startDialogue(DialogueManager dialogueManager) {
        if (!active) {
            return false;
        }
        talking = true;
        interactHintTimer = 0f;
        if (dialogueManager != null) {
            dialogueManager.start(dialogueId);
        }
        return true;
    }

    public void setQuestTriggered(boolean q) {
        questTriggered = q;
    }

    public boolean hasPendingQuest() {
        return questTriggered;
    }

    public String getNpcName() {
        return npcName;
    }

    public String getDialogueId() {
        return dialogueId;
    }

    public boolean isNear(float px, float py, float radius) {
        float dx = px - x;
        float dy = py - y;
        return dx * dx + dy * dy <= radius * radius;
    }

    @Override
    public void draw(Canvas canvas) {
        if (!active) {
            return;
        }
        float bob = (float) Math.sin(animTimer * 2f) * 2f;
        canvas.drawCircle(x, y + bob, 7f, headPaint);
        canvas.drawRect(x - 8f, y + bob + 6f, x + 8f, y + bob + 16f, bodyPaint);
        // Exclamation hint when a quest is available.
        if (hasPendingQuest()) {
            float pulse = 1f + 0.15f * (float) Math.sin(animTimer * 5f);
            canvas.drawCircle(x, y + bob - 12f, 4f * pulse, accentPaint);
        }
    }

    @Override
    public void reset() {
        super.reset();
        talking = false;
        questTriggered = false;
    }
}
