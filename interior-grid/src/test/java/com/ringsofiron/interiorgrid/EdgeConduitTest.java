package com.ringsofiron.interiorgrid;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;

/** Power conduits run along tile edges, through full-tile and thin walls. */
class EdgeConduitTest {

    private final Engine engine = new Engine();
    private ShipGridComponent grid;

    /**
     * A reactor at (1, 1) and a turret mount at (5, 1), with a full-tile wall at (3, 1) between them
     * and a conduit run up to the wall on either side.
     */
    private static ChassisBlueprint walledOff() {
        return new ChassisBlueprint(
                "#######",
                "#..I..#",
                "#######")
                .mount("reactor", MountFootprint.MountSize.S, 1, 1)
                .mount("turret_mount", MountFootprint.MountSize.S, 5, 1)
                .conduitRun(1, 1, 2, 1)
                .conduitRun(4, 1, 5, 1);
    }

    /**
     * A reactor at (2, 1) and a turret mount at (3, 1), either side of a closed thin wall on x = 3,
     * with no conduit across a tile.
     */
    private static ChassisBlueprint thinWalled() {
        return new ChassisBlueprint(
                "######",
                "#....#",
                "######")
                .mount("reactor", MountFootprint.MountSize.S, 2, 1)
                .mount("turret_mount", MountFootprint.MountSize.S, 3, 1)
                .edgeWall(3, 1, Side.LEFT, WeightClass.MEDIUM, false);
    }

    private SubsystemComponent build(ChassisBlueprint bp) {
        engine.addSystem(new PowerRoutingSystem());
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(bp);
        Entity ship = new Entity();
        ShipSubsystemRegistryComponent registry = new ShipSubsystemRegistryComponent(ship);
        ship.add(r.grid);
        ship.add(registry);
        engine.addEntity(ship);
        SubsystemComponent turret = null;
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            Entity e = new Entity();
            SubsystemComponent sub = new SubsystemComponent(p.footprint, p.type);
            e.add(sub);
            p.footprint.installed = e;
            engine.addEntity(e);
            registry.registerSubsystem(e);
            if (p.type == SubsystemComponent.SubsystemType.TURRET_MOUNT) turret = sub;
        }
        grid = r.grid;
        reroute();
        return turret;
    }

    private void reroute() {
        grid.gridDirty = true;
        engine.update(0f);
    }

    /** Around the wall tile: up its left edge, along its top and down its right edge. */
    private static ChassisBlueprint aroundTheWall(ChassisBlueprint bp) {
        return bp.edgeConduit(3, 1, Side.LEFT).edgeConduit(3, 1, Side.TOP).edgeConduit(3, 1, Side.RIGHT);
    }

    @Test
    void aFullTileWallStopsPowerWithoutAConduit() {
        assertFalse(build(walledOff()).powered);
    }

    @Test
    void conduitsAlongAWallsEdgesCarryPowerPastIt() {
        SubsystemComponent turret = build(aroundTheWall(walledOff()));
        assertTrue(turret.powered);
        assertSame(grid.circuits.first(), turret.circuit);
        assertTrue(grid.getConduit(3, 1, Side.TOP).isPowered);
        assertTrue(grid.getTileConduit(4, 1).isPowered, "the conduit beyond the wall has power");
    }

    @Test
    void conduitsMeetOnlyAtTheEndsOfTheirEdges() {
        // Without the top edge the two sides never meet: edge conduits join only at their ends
        SubsystemComponent turret = build(walledOff().edgeConduit(3, 1, Side.LEFT).edgeConduit(3, 1, Side.RIGHT));
        assertFalse(turret.powered);
        assertTrue(grid.getConduit(3, 1, Side.LEFT).isPowered);
        assertFalse(grid.getConduit(3, 1, Side.RIGHT).isPowered);
    }

    @Test
    void destroyingTheWallAConduitRunsInCutsIt() {
        SubsystemComponent turret = build(aroundTheWall(walledOff()));
        grid.getTile(3, 1).setIntegrity(0f);
        reroute();
        assertFalse(turret.powered);
        assertFalse(grid.getConduit(3, 1, Side.TOP).isPowered);
    }

    @Test
    void aConduitInAThinWallFeedsBothSides() {
        assertFalse(build(thinWalled()).powered, "nothing carries power to it");
        engine.removeAllEntities();
        SubsystemComponent turret = build(thinWalled().edgeConduit(3, 1, Side.LEFT));
        assertTrue(turret.powered);
        assertSame(grid.getConduit(2, 1, Side.RIGHT), grid.getConduit(3, 1, Side.LEFT), "one edge, from either tile");
    }

    @Test
    void edgeConduitsAreSaved() {
        build(aroundTheWall(walledOff()));
        ShipGridComponent back = WallLightSaveTest.roundTrip(grid);
        assertNotNull(back.getConduit(3, 1, Side.LEFT));
        assertNotNull(back.getConduit(3, 1, Side.TOP));
        assertNotNull(back.getConduit(3, 1, Side.RIGHT));
        assertNull(back.getConduit(3, 1, Side.BOTTOM));
    }
}
