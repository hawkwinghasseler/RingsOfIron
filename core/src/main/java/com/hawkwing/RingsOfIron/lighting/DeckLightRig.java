package com.hawkwing.RingsOfIron.lighting;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;

import java.util.Locale;

/**
 * The lights on a deck, from the emissive layers of the art drawn there: the wall lights that
 * light its rooms while the craft has power (see {@link WallLightLayout}), each mounted system's
 * lamps, screens and glow (lit while the system runs, dark when it loses power or is switched
 * off or wrecked), live conduits, and fires. Placed in the grid's local meters, which are its
 * craft's body frame.
 *
 * <p>A part the art marks with {@code data-light} lights (point, cone) or only glows (glow) as
 * marked, with its own reach and intensity if given. Otherwise its size decides: parts of at
 * least {@link #CAST_MIN_SIZE} light the room, the biggest {@link #CASTS_PER_SYSTEM} of a system.
 */
public final class DeckLightRig {
    /** Glowing parts smaller than this (SVG units) only glow; bigger ones light the room. */
    public static final float CAST_MIN_SIZE = 8f;
    /** At most this many parts of one system's art light the room, the biggest first. */
    public static final int CASTS_PER_SYSTEM = 3;
    /** How far a light reaches, tiles: this much... */
    static final float REACH_BASE = 0.6f;
    /** ...plus this much per tile of the glowing part's size (square root of its area)... */
    static final float REACH_PER_TILE = 2.2f;
    /** ...up to this. */
    static final float REACH_MAX = 7f;

    /** Art of the light hung on deck walls. */
    public static final String WALL_LIGHT = "decor/wall_light";

    private final EmissiveLights art;
    private final float[] order = new float[64];
    private final java.util.Map<ShipGridComponent, Layout> layouts = new java.util.WeakHashMap<>();

    /** A grid's wall lights, for the layout revision they were worked out for. */
    private static final class Layout {
        final int revision;
        final com.badlogic.gdx.utils.Array<WallLightLayout.Placement> lights;

        Layout(int revision, com.badlogic.gdx.utils.Array<WallLightLayout.Placement> lights) {
            this.revision = revision;
            this.lights = lights;
        }
    }

    public DeckLightRig(EmissiveLights art) {
        this.art = art;
    }

    /** Adds the lights of {@code grid} to {@code out}. */
    public void collect(ShipGridComponent grid, LightSpots out) {
        EmissiveLights.Art wall = art.get(WALL_LIGHT);
        if (wall != null) {
            boolean power = hasPower(grid);
            for (WallLightLayout.Placement p : wallLights(grid)) wallLight(grid, p, wall, power, out);
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
                if (conduit != null && t.isPowerConduit() && t.isPowered && t.mount == null) {
                    tile(grid, x, y, conduit, false, 0f, out);
                }
                Tile.FireSize fire = t.fireSize();
                if (fire != Tile.FireSize.NONE) {
                    EmissiveLights.Art f = art.get("effects/fire_" + fire.name().toLowerCase(Locale.ROOT));
                    if (f != null) tile(grid, x, y, f, true, 1f + x * 7.31f + y * 3.17f, out);
                }
            }
        }
    }

    /** Where {@code grid}'s wall lights hang, worked out again when its layout changes. */
    public com.badlogic.gdx.utils.Array<WallLightLayout.Placement> wallLights(ShipGridComponent grid) {
        Layout l = layouts.get(grid);
        if (l == null || l.revision != grid.layoutRevision) {
            layouts.put(grid, l = new Layout(grid.layoutRevision, WallLightLayout.of(grid)));
        }
        return l.lights;
    }

    /** A wall light's lights: its art's top against the wall, centered along the edge. */
    private void wallLight(ShipGridComponent grid, WallLightLayout.Placement p, EmissiveLights.Art a, boolean on, LightSpots out) {
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
        // The biggest few parts light the room; the rest only glow
        float castFloor = castFloor(a);
        for (EmissiveLights.Source src : a.lights) {
            float[] p = rotated(a, src.x, src.y, m.rotation);
            LightSpot s = place(grid, m.originX + p[0], m.originY + p[1], src, out);
            s.on = on;
            float weight = src.w * src.h * src.alpha;
            if (src.kind == null && (src.size() < CAST_MIN_SIZE || weight < castFloor)) s.reach = 0f;
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

    /** The weight (area times opacity) a part must reach to be among the system's biggest. */
    private float castFloor(EmissiveLights.Art a) {
        int n = 0;
        for (EmissiveLights.Source src : a.lights) {
            if (src.kind == null && src.size() >= CAST_MIN_SIZE && n < order.length) order[n++] = src.w * src.h * src.alpha;
        }
        if (n <= CASTS_PER_SYSTEM) return 0f;
        java.util.Arrays.sort(order, 0, n);
        return order[n - CASTS_PER_SYSTEM];
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
