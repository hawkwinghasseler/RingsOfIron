package com.ringsofiron.combatsys.defs;

/** Secondary fragments thrown off the inside of a penetrated hull tile (TOML {@code spall = {...}}). */
public class SpallDef {
    /** Number of secondary rays. 0 disables spall. */
    public int count;
    /** Half-angle of the cone around the shot line, degrees. */
    public float angleDeg;
    /** Share of the energy the hull absorbed that is thrown inward as spall. */
    public float fraction;

    public SpallDef() {
    }

    public SpallDef(int count, float angleDeg, float fraction) {
        this.count = count;
        this.angleDeg = angleDeg;
        this.fraction = fraction;
    }
}
