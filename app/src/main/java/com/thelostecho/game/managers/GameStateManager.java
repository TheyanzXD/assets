package com.thelostecho.game.managers;

import java.util.ArrayDeque;

/**
 * Stack-based game state machine. UI states (menu/settings/credits/ending) live
 * at the top while gameplay states keep the world alive underneath; the back
 * button pops the stack instead of exiting the app mid-game.
 */
public final class GameStateManager {

    public enum GameState {
        LOADING, MAIN_MENU, TUTORIAL_SLUM, LAB_FACILITY, SUB_BASEMENT,
        DATA_CENTER, PAUSE, GAME_OVER, ENDING_CHOICE, CREDITS
    }

    public static final int AREA_SLUM = 0;
    public static final int AREA_LAB = 1;
    public static final int AREA_BASEMENT = 2;
    public static final int AREA_DATA = 3;

    private final ArrayDeque<GameState> stack = new ArrayDeque<GameState>();

    public GameStateManager() {
        stack.push(GameState.MAIN_MENU);
    }

    public GameState current() {
        GameState top = stack.peek();
        return top != null ? top : GameState.MAIN_MENU;
    }

    public void push(GameState state) {
        stack.push(state);
    }

    public void pop() {
        if (stack.size() > 1) {
            stack.pop();
        }
    }

    /** Replaces the top of the stack. */
    public void setState(GameState state) {
        if (stack.isEmpty()) {
            stack.push(state);
        } else {
            stack.pop();
            stack.push(state);
        }
    }

    /** Handles the Android back button; returns true if consumed. */
    public boolean handleBack() {
        GameState top = current();
        if (top == GameState.PAUSE || top == GameState.ENDING_CHOICE) {
            pop();
            return true;
        }
        if (top == GameState.MAIN_MENU) {
            return false; // allow the activity to exit
        }
        return true;
    }

    public boolean isGameplayState() {
        GameState s = current();
        return s == GameState.TUTORIAL_SLUM || s == GameState.LAB_FACILITY
                || s == GameState.SUB_BASEMENT || s == GameState.DATA_CENTER;
    }

    public boolean isMenuState() {
        GameState s = current();
        return s == GameState.MAIN_MENU || s == GameState.CREDITS
                || s == GameState.GAME_OVER || s == GameState.ENDING_CHOICE;
    }

    public boolean isPaused() {
        return current() == GameState.PAUSE;
    }

    /** Maps a gameplay state to its area index (for music/map). */
    public int areaForState(GameState s) {
        switch (s) {
            case LAB_FACILITY:
                return AREA_LAB;
            case SUB_BASEMENT:
                return AREA_BASEMENT;
            case DATA_CENTER:
                return AREA_DATA;
            case TUTORIAL_SLUM:
            default:
                return AREA_SLUM;
        }
    }

    /** Maps an area index back to a gameplay state. */
    public GameState stateForArea(int area) {
        switch (area) {
            case AREA_LAB:
                return GameState.LAB_FACILITY;
            case AREA_BASEMENT:
                return GameState.SUB_BASEMENT;
            case AREA_DATA:
                return GameState.DATA_CENTER;
            default:
                return GameState.TUTORIAL_SLUM;
        }
    }

    public void resetToMenu() {
        stack.clear();
        stack.push(GameState.MAIN_MENU);
    }
}
