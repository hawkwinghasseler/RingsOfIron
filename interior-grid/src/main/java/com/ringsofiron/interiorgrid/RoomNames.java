package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectIntMap;

import java.util.Comparator;

/**
 * Names a ship's rooms for the HUD, so an alert can say where ("FIRE in HOLD 2"). The rule is the
 * one the UI overhaul's functional specs share (documentation/ui-overhaul): a room is named, in
 * capitals and at most 10 characters, by the first rule that applies.
 *
 * <ol>
 *   <li>The room with the helm is BRIDGE.</li>
 *   <li>Otherwise, by the kind of system that fills most of its tiles: HOLD, REACTOR, ENGINES,
 *       TANKS, LIFE SUP, SHIELDS, GUNS, AIRLOCK, PODS; a room mostly of medbay deck is MEDBAY.</li>
 *   <li>A room with no system is named by its place in the hull, from its centre against the
 *       hull's: FWD, MID or AFT, then PORT or STBD when it is off the centreline ("AFT STBD").</li>
 *   <li>Two rooms with the same name are numbered from the bow: HOLD 1, HOLD 2.</li>
 * </ol>
 *
 * The nose is +x in the ship's body frame and port is +y, as for weapon mounts. Names are worked out
 * from a layout and kept with it: one {@code RoomNames} per ship caches them on the grid's
 * {@link ShipGridComponent#layoutRevision} and fitted systems, so they don't change under the player in
 * a fight unless the rooms themselves do.
 */
public final class RoomNames {
    /** Longest name, characters. */
    public static final int MAX_LENGTH = 10;
    /** Share of the hull's half-width off the centreline beyond which a place is PORT or STBD. */
    static final float OFF_CENTRE = 0.34f;

    private final IntMap<String> byCompartment = new IntMap<>();
    private final Array<Compartment> order = new Array<>();
    private int revision = Integer.MIN_VALUE;
    private int compartmentCount = -1;
    private int installedCount = -1;
    /** The hull's extent in body meters. */
    private float minX, maxX, minY, maxY;

    /** The name of the room whose air fills tile ({@code x}, {@code y}), or "" for none. */
    public String at(ShipGridComponent grid, int x, int y) {
        refresh(grid);
        Tile t = grid.inBounds(x, y) ? grid.getTile(x, y) : null;
        if (t == null || t.compartment == null) return "";
        String name = byCompartment.get(t.compartment.id);
        return name == null ? "" : name;
    }

    /** The name of {@code c}, or "" when it is not one of the grid's rooms. */
    public String of(ShipGridComponent grid, Compartment c) {
        refresh(grid);
        String name = c == null ? null : byCompartment.get(c.id);
        return name == null ? "" : name;
    }

    /** The rooms, bow first. Do not change the array. */
    public Array<Compartment> rooms(ShipGridComponent grid) {
        refresh(grid);
        return order;
    }

    /**
     * Where body point ({@code x}, {@code y}) sits in the hull, as rule 3 names a room: "FWD",
     * "MID PORT", "AFT STBD".
     */
    public String place(ShipGridComponent grid, float x, float y) {
        refresh(grid);
        return place(x, y, minX, maxX, minY, maxY);
    }

    /** Forgets the names, for use on another ship. */
    public void reset() {
        revision = Integer.MIN_VALUE;
        compartmentCount = -1;
        installedCount = -1;
        byCompartment.clear();
        order.clear();
    }

    /** Works the names out again if the rooms have changed since the last call. */
    public void refresh(ShipGridComponent grid) {
        int installed = 0;
        for (MountFootprint m : grid.mounts) if (m.installed != null) installed++;
        if (grid.layoutRevision == revision && grid.compartments.size == compartmentCount && installed == installedCount) return;
        revision = grid.layoutRevision;
        compartmentCount = grid.compartments.size;
        installedCount = installed;
        name(grid);
    }

    private void name(ShipGridComponent grid) {
        byCompartment.clear();
        order.clear();
        bounds(grid);
        Array<SubsystemComponent> systems = systems(grid);
        Vector2 at = new Vector2();
        ObjectIntMap<String> uses = new ObjectIntMap<>();
        Array<String> names = new Array<>();
        Array<Compartment> rooms = new Array<>(grid.compartments);
        // Bow first, so numbering runs from the bow
        rooms.sort(Comparator.comparingDouble((Compartment c) -> -centre(grid, c, at).x));
        for (Compartment c : rooms) {
            String name = byRule(grid, c, systems, at);
            names.add(name);
            uses.getAndIncrement(name, 0, 1);
        }
        ObjectIntMap<String> numbered = new ObjectIntMap<>();
        for (int i = 0; i < rooms.size; i++) {
            String name = names.get(i);
            if (uses.get(name, 0) > 1) {
                int n = numbered.getAndIncrement(name, 0, 1) + 1;
                String suffix = " " + n;
                name = clip(name, MAX_LENGTH - suffix.length()) + suffix;
            }
            byCompartment.put(rooms.get(i).id, name);
            order.add(rooms.get(i));
        }
    }

    private String byRule(ShipGridComponent grid, Compartment c, Array<SubsystemComponent> systems, Vector2 at) {
        ObjectIntMap<SubsystemComponent.SubsystemType> tiles = new ObjectIntMap<>();
        int medbay = 0;
        for (int i = 0; i < c.tiles.size; i++) {
            int packed = c.tiles.get(i);
            int x = packed % grid.width, y = packed / grid.width;
            Tile t = grid.getTile(x, y);
            if (t != null && t.covering == FloorCovering.MEDBAY) medbay++;
            for (SubsystemComponent s : systems) {
                if (s.footprint != null && s.footprint.covers(x, y)) {
                    if (s.type == SubsystemComponent.SubsystemType.HELM) return "BRIDGE";
                    tiles.getAndIncrement(s.type, 0, 1);
                }
            }
        }
        SubsystemComponent.SubsystemType most = null;
        int best = 0;
        for (ObjectIntMap.Entry<SubsystemComponent.SubsystemType> e : tiles) {
            if (label(e.key) == null) continue;
            if (e.value > best || (e.value == best && most != null && e.key.ordinal() < most.ordinal())) {
                most = e.key;
                best = e.value;
            }
        }
        if (medbay > best && medbay * 2 >= c.tiles.size) return "MEDBAY";
        if (most != null) return label(most);
        centre(grid, c, at);
        return place(at.x, at.y, minX, maxX, minY, maxY);
    }

    /** Rule 2's name for a kind of system, or null for one that does not name a room. */
    static String label(SubsystemComponent.SubsystemType type) {
        switch (type) {
            case CARGO: return "HOLD";
            case REACTOR: return "REACTOR";
            case ENGINE: return "ENGINES";
            case TANK: return "TANKS";
            case LIFE_SUPPORT: return "LIFE SUP";
            case SHIELD_EMITTER: return "SHIELDS";
            case TURRET_MOUNT: return "GUNS";
            case AIRLOCK:
            case DOCKING_PORT: return "AIRLOCK";
            case ESCAPE_POD: return "PODS";
            default: return null;
        }
    }

    /** Rule 3, for a point and the hull's extent, all in body meters. */
    public static String place(float x, float y, float minX, float maxX, float minY, float maxY) {
        float length = Math.max(1e-3f, maxX - minX);
        float u = (x - minX) / length;
        String along = u >= 2f / 3f ? "FWD" : u <= 1f / 3f ? "AFT" : "MID";
        float midY = (minY + maxY) * 0.5f, half = Math.max(1e-3f, (maxY - minY) * 0.5f);
        float side = (y - midY) / half;
        if (side > OFF_CENTRE) return along + " PORT";
        if (side < -OFF_CENTRE) return along + " STBD";
        return along;
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, Math.max(0, n)).trim();
    }

    private static Vector2 centre(ShipGridComponent grid, Compartment c, Vector2 out) {
        float sx = 0f, sy = 0f;
        for (int i = 0; i < c.tiles.size; i++) {
            int packed = c.tiles.get(i);
            sx += packed % grid.width;
            sy += packed / grid.width;
        }
        int n = Math.max(1, c.tiles.size);
        float tx = sx / n, ty = sy / n;
        return out.set(grid.gridOriginLocal.x + (tx + 0.5f) * grid.tileSize, grid.gridOriginLocal.y + (ty + 0.5f) * grid.tileSize);
    }

    private void bounds(ShipGridComponent grid) {
        minX = minY = Float.MAX_VALUE;
        maxX = maxY = -Float.MAX_VALUE;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null || t.isVoid()) continue;
                float lx = grid.gridOriginLocal.x + (x + 0.5f) * grid.tileSize;
                float ly = grid.gridOriginLocal.y + (y + 0.5f) * grid.tileSize;
                minX = Math.min(minX, lx);
                maxX = Math.max(maxX, lx);
                minY = Math.min(minY, ly);
                maxY = Math.max(maxY, ly);
            }
        }
        if (minX > maxX) minX = maxX = minY = maxY = 0f;
    }

    private static Array<SubsystemComponent> systems(ShipGridComponent grid) {
        Array<SubsystemComponent> out = new Array<>();
        for (MountFootprint m : grid.mounts) {
            Entity e = m.installed;
            SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (s != null) out.add(s);
        }
        return out;
    }
}
