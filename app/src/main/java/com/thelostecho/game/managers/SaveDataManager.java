package com.thelostecho.game.managers;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Persistent save system. Up to 3 slots stored as AES-encrypted JSON files in
 * internal storage. Saves hold the full game state: level, player position,
 * stamina, inventory, quests, unlocked doors, disabled turrets, lore, narrative
 * flags, play time and the ending choice.
 */
public final class SaveDataManager {

    public static final int SLOT_COUNT = 3;
    private static final String FILE_PREFIX = "save_slot_";
    private static final String FILE_SUFFIX = ".dat";

    private static SaveDataManager instance;
    private final Context appContext;

    public static final class SaveInfo {
        public final int slot;
        public final boolean exists;
        public final long timestamp;
        public final String levelName;

        SaveInfo(int slot, boolean exists, long timestamp, String levelName) {
            this.slot = slot;
            this.exists = exists;
            this.timestamp = timestamp;
            this.levelName = levelName;
        }
    }

    public static final class SaveData {
        public int levelId = GameStateManager.AREA_SLUM;
        public float playerX = 200f;
        public float playerY = 200f;
        public float stamina = 100f;
        public float checkpointX = 200f;
        public float checkpointY = 200f;
        public long playTimeSeconds = 0L;
        public int endingChoice = 0;
        public int alertsTriggered = 0;
        public final ArrayList<String> inventory = new ArrayList<String>();
        public final ArrayList<String> activeQuests = new ArrayList<String>();
        public final ArrayList<String> completedQuests = new ArrayList<String>();
        public final ArrayList<String> unlockedDoors = new ArrayList<String>();
        public final ArrayList<Integer> disabledTurrets = new ArrayList<Integer>();
        public final ArrayList<String> loreDiscovered = new ArrayList<String>();
        public final org.json.JSONObject narrativeFlags = new org.json.JSONObject();
    }

    private SaveDataManager(Context context) {
        appContext = context.getApplicationContext();
    }

    public static synchronized SaveDataManager getInstance(Context context) {
        if (instance == null) {
            instance = new SaveDataManager(context);
        }
        return instance;
    }

    private File fileForSlot(int slot) {
        return new File(appContext.getFilesDir(), FILE_PREFIX + slot + FILE_SUFFIX);
    }

    public boolean hasSave(int slot) {
        return slot >= 0 && slot < SLOT_COUNT && fileForSlot(slot).exists();
    }

    public boolean save(int slot, SaveData data) {
        if (slot < 0 || slot >= SLOT_COUNT || data == null) {
            return false;
        }
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("levelId", data.levelId);
            root.put("playerX", (double) data.playerX);
            root.put("playerY", (double) data.playerY);
            root.put("stamina", (double) data.stamina);
            root.put("checkpointX", (double) data.checkpointX);
            root.put("checkpointY", (double) data.checkpointY);
            root.put("playTimeSeconds", data.playTimeSeconds);
            root.put("endingChoice", data.endingChoice);
            root.put("alertsTriggered", data.alertsTriggered);
            root.put("timestamp", System.currentTimeMillis());
            root.put("inventory", toJsonArray(data.inventory));
            root.put("activeQuests", toJsonArray(data.activeQuests));
            root.put("completedQuests", toJsonArray(data.completedQuests));
            root.put("unlockedDoors", toJsonArray(data.unlockedDoors));
            JSONArray turrets = new JSONArray();
            for (int i = 0; i < data.disabledTurrets.size(); i++) {
                turrets.put(data.disabledTurrets.get(i).intValue());
            }
            root.put("disabledTurrets", turrets);
            root.put("loreDiscovered", toJsonArray(data.loreDiscovered));
            root.put("flags", data.narrativeFlags);

            String json = root.toString();
            String encrypted = EncryptionUtil.encrypt(appContext, json);
            FileOutputStream fos = new FileOutputStream(fileForSlot(slot));
            fos.write(encrypted.getBytes("UTF-8"));
            fos.flush();
            fos.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Loads a slot into a fresh SaveData; null on error or missing save. */
    public SaveData load(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return null;
        }
        File file = fileForSlot(slot);
        if (!file.exists()) {
            return null;
        }
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] buf = new byte[(int) file.length()];
            int read = fis.read(buf);
            fis.close();
            if (read <= 0) {
                return null;
            }
            String encrypted = new String(buf, "UTF-8").trim();
            String json = EncryptionUtil.decrypt(appContext, encrypted);
            if (json == null) {
                return null;
            }
            JSONObject root = new JSONObject(json);
            SaveData data = new SaveData();
            data.levelId = root.optInt("levelId", GameStateManager.AREA_SLUM);
            data.playerX = (float) root.optDouble("playerX", 200.0);
            data.playerY = (float) root.optDouble("playerY", 200.0);
            data.stamina = (float) root.optDouble("stamina", 100.0);
            data.checkpointX = (float) root.optDouble("checkpointX", data.playerX);
            data.checkpointY = (float) root.optDouble("checkpointY", data.playerY);
            data.playTimeSeconds = root.optLong("playTimeSeconds", 0L);
            data.endingChoice = root.optInt("endingChoice", 0);
            data.alertsTriggered = root.optInt("alertsTriggered", 0);
            readStrings(root.optJSONArray("inventory"), data.inventory);
            readStrings(root.optJSONArray("activeQuests"), data.activeQuests);
            readStrings(root.optJSONArray("completedQuests"), data.completedQuests);
            readStrings(root.optJSONArray("unlockedDoors"), data.unlockedDoors);
            JSONArray turrets = root.optJSONArray("disabledTurrets");
            if (turrets != null) {
                for (int i = 0; i < turrets.length(); i++) {
                    data.disabledTurrets.add(turrets.optInt(i, 0));
                }
            }
            readStrings(root.optJSONArray("loreDiscovered"), data.loreDiscovered);
            JSONObject flags = root.optJSONObject("flags");
            if (flags != null) {
                Iterator<String> keys = flags.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    try {
                        data.narrativeFlags.put(key, flags.get(key));
                    } catch (JSONException ignored) {
                    }
                }
            }
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    public void deleteSlot(int slot) {
        File file = fileForSlot(slot);
        if (file.exists()) {
            file.delete();
        }
    }

    public List<SaveInfo> getSlotInfos() {
        ArrayList<SaveInfo> infos = new ArrayList<SaveInfo>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            File file = fileForSlot(slot);
            boolean exists = file.exists();
            long timestamp = 0L;
            String levelName = "-";
            if (exists) {
                SaveData d = load(slot);
                if (d != null) {
                    timestamp = file.lastModified();
                    levelName = levelNameFor(d.levelId);
                } else {
                    exists = false;
                }
            }
            infos.add(new SaveInfo(slot, exists, timestamp, levelName));
        }
        return infos;
    }

    public String levelNameFor(int area) {
        switch (area) {
            case GameStateManager.AREA_LAB:
                return "Aethelgard Lab";
            case GameStateManager.AREA_BASEMENT:
                return "Sub-Basement";
            case GameStateManager.AREA_DATA:
                return "Data Center";
            default:
                return "Slum District";
        }
    }

    private JSONArray toJsonArray(ArrayList<String> list) throws JSONException {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < list.size(); i++) {
            arr.put(list.get(i));
        }
        return arr;
    }

    private void readStrings(JSONArray arr, ArrayList<String> out) {
        out.clear();
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            out.add(arr.optString(i, ""));
        }
    }
}
