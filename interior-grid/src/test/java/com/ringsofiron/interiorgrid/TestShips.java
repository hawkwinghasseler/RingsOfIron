package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Entity;

/** A small two-room ship for the interior tests. */
final class TestShips {
    private TestShips() {}

    static final float DT = GridHazardSystem.HAZARD_TICK;

    /**
     * Two carpeted rooms, 9 by 6 tiles each, split by a light wall at x = 10 with a door at
     * y = 3. A reactor, a life support unit and other systems stand in the left room.
     */
    static final String[] LAYOUT = {
        "####################  ",
        "#..................#  ",
        "#..R++++++++++++HH.#  ",
        "#..................#  ",
        "#..LL..........I...#  ",
        "#..................#  ",
        "#..................#  ",
        "####################  "
    };

    static ChassisBlueprint blueprint() {
        ChassisBlueprint bp = new ChassisBlueprint(LAYOUT).tileSize(1f);
        for (int y = 1; y <= 6; y++) bp.edgeWall(10, y, Side.LEFT, WeightClass.LIGHT, y == 3);
        bp.covering(FloorCovering.CARPET, 1, 1, 9, 6);
        return bp;
    }

    /** The grid with every system installed on an entity of its own, and one hazard step run. */
    static ShipGridComponent grid() {
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(blueprint());
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            Entity e = new Entity();
            e.add(new SubsystemComponent(p.footprint, p.type));
            p.footprint.installed = e;
        }
        new GridHazardSystem(1).step(r.grid, DT);
        return r.grid;
    }

    static EdgeWall door(ShipGridComponent g) {
        return g.getEdge(10, 3, Side.LEFT);
    }

    static Compartment left(ShipGridComponent g) {
        return g.compartmentAt(5, 1);
    }

    static Compartment right(ShipGridComponent g) {
        return g.compartmentAt(15, 1);
    }

    static double totalMoles(ShipGridComponent g) {
        double n = 0;
        for (Compartment c : g.compartments) n += c.totalMoles();
        return n;
    }

    static SubsystemComponent system(ShipGridComponent g, SubsystemComponent.SubsystemType type) {
        for (MountFootprint m : g.mounts) {
            SubsystemComponent s = m.installed.getComponent(SubsystemComponent.class);
            if (s.type == type) return s;
        }
        return null;
    }

    static void run(GridHazardSystem hz, ShipGridComponent g, float seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) hz.step(g, DT);
    }

    static boolean anyFire(ShipGridComponent g) {
        return g.hasFire();
    }
}
