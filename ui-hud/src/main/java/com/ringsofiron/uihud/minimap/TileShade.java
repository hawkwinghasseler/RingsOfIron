package com.ringsofiron.uihud.minimap;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.Tile;

/**
 * Colors for the blueprint minimap. Pure functions of tile state so they can be tested
 * without a GL context.
 *
 * Floors are a translucent blueprint blue that goes dark as the compartment loses pressure,
 * hazed grey by smoke and overlaid orange by fire. Walls are bright cyan lines that shift to
 * red as their integrity drops. Breaches blink red.
 */
public final class TileShade {
    public static final Color FLOOR = new Color(0.12f, 0.42f, 0.62f, 0.55f);
    public static final Color VACUUM = new Color(0.08f, 0.02f, 0.05f, 0.75f);
    public static final Color SMOKE = new Color(0.55f, 0.55f, 0.55f, 1f);
    public static final Color FIRE = new Color(1f, 0.5f, 0.08f, 1f);
    public static final Color WALL_INTACT = new Color(0.45f, 0.85f, 1f, 0.95f);
    public static final Color WALL_BROKEN = new Color(1f, 0.25f, 0.2f, 0.95f);
    public static final Color BREACH = new Color(1f, 0.1f, 0.1f, 1f);
    public static final Color BULKHEAD_OPEN = new Color(0.4f, 1f, 0.5f, 0.95f);
    public static final Color BULKHEAD_SEALED = new Color(1f, 0.85f, 0.3f, 0.95f);
    public static final Color SYSTEM_OK = new Color(0.4f, 1f, 0.5f, 0.9f);
    public static final Color SYSTEM_UNPOWERED = new Color(1f, 0.7f, 0.2f, 0.9f);
    public static final Color SYSTEM_DESTROYED = new Color(1f, 0.25f, 0.2f, 0.9f);

    /** Smoke density (kg/m³) drawn as full haze; matches "around 0.01 is thick smoke". */
    public static final float THICK_SMOKE = 0.01f;

    private TileShade() {}

    /**
     * Fill color of a floor (open) tile. {@code blinkOn} toggles alert blinking.
     * Returns {@code out} with alpha 0 for tiles that draw nothing (void space).
     */
    public static Color fill(Tile tile, boolean blinkOn, Color out) {
        if (tile == null || tile.isVoid()) return out.set(0f, 0f, 0f, 0f);
        if (tile.isHullBreach()) return blinkOn ? out.set(BREACH) : out.set(VACUUM);
        if (tile.isWall() && !tile.isOpen()) return wall(tile.getIntegrity(), out);

        Compartment c = tile.compartment;
        float pressure = c != null ? MathUtils.clamp(c.pressureRatio(), 0f, 1f) : 0f;
        out.set(VACUUM).lerp(FLOOR, pressure);

        float smoke = c != null ? MathUtils.clamp(c.smokeDensity() / THICK_SMOKE, 0f, 1f) : 0f;
        if (smoke > 0f) out.lerp(SMOKE, smoke * 0.6f);

        if (tile.fireIntensity > 0f) {
            float f = 0.6f + 0.4f * MathUtils.clamp(tile.fireIntensity, 0f, 1f);
            out.lerp(FIRE, f);
            out.a = Math.max(out.a, 0.9f);
        }
        return out;
    }

    /** Wall color by integrity (1 = intact cyan, 0 = red). */
    public static Color wall(float integrity, Color out) {
        return out.set(WALL_BROKEN).lerp(WALL_INTACT, MathUtils.clamp(integrity, 0f, 1f));
    }

    /** Color of a thin edge wall, or alpha 0 if it is destroyed. */
    public static Color edge(EdgeWall wall, Color out) {
        if (wall == null || wall.isDestroyed()) return out.set(0f, 0f, 0f, 0f);
        if (wall.hasBulkhead) return out.set(wall.bulkheadSealed ? BULKHEAD_SEALED : BULKHEAD_OPEN);
        return wall(wall.integrity, out);
    }
}
