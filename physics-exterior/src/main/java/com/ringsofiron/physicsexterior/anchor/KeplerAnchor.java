package com.ringsofiron.physicsexterior.anchor;

import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;

/**
 * The bubble anchor on an exact, analytic Kepler orbit around the current SOI body: no drift,
 * and any jump in time (rails warp, loading) costs the same as one tick. A rebase restarts it on
 * the conic through the player's true state.
 */
public class KeplerAnchor implements AnchorModel {
    private final StateVector scratch = new StateVector();
    private KeplerOrbit orbit;

    public KeplerAnchor(KeplerOrbit orbit) {
        this.orbit = orbit;
    }

    /** The anchor's current conic, e.g. for drawing the predicted orbit or SOI prediction. */
    public KeplerOrbit getOrbit() {
        return orbit;
    }

    @Override
    public double getMu() {
        return orbit.mu;
    }

    @Override
    public void stateAt(double t, double[] out) {
        orbit.stateAt(t, scratch);
        out[0] = scratch.x;
        out[1] = scratch.y;
        out[2] = scratch.vx;
        out[3] = scratch.vy;
    }

    @Override
    public void reset(double x, double y, double vx, double vy, double t) {
        orbit = KeplerOrbit.fromState(x, y, vx, vy, orbit.mu, t);
    }
}
