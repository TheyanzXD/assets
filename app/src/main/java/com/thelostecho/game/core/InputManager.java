package com.thelostecho.game.core;

import android.graphics.RectF;
import android.view.MotionEvent;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Central input processor. Reads raw MotionEvents on the UI thread and exposes
 * (a) a virtual joystick, (b) edge-detected button presses and (c) gesture
 * events (tap / double-tap / long-press / swipe). All state reads and the event
 * queue are synchronized because the game thread consumes them concurrently.
 */
public final class InputManager {

    public static final int MAX_POINTERS = 5;

    public static final int EV_TAP = 0;
    public static final int EV_DOUBLE_TAP = 1;
    public static final int EV_LONG_PRESS = 2;
    public static final int EV_SWIPE = 3;
    public static final int EV_BUTTON_PRESSED = 4;
    public static final int EV_BUTTON_RELEASED = 5;

    public static final int BTN_SONAR = 0;
    public static final int BTN_INTERACT = 1;
    public static final int BTN_INVENTORY = 2;
    public static final int BTN_PAUSE = 3;
    public static final int BTN_WALKMAN = 4;
    public static final int BTN_COUNT = 5;

    private static final float TAP_MAX_TIME = 0.28f;
    private static final float DOUBLE_TAP_WINDOW = 0.35f;
    private static final float LONG_PRESS_TIME = 0.6f;

    /** Gesture event object (pooled to keep steady-state allocation at zero). */
    public static final class InputEvent {
        public int type;
        public int button;
        public float x;
        public float y;
        public float dx;
        public float dy;
    }

    private static final class TouchPoint {
        boolean down;
        boolean tapCandidate;
        boolean longPressFired;
        boolean moved;
        int pointerId = -1;
        int buttonIndex = -1;
        float startX;
        float startY;
        float lastX;
        float lastY;
        float downTime;
        float lastUpTime;
    }

    private final TouchPoint[] pointers;
    private final RectF[] buttonRects;
    private final boolean[] buttonDown;
    private final boolean[] buttonConsumed;
    private final RectF joystickZone;

    private final ArrayDeque<InputEvent> eventQueue = new ArrayDeque<InputEvent>();
    private final ArrayDeque<InputEvent> freePool = new ArrayDeque<InputEvent>();

    private float screenW;
    private float screenH;
    private float density;

    private float joystickX;
    private float joystickY;
    private boolean joystickActive;
    private int joystickSlot = -1;
    private float joystickOriginX;
    private float joystickOriginY;
    private float joystickRadius;
    private boolean dpadScheme = true;

    private float lastTapX;
    private float lastTapY;
    private float lastTapTime;

    public InputManager() {
        pointers = new TouchPoint[MAX_POINTERS];
        for (int i = 0; i < MAX_POINTERS; i++) {
            pointers[i] = new TouchPoint();
        }
        buttonRects = new RectF[BTN_COUNT];
        for (int i = 0; i < BTN_COUNT; i++) {
            buttonRects[i] = new RectF();
        }
        buttonDown = new boolean[BTN_COUNT];
        buttonConsumed = new boolean[BTN_COUNT];
        joystickZone = new RectF();
    }

    /** Recomputes all button/joystick regions. Call on surface size change. */
    public synchronized void updateLayout(float w, float h, float d) {
        screenW = w;
        screenH = h;
        density = d;
        float pad = 24f * d;
        joystickZone.set(0f, h * 0.32f, w * 0.44f, h);

        setButtonRect(BTN_SONAR, w - 88f * d, h - 96f * d, 55f * d);
        setButtonRect(BTN_INTERACT, w - 88f * d, h - 206f * d, 42f * d);
        setButtonRect(BTN_WALKMAN, w - 198f * d, h - 96f * d, 42f * d);
        setButtonRect(BTN_INVENTORY, w - 54f * d, h * 0.45f, 34f * d);
        setButtonRect(BTN_PAUSE, w - 44f * d, 66f * d, 26f * d);
        joystickRadius = 72f * d;
        lastTapTime = -1f;
    }

    private void setButtonRect(int btn, float cx, float cy, float radius) {
        buttonRects[btn].set(cx - radius, cy - radius, cx + radius, cy + radius);
    }

    public void setDpadScheme(boolean dpad) {
        synchronized (this) {
            dpadScheme = dpad;
            if (!dpad) {
                cancelAllInternal();
            }
        }
    }

    public boolean isDpadScheme() {
        synchronized (this) {
            return dpadScheme;
        }
    }

    /** Handles a single MotionEvent. UI thread only. */
    public void process(MotionEvent event) {
        synchronized (this) {
            int action = event.getActionMasked();
            int index = event.getActionIndex();
            int id = event.getPointerId(index);
            float now = event.getEventTime() / 1000f;
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    pointerDown(id, event.getX(index), event.getY(index), now);
                    break;
                case MotionEvent.ACTION_MOVE:
                    int count = Math.min(event.getPointerCount(), MAX_POINTERS);
                    for (int i = 0; i < count; i++) {
                        pointerMove(event.getPointerId(i), event.getX(i), event.getY(i), now);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    pointerUp(id, event.getX(index), event.getY(index), now);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    cancelAllInternal();
                    break;
                default:
                    break;
            }
        }
    }

    private void pointerDown(int pointerId, float x, float y, float now) {
        int slot = findFreeSlot();
        if (slot < 0) {
            return;
        }
        TouchPoint p = pointers[slot];
        p.down = true;
        p.pointerId = pointerId;
        p.startX = x;
        p.startY = y;
        p.lastX = x;
        p.lastY = y;
        p.downTime = now;
        p.moved = false;
        p.tapCandidate = false;
        p.longPressFired = false;
        p.buttonIndex = -1;

        if (dpadScheme && joystickSlot < 0 && joystickZone.contains(x, y)) {
            joystickSlot = slot;
            joystickActive = true;
            joystickOriginX = x;
            joystickOriginY = y;
            joystickX = 0f;
            joystickY = 0f;
            return;
        }
        int btn = buttonAt(x, y);
        if (btn >= 0) {
            if (!buttonDown[btn]) {
                buttonDown[btn] = true;
                buttonConsumed[btn] = false;
            }
            p.buttonIndex = btn;
            queueEvent(EV_BUTTON_PRESSED, btn, x, y, 0f, 0f);
            return;
        }
        p.tapCandidate = true;
    }

    private void pointerMove(int pointerId, float x, float y, float now) {
        int slot = findSlotById(pointerId);
        if (slot < 0) {
            return;
        }
        TouchPoint p = pointers[slot];
        if (!p.moved) {
            float dx = x - p.startX;
            float dy = y - p.startY;
            float threshold = 18f * density;
            if (dx * dx + dy * dy > threshold * threshold) {
                p.moved = true;
            }
        }
        if (joystickSlot == slot) {
            updateJoystick(x, y);
        } else if (p.tapCandidate && !p.moved) {
            float held = now - p.downTime;
            if (held >= LONG_PRESS_TIME && !p.longPressFired) {
                p.longPressFired = true;
                queueEvent(EV_LONG_PRESS, -1, p.startX, p.startY, 0f, 0f);
            }
        }
        p.lastX = x;
        p.lastY = y;
    }

    private void pointerUp(int pointerId, float x, float y, float now) {
        int slot = findSlotById(pointerId);
        if (slot < 0) {
            return;
        }
        TouchPoint p = pointers[slot];
        if (joystickSlot == slot) {
            joystickActive = false;
            joystickSlot = -1;
            joystickX = 0f;
            joystickY = 0f;
        }
        if (p.buttonIndex >= 0) {
            int btn = p.buttonIndex;
            if (buttonDown[btn]) {
                buttonDown[btn] = false;
            }
            queueEvent(EV_BUTTON_RELEASED, btn, x, y, 0f, 0f);
        } else if (p.tapCandidate && !p.moved) {
            float held = now - p.downTime;
            if (held <= TAP_MAX_TIME) {
                boolean near = Math.abs(x - lastTapX) < 40f * density
                        && Math.abs(y - lastTapY) < 40f * density;
                if (lastTapTime >= 0f && now - lastTapTime <= DOUBLE_TAP_WINDOW && near) {
                    queueEvent(EV_DOUBLE_TAP, -1, x, y, 0f, 0f);
                    lastTapTime = -1f;
                } else {
                    queueEvent(EV_TAP, -1, x, y, 0f, 0f);
                    lastTapX = x;
                    lastTapY = y;
                    lastTapTime = now;
                }
            }
        } else if (p.moved) {
            float dx = x - p.startX;
            float dy = y - p.startY;
            float minSwipe = 70f * density;
            if (dx * dx + dy * dy > minSwipe * minSwipe) {
                queueEvent(EV_SWIPE, -1, x, y, dx, dy);
            }
        }
        p.down = false;
        p.pointerId = -1;
        p.lastUpTime = now;
    }

    private void updateJoystick(float x, float y) {
        float dx = x - joystickOriginX;
        float dy = y - joystickOriginY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > joystickRadius && len > 0.0001f) {
            dx = dx / len * joystickRadius;
            dy = dy / len * joystickRadius;
        }
        float max = joystickRadius;
        if (max <= 0f) {
            max = 1f;
        }
        joystickX = dx / max;
        joystickY = dy / max;
    }

    private void cancelAllInternal() {
        joystickActive = false;
        joystickSlot = -1;
        joystickX = 0f;
        joystickY = 0f;
        for (int i = 0; i < BTN_COUNT; i++) {
            buttonDown[i] = false;
        }
        for (int i = 0; i < MAX_POINTERS; i++) {
            pointers[i].down = false;
            pointers[i].pointerId = -1;
            pointers[i].buttonIndex = -1;
        }
        eventQueue.clear();
    }

    private int buttonAt(float x, float y) {
        for (int i = 0; i < BTN_COUNT; i++) {
            if (buttonRects[i].contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private int findFreeSlot() {
        for (int i = 0; i < MAX_POINTERS; i++) {
            if (!pointers[i].down) {
                return i;
            }
        }
        return -1;
    }

    private int findSlotById(int pointerId) {
        for (int i = 0; i < MAX_POINTERS; i++) {
            if (pointers[i].down && pointers[i].pointerId == pointerId) {
                return i;
            }
        }
        return -1;
    }

    private InputEvent obtain() {
        InputEvent e = freePool.poll();
        return e != null ? e : new InputEvent();
    }

    private void queueEvent(int type, int button, float x, float y, float dx, float dy) {
        InputEvent e = obtain();
        e.type = type;
        e.button = button;
        e.x = x;
        e.y = y;
        e.dx = dx;
        e.dy = dy;
        eventQueue.addLast(e);
    }

    /** Moves all pending events into {@code out}. Game thread only. */
    public synchronized void drainEvents(List<InputEvent> out) {
        while (!eventQueue.isEmpty()) {
            out.add(eventQueue.poll());
        }
    }

    /** Returns an event to the pool. Call after fully processing it. */
    public synchronized void recycleEvent(InputEvent e) {
        if (freePool.size() < 32) {
            freePool.addLast(e);
        }
    }

    public synchronized float getJoystickX() {
        return joystickX;
    }

    public synchronized float getJoystickY() {
        return joystickY;
    }

    public synchronized boolean isJoystickActive() {
        return joystickActive;
    }

    public synchronized float getJoystickOriginX() {
        return joystickOriginX;
    }

    public synchronized float getJoystickOriginY() {
        return joystickOriginY;
    }

    public synchronized float getJoystickRadius() {
        return joystickRadius;
    }

    public synchronized boolean isButtonDown(int btn) {
        return btn >= 0 && btn < BTN_COUNT && buttonDown[btn];
    }

    public synchronized boolean consumeButton(int btn) {
        if (btn >= 0 && btn < BTN_COUNT && buttonDown[btn] && !buttonConsumed[btn]) {
            buttonConsumed[btn] = true;
            return true;
        }
        return false;
    }

    public void getButtonRect(int btn, RectF out) {
        if (btn >= 0 && btn < BTN_COUNT) {
            out.set(buttonRects[btn]);
        }
    }

    public float getDensity() {
        return density;
    }
}
