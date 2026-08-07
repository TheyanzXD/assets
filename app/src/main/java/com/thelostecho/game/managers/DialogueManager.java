package com.thelostecho.game.managers;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Branching dialogue system. Nodes are loaded from res/raw/dialogue.json when
 * present, otherwise from a built-in fallback tree. Each node has a speaker, a
 * line of text and zero or more choices; choices may require an inventory item
 * or quest, and may start a quest when selected. Integrates with the
 * DialogueBoxRenderer through the scene.
 */
public final class DialogueManager {

    public static final class Choice {
        public String text;
        public String next;
        public String requireItem;
        public String requireQuest;
        public String startQuest;
    }

    public static final class Node {
        public String id;
        public String speaker;
        public String text;
        public final ArrayList<Choice> choices = new ArrayList<Choice>();
    }

    private static DialogueManager instance;

    private final Map<String, Node> nodes = new HashMap<String, Node>();
    private Node currentNode;
    private boolean active = false;

    private DialogueManager() {
    }

    public static synchronized DialogueManager getInstance() {
        if (instance == null) {
            instance = new DialogueManager();
        }
        return instance;
    }

    /** Loads dialogue data; falls back to the built-in tree on any error. */
    public void load(Context context) {
        nodes.clear();
        String raw = null;
        try {
            int resId = context.getResources().getIdentifier("dialogue",
                    "raw", context.getPackageName());
            if (resId != 0) {
                java.io.InputStream is = context.getResources().openRawResource(resId);
                byte[] buf = new byte[is.available()];
                is.read(buf);
                is.close();
                raw = new String(buf, "UTF-8");
            }
        } catch (Exception ignored) {
            raw = null;
        }
        if (raw == null) {
            raw = DEFAULT_DIALOGUE;
        }
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray arr = root.getJSONArray("nodes");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject n = arr.getJSONObject(i);
                Node node = new Node();
                node.id = n.getString("id");
                node.speaker = n.optString("speaker", "");
                node.text = n.optString("text", "");
                JSONArray choices = n.optJSONArray("choices");
                if (choices != null) {
                    for (int c = 0; c < choices.length(); c++) {
                        JSONObject ch = choices.getJSONObject(c);
                        Choice choice = new Choice();
                        choice.text = ch.optString("text", "...");
                        choice.next = ch.optString("next", null);
                        choice.requireItem = ch.optString("requireItem", null);
                        choice.requireQuest = ch.optString("requireQuest", null);
                        choice.startQuest = ch.optString("startQuest", null);
                        node.choices.add(choice);
                    }
                }
                nodes.put(node.id, node);
            }
        } catch (Exception ignored) {
            nodes.clear();
        }
        currentNode = null;
        active = false;
    }

    public void start(String dialogueId) {
        Node n = nodes.get(dialogueId);
        if (n == null) {
            active = false;
            return;
        }
        currentNode = n;
        active = true;
    }

    public boolean isActive() {
        return active;
    }

    public String getSpeaker() {
        return currentNode != null ? currentNode.speaker : "";
    }

    public String getText() {
        return currentNode != null ? currentNode.text : "";
    }

    /** Returns texts of the choices available under current conditions. */
    public String[] getChoices() {
        if (currentNode == null || currentNode.choices.isEmpty()) {
            return null;
        }
        ArrayList<String> out = new ArrayList<String>();
        for (int i = 0; i < currentNode.choices.size(); i++) {
            Choice c = currentNode.choices.get(i);
            if (conditionMet(c)) {
                out.add(c.text);
            }
        }
        return out.toArray(new String[out.size()]);
    }

    /** Picks the (unfiltered index) choice; applies its effects. */
    public void choose(int index) {
        if (currentNode == null || index < 0 || index >= currentNode.choices.size()) {
            return;
        }
        Choice c = currentNode.choices.get(index);
        if (!conditionMet(c)) {
            return;
        }
        if (c.startQuest != null) {
            QuestManager.getInstance().startQuest(c.startQuest);
        }
        if (c.next != null) {
            start(c.next);
        } else {
            active = false;
            currentNode = null;
        }
    }

    private boolean conditionMet(Choice c) {
        if (c.requireItem != null && !InventoryManager.getInstance().hasItem(c.requireItem)) {
            return false;
        }
        if (c.requireQuest != null) {
            QuestManager qm = QuestManager.getInstance();
            if (!qm.isCompleted(c.requireQuest)) {
                return false;
            }
        }
        return true;
    }

    public void end() {
        active = false;
        currentNode = null;
    }

    public String getCurrentNodeId() {
        return currentNode != null ? currentNode.id : "";
    }

    private static final String DEFAULT_DIALOGUE =
            "{\n" +
            "  \"nodes\": [\n" +
            "    {\"id\": \"meera_intro\", \"speaker\": \"Meera\", \"text\": " +
            "\"Raka! The city is quieter than it's ever been. I heard a pulse down by the old slum ruins - " +
            "the Walkman is still working. Find it. It might be the only way to fight the drones.\", " +
            "\"choices\": [\n" +
            "      {\"text\": \"I'll find the Walkman.\", \"next\": \"meera_walkman\", \"startQuest\": \"q_awakening\"},\n" +
            "      {\"text\": \"The drones... how many are there?\", \"next\": \"meera_drones\"}\n" +
            "    ]},\n" +
            "    {\"id\": \"meera_walkman\", \"speaker\": \"Meera\", \"text\": " +
            "\"Good. And if you find a keycard, keep it. Aethelgard's doors still open for the right colours.\", " +
            "\"choices\": []},\n" +
            "    {\"id\": \"meera_drones\", \"speaker\": \"Meera\", \"text\": " +
            "\"Too many. But they hunt by sound and sight. Shadows hide you. Move soft, and they'll never know you're here.\", " +
            "\"choices\": [{\"text\": \"Understood.\", \"next\": null}]},\n" +
            "    {\"id\": \"juno_talk\", \"speaker\": \"Old Juno\", \"text\": " +
            "\"The lab keeps its secrets under the city. The blue keycard opens the west gate. Watch the turret - " +
            "it hums before it fires.\", \"choices\": [\n" +
            "      {\"text\": \"Thanks, Juno.\", \"next\": null, \"startQuest\": \"q_first_escape\"}\n" +
            "    ]},\n" +
            "    {\"id\": \"warden_lab\", \"speaker\": \"Warden Kess\", \"text\": " +
            "\"You're not cleared for this floor. If you had a blue keycard, the gate would open. " +
            "The security office keeps spares - if you can reach it.\", \"choices\": [\n" +
            "      {\"text\": \"I'll get that keycard.\", \"next\": null, \"startQuest\": \"q_lab_clearance\"},\n" +
            "      {\"text\": \"The turrets?\", \"next\": \"warden_turrets\"}\n" +
            "    ]},\n" +
            "    {\"id\": \"warden_turrets\", \"speaker\": \"Warden Kess\", \"text\": " +
            "\"Old units. They answer to acoustic codes. The sub-basement has two of them guarding the archive - " +
            "play the right sequence and they power down for good.\", \"choices\": [\n" +
            "      {\"text\": \"Good to know.\", \"next\": null, \"startQuest\": \"q_silence_drones\"}\n" +
            "    ]},\n" +
            "    {\"id\": \"data_center_intro\", \"speaker\": \"Meera\", \"text\": " +
            "\"This is it, Raka. Two consoles. The left one can send your parents to safety but keeps the city watched. " +
            "The right one shuts Aethelgard's network down forever - but I can't guarantee they'll make it out. " +
            "Choose, and choose for all of us.\", \"choices\": [\n" +
            "      {\"text\": \"Save my parents. Keep the city watched.\", \"next\": null},\n" +
            "      {\"text\": \"Paralyze the city. Free everyone.\", \"next\": null}\n" +
            "    ]}\n" +
            "  ]\n" +
            "}";
}
