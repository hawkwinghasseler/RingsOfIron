package com.ringsofiron.physicsexterior;

import com.badlogic.gdx.math.Vector2;

/**
 * The gravity felt inside the bubble: the difference between the parent body's pull at a point
 * and its pull on the free-falling anchor, {@code g(R_a + r) - g(R_a)} with
 * {@code g(X) = -mu X / |X|^3}. Computed in doubles and cast to float at the end.
 *
 * This is the one shared field for everything in the bubble: {@code TidalGravitySystem} applies
 * it to Box2D bodies, and combat's projectile integrator and the HUD's trajectory preview should
 * call the same {@link #accelAt} so all of them agree. It is zero when the anchor has no gravity.
 */
public class TidalField {
    private final PhysicsManager physics;

    TidalField(PhysicsManager physics) {
        this.physics = physics;
    }

    /** Tidal acceleration at a bubble-frame position, in m/s^2, at the current sim time. */
    public Vector2 accelAt(float localX, float localY, Vector2 out) {
        double mu = physics.getAnchorModel().getMu();
        if (mu == 0.0) return out.setZero();

        double ax = physics.getOriginX();
        double ay = physics.getOriginY();
        double bx = ax + localX;
        double by = ay + localY;
        double ra2 = ax * ax + ay * ay;
        double rb2 = bx * bx + by * by;
        double ka = mu / (ra2 * Math.sqrt(ra2));
        double kb = mu / (rb2 * Math.sqrt(rb2));
        return out.set((float) (ka * ax - kb * bx), (float) (ka * ay - kb * by));
    }
}
