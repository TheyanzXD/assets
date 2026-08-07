package com.thelostecho.game.ai;

/**
 * Hearing logic: simple radius test with modifiers from the player's movement
 * mode. Player movement modes are defined here so both AI and the Player entity
 * share one source of truth.
 */
public final class HearingLogic {

    public static final int MODE_IDLE = 0;
    public static final int MODE_WALK = 1;
    public static final int MODE_SNEAK = 2;
    public static final int MODE_RUN = 3;

    private static final float IDLE_MULT = 0.5f;
    private static final float WALK_MULT = 1.0f;
    private static final float SNEAK_MULT = 0.5f;
    private static final float RUN_MULT = 1.5f;

    private HearingLogic() {
    }

    /**
     * @param baseRadius hearing radius of the listener in world units
     * @param playerMode one of the MODE_* constants
     */
    public static boolean hears(float ex, float ey, float px, float py,
                                float baseRadius, int playerMode) {
        float dx = px - ex;
        float dy = py - ey;
        float distSq = dx * dx + dy * dy;
        float effective = baseRadius * modeMultiplier(playerMode);
        return distSq <= effective * effective;
    }

    public static float modeMultiplier(int mode) {
        switch (mode) {
            case MODE_IDLE:
                return IDLE_MULT;
            case MODE_SNEAK:
                return SNEAK_MULT;
            case MODE_RUN:
                return RUN_MULT;
            case MODE_WALK:
            default:
                return WALK_MULT;
        }
    }
}
