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
 * A patrolling drone. Full AI: waypoint patrol with random pauses, a 60 degree
 * vision cone with line-of-sight raycasting, hearing (modified by the player's
 * movement mode and hide status), a four-state FSM (PATROL / SUSPICIOUS /
 * ALERT / RETURN_TO_PATROL), A* chasing and alert propagation to nearby drones.
 */
public final class DroneEnemy extends GameObject {

    public enum State { PATROL, SUSPICIOUS, ALERT, RETURN_TO_PATROL }

    public static final float CONE_ANGLE = (float) Math.PI / 3f;
    public static final float SIGHT_RANGE = 400f;
    public static final float HEARING_RADIUS = 250f;
    public static final float PATROL_SPEED = 150f;
    public static final float ALERT_SPEED = 195f;
    public static final float ALERT_NOTIFY_RADIUS = 300f;
    private static final float HIDDEN_MULT = 0.3f;
    private static final float SPOT_TIME = 0.3f;
    private static final float LOSE_SIGHT_TIME = 5f;
    private static final float SUSPICIOUS_TIMEOUT = 3f;

    private final TileMapRenderer map;
    private final AIPathfinding pathfinding;
    private final Random rng = new Random();
    private final StateMachine<State> fsm;

    private final ArrayList<PointF> waypoints = new ArrayList<PointF>();
    private final ArrayList<Point> pathBuffer = new ArrayList<Point>();
    private int waypointIndex = 0;
    private float waypointPause = 0f;

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint domePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rotorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float facingRad = 0f;
    private float speed = PATROL_SPEED;
    private float sightTimer = 0f;
    private float lostSightTimer = 0f;
    private float suspiciousTimer = 0f;
    private float repathTimer = 0f;
    private int pathIndex = 0;
    private float lastKnownX;
    private float lastKnownY;
    private float startX;
    private float startY;
    private boolean alertFlag = false;
    private boolean suspiciousFlag = false;
    private float rotorAnim = 0f;

    public DroneEnemy(TileMapRenderer map, AIPathfinding pathfinding) {
        this.map = map;
        this.pathfinding = pathfinding;
        width = 34f;
        height = 34f;
        bodyPaint.setARGB(255, 80, 90, 110);
        rotorPaint.setARGB(255, 40, 44, 55);
        domePaint.setARGB(255, 0, 180, 255);
        fsm = new StateMachine<State>(new StateListener(), State.values());
        startX = x;
        startY = y;
    }

    public void setWaypoints(List<PointF> pts) {
        waypoints.clear();
        waypoints.addAll(pts);
        if (!waypoints.isEmpty()) {
            waypointIndex = 0;
            facingRad = (float) Math.atan2(
                    waypoints.get(0).y - y, waypoints.get(0).x - x);
        }
    }

    public void init(float x, float y) {
        this.x = x;
        this.y = y;
        startX = x;
        startY = y;
        active = true;
        fsm.reset();
    }

    /** Core AI update driven from the scene. */
    public void update(float delta, float px, float py, int playerMode, boolean playerHidden) {
        if (!active) {
            return;
        }
        rotorAnim += delta * 40f;

        float dist = distTo(px, py);
        float visionRange = SIGHT_RANGE * (playerHidden ? HIDDEN_MULT : 1f);
        boolean canSee = !playerHidden && VisionConeLogic.canSee(map, x, y,
                facingRad, CONE_ANGLE, visionRange, px, py);
        boolean canHear = HearingLogic.hears(x, y, px, py, HEARING_RADIUS, playerMode);

        if (fsm.isIn(State.PATROL)) {
            if (canSee || canHear) {
                lastKnownX = px;
                lastKnownY = py;
                suspiciousFlag = true;
                fsm.setState(State.SUSPICIOUS);
            }
        } else if (fsm.isIn(State.SUSPICIOUS)) {
            if (canSee) {
                sightTimer += delta;
                lastKnownX = px;
                lastKnownY = py;
            } else {
                sightTimer = 0f;
            }
            if (sightTimer >= SPOT_TIME) {
                alertFlag = true;
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
        } else if (fsm.isIn(State.ALERT)) {
            lastKnownX = px;
            lastKnownY = py;
            if (canSee) {
                lostSightTimer = 0f;
            } else {
                lostSightTimer += delta;
                if (lostSightTimer >= LOSE_SIGHT_TIME) {
                    fsm.setState(State.RETURN_TO_PATROL);
                    return;
                }
            }
        } else if (fsm.isIn(State.RETURN_TO_PATROL)) {
            // Fall back toward patrol start.
        }

        // Movement is driven by the FSM listener, then the dome color.
        fsm.update(delta);

        // Keep facing toward the player when alert, else look ahead of travel.
        if (fsm.isIn(State.ALERT)) {
            float dx = px - x;
            float dy = py - y;
            if (dx * dx + dy * dy > 0.001f) {
                facingRad = (float) Math.atan2(dy, dx);
            }
        }

        // Visual state color on the dome.
        int color;
        if (fsm.isIn(State.ALERT)) {
            color = 0xFFFF3B3B;
        } else if (fsm.isIn(State.SUSPICIOUS)) {
            color = 0xFFFFD54F;
        } else {
            color = 0xFF00B4FF;
        }
        domePaint.setColor(color);
    }

    private void moveTo(float tx, float ty, float delta, float spd) {
        if (repathTimer <= 0f) {
            repathTimer = 0.35f;
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
            float step = spd * delta;
            if (step >= d) {
                x = cx;
                y = cy;
                pathIndex++;
                continue;
            }
            float nx = x + dx / d * step;
            float ny = y + dy / d * step;
            float half = width * 0.5f;
            if (map.isWalkableRect(nx - half, y - half, nx + half, y + half)) {
                x = nx;
            }
            if (map.isWalkableRect(x - half, ny - half, x + half, ny + half)) {
                y = ny;
            }
            if (speed == 0f) {
                speed = spd;
            }
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
        if (waypoints.isEmpty()) {
            return;
        }
        if (waypointPause > 0f) {
            waypointPause -= delta;
            return;
        }
        PointF wp = waypoints.get(waypointIndex);
        float dx = wp.x - x;
        float dy = wp.y - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 12f) {
            waypointIndex = (waypointIndex + 1) % waypoints.size();
            waypointPause = 1f + rng.nextFloat() * 2f;
            PointF next = waypoints.get(waypointIndex);
            facingRad = (float) Math.atan2(next.y - y, next.x - x);
        } else {
            facingRad = (float) Math.atan2(dy, dx);
            moveLinearToward(wp.x, wp.y, delta, PATROL_SPEED);
        }
    }

    private class StateListener implements StateMachine.StateListener<State> {
        @Override
        public void onStateEnter(State state) {
            if (state == State.ALERT) {
                speed = ALERT_SPEED;
                alertFlag = true;
            } else if (state == State.SUSPICIOUS) {
                speed = PATROL_SPEED;
            } else {
                speed = PATROL_SPEED;
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
                    moveTo(lastKnownX, lastKnownY, delta, speed);
                    break;
                case ALERT:
                    moveTo(lastKnownX, lastKnownY, delta, speed);
                    break;
                case RETURN_TO_PATROL:
                    moveTo(startX, startY, delta, PATROL_SPEED);
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

    // --- Public API ----------------------------------------------------------

    /** External alarm propagation: snap into alert and chase the given point. */
    public void forceAlert(float ax, float ay) {
        if (!active) {
            return;
        }
        lastKnownX = ax;
        lastKnownY = ay;
        alertFlag = true;
        fsm.setState(State.ALERT);
    }

    /** Consumed once per transition into ALERT (alarm + hive notification). */
    public boolean consumeAlertFlag() {
        boolean f = alertFlag;
        alertFlag = false;
        return f;
    }

    public boolean consumeSuspiciousFlag() {
        boolean f = suspiciousFlag;
        suspiciousFlag = false;
        return f;
    }

    public State getState() {
        return fsm.getCurrent();
    }

    public boolean isAlert() {
        return fsm.isIn(State.ALERT);
    }

    public boolean isChasing() {
        return fsm.isIn(State.ALERT) || fsm.isIn(State.SUSPICIOUS);
    }

    public float getFacingRad() {
        return facingRad;
    }

    public float getSightRange() {
        return SIGHT_RANGE;
    }

    public float getLastKnownX() {
        return lastKnownX;
    }

    public float getLastKnownY() {
        return lastKnownY;
    }

    @Override
    public void draw(Canvas canvas) {
        if (!active) {
            return;
        }
        float r = width * 0.5f;
        // Rotor blur (fast, static look).
        canvas.drawOval(x - r, y - r - 6f, x + r, y + r - 6f, rotorPaint);
        // Body.
        canvas.drawCircle(x, y, r, bodyPaint);
        // Dome (the alert light).
        float domeR = r * 0.55f;
        canvas.drawCircle(x, y - 2f, domeR, domePaint);
        // Ring accents.
        canvas.drawOval(x - r, y - r, x + r, y + r, rotorPaint);
        // Pointing antenna toward facing.
        float ax = x + (float) Math.cos(facingRad) * (r + 6f);
        float ay = y + (float) Math.sin(facingRad) * (r + 6f);
        canvas.drawLine(x, y, ax, ay, domePaint);
    }

    @Override
    public void reset() {
        super.reset();
        x = startX;
        y = startY;
        fsm.reset();
        waypointIndex = 0;
        waypointPause = 0f;
        sightTimer = 0f;
        lostSightTimer = 0f;
        suspiciousTimer = 0f;
        alertFlag = false;
        suspiciousFlag = false;
        speed = PATROL_SPEED;
    }
}
