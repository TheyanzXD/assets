package com.thelostecho.game.story;

import org.json.JSONObject;

import java.util.Iterator;

/**
 * Tracks player choices and narrative flags across the game. Flags are
 * serialized into saves so minor choices (which affect NPC dialogue and side
 * quest availability) and the major ending choice persist.
 */
public final class BranchingNarrative {

    public static final String FLAG_ENDING_CHOICE = "ending_choice";
    public static final String FLAG_ENDING_SAVE_PARENTS = "ending_save_parents";
    public static final String FLAG_ENDING_PARALYZE = "ending_paralyze";
    public static final String FLAG_HEARD_MEERA_WARNING = "heard_meera_warning";
    public static final String FLAG_JUNO_SAVED = "juno_saved";
    public static final String FLAG_WARDEN_ALERTED = "warden_alerted";

    private static BranchingNarrative instance;

    private final JSONObject flags = new JSONObject();

    private BranchingNarrative() {
    }

    public static synchronized BranchingNarrative getInstance() {
        if (instance == null) {
            instance = new BranchingNarrative();
        }
        return instance;
    }

    public void recordChoice(String name, int value) {
        try {
            flags.put(name, value);
        } catch (Exception ignored) {
        }
    }

    public void setFlag(String name, boolean value) {
        try {
            flags.put(name, value);
        } catch (Exception ignored) {
        }
    }

    public boolean getFlag(String name, boolean defaultValue) {
        return flags.optBoolean(name, defaultValue);
    }

    public int getIntFlag(String name, int defaultValue) {
        return flags.optInt(name, defaultValue);
    }

    public boolean hasFlag(String name) {
        return flags.has(name);
    }

    public int getEndingChoice() {
        return flags.optInt(FLAG_ENDING_CHOICE, 0);
    }

    /** Exports flags for save serialization. */
    public JSONObject exportFlags() {
        return flags;
    }

    /** Restores flags from a save. */
    public void restore(JSONObject saved) {
        flags.remove("x");
        Iterator<String> keys = saved.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                flags.put(key, saved.get(key));
            } catch (Exception ignored) {
            }
        }
    }

    public void reset() {
        Iterator<String> keys = flags.keys();
        java.util.ArrayList<String> toRemove = new java.util.ArrayList<String>();
        while (keys.hasNext()) {
            toRemove.add(keys.next());
        }
        for (int i = 0; i < toRemove.size(); i++) {
            flags.remove(toRemove.get(i));
        }
    }
}
