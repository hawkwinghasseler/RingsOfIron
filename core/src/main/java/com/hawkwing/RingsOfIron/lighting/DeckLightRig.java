package com.hawkwing.RingsOfIron.lighting;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.TileConduit;
import com.ringsofiron.interiorgrid.WallLight;
import com.ringsofiron.interiorgrid.WallLightLayout;

import java.util.Locale;

/**
 * The lights on a deck, from the emissive layers of the art drawn there: the wall lights that
 * light its rooms while the craft has power (as the layout hangs them, or worked out by
 * {@link WallLightLayout} for a layout that does not say), each mounted system's
 * lamps, screens and glow (lit while the system runs, dark when it loses power or is switched
 * off or wrecked), live conduits, fires, tiles hot enough to glow ({@link BlackBody}), and the lamps of bulkhead doors (red shut, green open,
 * while the craft has power), which light a little of the floor each side. Placed in the grid's local meters, which are its
 * craft's body frame.
 *
 * <p>A part the art marks with {@code data-light} lights (point, cone) or only glows (glow) as
 * marked, with its own reach and intensity if given. Otherwise its size decides: parts of at
 * least {@link #CAST_MIN_SIZE} can light the room. A system lights the room from at most
 * {@link #CASTS_PER_SYSTEM} parts, those the art marks first, then the biggest; the rest glow.
 */
public final class DeckLightRig {
    /** Glowing parts smaller than this (SVG units) only glow; bigger ones light the room. */
    public static final float CAST_MIN_SIZE = 8f;
    /** At most this many parts of one system's art light the room, the marked ones first, then the biggest. */
    public static final int CASTS_PER_SYSTEM = 3;
    /** How far a light reaches, tiles: this much... */
    static final float REACH_BASE = 0.6f;
    /** ...plus this much per tile of the glowing part's size (square root of its area)... */
    static final float REACH_PER_TILE = 2.2f;
    /** ...up to this. */
    static final float REACH_MAX = 7f;

    /** How far a bulkhead door's lamps light the floor each side of it, tiles. */
    static final float DOOR_LAMP_REACH = 1.6f;
    /** How strong a door's lamp light is, 0-1. */
    static final float DOOR_LAMP_STRENGTH = 0.35f;
    /** How far off the door's line its light is placed on each side, tiles, so a shut door lights both. */
    static final float DOOR_LAMP_OFFSET = 0.2f;

    /** How far a tile glowing at full {@link BlackBody#brightness} lights the deck around it, tiles. */
    static final float HEAT_REACH = 2.5f;
    /** How strong that light is, at full brightness, 0-1. */
    static final float HEAT_STRENGTH = 0.7f;
    /** Below this brightness a hot tile only glows: dull red heat lights nothing much around it. */
    static final float HEAT_CASTS = 0.15f;

    /** Art of the light hung on deck walls. */
    public static final String WALL_LIGHT = "decor/wall_light";

    private final EmissiveLights art;
    /** A part the art marks as a light ranks above any it does not: more than any part's area. */
    private static final float MARKED = 1e7f;

    /** Which parts of the art being placed light the room. */
    private boolean[] cast = new boolean[16];

    public DeckLightRig(EmissiveLights art) {
        this.art = art;
    }

    /** Adds the lights of {@code grid} to {@code out}. */
    public void collect(ShipGridComponent grid, LightSpots out) {
        EmissiveLights.Art wall = art.get(WALL_LIGHT);
        if (wall != null) {
            boolean power = hasPower(grid);
            for (WallLight p : WallLightLayout.lightsOf(grid)) wallLight(grid, p, wall, power, out);
        }
        for (MountFootprint m : grid.mounts) {
            Entity e = m.installed;
            SubsystemComponent sub = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (sub == null) continue;
            EmissiveLights.Art a = componentArt(sub, m);
            if (a != null) mount(grid, m, a, sub.isOperational && sub.health > 0f, out);
        }
        EmissiveLights.Art conduit = art.get("tiles/conduit");
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null) continue;
                TileConduit c = grid.getTileConduit(x, y);
                if (conduit != null && c != null && c.isPowered && t.mount == null) {
                    tile(grid, x, y, conduit, false, 0f, out);
                }
                Tile.FireSize fire = t.fireSize();
                if (fire != Tile.FireSize.NONE) {
                    EmissiveLights.Art f = art.get("effects/fire_" + fire.name().toLowerCase(Locale.ROOT));
                    if (f != null) tile(grid, x, y, f, true, 1f + x * 7.31f + y * 3.17f, out);
                }
            }
        }
        heat(grid, out);
        // Last, so they are the first left out when a scene has more lights than it can cast
        doors(grid, out);
    }

    /**
     * Every tile above the Draper point glows as a black body does, power or not: dull red where
     * steel is barely glowing, brightening through orange as it nears a fire's heat. The brighter
     * ones light the deck around them too.
     */
    private void heat(ShipGridComponent grid, LightSpots out) {
        float ts = grid.tileSize;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null || t.isVoid()) continue;
                float b = BlackBody.brightness(t.temperature);
                if (b <= 0f) continue;
                LightSpot s = out.add();
                s.x = grid.gridOriginLocal.x + (x + 0.5f) * ts;
                s.y = grid.gridOriginLocal.y + (y + 0.5f) * ts;
                s.reach = b < HEAT_CASTS ? 0f : HEAT_REACH * b * ts;
                s.strength = HEAT_STRENGTH * b;
                // The glow reads by eye, so it rises faster than the light it casts
                float seen = (float) Math.sqrt(b);
                s.glow = ts * (0.5f + 0.5f * seen);
                BlackBody.color(t.temperature, s.color);
                s.color.a = seen;
            }
        }
    }

    /**
     * Each standing bulkhead door's lamps, while the craft has power: a short light in their color
     * on the floor each side of the door, from its middle. The lamps' color is the horizontal
     * door art's (the vertical art is the same turned).
     */
    private void doors(ShipGridComponent grid, LightSpots out) {
        if (!hasPower(grid)) return;
        EmissiveLights.Art open = art.get("edges/bulkhead_door_open_h"), shut = art.get("edges/bulkhead_door_sealed_h");
        for (int y = 0; y <= grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                EdgeWall e = grid.edgeH[y][x];
                if (e != null && e.hasBulkhead && !e.isDestroyed()) {
                    EmissiveLights.Art a = e.bulkheadSealed ? shut : open;
                    doorLamp(grid, a, x, y - 1, x + 0.5f, y - DOOR_LAMP_OFFSET, out);
                    doorLamp(grid, a, x, y, x + 0.5f, y + DOOR_LAMP_OFFSET, out);
                }
            }
        }
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x <= grid.width; x++) {
                EdgeWall e = grid.edgeV[y][x];
                if (e != null && e.hasBulkhead && !e.isDestroyed()) {
                    EmissiveLights.Art a = e.bulkheadSealed ? shut : open;
                    doorLamp(grid, a, x - 1, y, x - DOOR_LAMP_OFFSET, y + 0.5f, out);
                    doorLamp(grid, a, x, y, x + DOOR_LAMP_OFFSET, y + 0.5f, out);
                }
            }
        }
    }

    /** A door lamp's light at ({@code tx}, {@code ty}) tiles, if the tile it shines on, (x, y), is floor. */
    private void doorLamp(ShipGridComponent grid, EmissiveLights.Art a, int x, int y, float tx, float ty, LightSpots out) {
        if (a == null || a.lights.length == 0) return;
        Tile t = grid.getTile(x, y);
        if (t == null || !t.isOpen()) return;
        EmissiveLights.Source lamp = a.lights[0];
        float ts = grid.tileSize;
        LightSpot s = out.add();
        s.x = grid.gridOriginLocal.x + tx * ts;
        s.y = grid.gridOriginLocal.y + ty * ts;
        s.reach = DOOR_LAMP_REACH * ts;
        s.strength = DOOR_LAMP_STRENGTH;
        // The lamps themselves are drawn by the door's own glow art
        s.glow = 0f;
        s.color.set(lamp.color);
        s.color.a = 0f;
    }

    /** A wall light's lights: its art's top against the wall, centered along the edge. */
    private void wallLight(ShipGridComponent grid, WallLight p, EmissiveLights.Art a, boolean on, LightSpots out) {
        float w = a.width / EmissiveLights.UNITS_PER_TILE, h = a.height / EmissiveLights.UNITS_PER_TILE;
        int r = p.rotation();
        float cos = MathUtils.cos(r * MathUtils.HALF_PI), sin = MathUtils.sin(r * MathUtils.HALF_PI);
        for (EmissiveLights.Source src : a.lights) {
            // From the middle of the canvas's top edge, which is the middle of the wall edge
            float dx = (src.x - a.left) / EmissiveLights.UNITS_PER_TILE - w / 2f;
            float dy = (src.y - a.bottom) / EmissiveLights.UNITS_PER_TILE - h;
            LightSpot s = place(grid, p.edgeX() + cos * dx - sin * dy, p.edgeY() + sin * dx + cos * dy, src, out);
            s.on = on;
            if (s.isCone) s.direction += r * MathUtils.HALF_PI;
        }
    }

    /** The art of an installed system, falling back to its behavior's built-in component's art. */
    EmissiveLights.Art componentArt(SubsystemComponent sub, MountFootprint m) {
        String size = m.size.name().toLowerCase(Locale.ROOT);
        EmissiveLights.Art a = sub.component == null ? null : art.get("components/" + sub.component + "_" + size);
        if (a == null && sub.type != null) a = art.get("components/" + sub.type.name().toLowerCase(Locale.ROOT) + "_" + size);
        return a;
    }

    private void mount(ShipGridComponent grid, MountFootprint m, EmissiveLights.Art a, boolean on, LightSpots out) {
        // A few parts light the room; the rest only glow
        boolean[] casting = casting(a);
        for (int i = 0; i < a.lights.length; i++) {
            EmissiveLights.Source src = a.lights[i];
            float[] p = rotated(a, src.x, src.y, m.rotation);
            LightSpot s = place(grid, m.originX + p[0], m.originY + p[1], src, out);
            s.on = on;
            if (!casting[i]) s.reach = 0f;
            if (s.isCone) s.direction += m.rotation * MathUtils.HALF_PI;
        }
    }

    private void tile(ShipGridComponent grid, int x, int y, EmissiveLights.Art a, boolean casts, float flicker, LightSpots out) {
        for (EmissiveLights.Source src : a.lights) {
            LightSpot s = place(grid, x + (src.x - a.left) / EmissiveLights.UNITS_PER_TILE,
                    y + (src.y - a.bottom) / EmissiveLights.UNITS_PER_TILE, src, out);
            if (src.kind == null ? !casts || src.size() < CAST_MIN_SIZE : src.kind == EmissiveLights.Kind.GLOW) s.reach = 0f;
            s.flicker = flicker;
        }
    }

    /** A spot for {@code src} at ({@code tx}, {@code ty}) in tiles from the grid's corner. */
    private LightSpot place(ShipGridComponent grid, float tx, float ty, EmissiveLights.Source src, LightSpots out) {
        float t = grid.tileSize;
        LightSpot s = out.add();
        s.x = grid.gridOriginLocal.x + tx * t;
        s.y = grid.gridOriginLocal.y + ty * t;
        float part = (float) Math.sqrt(src.w * src.h) / EmissiveLights.UNITS_PER_TILE;
        s.reach = src.kind == EmissiveLights.Kind.GLOW ? 0f
                : (src.reach > 0f ? src.reach : Math.min(REACH_MAX, REACH_BASE + REACH_PER_TILE * part)) * t;
        s.strength = src.intensity >= 0f ? src.intensity : MathUtils.clamp(0.35f + 0.65f * src.alpha, 0f, 1f);
        s.glow = src.size() / EmissiveLights.UNITS_PER_TILE * t;
        s.color.set(src.color);
        s.color.a = src.alpha;
        s.isCone = src.isCone;
        s.direction = src.direction * MathUtils.degreesToRadians;
        s.cone = src.cone * MathUtils.degreesToRadians;
        return s;
    }

    /** Which of {@code a}'s parts light the room: at most {@link #CASTS_PER_SYSTEM}, by {@link #castRank}. */
    private boolean[] casting(EmissiveLights.Art a) {
        int n = a.lights.length;
        if (cast.length < n) cast = new boolean[n];
        java.util.Arrays.fill(cast, 0, n, false);
        for (int k = 0; k < CASTS_PER_SYSTEM; k++) {
            int best = -1;
            float bestRank = 0f;
            for (int i = 0; i < n; i++) {
                float rank = cast[i] ? 0f : castRank(a.lights[i]);
                if (rank > bestRank) {
                    best = i;
                    bestRank = rank;
                }
            }
            if (best < 0) break;
            cast[best] = true;
        }
        return cast;
    }

    /** How strong a claim a part has to light the room: 0 for none, any marked light above any unmarked part. */
    private static float castRank(EmissiveLights.Source src) {
        float weight = src.w * src.h * src.alpha;
        if (src.kind == null) return src.size() < CAST_MIN_SIZE ? 0f : weight;
        return src.kind == EmissiveLights.Kind.GLOW ? 0f : MARKED + weight;
    }

    /**
     * A point of art drawn at rotation 0, in tiles from its canvas's bottom-left corner, once the
     * art is turned {@code rotation} counter-clockwise quarter turns within its footprint.
     */
    static float[] rotated(EmissiveLights.Art a, float x, float y, int rotation) {
        float u = (x - a.left) / EmissiveLights.UNITS_PER_TILE, v = (y - a.bottom) / EmissiveLights.UNITS_PER_TILE;
        float w = a.width / EmissiveLights.UNITS_PER_TILE, h = a.height / EmissiveLights.UNITS_PER_TILE;
        switch (Math.floorMod(rotation, 4)) {
            case 1: return new float[]{h - v, u};
            case 2: return new float[]{w - u, h - v};
            case 3: return new float[]{v, w - u};
            default: return new float[]{u, v};
        }
    }

    /** True if any reactor aboard is running: the deck's lights are on. */
    public static boolean hasPower(ShipGridComponent grid) {
        for (MountFootprint m : grid.mounts) {
            SubsystemComponent sub = m.installed == null ? null : m.installed.getComponent(SubsystemComponent.class);
            if (sub != null && sub.type == SubsystemComponent.SubsystemType.REACTOR && sub.isOperational) return true;
        }
        return false;
    }
}
