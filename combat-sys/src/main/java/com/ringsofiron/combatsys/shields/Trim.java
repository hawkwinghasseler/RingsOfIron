package com.ringsofiron.combatsys.shields;

/**
 * Which way the crew lean the resting cloud (documentation/combat-sys/Design.md, 5.11): evenly round
 * the hull, or thicker toward one side, as far as the emitters there can reach. It moves no more mass
 * than is out, so the far side thins to pay for it.
 */
public enum Trim {
    EVEN("even", Float.NaN),
    BOW("bow", 0f),
    PORT("port", 90f),
    STERN("stern", 180f),
    STARBOARD("starboard", 270f);

    public final String label;
    /** The side it leans to, degrees off the bow counterclockwise; NaN for even. */
    public final float bearing;

    Trim(String label, float bearing) {
        this.label = label;
        this.bearing = bearing;
    }

    /** The next trim round: even, bow, port, stern, starboard, and back to even. */
    public Trim next() {
        Trim[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** The trim named {@code name} (case aside), or null. */
    public static Trim named(String name) {
        for (Trim t : values()) if (t.label.equalsIgnoreCase(name) || t.name().equalsIgnoreCase(name)) return t;
        return null;
    }
}
