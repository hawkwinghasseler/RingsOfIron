package com.ringsofiron.combatsys.defs;

/**
 * Secondary fragments thrown off the inside of a penetrated hull tile (TOML {@code spall = {...}}).
 * They take their energy out of what the round still carries past that tile, and a light round
 * throws less: {@link #massScale} scales both the share and the number of rays.
 */
public class SpallDef {
    /** Round mass, kg, at which spall is half what the def gives; a round much heavier throws it all. */
    public static final float HALF_MASS = 5f;

    /** Number of secondary rays for a heavy round. 0 disables spall. */
    public int count;
    /** Half-angle of the cone around the shot line, degrees. */
    public float angleDeg;
    /** Share of the energy the round still has past the hull tile that is thrown inward as spall, for a heavy round. */
    public float fraction;

    /** How much of its def's spall a round of {@code mass} kg throws: {@code m / (m + HALF_MASS)}. */
    public static float massScale(float mass) {
        return mass <= 0f ? 0f : mass / (mass + HALF_MASS);
    }

    public SpallDef() {
    }

    public SpallDef(int count, float angleDeg, float fraction) {
        this.count = count;
        this.angleDeg = angleDeg;
        this.fraction = fraction;
    }
}
