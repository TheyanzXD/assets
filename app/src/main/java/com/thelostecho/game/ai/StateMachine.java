package com.thelostecho.game.ai;

/**
 * Minimal, allocation-free finite state machine. The owning entity provides an
 * {@link StateListener} that receives enter/update/exit callbacks keyed by the
 * enum state, so enemy behaviour stays data-driven and consistent.
 */
public final class StateMachine<S extends Enum<S>> {

    public interface StateListener<S> {
        void onStateEnter(S state);

        void onStateUpdate(S state, float delta);

        void onStateExit(S state);
    }

    private final StateListener<S> listener;
    private final S[] states;
    private S current;
    private S previous;
    private float timeInState = 0f;

    @SafeVarargs
    public StateMachine(StateListener<S> listener, S... allStates) {
        this.listener = listener;
        this.states = allStates;
        if (states.length > 0) {
            this.current = states[0];
            this.previous = states[0];
        }
    }

    public void setState(S newState) {
        if (current == newState) {
            return;
        }
        if (listener != null && current != null) {
            listener.onStateExit(current);
        }
        previous = current;
        current = newState;
        timeInState = 0f;
        if (listener != null && current != null) {
            listener.onStateEnter(current);
        }
    }

    public void update(float delta) {
        timeInState += delta;
        if (listener != null && current != null) {
            listener.onStateUpdate(current, delta);
        }
    }

    public S getCurrent() {
        return current;
    }

    public S getPrevious() {
        return previous;
    }

    public float getTimeInState() {
        return timeInState;
    }

    public boolean isIn(S state) {
        return current == state;
    }

    /** Forces the machine back to its initial state (used on entity reset). */
    public void reset() {
        if (states.length > 0) {
            current = states[0];
            previous = states[0];
        }
        timeInState = 0f;
    }
}
