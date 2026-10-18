package com.ringsofiron.aicrew.pilot;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;

/**
 * Sim time and the orbital frame an NPC pilot flies its burns in. Prograde is the ship's
 * velocity relative to its parent body; radial points from the parent body to the ship.
 *
 * The game wires a real one from macro-sim's bubble gateway (true parent-relative
 * state). {@link #bubbleOnly} is a stand-in that treats the bubble origin as the parent,
 * good enough to test the pilot without the rails simulation.
 */
public interface PilotReference {

    double simSeconds();

    /** The ship's velocity relative to its parent body, m/s. {@code bubbleVel} is its velocity in the bubble. */
    Vector2 parentRelativeVelocity(Entity ship, Vector2 bubbleVel, Vector2 out);

    /** The ship's position relative to its parent body, m. {@code bubblePos} is its position in the bubble. */
    Vector2 parentRelativePosition(Entity ship, Vector2 bubblePos, Vector2 out);

    /** A frame where the bubble origin stands in for the parent body. */
    static PilotReference bubbleOnly(java.util.function.DoubleSupplier simSeconds) {
        return new PilotReference() {
            @Override
            public double simSeconds() {
                return simSeconds.getAsDouble();
            }

            @Override
            public Vector2 parentRelativeVelocity(Entity ship, Vector2 bubbleVel, Vector2 out) {
                return out.set(bubbleVel);
            }

            @Override
            public Vector2 parentRelativePosition(Entity ship, Vector2 bubblePos, Vector2 out) {
                return out.set(bubblePos);
            }
        };
    }
}
