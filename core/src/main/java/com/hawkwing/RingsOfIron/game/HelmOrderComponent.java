package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;

/**
 * On the player's ship: what the helmsman has been ordered to fly while the player is away from the
 * helm ({@link HelmOrders}). Saved with the ship.
 */
public class HelmOrderComponent implements Component {
    public enum Mode {
        /** Hold the heading the player left, as round 2's helmsman does. */
        NONE,
        /** Fly the planned burns with the main engine. */
        FLY_BURNS,
        /** Match the navigation target's velocity. */
        MATCH_VELOCITY,
        /** Keep station where the ship is relative to the navigation target. */
        HOLD_POSITION,
        /** Close on the navigation target and hold at {@link #distance}. */
        APPROACH
    }

    public Mode mode = Mode.NONE;
    /** Who the order flies relative to (the navigation target when it was given). */
    public String targetUuid;
    /** Approach: how far off the target's hull to stop, m. */
    public double distance = HelmOrders.APPROACH_DISTANCES[0];
    /** Hold position: where to hold, relative to the target, m. */
    public double holdX, holdY;
    /** A burn being flown: delta-v still to go, m/s, in the parent body's frame. */
    public double burnX, burnY;
    public boolean burning;
    /** Sim second the helm was last manned while the order stood; for dropping it when nobody comes. */
    public double mannedAt = -1;
}
