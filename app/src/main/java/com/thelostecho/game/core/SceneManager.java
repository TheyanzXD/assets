package com.thelostecho.game.core;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;

import com.thelostecho.game.ai.AIPathfinding;
import com.thelostecho.game.entities.AcousticPuzzle;
import com.thelostecho.game.entities.CollectibleItem;
import com.thelostecho.game.entities.DoorTerminal;
import com.thelostecho.game.entities.DroneEnemy;
import com.thelostecho.game.entities.GuardEnemy;
import com.thelostecho.game.entities.InteractiveProp;
import com.thelostecho.game.entities.NPC;
import com.thelostecho.game.entities.Player;
import com.thelostecho.game.entities.Projectile;
import com.thelostecho.game.entities.TurretEnemy;
import com.thelostecho.game.graphics.DialogueBoxRenderer;
import com.thelostecho.game.graphics.DistortionEffect;
import com.thelostecho.game.graphics.HUDOverlay;
import com.thelostecho.game.graphics.LightingRenderer;
import com.thelostecho.game.graphics.MapTransitionEffect;
import com.thelostecho.game.graphics.ParticleSystem;
import com.thelostecho.game.graphics.SonarWaveRenderer;
import com.thelostecho.game.graphics.TileMapRenderer;
import com.thelostecho.game.graphics.VisionConeRenderer;
import com.thelostecho.game.managers.DialogueManager;
import com.thelostecho.game.managers.GameStateManager;
import com.thelostecho.game.managers.InventoryManager;
import com.thelostecho.game.managers.QuestManager;
import com.thelostecho.game.managers.SaveDataManager;
import com.thelostecho.game.story.BranchingNarrative;
import com.thelostecho.game.story.ChoiceEventHandler;
import com.thelostecho.game.ui.CreditsView;
import com.thelostecho.game.ui.EndingSummaryView;
import com.thelostecho.game.ui.MainMenuView;
import com.thelostecho.game.ui.SettingsView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.json.JSONObject;

/**
 * The scene hub. Owns every entity, renderer and manager; runs the game rules
 * (input routing, interaction, stealth detection, quests, saves, endings) and
 * draws the world under a single density-aware canvas transform. Called only
 * from GameThread (update/render) and the UI thread (sizing/lifecycle).
 */
public final class SceneManager {

    public static final int AREA_SLUM = 0;
    public static final int AREA_LAB = 1;
    public static final int AREA_BASEMENT = 2;
    public static final int AREA_DATA = 3;

    private static final float INTERACT_RADIUS = 70f;
    private static final float GAME_OVER_DISTANCE = 100f;
    private static final float GAME_OVER_TIME = 2f;
    private static final float STUN_RADIUS = 90f;

    private final Context context;
    private final GameSurfaceView view;
    private final InputManager input;
    private final PerformanceMonitor monitor;
    private final float density;
    private final float scale;

    private final TileMapRenderer map;
    private final AIPathfinding pathfinding;
    private final AudioManager audio;
    private final SaveDataManager save;
    private final InventoryManager inventory;
    private final QuestManager quests;
    private final DialogueManager dialogue;
    private final BranchingNarrative narrative;
    private final ChoiceEventHandler choiceHandler;
    private final GameStateManager gsm;

    private final Player player;
    private final HUDOverlay hud;
    private final SonarWaveRenderer sonar;
    private final ParticleSystem particles;
    private final LightingRenderer lighting;
    private final DialogueBoxRenderer dialogueBox;
    private final MapTransitionEffect transition;
    private final VisionConeRenderer visionCone;
    private final DistortionEffect distortion;

    private final MainMenuView menuView = new MainMenuView();
    private final SettingsView settingsView = new SettingsView();
    private final EndingSummaryView endingView = new EndingSummaryView();
    private final CreditsView creditsView = new CreditsView();

    private final ArrayList<DroneEnemy> drones = new ArrayList<DroneEnemy>();
    private final ArrayList<GuardEnemy> guards = new ArrayList<GuardEnemy>();
    private final ArrayList<TurretEnemy> turrets = new ArrayList<TurretEnemy>();
    private final ArrayList<InteractiveProp> props = new ArrayList<InteractiveProp>();
    private final ArrayList<DoorTerminal> doors = new ArrayList<DoorTerminal>();
    private final ArrayList<AcousticPuzzle> puzzles = new ArrayList<AcousticPuzzle>();
    private final ArrayList<NPC> npcs = new ArrayList<NPC>();
    private final ArrayList<CollectibleItem> collectibles = new ArrayList<CollectibleItem>();
    private final Projectile[] projectiles = new Projectile[16];

    private final HashMap<String, int[]> doorTiles = new HashMap<String, int[]>();
    private final HashMap<InteractiveProp, Integer> consoleChoices = new HashMap<InteractiveProp, Integer>();
    private final ArrayList<String> unlockedDoors = new ArrayList<String>();
    private final ArrayList<Integer> disabledTurretIndices = new ArrayList<Integer>();
    private final ArrayList<String> loreDiscovered = new ArrayList<String>();

    private final ArrayList<InputManager.InputEvent> events = new ArrayList<InputManager.InputEvent>();
    private final ArrayList<RectF> choiceRects = new ArrayList<RectF>();
    private final ArrayList<RectF> choiceRectPool = new ArrayList<RectF>();
    private int choicePoolIndex = 0;
    private final float[] stats = new float[PerformanceMonitor.STAT_COUNT];
    private final RectF tmpRect = new RectF();

    private float viewW = 1f;
    private float viewH = 1f;
    private float worldTime = 0f;
    private float playTimeTotal = 0f;
    private int currentArea = AREA_SLUM;
    private int currentMusicArea = -1;
    private int alertsCount = 0;
    private int gameOverCount = 0;
    private float gameOverTimer = 0f;
    private int lastSaveSlot = 0;

    private boolean walkmanOpen = false;
    private boolean puzzleActive = false;
    private AcousticPuzzle activePuzzle = null;
    private boolean endingSummaryMode = false;
    private int pendingEnding = 0;
    private boolean exitRequested = false;
    private boolean droneHumStarted = false;

    private String lastTalkNpcId = "";
    private final RectF toneRectLow = new RectF();
    private final RectF toneRectMid = new RectF();
    private final RectF toneRectHigh = new RectF();
    private final Paint tonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint toneActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint toneTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint choicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SceneManager(Context context, GameSurfaceView view,
                        InputManager input, PerformanceMonitor monitor) {
        this.context = context.getApplicationContext();
        this.view = view;
        this.input = input;
        this.monitor = monitor;
        this.density = context.getResources().getDisplayMetrics().density;
        this.scale = density;

        map = new TileMapRenderer(density);
        pathfinding = new AIPathfinding(map);
        audio = AudioManager.getInstance(context);
        save = SaveDataManager.getInstance(context);
        inventory = InventoryManager.getInstance();
        quests = QuestManager.getInstance();
        dialogue = DialogueManager.getInstance();
        narrative = BranchingNarrative.getInstance();
        choiceHandler = ChoiceEventHandler.getInstance(context);
        gsm = new GameStateManager();

        player = new Player(input, map, inventory);
        hud = new HUDOverlay();
        sonar = new SonarWaveRenderer();
        particles = new ParticleSystem();
        lighting = new LightingRenderer();
        dialogueBox = new DialogueBoxRenderer();
        transition = new MapTransitionEffect();
        visionCone = new VisionConeRenderer();
        distortion = new DistortionEffect();

        for (int i = 0; i < projectiles.length; i++) {
            projectiles[i] = new Projectile();
        }

        dialogue.load(context);
        quests.setListener(null);

        tonePaint.setARGB(150, 50, 70, 100);
        toneActivePaint.setARGB(190, 90, 200, 240);
        toneTextPaint.setARGB(255, 235, 245, 255);
        toneTextPaint.setTextAlign(Paint.Align.CENTER);
        overlayTextPaint.setARGB(255, 255, 90, 90);
        overlayTextPaint.setTextAlign(Paint.Align.CENTER);
        overlayTextPaint.setFakeBoldText(true);

        float metricsW = context.getResources().getDisplayMetrics().widthPixels;
        float metricsH = context.getResources().getDisplayMetrics().heightPixels;
        viewW = Math.max(1f, metricsW);
        viewH = Math.max(1f, metricsH);
        map.setViewSize(viewW, viewH);
        lighting.setViewSize(viewW, viewH);
        lighting.setAmbientColor(0xAF080A1E);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public void onSurfaceCreated() {
    }

    public void onSurfaceChanged(int w, int h) {
        viewW = Math.max(1f, w);
        viewH = Math.max(1f, h);
        map.setViewSize(viewW, viewH);
        lighting.setViewSize(viewW, viewH);
    }

    public void onResume() {
    }

    public void onPause() {
        saveAuto();
    }

    public void dispose() {
        saveAuto();
        audio.release();
        lighting.dispose();
        sonar.dispose();
        particles.clear();
        hud.dispose();
        dialogueBox.dispose();
        visionCone.dispose();
        distortion.dispose();
        menuView.dispose();
        settingsView.dispose();
        endingView.dispose();
        creditsView.dispose();
    }

    // ------------------------------------------------------------------
    // Main update dispatch
    // ------------------------------------------------------------------

    public void update(float delta) {
        worldTime += delta;
        audio.update(delta);
        input.drainEvents(events);

        switch (gsm.current()) {
            case MAIN_MENU:
                updateMainMenu(delta);
                break;
            case SETTINGS:
                updateSettings(delta);
                break;
            case CREDITS:
                updateCredits(delta);
                break;
            case ENDING_CHOICE:
                updateEndingChoice(delta);
                break;
            case GAME_OVER:
                updateGameOver(delta);
                break;
            case PAUSE:
                updatePause(delta);
                break;
            default:
                updateGameplay(delta);
                break;
        }

        for (int i = 0; i < events.size(); i++) {
            input.recycleEvent(events.get(i));
        }
        events.clear();
    }

    // ------------------------------------------------------------------
    // State updates
    // ------------------------------------------------------------------

    private void updateMainMenu(float delta) {
        menuView.setHasContinue(save.hasSave(0));
        menuView.update(delta);
        for (int i = 0; i < events.size(); i++) {
            InputManager.InputEvent e = events.get(i);
            if (e.type != InputManager.EV_TAP) {
                continue;
            }
            int action = menuView.handleTap(e.x, e.y);
            switch (action) {
                case MainMenuView.ACTION_NEW_GAME:
                    audio.playSfx(AudioManager.SFX_MENU_CLICK);
                    startNewGame();
                    break;
                case MainMenuView.ACTION_CONTINUE:
                    audio.playSfx(AudioManager.SFX_MENU_CLICK);
                    loadGame(0);
                    break;
                case MainMenuView.ACTION_SETTINGS:
                    audio.playSfx(AudioManager.SFX_MENU_CLICK);
                    settingsView.setVolumes(audio.getSfxVolume(), audio.getMusicVolume());
                    settingsView.setDpadScheme(input.isDpadScheme());
                    gsm.push(GameStateManager.GameState.SETTINGS);
                    break;
                case MainMenuView.ACTION_CREDITS:
                    audio.playSfx(AudioManager.SFX_MENU_CLICK);
                    creditsView.reset();
                    endingSummaryMode = false;
                    gsm.push(GameStateManager.GameState.CREDITS);
                    break;
                case MainMenuView.ACTION_EXIT:
                    requestExit();
                    break;
                default:
                    break;
            }
        }
    }

    private void updateSettings(float delta) {
        settingsView.update(delta);
        for (int i = 0; i < events.size(); i++) {
            InputManager.InputEvent e = events.get(i);
            if (e.type != InputManager.EV_TAP) {
                continue;
            }
            int action = settingsView.handleTap(e.x, e.y);
            switch (action) {
                case SettingsView.ACTION_SFX_UP:
                    audio.setSfxVolume(audio.getSfxVolume() + 0.1f);
                    audio.playSfx(AudioManager.SFX_MENU_CLICK);
                    break;
                case SettingsView.ACTION_SFX_DOWN:
                    audio.setSfxVolume(audio.getSfxVolume() - 0.1f);
                    audio.playSfx(AudioManager.SFX_MENU_CLICK);
                    break;
                case SettingsView.ACTION_MUSIC_UP:
                    audio.setMusicVolume(audio.getMusicVolume() + 0.1f);
                    break;
                case SettingsView.ACTION_MUSIC_DOWN:
                    audio.setMusicVolume(audio.getMusicVolume() - 0.1f);
                    break;
                case SettingsView.ACTION_TOGGLE_CONTROL:
                    boolean next = !settingsView.getDpadScheme();
                    settingsView.setDpadScheme(next);
                    input.setDpadScheme(next);
                    audio.playSfx(AudioManager.SFX_MENU_CLICK);
                    break;
                case SettingsView.ACTION_RESET:
                    resetProgress();
                    audio.playSfx(AudioManager.SFX_DOOR_LOCKED);
                    break;
                case SettingsView.ACTION_BACK:
                    audio.playSfx(AudioManager.SFX_MENU_CLICK);
                    gsm.pop();
                    break;
                default:
                    break;
            }
            settingsView.setVolumes(audio.getSfxVolume(), audio.getMusicVolume());
        }
    }

    private void updateCredits(float delta) {
        if (endingSummaryMode) {
            for (int i = 0; i < events.size(); i++) {
                InputManager.InputEvent e = events.get(i);
                if (e.type != InputManager.EV_TAP) {
                    continue;
                }
                int action = endingView.handleTap(e.x, e.y);
                if (action == EndingSummaryView.ACTION_REPLAY) {
                    audio.playSfx(AudioManager.SFX_MENU_CLICK);
                    startNewGame();
                } else if (action == EndingSummaryView.ACTION_MAIN_MENU) {
                    audio.playSfx(AudioManager.SFX_MENU_CLICK);
                    gsm.resetToMenu();
                    endingSummaryMode = false;
                    pendingEnding = 0;
                }
            }
            return;
        }
        creditsView.update(delta);
        for (int i = 0; i < events.size(); i++) {
            InputManager.InputEvent e = events.get(i);
            if (e.type == InputManager.EV_TAP) {
                audio.playSfx(AudioManager.SFX_MENU_CLICK);
                gsm.pop();
                break;
            }
        }
    }

    private void updateEndingChoice(float delta) {
        transition.update(delta);
        transition.consumeCompletion(); // darkest frame marker (no map swap needed)
        if (!transition.isActive() && pendingEnding > 0) {
            endingView.setData(pendingEnding, (long) playTimeTotal, alertsCount, inventory.size());
            endingSummaryMode = true;
            gsm.setState(GameStateManager.GameState.CREDITS);
            pendingEnding = 0;
            audio.playSfx(AudioManager.SFX_COLLECT);
        }
    }

    private void updateGameOver(float delta) {
        transition.update(delta);
        if (transition.consumeCompletion()) {
            player.respawn();
            gsm.setState(gsm.stateForArea(currentArea));
            audio.playSfx(AudioManager.SFX_SONAR);
            saveAuto();
        }
    }

    private void updatePause(float delta) {
        for (int i = 0; i < events.size(); i++) {
            InputManager.InputEvent e = events.get(i);
            if (e.type == InputManager.EV_BUTTON_PRESSED
                    && e.button == InputManager.BTN_PAUSE) {
                audio.playSfx(AudioManager.SFX_MENU_CLICK);
                gsm.pop();
                break;
            }
        }
    }

    // ------------------------------------------------------------------
    // Gameplay update
    // ------------------------------------------------------------------

    private void updateGameplay(float delta) {
        map.updateCamera(player.x, player.y);
        playTimeTotal += delta;

        processGameplayEvents();

        if (dialogue.isActive()) {
            updateDialogue(delta);
            audio.update(delta);
            hud.update(delta, player, gsm);
            return;
        }

        player.update(delta);
        handlePlayerRequests();
        updateEnemies(delta);
        updateProjectiles(delta);
        updatePuzzles(delta);
        updateCollectibles(delta);
        updateProps(delta);
        updateNpcs(delta);
        updateEffects(delta);
        updateAudioHints(delta);
        updateDetectionAndGameOver(delta);
        updateMusicAndLighting(delta);

        if (transition.isActive()) {
            transition.update(delta);
        }
        hud.update(delta, player, gsm);
    }

    private void processGameplayEvents() {
        for (int i = 0; i < events.size(); i++) {
            InputManager.InputEvent e = events.get(i);
            switch (e.type) {
                case InputManager.EV_TAP:
                    handleWorldTap(e.x, e.y);
                    break;
                case InputManager.EV_DOUBLE_TAP:
                    player.clearMoveTarget();
                    break;
                case InputManager.EV_LONG_PRESS:
                    throwStone(map.screenToWorldX(e.x), map.screenToWorldY(e.y));
                    break;
                case InputManager.EV_BUTTON_PRESSED:
                    handleButtonPress(e.button);
                    break;
                default:
                    break;
            }
        }
    }

    private void handleWorldTap(float sx, float sy) {
        if (puzzleActive || walkmanOpen) {
            int tone = toneAt(sx, sy);
            if (tone >= 0) {
                audio.playSfx(AudioManager.SFX_SONAR, 0.7f, 0.8f + tone * 0.3f);
                if (puzzleActive && activePuzzle != null) {
                    activePuzzle.pressTone(tone);
                    if (activePuzzle.isSolved()) {
                        puzzleActive = false;
                        activePuzzle = null;
                        walkmanOpen = false;
                    }
                }
                return;
            }
            if (walkmanOpen && !puzzleActive) {
                walkmanOpen = false;
                return;
            }
            return;
        }
        float wx = map.screenToWorldX(sx);
        float wy = map.screenToWorldY(sy);
        player.setMoveTarget(wx, wy);
    }

    private void handleButtonPress(int button) {
        switch (button) {
            case InputManager.BTN_INVENTORY:
                hud.setInventoryOpen(!hud.isInventoryOpen());
                audio.playSfx(AudioManager.SFX_MENU_CLICK);
                break;
            case InputManager.BTN_WALKMAN:
                walkmanOpen = !walkmanOpen;
                if (!puzzleActive) {
                    audio.playSfx(AudioManager.SFX_MENU_CLICK);
                }
                break;
            case InputManager.BTN_PAUSE:
                audio.playSfx(AudioManager.SFX_MENU_CLICK);
                togglePause();
                break;
            default:
                break;
        }
    }

    private void togglePause() {
        if (gsm.isGameplayState()) {
            gsm.push(GameStateManager.GameState.PAUSE);
        } else if (gsm.isPaused()) {
            gsm.pop();
        }
    }

    private void handlePlayerRequests() {
        if (player.consumePulseFired()) {
            audio.playSfx(AudioManager.SFX_SONAR);
            sonar.spawnWave(player.x, player.y, 1f, true);
            particles.stream(player.x, player.y, 0xFF50E8FF, 50f, 40f, 0.5f);
            if (distortion.isEnabled()) {
                distortion.trigger(player.x, player.y, 520f);
            }
            // Sonar blast stuns guards at close range.
            for (int i = 0; i < guards.size(); i++) {
                GuardEnemy g = guards.get(i);
                if (g.isActive() && g.distTo(player.x, player.y) < STUN_RADIUS) {
                    g.stun(6f);
                    particles.burst(g.x, g.y, 0xFFFFE070, 12, 120f, 0.4f);
                }
            }
        }
        if (player.consumeFootstepRequest()) {
            audio.playFootstep(player.footstepSurface, player.footstepSneak);
        }
        if (player.consumeInteractRequest()) {
            handleInteract();
        }
    }

    private void updateEnemies(float delta) {
        int mode = player.getMovementMode();
        boolean hidden = player.isHidden();

        for (int i = 0; i < drones.size(); i++) {
            DroneEnemy d = drones.get(i);
            if (!d.isActive()) {
                continue;
            }
            d.update(delta, player.x, player.y, mode, hidden);
            if (d.consumeAlertFlag()) {
                onDroneAlerted(d);
            }
        }
        for (int i = 0; i < guards.size(); i++) {
            GuardEnemy g = guards.get(i);
            if (!g.isActive()) {
                continue;
            }
            g.update(delta, player.x, player.y, mode, hidden);
            if (g.consumeStunRequested()) {
                audio.playSfx(AudioManager.SFX_STUN);
            }
            if (g.consumeAlarmFlag()) {
                onGuardAlarm(g);
            }
        }
        for (int i = 0; i < turrets.size(); i++) {
            TurretEnemy t = turrets.get(i);
            if (!t.isActive()) {
                continue;
            }
            t.update(delta, player.x, player.y, mode, hidden);
            if (t.consumeFireRequest()) {
                fireTurret(t);
            }
            if (t.consumeDisabledFlag()) {
                onTurretDisabled(i);
            }
        }
    }

    private void onDroneAlerted(DroneEnemy d) {
        audio.playSfx(AudioManager.SFX_DRONE_ALERT);
        alertsCount++;
        for (int i = 0; i < drones.size(); i++) {
            DroneEnemy other = drones.get(i);
            if (other != d && other.isActive()
                    && other.distTo(d.x, d.y) < DroneEnemy.ALERT_NOTIFY_RADIUS) {
                other.forceAlert(d.x, d.y);
            }
        }
    }

    private void onGuardAlarm(GuardEnemy g) {
        audio.playSfx(AudioManager.SFX_ALARM);
        alertsCount++;
        for (int i = 0; i < drones.size(); i++) {
            if (drones.get(i).isActive()) {
                drones.get(i).forceAlert(g.x, g.y);
            }
        }
        for (int i = 0; i < guards.size(); i++) {
            GuardEnemy other = guards.get(i);
            if (other != g && other.isActive() && !other.isStunned()) {
                other.forceAlert(g.x, g.y);
            }
        }
    }

    private void fireTurret(TurretEnemy t) {
        audio.playSfx(AudioManager.SFX_TURRET_FIRE);
        particles.burst(t.x, t.y, 0xFFFF8040, 8, 100f, 0.3f);
        Projectile p = acquireProjectile();
        if (p != null) {
            p.init(t.x, t.y, t.getBarrelAngle());
        }
    }

    private void onTurretDisabled(int index) {
        audio.playSfx(AudioManager.SFX_COLLECT);
        particles.burst(turrets.get(index).x, turrets.get(index).y,
                0xFF50FF90, 16, 90f, 0.6f);
        if (!disabledTurretIndices.contains(index)) {
            disabledTurretIndices.add(index);
        }
        quests.advanceObjective(QuestManager.TYPE_SOLVE_PUZZLE,
                "turret_" + (index + 1), 1);
        saveAuto();
    }

    private void updateProjectiles(float delta) {
        for (int i = 0; i < projectiles.length; i++) {
            Projectile p = projectiles[i];
            if (!p.isActive()) {
                continue;
            }
            p.update(map, delta, player);
            if (p.consumeHitPlayer()) {
                triggerGameOver();
            }
        }
    }

    private void updatePuzzles(float delta) {
        for (int i = 0; i < puzzles.size(); i++) {
            AcousticPuzzle p = puzzles.get(i);
            if (!p.isActive()) {
                continue;
            }
            p.update(delta);
            if (p.consumeSolveFlag()) {
                onPuzzleSolved(p);
            }
        }
    }

    private void onPuzzleSolved(AcousticPuzzle p) {
        audio.playSfx(AudioManager.SFX_COLLECT);
        particles.burst(p.x, p.y, 0xFF50E8FF, 16, 100f, 0.6f);
        int tIdx = p.getBoundTurretIndex();
        if (tIdx >= 0 && tIdx < turrets.size()) {
            turrets.get(tIdx).setDisabled(true);
        }
        String doorId = p.getBoundDoorId();
        if (doorId != null && !doorId.isEmpty() && !unlockedDoors.contains(doorId)) {
            unlockDoorById(doorId);
        }
        if (activePuzzle == p) {
            activePuzzle = null;
            puzzleActive = false;
            walkmanOpen = false;
        }
        saveAuto();
    }

    private void updateCollectibles(float delta) {
        for (int i = 0; i < collectibles.size(); i++) {
            CollectibleItem c = collectibles.get(i);
            if (!c.isActive()) {
                continue;
            }
            c.update(delta, player);
            if (c.consumePickupFlag()) {
                audio.playSfx(AudioManager.SFX_COLLECT);
                particles.burst(c.x, c.y, 0xFFFFE060, 10, 70f, 0.5f);
                quests.advanceObjective(QuestManager.TYPE_COLLECT_ITEM, c.getItemId(), 1);
                saveAuto();
            }
        }
    }

    private void updateProps(float delta) {
        for (int i = 0; i < props.size(); i++) {
            InteractiveProp p = props.get(i);
            if (!p.isActive()) {
                continue;
            }
            p.update(delta, player);
            if (p.consumeUnlockedFlag()) {
                audio.playSfx(AudioManager.SFX_DOOR_OPEN);
            }
        }
        for (int i = 0; i < doors.size(); i++) {
            DoorTerminal d = doors.get(i);
            if (!d.isActive()) {
                continue;
            }
            d.update(delta, player);
            if (d.consumeUnlockedFlag()) {
                audio.playSfx(AudioManager.SFX_DOOR_OPEN);
                openDoorTiles(d);
            }
        }
    }

    private void updateNpcs(float delta) {
        for (int i = 0; i < npcs.size(); i++) {
            NPC n = npcs.get(i);
            if (n.isActive()) {
                n.update(delta);
            }
        }
    }

    private void updateEffects(float delta) {
        sonar.update(delta, map);
        particles.update(delta);
        distortion.update(delta);
    }

    private void updateAudioHints(float delta) {
        audio.startDroneHum();
        float hum = 0f;
        for (int i = 0; i < drones.size(); i++) {
            DroneEnemy d = drones.get(i);
            if (d.isActive()) {
                float f = 1f - d.distTo(player.x, player.y) / 700f;
                if (f > hum) {
                    hum = f;
                }
            }
        }
        audio.setDroneHumVolume(Math.max(0f, Math.min(1f, hum)));
    }

    private void updateDetectionAndGameOver(float delta) {
        int level = HUDOverlay.DETECTION_NONE;
        boolean anyAlert = false;
        float nearestAlert = Float.MAX_VALUE;
        for (int i = 0; i < drones.size(); i++) {
            DroneEnemy d = drones.get(i);
            if (!d.isActive()) {
                continue;
            }
            if (d.isAlert()) {
                anyAlert = true;
                float dist = d.distTo(player.x, player.y);
                if (dist < nearestAlert) {
                    nearestAlert = dist;
                }
                level = HUDOverlay.DETECTION_ALERT;
            } else if (d.isChasing()) {
                if (level < HUDOverlay.DETECTION_SUSPICIOUS) {
                    level = HUDOverlay.DETECTION_SUSPICIOUS;
                }
            }
        }
        for (int i = 0; i < guards.size(); i++) {
            GuardEnemy g = guards.get(i);
            if (!g.isActive()) {
                continue;
            }
            if (g.isAlert()) {
                anyAlert = true;
                float dist = g.distTo(player.x, player.y);
                if (dist < nearestAlert) {
                    nearestAlert = dist;
                }
                level = HUDOverlay.DETECTION_ALERT;
            } else if (g.getState() != GuardEnemy.State.PATROL) {
                if (level < HUDOverlay.DETECTION_SUSPICIOUS) {
                    level = HUDOverlay.DETECTION_SUSPICIOUS;
                }
            }
        }
        hud.setDetectionLevel(level);

        if (anyAlert && nearestAlert < GAME_OVER_DISTANCE) {
            gameOverTimer += delta;
        } else {
            gameOverTimer = 0f;
        }
        if (gameOverTimer >= GAME_OVER_TIME) {
            triggerGameOver();
        }
    }

    private void triggerGameOver() {
        gameOverTimer = 0f;
        gameOverCount++;
        alertsCount++;
        audio.playSfx(AudioManager.SFX_ALARM);
        transition.start(0.8f, 0.2f, 0.8f);
        gsm.setState(GameStateManager.GameState.GAME_OVER);
    }

    private void updateMusicAndLighting(float delta) {
        int area = gsm.areaForState(gsm.current());
        if (area != currentMusicArea) {
            currentMusicArea = area;
            audio.playMusic(area);
        }
        // Rebuild the light set for this frame.
        lighting.clearLights();
        float s = map.getScale();
        lighting.addLight(map.worldToScreenX(player.x), map.worldToScreenY(player.y),
                190f * s, LightingRenderer.LIGHT_CYAN);
        for (int i = 0; i < drones.size(); i++) {
            DroneEnemy d = drones.get(i);
            if (!d.isActive()) {
                continue;
            }
            int type = d.isAlert() ? LightingRenderer.LIGHT_RED
                    : (d.isChasing() ? LightingRenderer.LIGHT_YELLOW
                    : LightingRenderer.LIGHT_WARM);
            lighting.addLight(map.worldToScreenX(d.x), map.worldToScreenY(d.y),
                    230f * s, type);
        }
        for (int i = 0; i < doors.size(); i++) {
            DoorTerminal d = doors.get(i);
            if (d.isActive()) {
                lighting.addLight(map.worldToScreenX(d.x), map.worldToScreenY(d.y),
                        220f * s, LightingRenderer.LIGHT_WARM);
            }
        }
        for (int i = 0; i < puzzles.size(); i++) {
            AcousticPuzzle p = puzzles.get(i);
            if (p.isActive()) {
                lighting.addLight(map.worldToScreenX(p.x), map.worldToScreenY(p.y),
                        200f * s, LightingRenderer.LIGHT_CYAN);
            }
        }
        lighting.update();
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    private void handleInteract() {
        NPC npc = nearestNPC(INTERACT_RADIUS);
        if (npc != null) {
            if (npc.startDialogue(dialogue)) {
                lastTalkNpcId = npc.getDialogueId();
                npc.setQuestTriggered(false);
                audio.playSfx(AudioManager.SFX_INTERACT);
                refreshDialogueLine();
            }
            return;
        }
        DoorTerminal door = nearestDoor(INTERACT_RADIUS);
        if (door != null) {
            if (door.interact(inventory)) {
                audio.playSfx(AudioManager.SFX_DOOR_OPEN);
            } else {
                audio.playSfx(AudioManager.SFX_DOOR_LOCKED);
            }
            return;
        }
        InteractiveProp crate = nearestProp(InteractiveProp.Kind.CRATE, INTERACT_RADIUS);
        if (crate != null) {
            pushCrate(crate);
            return;
        }
        AcousticPuzzle puzzle = nearestPuzzle(90f);
        if (puzzle != null) {
            if (!puzzle.isSolved()) {
                activePuzzle = puzzle;
                puzzleActive = true;
                audio.playSfx(AudioManager.SFX_INTERACT);
            } else {
                audio.playSfx(AudioManager.SFX_MENU_CLICK);
            }
            return;
        }
        InteractiveProp console = nearestProp(InteractiveProp.Kind.CONSOLE, INTERACT_RADIUS);
        if (console != null) {
            Integer choice = consoleChoices.get(console);
            if (choice != null) {
                choiceHandler.handleChoice(this, choice.intValue());
            } else {
                loreDiscovered.add("console_" + currentArea);
                audio.playSfx(AudioManager.SFX_INTERACT);
                dialogueBox.setLine("Console", loreForArea(currentArea));
            }
            return;
        }
    }

    private String loreForArea(int area) {
        switch (area) {
            case AREA_LAB:
                return "// AETH-WATCH // Clearance B. Drone cadence has increased "
                        + "since the power plant was sealed. Do not approach the archive.";
            case AREA_BASEMENT:
                return "// ARCHIVE LOG 441 // The acoustic locks are still active. "
                        + "Play the old cadence to release the turrets.";
            case AREA_DATA:
                return "// SIGNAL CORE // Two protocols: SAFE-GUARD (family) and "
                        + "NET-KILL (city). Only one can be executed.";
            default:
                return "// SLUM RELAY // The Walkman frequencies still echo down here. "
                        + "Listen closely, Raka.";
        }
    }

    private void pushCrate(InteractiveProp crate) {
        int tile = TileMapRenderer.TILE;
        int curTx = (int) Math.floor(crate.x / tile);
        int curTy = (int) Math.floor(crate.y / tile);
        int dirX = crate.x > player.x ? 1 : (crate.x < player.x ? -1 : 0);
        int dirY = crate.y > player.y ? 1 : (crate.y < player.y ? -1 : 0);
        if (dirX == 0 && dirY == 0) {
            dirX = player.x < crate.x ? 1 : -1;
        }
        int nx = curTx + dirX;
        int ny = curTy + dirY;
        if (map.isFloorTile(nx, ny)) {
            map.setBlocked(curTx, curTy, false);
            map.setBlocked(nx, ny, true);
            crate.x = nx * tile + tile * 0.5f;
            crate.y = ny * tile + tile * 0.5f;
            audio.playSfx(AudioManager.SFX_DOOR_OPEN, 0.5f, 1.2f);
            particles.burst(crate.x, crate.y, 0xFFBCAAA4, 8, 50f, 0.4f);
        } else {
            audio.playSfx(AudioManager.SFX_DOOR_LOCKED, 0.5f, 1f);
        }
    }

    private void throwStone(float wx, float wy) {
        if (inventory.hasItem("stone")) {
            inventory.removeItem("stone");
            audio.playSfx(AudioManager.SFX_STONE);
            particles.burst(wx, wy, 0xFFBCAAA4, 8, 60f, 0.4f);
            for (int i = 0; i < guards.size(); i++) {
                GuardEnemy g = guards.get(i);
                if (g.isActive() && g.distTo(wx, wy) < 90f) {
                    g.distract(wx, wy);
                }
            }
        } else {
            audio.playSfx(AudioManager.SFX_DOOR_LOCKED, 0.4f, 1f);
        }
    }

    // ------------------------------------------------------------------
    // Dialogue
    // ------------------------------------------------------------------

    private void refreshDialogueLine() {
        dialogueBox.setLine(dialogue.getSpeaker(), dialogue.getText());
    }

    private void updateDialogue(float delta) {
        dialogueBox.update(delta);
        for (int i = 0; i < events.size(); i++) {
            InputManager.InputEvent e = events.get(i);
            if (e.type != InputManager.EV_TAP) {
                continue;
            }
            boolean hitChoice = false;
            String[] choices = dialogue.getChoices();
            if (choices != null) {
                for (int c = 0; c < choiceRects.size(); c++) {
                    if (choiceRects.get(c).contains(e.x, e.y)) {
                        dialogue.choose(c);
                        refreshDialogueLine();
                        hitChoice = true;
                        break;
                    }
                }
            }
            if (hitChoice) {
                if (!dialogue.isActive()) {
                    endDialogue();
                }
                continue;
            }
            dialogueBox.tap();
            if (dialogueBox.consumePageAdvance()) {
                String[] ch = dialogue.getChoices();
                if (ch == null || ch.length == 0) {
                    dialogue.end();
                    endDialogue();
                }
            }
        }
    }

    private void endDialogue() {
        dialogueBox.finish();
        if (!lastTalkNpcId.isEmpty()) {
            quests.advanceObjective(QuestManager.TYPE_TALK_TO_NPC, lastTalkNpcId, 1);
            lastTalkNpcId = "";
        }
    }

    // ------------------------------------------------------------------
    // Proximity helpers
    // ------------------------------------------------------------------

    private NPC nearestNPC(float radius) {
        NPC best = null;
        float bestD = radius * radius;
        for (int i = 0; i < npcs.size(); i++) {
            NPC n = npcs.get(i);
            if (!n.isActive()) {
                continue;
            }
            float dx = n.x - player.x;
            float dy = n.y - player.y;
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    private DoorTerminal nearestDoor(float radius) {
        DoorTerminal best = null;
        float bestD = radius * radius;
        for (int i = 0; i < doors.size(); i++) {
            DoorTerminal d = doors.get(i);
            if (!d.isActive()) {
                continue;
            }
            float dx = d.x - player.x;
            float dy = d.y - player.y;
            float dist = dx * dx + dy * dy;
            if (dist < bestD) {
                bestD = dist;
                best = d;
            }
        }
        return best;
    }

    private InteractiveProp nearestProp(InteractiveProp.Kind kind, float radius) {
        InteractiveProp best = null;
        float bestD = radius * radius;
        for (int i = 0; i < props.size(); i++) {
            InteractiveProp p = props.get(i);
            if (!p.isActive() || p.getKind() != kind) {
                continue;
            }
            float dx = p.x - player.x;
            float dy = p.y - player.y;
            float dist = dx * dx + dy * dy;
            if (dist < bestD) {
                bestD = dist;
                best = p;
            }
        }
        return best;
    }

    private AcousticPuzzle nearestPuzzle(float radius) {
        AcousticPuzzle best = null;
        float bestD = radius * radius;
        for (int i = 0; i < puzzles.size(); i++) {
            AcousticPuzzle p = puzzles.get(i);
            if (!p.isActive() || p.isSolved()) {
                continue;
            }
            float dx = p.x - player.x;
            float dy = p.y - player.y;
            float dist = dx * dx + dy * dy;
            if (dist < bestD) {
                bestD = dist;
                best = p;
            }
        }
        return best;
    }

    private Projectile acquireProjectile() {
        for (int i = 0; i < projectiles.length; i++) {
            if (!projectiles[i].isActive()) {
                return projectiles[i];
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Doors
    // ------------------------------------------------------------------

    private void openDoorTiles(DoorTerminal d) {
        int[] tiles = doorTiles.get(d.getDoorId());
        if (tiles != null) {
            map.openDoorTile(tiles[0], tiles[1]);
            if (tiles[2] >= 0) {
                map.openDoorTile(tiles[2], tiles[3]);
            }
        }
        if (!unlockedDoors.contains(d.getDoorId())) {
            unlockedDoors.add(d.getDoorId());
        }
        player.setCheckpoint(d.x + 48f, d.y);
        quests.advanceObjective(QuestManager.TYPE_SOLVE_PUZZLE, d.getDoorId(), 1);
        saveAuto();
    }

    private void unlockDoorById(String doorId) {
        for (int i = 0; i < doors.size(); i++) {
            DoorTerminal d = doors.get(i);
            if (d.getDoorId().equals(doorId) && d.isLocked()) {
                d.unlock();
                openDoorTiles(d);
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Area loading
    // ------------------------------------------------------------------

    private void startNewGame() {
        inventory.clear();
        quests.restoreState(null, null);
        narrative.reset();
        unlockedDoors.clear();
        disabledTurretIndices.clear();
        loreDiscovered.clear();
        alertsCount = 0;
        gameOverCount = 0;
        playTimeTotal = 0f;
        endingSummaryMode = false;
        pendingEnding = 0;
        loadArea(GameStateManager.AREA_SLUM);
        player.setMoveTarget(player.x, player.y);
        gsm.setState(GameStateManager.GameState.TUTORIAL_SLUM);
        dialogue.start("meera_intro");
        refreshDialogueLine();
        saveAuto();
    }

    private boolean loadGame(int slot) {
        SaveDataManager.SaveData d = save.load(slot);
        if (d == null) {
            return false;
        }
        inventory.restore(d.inventory);
        quests.restoreState(d.activeQuests, d.completedQuests);
        narrative.restore(d.narrativeFlags);
        lastSaveSlot = slot;
        playTimeTotal = d.playTimeSeconds;
        alertsCount = d.alertsTriggered;

        loadArea(d.levelId);
        player.teleport(d.playerX, d.playerY);
        player.setCheckpoint(d.checkpointX, d.checkpointY);
        player.setStamina(d.stamina);

        unlockedDoors.clear();
        unlockedDoors.addAll(d.unlockedDoors);
        for (int i = 0; i < unlockedDoors.size(); i++) {
            unlockDoorById(unlockedDoors.get(i));
        }
        disabledTurretIndices.clear();
        disabledTurretIndices.addAll(d.disabledTurrets);
        for (int i = 0; i < disabledTurretIndices.size(); i++) {
            int idx = disabledTurretIndices.get(i);
            if (idx >= 0 && idx < turrets.size()) {
                turrets.get(idx).setDisabled(true);
            }
        }
        gsm.setState(gsm.stateForArea(d.levelId));
        return true;
    }

    private void loadArea(int area) {
        currentArea = area;
        int w = 60;
        int h = 40;
        long seed = 11;
        int surface = TileMapRenderer.SURFACE_CONCRETE;
        boolean exterior = area == AREA_SLUM;
        switch (area) {
            case AREA_LAB:
                w = 70;
                h = 44;
                seed = 22;
                surface = TileMapRenderer.SURFACE_METAL;
                break;
            case AREA_BASEMENT:
                w = 55;
                h = 40;
                seed = 33;
                surface = TileMapRenderer.SURFACE_METAL;
                break;
            case AREA_DATA:
                w = 64;
                h = 42;
                seed = 44;
                surface = TileMapRenderer.SURFACE_CONCRETE;
                break;
            default:
                break;
        }
        map.setParallaxEnabled(exterior);
        map.setAreaSurface(surface);
        map.generateMap(w, h, seed, surface);
        map.setViewSize(viewW, viewH);

        clearWorld();
        doorTiles.clear();
        consoleChoices.clear();

        if (area == AREA_SLUM) {
            buildSlum();
        } else if (area == AREA_LAB) {
            buildLab();
        } else if (area == AREA_BASEMENT) {
            buildBasement();
        } else {
            buildDataCenter();
        }

        lighting.setAmbientColor(area == AREA_SLUM ? 0xAF080A1E : 0xA0101620);
        currentMusicArea = -1;
    }

    private void clearWorld() {
        drones.clear();
        guards.clear();
        turrets.clear();
        props.clear();
        doors.clear();
        puzzles.clear();
        npcs.clear();
        collectibles.clear();
        for (int i = 0; i < projectiles.length; i++) {
            projectiles[i].setActive(false);
        }
        particles.clear();
        sonar.clear();
        puzzleActive = false;
        walkmanOpen = false;
        activePuzzle = null;
        dialogueBox.finish();
        hud.setInventoryOpen(false);
        hud.setDetectionLevel(HUDOverlay.DETECTION_NONE);
    }

    private PointF floorNear(int tx, int ty) {
        int w = map.getMapWidthTiles();
        int h = map.getMapHeightTiles();
        for (int r = 0; r <= 10; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int x = tx + dx;
                    int y = ty + dy;
                    if (x > 0 && y > 0 && x < w - 1 && y < h - 1 && map.isFloorTile(x, y)) {
                        return new PointF(x * TileMapRenderer.TILE + TileMapRenderer.TILE * 0.5f,
                                y * TileMapRenderer.TILE + TileMapRenderer.TILE * 0.5f);
                    }
                }
            }
        }
        return new PointF(tx * TileMapRenderer.TILE + TileMapRenderer.TILE * 0.5f,
                ty * TileMapRenderer.TILE + TileMapRenderer.TILE * 0.5f);
    }

    private void addDoor(String id, int tx, int ty, String requiredKey) {
        map.setBlocked(tx, ty, true);
        map.setBlocked(tx + 1, ty, true);
        DoorTerminal door = new DoorTerminal();
        door.init(tx * TileMapRenderer.TILE + TileMapRenderer.TILE * 0.5f,
                ty * TileMapRenderer.TILE + TileMapRenderer.TILE * 0.5f, id, requiredKey);
        doors.add(door);
        doorTiles.put(id, new int[]{tx, ty, tx + 1, ty});
    }

    private void addCrate(int tx, int ty) {
        map.setBlocked(tx, ty, true);
        InteractiveProp crate = new InteractiveProp(InteractiveProp.Kind.CRATE);
        crate.init(tx * TileMapRenderer.TILE + TileMapRenderer.TILE * 0.5f,
                ty * TileMapRenderer.TILE + TileMapRenderer.TILE * 0.5f);
        props.add(crate);
    }

    private void buildSlum() {
        PointF spawn = floorNear(3, 3);
        player.teleport(spawn.x, spawn.y);
        player.setCheckpoint(spawn.x, spawn.y);

        NPC meera = new NPC();
        meera.init(spawn.x + 64f, spawn.y, "Meera", "meera_intro", 0xFF4FA3A3);
        meera.setQuestTriggered(true);
        npcs.add(meera);
        NPC juno = new NPC();
        PointF junoPos = floorNear(12, 8);
        juno.init(junoPos.x, junoPos.y, "Old Juno", "juno_talk", 0xFFA08050);
        juno.setQuestTriggered(true);
        npcs.add(juno);

        addCollectible(28, 12, "walkman", 0xFFFFE082);
        addCollectible(20, 20, "stone", 0xFFBCAAA4);
        addCollectible(24, 10, "stone", 0xFFBCAAA4);
        addCollectible(34, 28, "battery_pack", 0xFF80D8FF);

        addCrate(10, 10);
        addCrate(10, 11);
        addCrate(16, 20);
        addCrate(17, 20);

        addDoor("lab_gate", 57, 20, DoorTerminal.KEY_BLUE);

        AcousticPuzzle tutorial = new AcousticPuzzle();
        tutorial.init(40 * 32 + 16, 30 * 32 + 16, new int[]{AcousticPuzzle.TONE_LOW,
                AcousticPuzzle.TONE_MID, AcousticPuzzle.TONE_HIGH});
        tutorial.bind(-1, "");
        puzzles.add(tutorial);

        DroneEnemy drone1 = new DroneEnemy(map, pathfinding);
        drone1.init(200f, 400f);
        drone1.setWaypoints(makeWaypoints(new float[]{
                200, 400, 420, 400, 420, 600, 200, 600}));
        drones.add(drone1);

        DroneEnemy drone2 = new DroneEnemy(map, pathfinding);
        drone2.init(900f, 200f);
        drone2.setWaypoints(makeWaypoints(new float[]{
                900, 200, 900, 600, 620, 600}));
        drones.add(drone2);
    }

    private void buildLab() {
        PointF spawn = floorNear(2, 20);
        player.teleport(spawn.x, spawn.y);
        player.setCheckpoint(spawn.x, spawn.y);

        NPC warden = new NPC();
        PointF wardenPos = floorNear(6, 20);
        warden.init(wardenPos.x, wardenPos.y, "Warden Kess", "warden_lab", 0xFF6E8CA0);
        warden.setQuestTriggered(true);
        npcs.add(warden);

        addCollectible(30, 6, "keycard_blue", 0xFF4FC3F7);
        addCollectible(8, 36, "stone", 0xFFBCAAA4);
        addCollectible(8, 37, "stone", 0xFFBCAAA4);
        addCollectible(46, 6, "battery_pack", 0xFF80D8FF);
        addCollectible(50, 8, "keycard_yellow", 0xFFFFEE58);

        TurretEnemy t0 = new TurretEnemy(map);
        PointF turretPos = floorNear(44, 22);
        t0.init(turretPos.x, turretPos.y);
        turrets.add(t0);

        AcousticPuzzle puzzle1 = new AcousticPuzzle();
        PointF p1 = floorNear(40, 26);
        puzzle1.init(p1.x, p1.y, new int[]{AcousticPuzzle.TONE_MID,
                AcousticPuzzle.TONE_HIGH, AcousticPuzzle.TONE_LOW,
                AcousticPuzzle.TONE_HIGH});
        puzzle1.bind(0, "");
        puzzles.add(puzzle1);

        addDoor("lab_door", 55, 22, DoorTerminal.KEY_BLUE);
        addDoor("basement_gate", 68, 10, DoorTerminal.KEY_YELLOW);

        GuardEnemy g1 = new GuardEnemy(map, pathfinding);
        g1.init(300f, 600f);
        g1.setWaypoints(makeWaypoints(new float[]{300, 600, 520, 600}));
        guards.add(g1);

        GuardEnemy g2 = new GuardEnemy(map, pathfinding);
        g2.init(600f, 300f);
        g2.setWaypoints(makeWaypoints(new float[]{600, 300, 600, 700}));
        guards.add(g2);

        DroneEnemy drone = new DroneEnemy(map, pathfinding);
        drone.init(900f, 300f);
        drone.setWaypoints(makeWaypoints(new float[]{900, 300, 1300, 300}));
        drones.add(drone);
    }

    private void buildBasement() {
        PointF spawn = floorNear(2, 20);
        player.teleport(spawn.x, spawn.y);
        player.setCheckpoint(spawn.x, spawn.y);

        addCollectible(40, 6, "usb_drive", 0xFFB388FF);
        addCollectible(45, 10, "freq_modulator", 0xFF69F0AE);
        addCollectible(30, 34, "battery_pack", 0xFF80D8FF);
        addCollectible(12, 8, "keycard_red", 0xFFFF5252);

        addCrate(26, 10);
        addCrate(26, 11);

        TurretEnemy t1 = new TurretEnemy(map);
        PointF t1p = floorNear(30, 18);
        t1.init(t1p.x, t1p.y);
        turrets.add(t1);
        AcousticPuzzle pz1 = new AcousticPuzzle();
        PointF p1 = floorNear(26, 16);
        pz1.init(p1.x, p1.y, new int[]{AcousticPuzzle.TONE_HIGH,
                AcousticPuzzle.TONE_LOW, AcousticPuzzle.TONE_MID,
                AcousticPuzzle.TONE_HIGH, AcousticPuzzle.TONE_LOW});
        pz1.bind(0, "");
        puzzles.add(pz1);

        TurretEnemy t2 = new TurretEnemy(map);
        PointF t2p = floorNear(50, 30);
        t2.init(t2p.x, t2p.y);
        turrets.add(t2);
        AcousticPuzzle pz2 = new AcousticPuzzle();
        PointF p2 = floorNear(46, 28);
        pz2.init(p2.x, p2.y, new int[]{AcousticPuzzle.TONE_MID,
                AcousticPuzzle.TONE_HIGH, AcousticPuzzle.TONE_LOW,
                AcousticPuzzle.TONE_MID, AcousticPuzzle.TONE_HIGH,
                AcousticPuzzle.TONE_LOW});
        pz2.bind(1, "");
        puzzles.add(pz2);

        addDoor("data_gate", 52, 20, DoorTerminal.KEY_RED);

        DroneEnemy drone = new DroneEnemy(map, pathfinding);
        drone.init(500f, 200f);
        drone.setWaypoints(makeWaypoints(new float[]{500, 200, 1000, 200, 1000, 600}));
        drones.add(drone);

        GuardEnemy guard = new GuardEnemy(map, pathfinding);
        guard.init(600f, 900f);
        guard.setWaypoints(makeWaypoints(new float[]{600, 900, 600, 500}));
        guards.add(guard);
    }

    private void buildDataCenter() {
        PointF spawn = floorNear(2, 20);
        player.teleport(spawn.x, spawn.y);
        player.setCheckpoint(spawn.x, spawn.y);

        addCollectible(30, 6, "battery_pack", 0xFF80D8FF);
        addCollectible(34, 8, "battery_pack", 0xFF80D8FF);

        InteractiveProp left = new InteractiveProp(InteractiveProp.Kind.CONSOLE);
        PointF lp = floorNear(20, 20);
        left.init(lp.x, lp.y);
        props.add(left);
        consoleChoices.put(left, ChoiceEventHandler.CHOICE_SAVE_PARENTS);

        InteractiveProp right = new InteractiveProp(InteractiveProp.Kind.CONSOLE);
        PointF rp = floorNear(40, 20);
        right.init(rp.x, rp.y);
        props.add(right);
        consoleChoices.put(right, ChoiceEventHandler.CHOICE_PARALYZE_CITY);

        DroneEnemy drone = new DroneEnemy(map, pathfinding);
        drone.init(900f, 300f);
        drone.setWaypoints(makeWaypoints(new float[]{900, 300, 1500, 300, 1500, 700, 900, 700}));
        drones.add(drone);

        GuardEnemy guard = new GuardEnemy(map, pathfinding);
        guard.init(800f, 1000f);
        guard.setWaypoints(makeWaypoints(new float[]{800, 1000, 1500, 1000}));
        guards.add(guard);

        AcousticPuzzle consolePuzzle = new AcousticPuzzle();
        PointF cp = floorNear(30, 30);
        consolePuzzle.init(cp.x, cp.y, new int[]{AcousticPuzzle.TONE_LOW,
                AcousticPuzzle.TONE_HIGH, AcousticPuzzle.TONE_MID,
                AcousticPuzzle.TONE_LOW, AcousticPuzzle.TONE_HIGH,
                AcousticPuzzle.TONE_MID});
        consolePuzzle.bind(-1, "");
        puzzles.add(consolePuzzle);
    }

    private void addCollectible(int tx, int ty, String itemId, int color) {
        PointF p = floorNear(tx, ty);
        CollectibleItem item = new CollectibleItem(inventory);
        item.init(p.x, p.y, itemId, color);
        collectibles.add(item);
    }

    private ArrayList<PointF> makeWaypoints(float[] coords) {
        ArrayList<PointF> pts = new ArrayList<PointF>();
        for (int i = 0; i + 1 < coords.length; i += 2) {
            pts.add(new PointF(coords[i], coords[i + 1]));
        }
        return pts;
    }

    // ------------------------------------------------------------------
    // Save / reset
    // ------------------------------------------------------------------

    private void saveAuto() {
        SaveDataManager.SaveData d = new SaveDataManager.SaveData();
        d.levelId = currentArea;
        d.playerX = player.x;
        d.playerY = player.y;
        d.stamina = player.getStamina();
        d.checkpointX = player.getCheckpointX();
        d.checkpointY = player.getCheckpointY();
        d.playTimeSeconds = (long) playTimeTotal;
        d.endingChoice = narrative.getEndingChoice();
        d.alertsTriggered = alertsCount;
        d.inventory.addAll(inventory.getItems());
        List<QuestManager.Quest> activeQuests = quests.getActiveQuests();
        for (int i = 0; i < activeQuests.size(); i++) {
            d.activeQuests.add(activeQuests.get(i).id);
        }
        d.unlockedDoors.addAll(unlockedDoors);
        d.disabledTurrets.addAll(disabledTurretIndices);
        d.loreDiscovered.addAll(loreDiscovered);
        JSONObject flags = narrative.exportFlags();
        Iterator<String> keys = flags.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            d.narrativeFlags.put(key, flags.opt(key));
        }
        save.save(0, d);
    }

    private void resetProgress() {
        for (int slot = 0; slot < SaveDataManager.SLOT_COUNT; slot++) {
            save.deleteSlot(slot);
        }
        inventory.clear();
        quests.restoreState(null, null);
        narrative.reset();
        unlockedDoors.clear();
        disabledTurretIndices.clear();
        loreDiscovered.clear();
        alertsCount = 0;
    }

    private void requestExit() {
        exitRequested = true;
        if (context instanceof Activity) {
            final Activity act = (Activity) context;
            act.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    act.finish();
                }
            });
        }
    }

    // ------------------------------------------------------------------
    // Back handling
    // ------------------------------------------------------------------

    public boolean handleBack() {
        if (gsm.isGameplayState()) {
            togglePause();
            return true;
        }
        return gsm.handleBack();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public void render(Canvas canvas) {
        float w = canvas.getWidth();
        float h = canvas.getHeight();
        GameStateManager.GameState s = gsm.current();

        if (s == GameStateManager.GameState.MAIN_MENU) {
            menuView.draw(canvas, w, h, density);
            return;
        }
        if (s == GameStateManager.GameState.SETTINGS) {
            settingsView.draw(canvas, w, h, density);
            return;
        }
        if (s == GameStateManager.GameState.CREDITS) {
            if (endingSummaryMode) {
                endingView.draw(canvas, w, h, density);
            } else {
                creditsView.draw(canvas, w, h, density);
            }
            return;
        }

        // World rendering under a single transform.
        map.drawBackground(canvas);
        canvas.save();
        canvas.translate(-map.getCamX() * scale, -map.getCamY() * scale);
        canvas.scale(scale, scale);
        map.draw(canvas);
        drawPropsWorld(canvas);
        drawDoorsWorld(canvas);
        drawPuzzlesWorld(canvas);
        drawCollectiblesWorld(canvas);
        drawNpcsWorld(canvas);
        drawEnemiesWorld(canvas);
        drawProjectilesWorld(canvas);
        player.draw(canvas);
        particles.draw(canvas);
        sonar.draw(canvas);
        distortion.draw(canvas);
        canvas.restore();

        lighting.draw(canvas);

        if (puzzleActive || walkmanOpen) {
            drawToneButtons(canvas, w, h);
        }

        monitor.getStats(stats);
        hud.draw(canvas, input, player, quests, gsm, stats[PerformanceMonitor.STAT_INDEX_FPS]);

        if (dialogue.isActive()) {
            dialogueBox.draw(canvas, w, h, density);
            drawDialogueChoices(canvas, w, h);
        }

        transition.draw(canvas);

        if (s == GameStateManager.GameState.GAME_OVER) {
            drawGameOverText(canvas, w, h);
        }
    }

    private void drawPropsWorld(Canvas canvas) {
        for (int i = 0; i < props.size(); i++) {
            InteractiveProp p = props.get(i);
            if (p.isActive()) {
                p.draw(canvas);
            }
        }
    }

    private void drawDoorsWorld(Canvas canvas) {
        for (int i = 0; i < doors.size(); i++) {
            DoorTerminal d = doors.get(i);
            if (d.isActive()) {
                d.draw(canvas);
            }
        }
    }

    private void drawPuzzlesWorld(Canvas canvas) {
        for (int i = 0; i < puzzles.size(); i++) {
            AcousticPuzzle p = puzzles.get(i);
            if (p.isActive()) {
                p.draw(canvas);
            }
        }
    }

    private void drawCollectiblesWorld(Canvas canvas) {
        for (int i = 0; i < collectibles.size(); i++) {
            CollectibleItem c = collectibles.get(i);
            if (c.isActive()) {
                c.draw(canvas);
            }
        }
    }

    private void drawNpcsWorld(Canvas canvas) {
        for (int i = 0; i < npcs.size(); i++) {
            NPC n = npcs.get(i);
            if (n.isActive()) {
                n.draw(canvas);
            }
        }
    }

    private void drawEnemiesWorld(Canvas canvas) {
        for (int i = 0; i < drones.size(); i++) {
            DroneEnemy d = drones.get(i);
            if (d.isActive()) {
                d.draw(canvas);
            }
        }
        for (int i = 0; i < guards.size(); i++) {
            GuardEnemy g = guards.get(i);
            if (g.isActive()) {
                g.draw(canvas);
            }
        }
        for (int i = 0; i < turrets.size(); i++) {
            TurretEnemy t = turrets.get(i);
            if (t.isActive()) {
                t.draw(canvas);
            }
        }
    }

    private void drawProjectilesWorld(Canvas canvas) {
        for (int i = 0; i < projectiles.length; i++) {
            if (projectiles[i].isActive()) {
                projectiles[i].draw(canvas);
            }
        }
    }

    private int toneAt(float sx, float sy) {
        if (toneRectLow.contains(sx, sy)) {
            return AcousticPuzzle.TONE_LOW;
        }
        if (toneRectMid.contains(sx, sy)) {
            return AcousticPuzzle.TONE_MID;
        }
        if (toneRectHigh.contains(sx, sy)) {
            return AcousticPuzzle.TONE_HIGH;
        }
        return -1;
    }

    private void drawToneButtons(Canvas canvas, float w, float h) {
        float d = density;
        float r = 40f * d;
        float cy = h - 130f * d;
        float cx0 = w * 0.5f - 100f * d;
        float cx1 = w * 0.5f;
        float cx2 = w * 0.5f + 100f * d;
        toneRectLow.set(cx0 - r, cy - r, cx0 + r, cy + r);
        toneRectMid.set(cx1 - r, cy - r, cx1 + r, cy + r);
        toneRectHigh.set(cx2 - r, cy - r, cx2 + r, cy + r);

        toneTextPaint.setTextSize(20f * d);
        drawToneButton(canvas, cx0, cy, r, "LOW", toneRectLow);
        drawToneButton(canvas, cx1, cy, r, "MID", toneRectMid);
        drawToneButton(canvas, cx2, cy, r, "HIGH", toneRectHigh);
    }

    private void drawToneButton(Canvas canvas, float cx, float cy, float r,
                                String label, RectF rect) {
        boolean active = false;
        if (puzzleActive && activePuzzle != null) {
            int progress = activePuzzle.getProgress();
            int tone = label.equals("LOW") ? AcousticPuzzle.TONE_LOW
                    : label.equals("MID") ? AcousticPuzzle.TONE_MID
                    : AcousticPuzzle.TONE_HIGH;
            active = tone == activePuzzle.getToneAt(Math.min(progress, activePuzzle.getLength() - 1));
        }
        canvas.drawCircle(cx, cy, r, active ? toneActivePaint : tonePaint);
        canvas.drawText(label, cx, cy + 7f * density, toneTextPaint);
    }

    private void drawDialogueChoices(Canvas canvas, float w, float h) {
        String[] choices = dialogue.getChoices();
        choiceRects.clear();
        choicePoolIndex = 0;
        if (choices == null) {
            return;
        }
        float d = density;
        float y = h - 190f * d;
        dialogueBox.getLastBox(tmpRect);
        float left = tmpRect.left + 20f * d;
        choicePaint.setARGB(255, 170, 220, 255);
        choicePaint.setTextSize(16f * d);
        for (int i = 0; i < choices.length; i++) {
            canvas.drawText(choices[i], left, y, choicePaint);
            float width = choicePaint.measureText(choices[i]);
            RectF r = obtainChoiceRect();
            r.set(left - 8f * d, y - 16f * d, left + width + 8f * d, y + 6f * d);
            choiceRects.add(r);
            y += 26f * d;
        }
    }

    private RectF obtainChoiceRect() {
        if (choicePoolIndex >= choiceRectPool.size()) {
            choiceRectPool.add(new RectF());
        }
        return choiceRectPool.get(choicePoolIndex++);
    }

    private void drawGameOverText(Canvas canvas, float w, float h) {
        overlayTextPaint.setTextSize(64f * density);
        overlayTextPaint.setColor(0xFFFF4040);
        canvas.drawText("GAME OVER", w * 0.5f, h * 0.45f, overlayTextPaint);
        overlayTextPaint.setTextSize(18f * density);
        overlayTextPaint.setColor(0xFFDDDDEE);
        canvas.drawText("Returning to checkpoint...", w * 0.5f, h * 0.52f, overlayTextPaint);
    }

    // ------------------------------------------------------------------
    // Accessors used by ChoiceEventHandler / views
    // ------------------------------------------------------------------

    public GameStateManager getGameStateManager() {
        return gsm;
    }

    public BranchingNarrative getNarrative() {
        return narrative;
    }

    public MapTransitionEffect getTransition() {
        return transition;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPendingEnding(int ending) {
        pendingEnding = ending;
    }

    public boolean isExitRequested() {
        return exitRequested;
    }
}
