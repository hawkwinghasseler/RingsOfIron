package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;

/**
 * The goods a ship carries, stowed on the floor tiles of its cargo bays (the footprints of
 * its {@link SubsystemComponent.SubsystemType#CARGO} systems). Each bay tile holds one
 * stack of a single commodity, piled up to {@link #STACK_HEIGHT}, so a tile's volume is
 * {@code tileSize² × STACK_HEIGHT} and the hold's capacity is the sum over its bay tiles.
 *
 * Loading tops up the stacks already holding the commodity, then starts new stacks on
 * empty tiles in bay order; unloading takes from the newest stacks first. Every stack
 * carries its commodity's volume and mass per unit, so the hold knows its own mass and
 * where that mass sits ({@link #addMassTo}) without a commodity catalog.
 *
 * Saved field by field with the ship. {@link #revision} changes on every load or unload,
 * so the ship's mass can be updated when the cargo changes.
 */
public class CargoHoldComponent implements Component {

    /** Height cargo is stacked to on a bay tile, meters: a 3 m deck less headroom. */
    public static final float STACK_HEIGHT = 2.5f;
    private static final double EPS = 1e-9;

    /** One commodity piled on one bay tile. */
    public static class CargoStack {
        public String commodity;
        public double units;
        /** m³ per unit. */
        public double unitVolume;
        /** kg per unit. */
        public double unitMass;

        public double volume() {
            return units * unitVolume;
        }

        public double mass() {
            return units * unitMass;
        }
    }

    /** Bay tile coordinates, in loading order. */
    public int[] bayX = new int[0], bayY = new int[0];
    /** The stack on each bay tile, parallel to {@link #bayX}; null where the tile is empty. */
    public CargoStack[] stacks = new CargoStack[0];
    /** Volume of one bay tile, m³. */
    public double tileVolume;
    /** Bumped whenever cargo is loaded or unloaded. Not saved. */
    public transient int revision;
    /**
     * The {@link #revision} the ship's body mass last took in, for whoever keeps the two in
     * step; -1 until it first does. Not saved, so a loaded ship's mass is brought up to date.
     */
    public transient int massRevision = -1;

    /** For deserialization. */
    public CargoHoldComponent() {}

    /** A hold over the given bay tiles (pairs of x, y), each of {@code tileSize} meters. */
    public CargoHoldComponent(float tileSize, int[] tilesXY) {
        int n = tilesXY.length / 2;
        bayX = new int[n];
        bayY = new int[n];
        for (int i = 0; i < n; i++) {
            bayX[i] = tilesXY[i * 2];
            bayY[i] = tilesXY[i * 2 + 1];
        }
        stacks = new CargoStack[n];
        tileVolume = tileVolume(tileSize);
    }

    /** A hold over every tile of the CARGO placements, or null when there are none. */
    public static CargoHoldComponent forBays(float tileSize, Iterable<ChassisBlueprintLoader.SubsystemPlacement> placements) {
        int[] tiles = bayTiles(placements);
        return tiles.length == 0 ? null : new CargoHoldComponent(tileSize, tiles);
    }

    /** Total cargo volume of a loaded chassis's bays, m³. */
    public static double bayVolume(ChassisBlueprintLoader.LoadResult chassis) {
        return bayTiles(chassis.subsystems).length / 2 * tileVolume(chassis.grid.tileSize);
    }

    public static double tileVolume(float tileSize) {
        return (double) tileSize * tileSize * STACK_HEIGHT;
    }

    private static int[] bayTiles(Iterable<ChassisBlueprintLoader.SubsystemPlacement> placements) {
        int n = 0;
        for (ChassisBlueprintLoader.SubsystemPlacement p : placements) {
            if (p.type == SubsystemComponent.SubsystemType.CARGO) n += p.footprint.width() * p.footprint.height();
        }
        int[] out = new int[n * 2];
        int i = 0;
        for (ChassisBlueprintLoader.SubsystemPlacement p : placements) {
            if (p.type != SubsystemComponent.SubsystemType.CARGO) continue;
            MountFootprint m = p.footprint;
            for (int y = m.originY; y < m.originY + m.height(); y++) {
                for (int x = m.originX; x < m.originX + m.width(); x++) {
                    out[i++] = x;
                    out[i++] = y;
                }
            }
        }
        return out;
    }

    public int bayTileCount() {
        return stacks.length;
    }

    /** Volume of all bays, m³. */
    public double capacity() {
        return stacks.length * tileVolume;
    }

    public double usedVolume() {
        double v = 0;
        for (CargoStack s : stacks) if (s != null) v += s.volume();
        return v;
    }

    public double freeVolume() {
        return Math.max(0, capacity() - usedVolume());
    }

    /** Mass of everything aboard, kg. */
    public double mass() {
        double m = 0;
        for (CargoStack s : stacks) if (s != null) m += s.mass();
        return m;
    }

    /** Units of {@code commodity} aboard. */
    public double amount(String commodity) {
        double u = 0;
        for (CargoStack s : stacks) if (s != null && s.commodity.equals(commodity)) u += s.units;
        return u;
    }

    /** The stack on tile (x, y), or null when it is empty or not a bay tile. */
    public CargoStack stackAt(int x, int y) {
        for (int i = 0; i < stacks.length; i++) {
            if (bayX[i] == x && bayY[i] == y) return stacks[i];
        }
        return null;
    }

    /** How full tile (x, y) is, 0 to 1. */
    public float fillAt(int x, int y) {
        CargoStack s = stackAt(x, y);
        return s == null || tileVolume <= 0 ? 0f : (float) Math.min(1.0, s.volume() / tileVolume);
    }

    /**
     * Units of a commodity that fit: the room left on its own stacks plus every empty tile.
     * A tile holding another commodity has no room for this one, and goods without a volume
     * per unit have no room anywhere.
     */
    public double room(String commodity, double unitVolume) {
        if (unitVolume <= 0) return 0;
        double v = 0;
        for (CargoStack s : stacks) {
            if (s == null) v += tileVolume;
            else if (s.commodity.equals(commodity)) v += Math.max(0, tileVolume - s.volume());
        }
        return v / unitVolume;
    }

    /**
     * Stows up to {@code units} of a commodity: tops up its stacks, then starts new ones
     * on empty tiles. Returns the units stowed, less than asked when the bays fill.
     */
    public double load(String commodity, double units, double unitVolume, double unitMass) {
        if (units <= EPS || unitVolume <= 0) return 0;
        double left = units;
        for (CargoStack s : stacks) {
            if (left <= EPS) break;
            if (s == null || !s.commodity.equals(commodity)) continue;
            double add = Math.min(left, (tileVolume - s.volume()) / unitVolume);
            if (add <= 0) continue;
            s.units += add;
            // Keep the newest per-unit figures; a commodity's definition rarely changes
            s.unitVolume = unitVolume;
            s.unitMass = unitMass;
            left -= add;
        }
        for (int i = 0; i < stacks.length && left > EPS; i++) {
            if (stacks[i] != null) continue;
            CargoStack s = new CargoStack();
            s.commodity = commodity;
            s.unitVolume = unitVolume;
            s.unitMass = unitMass;
            s.units = Math.min(left, tileVolume / unitVolume);
            stacks[i] = s;
            left -= s.units;
        }
        double stowed = units - Math.max(0, left);
        if (stowed > 0) revision++;
        return stowed;
    }

    /** Takes up to {@code units} of a commodity off its newest stacks. Returns the units taken. */
    public double unload(String commodity, double units) {
        if (units <= EPS) return 0;
        double left = units;
        for (int i = stacks.length - 1; i >= 0 && left > EPS; i--) {
            CargoStack s = stacks[i];
            if (s == null || !s.commodity.equals(commodity)) continue;
            double take = Math.min(left, s.units);
            s.units -= take;
            left -= take;
            if (s.units <= EPS) stacks[i] = null;
        }
        double taken = units - Math.max(0, left);
        if (taken > 0) revision++;
        return taken;
    }

    /**
     * Adds the cargo's mass to structural mass properties, as a point mass at each loaded
     * tile's center plus the stack's own spin inertia, keeping the combined center of mass
     * and moment of inertia about it.
     */
    public void addMassTo(ShipGridComponent grid, ShipGridComponent.MassProperties props) {
        double m = props.mass;
        double mx = m * props.center.x, my = m * props.center.y;
        // Inertia about the body origin, by the parallel axis theorem
        double i0 = props.inertia + m * props.center.len2();
        double tileOwnI = grid.tileSize * grid.tileSize / 6.0;
        for (int i = 0; i < stacks.length; i++) {
            CargoStack s = stacks[i];
            if (s == null) continue;
            double sm = s.mass();
            Vector2 p = grid.tileToLocal(bayX[i], bayY[i]);
            m += sm;
            mx += sm * p.x;
            my += sm * p.y;
            i0 += sm * (p.len2() + tileOwnI);
        }
        props.mass = (float) m;
        if (m > 0) {
            props.center.set((float) (mx / m), (float) (my / m));
            props.inertia = (float) (i0 - m * props.center.len2());
        }
    }
}
