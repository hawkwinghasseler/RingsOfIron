package com.ringsofiron.physicsexterior;

import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.orbital.math.OrbitMath;

/**
 * The gravity felt inside the bubble: the difference between the parent body's pull at a point
 * and its pull on the free-falling anchor, {@code g(R_a + r) - g(R_a)} with
 * {@code g(X) = -mu X / |X|^3}, from orbital-mechanics' {@code OrbitMath.tidalAcceleration}.
 * Computed in doubles and cast to float at the end.
 *
 * This is the one shared field for everything in the bubble: {@code TidalGravitySystem} applies
 * it to Box2D bodies, and combat's projectile integrator and the HUD's trajectory preview should
 * call the same {@link #accelAt} so all of them agree. It is zero when the anchor has no gravity.
 */
public class TidalField {
    private final PhysicsManager physics;
    private final double[] accel = new double[2];

    TidalField(PhysicsManager physics) {
        this.physics = physics;
    }

    /** Tidal acceleration at a bubble-frame position, in m/s^2, at the current sim time. */
    public Vector2 accelAt(float localX, float localY, Vector2 out) {
        double mu = physics.getAnchorModel().getMu();
        if (mu == 0.0) return out.setZero();

        OrbitMath.tidalAcceleration(mu, physics.getOriginX(), physics.getOriginY(), localX, localY, accel);
        return out.set((float) accel[0], (float) accel[1]);
    }
}
