package com.hawkwing.RingsOfIron.lighting;

import com.badlogic.gdx.graphics.Color;

/**
 * A light some art gives off, placed on a craft: in its body frame, meters. Reused from frame
 * to frame by {@link LightSpots}.
 */
public final class LightSpot {
    public float x, y;
    /** How far its light reaches, meters; 0 for a glow that casts no light of its own. */
    public float reach;
    /** The size of the glowing part, meters: what the glow pass draws. */
    public float glow;
    /** Its color, alpha the light's strength. */
    public final Color color = new Color();
    /** A cone toward {@link #direction} (radians, body frame) over {@link #cone} radians each side. */
    public boolean isCone;
    public float direction, cone;
    /** Switched on: the system it belongs to is running, or the craft has power. */
    public boolean on;
    /** Flickers like a flame, from this seed; negative for a steady light. */
    public float flicker = -1f;

    /** True if this spot lights its surroundings, not only glows. */
    public boolean casts() {
        return reach > 0f;
    }

    LightSpot reset() {
        x = y = reach = glow = direction = cone = 0f;
        color.set(Color.WHITE);
        isCone = false;
        on = true;
        flicker = -1f;
        return this;
    }
}
