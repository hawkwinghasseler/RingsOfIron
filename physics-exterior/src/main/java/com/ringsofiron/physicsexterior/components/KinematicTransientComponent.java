package com.ringsofiron.physicsexterior.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.ObjectSet;
import com.ringsofiron.physicsexterior.TrueTrajectory;

/**
 * A body passing through the bubble too fast for Box2D (a "fast pass"). It follows its
 * {@link #trajectory} exactly and is swept against the Box2D world each tick by
 * {@code KinematicTransientSystem}, which fires a {@code FastPassImpactEvent} for each body it
 * passes through. It never gets a Box2D body while it has this component.
 *
 * Whoever owns the handoff (macro-sim's {@code BubbleTransitionSystem}) adds this component,
 * and removes it to promote the body to Box2D once {@link #relativeSpeed} drops below
 * {@code BubbleLimits.B2D_ENTRY_SPEED}, or to send it back to rails when it leaves the bubble.
 */
public class KinematicTransientComponent implements Component {
    public TrueTrajectory trajectory;
    /** Half the body's width, in meters. Wide bodies are swept with extra rays at +/- this offset. */
    public float radius;

    /** Bubble-frame position and velocity at the current tick, in meters and m/s. Written by the system. */
    public float x, y, vx, vy;
    /** Speed relative to the anchor, in m/s. Written by the system. */
    public float relativeSpeed;

    /** Bubble-frame position one tick ago, moved by any rebase since. */
    public transient float previousX, previousY;
    public transient boolean hasPrevious;
    /** Targets already reported as hit (their entity, or the body if it has none), so each is reported once per pass. */
    public final transient ObjectSet<Object> alreadyHit = new ObjectSet<>();

    public KinematicTransientComponent() {
    }

    public KinematicTransientComponent(TrueTrajectory trajectory, float radius) {
        this.trajectory = trajectory;
        this.radius = radius;
    }
}
