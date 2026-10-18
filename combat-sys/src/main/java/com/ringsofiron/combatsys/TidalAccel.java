package com.ringsofiron.combatsys;

import com.badlogic.gdx.math.Vector2;

/**
 * Tidal (differential) gravity in the bubble frame: the acceleration a free body at a local
 * position feels relative to the free-falling anchor. combat-sys/Design.md section 1.
 *
 * physics-exterior owns the real field ({@code TidalField.accelAt}); combat calls it through this
 * interface so guns, missiles, the HUD and Box2D bodies all agree, and so tests can use
 * {@link #ZERO} or {@link #linear}.
 */
public interface TidalAccel {
    /** Writes the acceleration at local position (x, y) meters into {@code out}, m/s^2, and returns it. */
    Vector2 accelAt(float x, float y, Vector2 out);

    /** Flat space: no tidal term. */
    TidalAccel ZERO = (x, y, out) -> out.setZero();

    /**
     * The linearized point-mass tidal field near an anchor in free fall, with +y pointing
     * radially away from the parent body: {@code a = n^2 * (-x, 2y)}. {@code meanMotion} is
     * {@code n = 2*pi / orbital period}, rad/s. Good enough for tests and for small bubbles.
     */
    static TidalAccel linear(final float meanMotion) {
        final float n2 = meanMotion * meanMotion;
        return (x, y, out) -> out.set(-n2 * x, 2f * n2 * y);
    }
}
