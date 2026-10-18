package com.ringsofiron.combatsys.fire;

import com.badlogic.gdx.math.Vector2;

/** Output of {@link FiringSolutionSolver}. Reusable: callers keep one and pass it in each tick. */
public class FiringSolution {
    /** True if an intercept exists within the max flight time. */
    public boolean valid;
    /** Unit vector to fire along, in the bubble frame (before adding the shooter's velocity). */
    public final Vector2 aimDir = new Vector2();
    /** Seconds from firing to intercept. */
    public float timeToImpact;
    /** Where the target is predicted to be at impact, local meters. Drawn as the HUD lead reticle. */
    public final Vector2 interceptPoint = new Vector2();
    /** Fixed-point passes the refinement took. */
    public int iterations;

    public void invalidate() {
        valid = false;
        aimDir.setZero();
        timeToImpact = 0f;
        interceptPoint.setZero();
        iterations = 0;
    }
}
