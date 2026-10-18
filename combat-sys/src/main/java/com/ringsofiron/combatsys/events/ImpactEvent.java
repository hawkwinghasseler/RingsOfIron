package com.ringsofiron.combatsys.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.combatsys.defs.ProjectileDef;

/**
 * A round or missile striking a body (combat-sys/Design.md section 5.1). Emitted by the
 * ballistics and missile systems, drained by {@code ImpactResolutionSystem} in tick order.
 */
public class ImpactEvent {
    /** The struck entity: a ship (with a {@code ShipGridComponent}) or any other body. */
    public Entity target;
    /** Who fired the round, or null. */
    public Entity owner;
    /** Hit point and outward surface normal, local (bubble) meters. */
    public final Vector2 worldPoint = new Vector2();
    public final Vector2 worldNormal = new Vector2();
    /** Round velocity minus the velocity of the hull surface at the hit point, m/s. */
    public final Vector2 relVelocity = new Vector2();
    /** Round velocity in the bubble frame, m/s. */
    public final Vector2 velocity = new Vector2();
    /** kg */
    public float mass;
    public ProjectileDef def;
    /**
     * Penetration budget override, J. Negative means "derive from kinetic energy and the def's
     * penetration factor" (the usual case); fragments and spall set it directly.
     */
    public float penetration = -1f;
    /**
     * A body ramming through on a fast pass rather than a round: nothing is put back in flight
     * after it (the body flies on along its own rails path). Section 1.4, "Ramming".
     */
    public boolean ram;
    /** Fixed tick number, for deterministic ordering. */
    public long tick;
}
