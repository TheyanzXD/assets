package com.thelostecho.game.story;

import android.content.Context;
import android.content.SharedPreferences;

import com.thelostecho.game.core.SceneManager;
import com.thelostecho.game.graphics.MapTransitionEffect;
import com.thelostecho.game.managers.GameStateManager;

/**
 * Handles the two moral choices in the Data Center. Interacting with the left
 * console triggers Choice A (Save Parents), the right one Choice B (Paralyze
 * the City). The choice is recorded in the narrative, the game transitions to
 * the ENDING_CHOICE state, and once the fade completes the scene shows the
 * matching EndingSummary. Each ending unlocks a local achievement.
 */
public final class ChoiceEventHandler {

    public static final int CHOICE_SAVE_PARENTS = 1;
    public static final int CHOICE_PARALYZE_CITY = 2;

    private static ChoiceEventHandler instance;

    private final SharedPreferences achievements;
    private int pendingEnding = 0;

    private ChoiceEventHandler(Context context) {
        achievements = context.getApplicationContext()
                .getSharedPreferences("lostecho_achievements", Context.MODE_PRIVATE);
    }

    public static synchronized ChoiceEventHandler getInstance(Context context) {
        if (instance == null) {
            instance = new ChoiceEventHandler(context);
        }
        return instance;
    }

    /**
     * Called when the player uses one of the two Data Center consoles.
     * The choice cannot be reversed once committed.
     */
    public void handleChoice(SceneManager scene, int choice) {
        if (scene == null) {
            return;
        }
        BranchingNarrative narrative = scene.getNarrative();
        if (narrative.getEndingChoice() != 0) {
            return; // already decided
        }
        narrative.recordChoice(BranchingNarrative.FLAG_ENDING_CHOICE, choice);
        if (choice == CHOICE_SAVE_PARENTS) {
            narrative.setFlag(BranchingNarrative.FLAG_ENDING_SAVE_PARENTS, true);
            unlock("achievement_guardian");
        } else {
            narrative.setFlag(BranchingNarrative.FLAG_ENDING_PARALYZE, true);
            unlock("achievement_liberator");
        }
        pendingEnding = choice;
        scene.setPendingEnding(choice);
        scene.getGameStateManager().setState(GameStateManager.GameState.ENDING_CHOICE);
        scene.getTransition().start(1.0f, 0.4f, 1.2f);
    }

    /** Consumed by the scene when the fade completes at the darkest frame. */
    public int consumePendingEnding() {
        int e = pendingEnding;
        pendingEnding = 0;
        return e;
    }

    private void unlock(String id) {
        try {
            achievements.edit().putBoolean(id, true).apply();
        } catch (Exception ignored) {
        }
    }

    public boolean isAchievementUnlocked(String id) {
        return achievements.getBoolean(id, false);
    }

    public static synchronized void resetInstance() {
        instance = null;
    }

    public static String endingTitle(int choice) {
        return choice == CHOICE_SAVE_PARENTS
                ? "ENDING A - The Guardian's Choice"
                : "ENDING B - The Paralyzed City";
    }

    public static String endingBody(int choice) {
        if (choice == CHOICE_SAVE_PARENTS) {
            return "Raka saved their parents. The city remains under Aethelgard's watchful eye, "
                    + "but the Echo of freedom still hums in every shadow. One day, the signal "
                    + "will go out. Until then, the family survives.";
        }
        return "Raka broadcasts the signal. Every drone in the city goes dark at once. "
                + "The citizens are free - but Raka's parents are unreachable. In the silence "
                + "that follows, the city learns to breathe on its own.";
    }
}
