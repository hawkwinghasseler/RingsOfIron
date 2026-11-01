package com.ringsofiron.interiorgrid;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;

/**
 * Power runs in conduits only: a layer of its own over the tiles, which systems and conduits share
 * without blocking each other, and which crosses thin walls the way pipes do.
 */
class ConduitPowerTest {

    private final Engine engine = new Engine();
    private ShipGridComponent grid;
    private ShipSubsystemRegistryComponent registry;

    /** A room 8 by 3 with a reactor in its left end and a helm in its right. */
    private static ChassisBlueprint room() {
        return new ChassisBlueprint(
                "##########",
                "#........#",
                "#........#",
                "#........#",
                "##########").tileSize(1f)
                .mount("reactor", MountFootprint.MountSize.S, 1, 2)
                .mount("helm", MountFootprint.MountSize.S, 7, 2);
    }

    private void build(ChassisBlueprint bp) {
        engine.addSystem(new PowerRoutingSystem());
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(bp);
        Entity ship = new Entity();
        registry = new ShipSubsystemRegistryComponent(ship);
        ship.add(r.grid);
        ship.add(registry);
        engine.addEntity(ship);
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            Entity e = new Entity();
            SubsystemComponent sub = new SubsystemComponent(p.footprint, p.type);
            p.component.configure(sub);
            e.add(sub);
            p.footprint.installed = e;
            engine.addEntity(e);
            registry.registerSubsystem(e);
        }
        grid = r.grid;
        reroute();
    }

    private void reroute() {
        grid.gridDirty = true;
        engine.update(0f);
    }

    private SubsystemComponent system(SubsystemComponent.SubsystemType type) {
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == type) return s;
        }
        return null;
    }

    @Test
    void aBareFloorCarriesNoPower() {
        build(room());
        SubsystemComponent reactor = system(SubsystemComponent.SubsystemType.REACTOR);
        SubsystemComponent helm = system(SubsystemComponent.SubsystemType.HELM);
        assertTrue(reactor.powered, "a reactor no conduit reaches still powers itself");
        assertTrue(reactor.isOperational);
        assertFalse(helm.powered, "the same room, but no conduit");
        assertFalse(helm.isOperational);
        assertNull(helm.circuit);
        assertEquals(1, grid.circuits.size, "the reactor's own");
    }

    @Test
    void aConduitRunUnderTheSystemsPowersThem() {
        // The run lies under both systems' tiles: neither blocks the other
        build(room().conduitRun(1, 2, 7, 2));
        SubsystemComponent helm = system(SubsystemComponent.SubsystemType.HELM);
        assertTrue(helm.powered);
        assertSame(system(SubsystemComponent.SubsystemType.REACTOR).circuit, helm.circuit);
        assertNotNull(grid.getTileConduit(1, 2), "a conduit under the reactor");
        assertNotNull(grid.getTile(1, 2).mount, "and the reactor over it");
        assertTrue(grid.getTileConduit(4, 2).isPowered);
        assertTrue(grid.getTile(4, 2).isFloor(), "the deck under a conduit is still deck");
    }

    @Test
    void aConduitNextToASystemDoesNotReachIt() {
        // The run stops a tile short of the helm
        build(room().conduitRun(1, 2, 6, 2));
        assertFalse(system(SubsystemComponent.SubsystemType.HELM).powered);
    }

    @Test
    void aConduitCrossesAThinWallOrADoorLikeAPipe() {
        build(room().conduitRun(1, 2, 7, 2)
                .edgeWall(4, 1, Side.LEFT, WeightClass.MEDIUM, false)
                .edgeWall(4, 2, Side.LEFT, WeightClass.MEDIUM, false)
                .edgeWall(4, 3, Side.LEFT, WeightClass.MEDIUM, true));
        assertNotNull(grid.getEdgeBetween(3, 2, 4, 2), "a wall between the conduit's tiles");
        assertTrue(system(SubsystemComponent.SubsystemType.HELM).powered, "the conduit runs through the wall");
        grid.getEdge(4, 3, Side.LEFT).bulkheadSealed = true;
        reroute();
        assertTrue(system(SubsystemComponent.SubsystemType.HELM).powered, "a shut door does not cut it");
    }

    @Test
    void aFullTileWallStopsPowerUnlessAConduitIsLaidOverIt() {
        ChassisBlueprint walled = new ChassisBlueprint(
                "##########",
                "#...I....#",
                "#...I....#",
                "#...I....#",
                "##########").tileSize(1f)
                .mount("reactor", MountFootprint.MountSize.S, 1, 2)
                .mount("helm", MountFootprint.MountSize.S, 7, 2)
                .conduitRun(1, 2, 3, 2)
                .conduitRun(5, 2, 7, 2);
        build(walled);
        assertFalse(system(SubsystemComponent.SubsystemType.HELM).powered, "the wall tile carries nothing");
        engine.removeAllEntities();
        build(walled.conduit(4, 2));
        assertTrue(system(SubsystemComponent.SubsystemType.HELM).powered, "a conduit may lie on a wall");
        assertTrue(grid.getTile(4, 2).isWall());
    }

    @Test
    void aCutConduitCarriesNothingButTheDeckUnderItIsWhole() {
        build(room().conduitRun(1, 2, 7, 2));
        grid.getTileConduit(4, 2).integrity = 0f;
        reroute();
        assertTrue(grid.getTileConduit(4, 2).isCut());
        assertFalse(system(SubsystemComponent.SubsystemType.HELM).powered);
        assertEquals(1f, grid.getTile(4, 2).getIntegrity(), "only the conduit was hit");
        assertFalse(grid.getTileConduit(6, 2).isPowered, "beyond the cut");
        assertTrue(grid.getTileConduit(2, 2).isPowered, "and before it, still live");
    }

    @Test
    void aWreckedDeckLeavesTheConduitOnItCarrying() {
        build(room().conduitRun(1, 2, 7, 2));
        grid.getTile(4, 2).setIntegrity(0f);
        reroute();
        assertTrue(system(SubsystemComponent.SubsystemType.HELM).powered, "the conduit has a condition of its own");
    }

    @Test
    void cargoTanksAndPodsWorkWithoutPower() {
        ChassisBlueprint bp = new ChassisBlueprint(
                "##########",
                "#........#",
                "#........#",
                "#........#",
                "####3#####").tileSize(1f)
                .mount("reactor", MountFootprint.MountSize.S, 1, 2)
                .mount("cargo", MountFootprint.MountSize.S, 3, 2)
                .mount("tank", MountFootprint.MountSize.S, 6, 2)
                .mount("escape_pod", MountFootprint.MountSize.S, 4, 0, 2);
        build(bp);
        for (SubsystemComponent.SubsystemType type : new SubsystemComponent.SubsystemType[]{
                SubsystemComponent.SubsystemType.CARGO, SubsystemComponent.SubsystemType.TANK,
                SubsystemComponent.SubsystemType.ESCAPE_POD}) {
            SubsystemComponent s = system(type);
            assertNotNull(s, type.name());
            assertFalse(type.needsPower(), type.name());
            assertFalse(s.powered, type + " has no conduit");
            assertTrue(s.isOperational, type + " needs none");
        }
        assertTrue(SubsystemComponent.SubsystemType.HELM.needsPower());
    }

    @Test
    void aConduitOutInSpaceIsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ChassisBlueprintLoader.load(new ChassisBlueprint(
                        "     ",
                        " ### ",
                        " #.# ",
                        " ### ",
                        "     ").conduitRun(2, 2, 2, 4)));
        assertTrue(e.getMessage().contains("out in space"), e.getMessage());
        IllegalArgumentException off = assertThrows(IllegalArgumentException.class,
                () -> ChassisBlueprintLoader.load(new ChassisBlueprint("###", "#.#", "###").conduit(5, 1)));
        assertTrue(off.getMessage().contains("off the grid"), off.getMessage());
    }

    @Test
    void conduitsAreSavedWithTheirCondition() {
        build(room().conduitRun(1, 2, 7, 2));
        grid.getTileConduit(3, 2).integrity = 0.5f;
        grid.getTileConduit(5, 2).shorted = true;
        ShipGridComponent back = WallLightSaveTest.roundTrip(grid);
        for (int x = 1; x <= 7; x++) assertNotNull(back.getTileConduit(x, 2), x + ",2");
        assertNull(back.getTileConduit(1, 1));
        assertEquals(0.5f, back.getTileConduit(3, 2).integrity, 1e-6f);
        assertTrue(back.getTileConduit(5, 2).shorted);
        assertFalse(back.getTileConduit(3, 2).shorted);
        assertEquals(1f, back.getTileConduit(1, 2).integrity, 1e-6f);
    }
}
