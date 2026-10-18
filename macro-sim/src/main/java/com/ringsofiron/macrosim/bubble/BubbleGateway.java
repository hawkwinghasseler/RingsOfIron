package com.ringsofiron.macrosim.bubble;

import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.Trajectory;

/**
 * What {@link BubbleTransitionSystem} needs from {@code physics-exterior}: the bubble anchor and the
 * handoff helpers ({@code spawnFromTrueState}, {@code trueStateOf}, body destruction,
 * {@code KinematicTransientComponent}). All states are true states in doubles, relative to the
 * anchor's parent body, never bubble-frame floats. Main thread only, except {@link #anchorTrajectory()}.
 */
public interface BubbleGateway {
    /** UUID of the body the anchor orbits (the player's current SOI body). */
    String anchorParentUuid();

    /** Anchor state at sim second {@code t}, parent-relative. */
    StateVector anchorStateAt(double t, StateVector out);

    /** Immutable copy of the anchor's trajectory, safe to evaluate on a background thread. */
    Trajectory anchorTrajectory();

    /** Creates the entity's Box2D body at this true state. The entity and UUID already exist. */
    void spawnFromTrueState(String uuid, StateVector trueState, double t);

    /** True state of a body that is in the bubble. */
    StateVector trueStateOf(String uuid, double t, StateVector out);

    /** Destroys the entity's Box2D body; the entity itself stays. */
    void destroyBody(String uuid);

    /** Starts drawing and collision-sweeping a fast body that stays on rails along {@code path}. */
    void beginKinematicTransient(String uuid, Trajectory path);

    void endKinematicTransient(String uuid);
}
