package com.thelostecho.game.managers;

import java.util.ArrayList;
import java.util.List;

/**
 * Player inventory. Holds key items (keycards, USB drive, frequency
 * modulator), the Walkman, throwing stones and stamina batteries. Item
 * definitions are built in so the HUD can show names and icons.
 */
public final class InventoryManager {

    public static final class ItemDef {
        public final String id;
        public final String name;
        public final String description;
        public final int iconColor;

        ItemDef(String id, String name, String description, int iconColor) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.iconColor = iconColor;
        }
    }

    private static InventoryManager instance;

    private final java.util.LinkedHashMap<String, ItemDef> definitions =
            new java.util.LinkedHashMap<String, ItemDef>();
    private final ArrayList<String> items = new ArrayList<String>();
    private final java.util.HashMap<String, Integer> counts = new java.util.HashMap<String, Integer>();

    private InventoryManager() {
        addDef("keycard_red", "Red Keycard", "Aethelgard security - clearance C", 0xFFFF5252);
        addDef("keycard_blue", "Blue Keycard", "Aethelgard security - clearance B", 0xFF4FC3F7);
        addDef("keycard_yellow", "Yellow Keycard", "Aethelgard security - clearance A", 0xFFFFEE58);
        addDef("usb_drive", "USB Drive", "Data stolen from the lab archive", 0xFFB388FF);
        addDef("freq_modulator", "Frequency Modulator", "Tunes the Walkman to lab systems", 0xFF69F0AE);
        addDef("walkman", "Walkman", "Plays tones: low, mid, high", 0xFFFFE082);
        addDef("stone", "Stone", "Throw it to distract guards", 0xFFBCAAA4);
        addDef("battery_pack", "Battery Pack", "Restores sonar stamina", 0xFF80D8FF);
    }

    private void addDef(String id, String name, String description, int color) {
        definitions.put(id, new ItemDef(id, name, description, color));
    }

    public static synchronized InventoryManager getInstance() {
        if (instance == null) {
            instance = new InventoryManager();
        }
        return instance;
    }

    public void addItem(String id) {
        if (!definitions.containsKey(id)) {
            return;
        }
        items.add(id);
        Integer c = counts.get(id);
        counts.put(id, (c == null ? 0 : c) + 1);
    }

    public void removeItem(String id) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).equals(id)) {
                items.remove(i);
                break;
            }
        }
        Integer c = counts.get(id);
        if (c != null) {
            if (c <= 1) {
                counts.remove(id);
            } else {
                counts.put(id, c - 1);
            }
        }
    }

    public boolean hasItem(String id) {
        Integer c = counts.get(id);
        return c != null && c > 0;
    }

    public int getCount(String id) {
        Integer c = counts.get(id);
        return c != null ? c : 0;
    }

    public List<String> getItems() {
        return items;
    }

    public ItemDef getDef(String id) {
        return definitions.get(id);
    }

    public int size() {
        return items.size();
    }

    /** Restores inventory from a save (list of item ids). */
    public void restore(List<String> savedItems) {
        items.clear();
        counts.clear();
        if (savedItems != null) {
            for (int i = 0; i < savedItems.size(); i++) {
                addItem(savedItems.get(i));
            }
        }
    }

    public void clear() {
        items.clear();
        counts.clear();
    }
}
