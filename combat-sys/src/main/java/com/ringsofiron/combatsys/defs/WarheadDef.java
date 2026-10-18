package com.ringsofiron.combatsys.defs;

/**
 * An explosive charge, the {@code [warhead.*]} table. In vacuum it does damage only through
 * fragments; inside a pressurized hull it adds overpressure that scales with air pressure.
 */
public class WarheadDef {
    public String id;
    public int fragmentCount = 20;
    /** kg per fragment */
    public float fragmentMass = 0.1f;
    /** Speed fragments are thrown at relative to the warhead, m/s. */
    public float fragmentSpeed = 2000f;
    /** Full angle of the fragment cone, degrees. */
    public float fragmentConeDeg = 60f;
    /** Overpressure reach inside the hull, tiles. */
    public int blastRadiusTiles = 2;
    /** Overpressure damage at the detonation tile at standard pressure, J. */
    public float blastDamage = 1e5f;
}
