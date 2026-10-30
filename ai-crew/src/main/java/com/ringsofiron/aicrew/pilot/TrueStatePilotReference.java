package com.ringsofiron.aicrew.pilot;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.aicrew.pilot.PilotReference;
import com.ringsofiron.physicsexterior.PhysicsManager;

import java.util.function.DoubleSupplier;

/**
 * The NPC pilot's orbital frame from physics-exterior's true state: bubble position and velocity
 * plus the anchor's, relative to the body the bubble orbits.
 */
public class TrueStatePilotReference implements PilotReference {
    private final PhysicsManager physics;
    private final DoubleSupplier simSeconds;

    public TrueStatePilotReference(PhysicsManager physics, DoubleSupplier simSeconds) {
        this.physics = physics;
        this.simSeconds = simSeconds;
    }

    @Override
    public double simSeconds() {
        return simSeconds.getAsDouble();
    }

    @Override
    public Vector2 parentRelativeVelocity(Entity ship, Vector2 bubbleVel, Vector2 out) {
        return out.set((float) (physics.getOriginVx() + bubbleVel.x), (float) (physics.getOriginVy() + bubbleVel.y));
    }

    @Override
    public Vector2 parentRelativePosition(Entity ship, Vector2 bubblePos, Vector2 out) {
        return out.set((float) (physics.getOriginX() + bubblePos.x), (float) (physics.getOriginY() + bubblePos.y));
    }
}
