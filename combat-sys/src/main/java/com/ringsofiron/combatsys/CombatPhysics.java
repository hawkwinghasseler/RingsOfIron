package com.ringsofiron.combatsys;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.physicsexterior.PhysicsManager;

/**
 * The one place combat converts between SI meters and Box2D units (combat-sys/Design.md 1.1).
 * Complements the helpers on {@link PhysicsManager} with the point-based queries combat needs.
 */
public final class CombatPhysics {
    /** Rounds past this distance from the anchor have left the bubble and are dropped, meters. */
    public static final float BUBBLE_EXIT_RADIUS = 25_000f;

    private static final float M = PhysicsManager.METERS_PER_UNIT;
    private static final float U = PhysicsManager.UNITS_PER_METER;

    private CombatPhysics() {
    }

    /** Applies a linear impulse in N*s at a local-meter point. */
    public static void applyImpulseAt(Body body, float ix, float iy, float px, float py) {
        body.applyLinearImpulse(ix * U, iy * U, px * U, py * U, true);
    }

    /** Velocity of the body's material at a local-meter point, m/s (includes spin). */
    public static Vector2 velocityAt(Body body, float px, float py, Vector2 out) {
        return out.set(body.getLinearVelocityFromWorldPoint(new Vector2(px * U, py * U))).scl(M);
    }

    /** Converts a local (bubble) point to body-local meters. */
    public static Vector2 toBodyLocal(Body body, float px, float py, Vector2 out) {
        return out.set(body.getLocalPoint(new Vector2(px * U, py * U))).scl(M);
    }

    /** Converts a body-local point in meters to local (bubble) meters. */
    public static Vector2 toWorld(Body body, float lx, float ly, Vector2 out) {
        return out.set(body.getWorldPoint(new Vector2(lx * U, ly * U))).scl(M);
    }

    /** Rotates a bubble-frame direction into the body's frame (no scaling). */
    public static Vector2 vectorToBodyLocal(Body body, float vx, float vy, Vector2 out) {
        return out.set(vx, vy).rotateRad(-body.getAngle());
    }

    /** Rotates a body-frame direction into the bubble frame (no scaling). */
    public static Vector2 vectorToWorld(Body body, float vx, float vy, Vector2 out) {
        return out.set(vx, vy).rotateRad(body.getAngle());
    }
}
