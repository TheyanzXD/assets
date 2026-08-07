package com.thelostecho.game.managers;

import java.util.ArrayList;
import java.util.List;

/**
 * Quest tracking. Quests are defined in code (mirroring a JSON data file) with
 * ordered objectives of type REACH_LOCATION / COLLECT_ITEM / TALK_TO_NPC /
 * SOLVE_PUZZLE / DEFEAT_ENEMY. The scene reports objective progress, the
 * manager updates the HUD tracker and fires completion events.
 */
public final class QuestManager {

    public static final String TYPE_REACH_LOCATION = "REACH_LOCATION";
    public static final String TYPE_COLLECT_ITEM = "COLLECT_ITEM";
    public static final String TYPE_TALK_TO_NPC = "TALK_TO_NPC";
    public static final String TYPE_SOLVE_PUZZLE = "SOLVE_PUZZLE";
    public static final String TYPE_DEFEAT_ENEMY = "DEFEAT_ENEMY";

    public static final class Objective {
        public final String type;
        public final String target;
        public final int required;
        public int progress;

        Objective(String type, String target, int required) {
            this.type = type;
            this.target = target;
            this.required = required;
        }

        boolean isComplete() {
            return progress >= required;
        }
    }

    public static final class Quest {
        public final String id;
        public final String title;
        public final String description;
        public final ArrayList<Objective> objectives = new ArrayList<Objective>();
        boolean active = false;
        boolean completed = false;
        int objectiveIndex = 0;

        Quest(String id, String title, String description) {
            this.id = id;
            this.title = title;
            this.description = description;
        }

        Objective currentObjective() {
            if (objectiveIndex < objectives.size()) {
                return objectives.get(objectiveIndex);
            }
            return null;
        }

        public String getTitle() {
            return title;
        }

        /** One-line HUD progress, e.g. "Find the Walkman (1/1)". */
        public String getProgressText() {
            Objective o = currentObjective();
            if (o == null) {
                return completed ? "Done." : "";
            }
            String label;
            if (TYPE_COLLECT_ITEM.equals(o.type)) {
                label = "Find " + prettyName(o.target);
            } else if (TYPE_REACH_LOCATION.equals(o.type)) {
                label = "Reach " + prettyName(o.target);
            } else if (TYPE_TALK_TO_NPC.equals(o.type)) {
                label = "Talk to " + prettyName(o.target);
            } else if (TYPE_SOLVE_PUZZLE.equals(o.type)) {
                label = "Solve " + prettyName(o.target);
            } else {
                label = "Deactivate " + prettyName(o.target);
            }
            return label + " (" + Math.min(o.progress, o.required) + "/" + o.required + ")";
        }

        private String prettyName(String id) {
            if (id == null) {
                return "?";
            }
            String[] parts = id.split("_");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                String p = parts[i];
                if (p.length() > 0) {
                    sb.append(Character.toUpperCase(p.charAt(0)));
                    sb.append(p.substring(1));
                }
                if (i < parts.length - 1) {
                    sb.append(' ');
                }
            }
            return sb.toString();
        }
    }

    public interface QuestListener {
        void onQuestStarted(Quest quest);

        void onQuestCompleted(Quest quest);
    }

    private static QuestManager instance;

    private final ArrayList<Quest> quests = new ArrayList<Quest>();
    private final ArrayList<Quest> active = new ArrayList<Quest>();
    private QuestListener listener;

    private QuestManager() {
        buildDefinitions();
    }

    public static synchronized QuestManager getInstance() {
        if (instance == null) {
            instance = new QuestManager();
        }
        return instance;
    }

    public void setListener(QuestListener l) {
        listener = l;
    }

    private void buildDefinitions() {
        Quest q1 = new Quest("q_awakening", "The Lost Echo",
                "Wake the Walkman in the slum ruins.");
        q1.objectives.add(new Objective(TYPE_COLLECT_ITEM, "walkman", 1));
        q1.objectives.add(new Objective(TYPE_TALK_TO_NPC, "meera", 1));
        addQuest(q1);

        Quest q2 = new Quest("q_first_escape", "Into the Machine",
                "Reach the Aethelgard lab entrance.");
        q2.objectives.add(new Objective(TYPE_REACH_LOCATION, "lab_entrance", 1));
        addQuest(q2);

        Quest q3 = new Quest("q_lab_clearance", "Lab Clearance",
                "Obtain a blue keycard from the security office.");
        q3.objectives.add(new Objective(TYPE_COLLECT_ITEM, "keycard_blue", 1));
        q3.objectives.add(new Objective(TYPE_SOLVE_PUZZLE, "lab_door", 1));
        addQuest(q3);

        Quest q4 = new Quest("q_silence_drones", "Silence the Watch",
                "Disable the turrets guarding the sub-basement.");
        q4.objectives.add(new Objective(TYPE_SOLVE_PUZZLE, "turret_1", 1));
        q4.objectives.add(new Objective(TYPE_SOLVE_PUZZLE, "turret_2", 1));
        addQuest(q4);

        Quest q5 = new Quest("q_data_choice", "The Last Signal",
                "Reach the Data Center and make a choice.");
        q5.objectives.add(new Objective(TYPE_REACH_LOCATION, "data_center", 1));
        addQuest(q5);
    }

    private void addQuest(Quest q) {
        quests.add(q);
    }

    public void startQuest(String id) {
        Quest q = find(id);
        if (q == null || q.active || q.completed) {
            return;
        }
        q.active = true;
        q.objectiveIndex = 0;
        active.add(q);
        if (listener != null) {
            listener.onQuestStarted(q);
        }
    }

    /** Reports progress for a world event. */
    public void advanceObjective(String type, String target, int amount) {
        for (int i = 0; i < active.size(); i++) {
            Quest q = active.get(i);
            Objective o = q.currentObjective();
            if (o != null && o.type.equals(type) && o.target.equals(target)) {
                o.progress = Math.min(o.required, o.progress + amount);
                if (o.isComplete()) {
                    q.objectiveIndex++;
                    Objective next = q.currentObjective();
                    if (next == null) {
                        completeQuest(q.id);
                    }
                }
                return;
            }
        }
    }

    public void completeQuest(String id) {
        Quest q = find(id);
        if (q == null) {
            return;
        }
        q.completed = true;
        q.active = false;
        active.remove(q);
        if (listener != null) {
            listener.onQuestCompleted(q);
        }
    }

    public boolean isActive(String id) {
        Quest q = find(id);
        return q != null && q.active;
    }

    public boolean isCompleted(String id) {
        Quest q = find(id);
        return q != null && q.completed;
    }

    public Quest getQuest(String id) {
        return find(id);
    }

    public List<Quest> getActiveQuests() {
        return active;
    }

    public int getActiveQuestCount() {
        return active.size();
    }

    /** Restores quest state from a save. */
    public void restoreState(List<String> activeIds, List<String> completedIds) {
        for (int i = 0; i < quests.size(); i++) {
            quests.get(i).active = false;
            quests.get(i).completed = false;
            quests.get(i).objectiveIndex = 0;
        }
        active.clear();
        if (completedIds != null) {
            for (int i = 0; i < completedIds.size(); i++) {
                Quest q = find(completedIds.get(i));
                if (q != null) {
                    q.completed = true;
                }
            }
        }
        if (activeIds != null) {
            for (int i = 0; i < activeIds.size(); i++) {
                Quest q = find(activeIds.get(i));
                if (q != null && !q.completed) {
                    q.active = true;
                    active.add(q);
                }
            }
        }
    }

    private Quest find(String id) {
        for (int i = 0; i < quests.size(); i++) {
            if (quests.get(i).id.equals(id)) {
                return quests.get(i);
            }
        }
        return null;
    }
}
