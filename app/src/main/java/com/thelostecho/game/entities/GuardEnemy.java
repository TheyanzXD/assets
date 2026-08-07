package com.thelostecho.game.entities;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;

import com.thelostecho.game.ai.AIPathfinding;
import com.thelostecho.game.ai.HearingLogic;
import com.thelostecho.game.ai.StateMachine;
import com.thelostecho.game.ai.VisionConeLogic;
import com.thelostecho.game.graphics.TileMapRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A human guard. Similar sensors to a drone (narrower vision, no rooftop flight
 * restrictions) but with two extra behaviours: it INVESTIGATES noises by moving
 * to the last heard position, and it can be STUNNED by a close-range sonar
 * blast, or distracted by a thrown stone. If it stays in ALERT for two seconds
 * it pulls the alarm, which instantly alerts every other guard/drone on the map
 * (unless stunned first).
 */
public final class GuardEnemy extends GameObject {

    public enum State { PATROL, SUSPICIOUS, INVESTIGATE, ALERT, RETURN_TO_PATROL }

    public static final float CONE_ANGLE = (float) Math.PI / 3.6f;
    public static final float SIGHT_RANGE = 320f;
    public static final float HEARING_RADIUS = 210f;
    public static final float WALK_SPEED = 120f;
    public static final float RUN_SPEED = 210f;
    private static final float HIDDEN_MULT = 0.3f;
    private static final float SPOT_TIME = 0.4f;
    private static final float SUSPICIOUS_TIMEOUT = 4f;
    private static final float ALARM_DELAY = 2f;
    private static final float STUN_DURATION = 6f;

    private final TileMapRenderer map;
    private final AIPathfinding pathfinding;
    private final Random rng = new Random();
    private final StateMachine<State> fsm;

    private final ArrayList<PointF> waypoints = new ArrayList<PointF>();
    private final ArrayList<Point> pathBuffer = new ArrayList<Point>();
    private int waypointIndex = 0;
    private float waypointPause = 0f;
    private float lookPause = 0f;
    private float lookAngle = 0f;

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint visorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vestPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float facingRad = 0f;
    private float sightTimer = 0f;
    private float suspiciousTimer = 0f;
    private float alarmTimer = 0f;
    private float stunTimer = 0f;
    private float repathTimer = 0f;
    private int pathIndex = 0;
    private float lastKnownX;
    private float lastKnownY;
    private float startX;
    private float startY;
    private boolean alarmFlag = false;
    private boolean stunRequested = false;
    private boolean distracted = false;
    private float animTimer = 0f;

    public GuardEnemy(TileMapRenderer map, AIPathfinding pathfinding) {
        this.map = map;
        this.pathfinding = pathfinding;
        width = 20f;
        height = 20f;
        bodyPaint.setARGB(255, 110, 96, 76);
        visorPaint.setARGB(255, 220, 220, 230);
        vestPaint.setARGB(255, 70, 70, 90);
        stunPaint.setARGB(120, 255, 230, 80);
        fsm = new StateMachine<State>(new StateListener(), State.values());
        startX = x;
        startY = y;
    }

    public void setWaypoints(List<PointF> pts) {
        waypoints.clear();
        waypoints.addAll(pts);
        if (!waypoints.isEmpty()) {
            waypointIndex = 0;
            facingRad = (float) Math.atan2(waypoints.get(0).y - y,
                    waypoints.get(0).x - x);
        }
    }

    public void init(float x, float y) {
        this.x = x;
        this.y = y;
        startX = x;
        startY = y;
        active = true;
        fsm.reset();
        stunTimer = 0f;
    }

    public void update(float delta, float px, float py, int playerMode, boolean playerHidden) {
        if (!active) {
            return;
        }
        animTimer += delta;
        if (stunTimer > 0f) {
            stunTimer -= delta;
            if (stunTimer <= 0f) {
                fsm.setState(State.PATROL);
            }
            return;
        }

        float dist = distTo(px, py);
        float visionRange = SIGHT_RANGE * (playerHidden ? HIDDEN_MULT : 1f);
        boolean canSee = !playerHidden && VisionConeLogic.canSee(map, x, y,
                facingRad, CONE_ANGLE, visionRange, px, py);
        boolean canHear = HearingLogic.hears(x, y, px, py, HEARING_RADIUS, playerMode);

        State cur = fsm.getCurrent();
        if (cur == State.PATROL) {
            if (canSee || canHear) {
                lastKnownX = px;
                lastKnownY = py;
                fsm.setState(State.SUSPICIOUS);
            }
        } else if (cur == State.SUSPICIOUS) {
            if (canSee) {
                sightTimer += delta;
                lastKnownX = px;
                lastKnownY = py;
            } else {
                sightTimer = 0f;
            }
            if (sightTimer >= SPOT_TIME) {
                alarmTimer = 0f;
                fsm.setState(State.ALERT);
                return;
            }
            if (canHear) {
                lastKnownX = px;
                lastKnownY = py;
                suspiciousTimer = 0f;
            } else {
                suspiciousTimer += delta;
                if (suspiciousTimer >= SUSPICIOUS_TIMEOUT) {
                    fsm.setState(State.PATROL);
                    return;
                }
            }
        } else if (cur == State.INVESTIGATE) {
            if (canSee) {
                sightTimer += delta;
                lastKnownX = px;
                lastKnownY = py;
                if (sightTimer >= SPOT_TIME) {
                    alarmTimer = 0f;
                    fsm.setState(State.ALERT);
                    return;
                }
            } else {
                sightTimer = 0f;
            }
            if (distTo(lastKnownX, lastKnownY) < 30f) {
                fsm.setState(State.PATROL);
            }
        } else if (cur == State.ALERT) {
            lastKnownX = px;
            lastKnownY = py;
            alarmTimer += delta;
            if (alarmTimer >= ALARM_DELAY) {
                alarmFlag = true;
                alarmTimer = 0f;
            }
        }

        fsm.update(delta);

        if (fsm.isIn(State.ALERT)) {
            float dx = px - x;
            float dy = py - y;
            if (dx * dx + dy * dy > 0.001f) {
                facingRad = (float) Math.atan2(dy, dx);
            }
        }
    }

    // --- Behaviour helpers ----------------------------------------------------

    private void moveTo(float tx, float ty, float delta, float spd) {
        if (repathTimer <= 0f) {
            repathTimer = 0.4f;
            List<Point> found = pathfinding.findPath(x, y, tx, ty);
            pathBuffer.clear();
            pathBuffer.addAll(found);
            pathIndex = 0;
        } else {
            repathTimer -= delta;
        }
        if (pathBuffer.isEmpty()) {
            return;
        }
        int tile = TileMapRenderer.TILE;
        while (pathIndex < pathBuffer.size()) {
            Point p = pathBuffer.get(pathIndex);
            float cx = p.x * tile + tile * 0.5f;
            float cy = p.y * tile + tile * 0.5f;
            float dx = cx - x;
            float dy = cy - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < 6f) {
                pathIndex++;
                continue;
            }
            float step = Math.min(spd * delta, d);
            float nx = x + dx / d * step;
            float ny = y + dy / d * step;
            float half = width * 0.5f;
            if (map.isWalkableRect(nx - half, y - half, nx + half, y + half)) {
                x = nx;
            }
            if (map.isWalkableRect(x - half, ny - half, x + half, ny + half)) {
                y = ny;
            }
            facingRad = (float) Math.atan2(dy, dx);
            break;
        }
    }

    private void moveLinearToward(float tx, float ty, float delta, float spd) {
        float dx = tx - x;
        float dy = ty - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 8f) {
            return;
        }
        float step = Math.min(spd * delta, d);
        float nx = x + dx / d * step;
        float ny = y + dy / d * step;
        float half = width * 0.5f;
        if (map.isWalkableRect(nx - half, y - half, nx + half, y + half)) {
            x = nx;
        }
        if (map.isWalkableRect(x - half, ny - half, x + half, ny + half)) {
            y = ny;
        }
    }

    private void patrolUpdate(float delta) {
        if (lookPause > 0f) {
            lookPause -= delta;
            facingRad += delta * 0.9f;
            return;
        }
        if (waypointPause > 0f) {
            waypointPause -= delta;
            return;
        }
        if (waypoints.isEmpty()) {
            return;
        }
        PointF wp = waypoints.get(waypointIndex);
        float dx = wp.x - x;
        float dy = wp.y - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 12f) {
            waypointIndex = (waypointIndex + 1) % waypoints.size();
            waypointPause = 0.8f + rng.nextFloat() * 1.6f;
            lookPause = 0.4f + rng.nextFloat() * 1.2f;
        } else {
            facingRad = (float) Math.atan2(dy, dx);
            moveLinearToward(wp.x, wp.y, delta, WALK_SPEED);
        }
    }

    private class StateListener implements StateMachine.StateListener<State> {
        @Override
        public void onStateEnter(State state) {
            if (state == State.INVESTIGATE) {
                sightTimer = 0f;
            }
            if (state == State.ALERT) {
                alarmTimer = 0f;
            }
            if (state == State.RETURN_TO_PATROL) {
                pathBuffer.clear();
                pathIndex = 0;
            }
        }

        @Override
        public void onStateUpdate(State state, float delta) {
            switch (state) {
                case PATROL:
                    patrolUpdate(delta);
                    break;
                case SUSPICIOUS:
                    facingRad = (float) Math.atan2(lastKnownY - y, lastKnownX - x);
                    break;
                case INVESTIGATE:
                    moveTo(lastKnownX, lastKnownY, delta, WALK_SPEED);
                    break;
                case ALERT:
                    moveTo(lastKnownX, lastKnownY, delta, RUN_SPEED);
                    break;
                case RETURN_TO_PATROL:
                    moveTo(startX, startY, delta, WALK_SPEED);
                    if (distTo(startX, startY) < 24f) {
                        fsm.setState(State.PATROL);
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onStateExit(State state) {
        }
    }

    // --- Public API ------------------------------------------------------------

    /** A thrown stone redirected the guard to investigate a position. */
    public void distract(float tx, float ty) {
        if (active && stunTimer <= 0f && !fsm.isIn(State.ALERT)) {
            lastKnownX = tx;
            lastKnownY = ty;
            distracted = true;
            fsm.setState(State.INVESTIGATE);
        }
    }

    /** Sonar blast at close range: stuns for a while and cancels the alarm. */
    public void stun(float seconds) {
        if (active) {
            stunTimer = Math.max(stunTimer, seconds);
            alarmTimer = 0f;
            alarmFlag = false;
            stunRequested = true;
            fsm.setState(State.PATROL);
        }
    }

    /** External alarm propagation: snap to alert immediately and sound off. */
    public void forceAlert(float ax, float ay) {
        if (!active || isStunned()) {
            return;
        }
        lastKnownX = ax;
        lastKnownY = ay;
        alarmTimer = 0f;
        alarmFlag = true;
        fsm.setState(State.ALERT);
    }

    public boolean consumeStunRequested() {
        boolean s = stunRequested;
        stunRequested = false;
        return s;
    }

    public boolean consumeAlarmFlag() {
        boolean f = alarmFlag;
        alarmFlag = false;
        return f;
    }

    public State getState() {
        return fsm.getCurrent();
    }

    public boolean isAlert() {
        return fsm.isIn(State.ALERT);
    }

    public boolean isStunned() {
        return stunTimer > 0f;
    }

    public boolean isDistracted() {
        return distracted;
    }

    public float getFacingRad() {
        return facingRad;
    }

    public float getSightRange() {
        return SIGHT_RANGE;
    }

    @Override
    public void draw(Canvas canvas) {
        if (!active) {
            return;
        }
        float r = width * 0.5f;
        if (stunTimer > 0f && ((int) (animTimer * 8f)) % 2 == 0) {
            canvas.drawCircle(x, y, r + 6f, stunPaint);
        }
        canvas.drawCircle(x, y, r, bodyPaint);
        canvas.drawCircle(x + 2f, y, r * 0.55f, vestPaint);
        float fx = x + (float) Math.cos(facingRad) * (r + 4f);
        float fy = y + (float) Math.sin(facingRad) * (r + 4f);
        canvas.drawLine(x, y, fx, fy, visorPaint);
        canvas.drawCircle(fx, fy, 2.5f, visorPaint);
    }

    @Override
    public void reset() {
        super.reset();
        x = startX;
        y = startY;
        fsm.reset();
        waypointIndex = 0;
        waypointPause = 0f;
        lookPause = 0f;
        sightTimer = 0f;
        suspiciousTimer = 0f;
        alarmTimer = 0f;
        stunTimer = 0f;
        alarmFlag = false;
        stunRequested = false;
        distracted = false;
    }
}
