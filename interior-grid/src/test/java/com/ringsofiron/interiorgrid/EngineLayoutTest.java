package com.ringsofiron.interiorgrid;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;

/** Engines mount on the outside of the hull: where they may go, and how the ship reaches them. */
class EngineLayoutTest {

    private static ChassisBlueprintLoader.LoadResult load(String... rows) {
        return ChassisBlueprintLoader.load(new ChassisBlueprint(rows));
    }

    private static String refusal(String... rows) {
        try {
            load(rows);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private static MountFootprint engine(ShipGridComponent grid) {
        for (MountFootprint m : grid.mounts) {
            if (m.kind == MountFootprint.MountKind.AUXILIARY) return m;
        }
        return null;
    }

    @Test
    void anEngineSetOneTileIntoTheAftHullPointsAftAndKeepsTheHullWhole() {
        ShipGridComponent g = load(
                "  #####",
                "EEER..#",
                "EEE...#",
                "  #...#",
                "  #####").grid;
        MountFootprint m = engine(g);
        assertEquals(Side.LEFT, EngineLayout.nozzle(m));
        assertTrue(EngineLayout.flangeInWall(g, m));
        for (int y = 2; y <= 3; y++) {
            assertEquals(Tile.WallType.HULL, g.getTile(2, y).wallType, "the flange is the hull wall");
            assertEquals(WeightClass.MEDIUM, g.getTile(2, y).weightClass);
            assertFalse(EngineLayout.isCasing(g, m, 2, y));
            for (int x = 0; x <= 1; x++) {
                assertEquals(Tile.WallType.HULL, g.getTile(x, y).wallType, "the casing outside is hull");
                assertEquals(EngineLayout.CASING, g.getTile(x, y).weightClass);
                assertTrue(EngineLayout.isCasing(g, m, x, y));
            }
        }
        CompartmentBuilder.rebuild(g);
        assertEquals(1, g.compartments.size, "the room behind it stays sealed");
    }

    @Test
    void anEngineAgainstTheHullStandsWhollyOutsideIt() {
        ShipGridComponent g = load(
                "#####",
                "#...#",
                "#...#",
                "#####",
                " E   ",
                " E   ").grid;
        MountFootprint m = engine(g);
        assertEquals(Side.BOTTOM, EngineLayout.nozzle(m));
        assertFalse(EngineLayout.flangeInWall(g, m));
        assertTrue(EngineLayout.isCasing(g, m, 1, 1), "the flange is outside too");
        assertTrue(EngineLayout.isFeedTile(g, m, 1, 3), "fed from the deck behind the wall");
    }

    @Test
    void anEngineMayNotReachMoreThanOneTileIntoTheShip() {
        // Through the aft hull and a tile on into the room
        String deep = refusal(
                " ######",
                "EEE...#",
                "EEE...#",
                " #....#",
                " ######");
        assertNotNull(deep);
        assertTrue(deep.contains("at most one tile into it"), deep);
        // Standing in a room, with no end out in space
        String inside = refusal(
                "######",
                "#.E..#",
                "#.E..#",
                "#....#",
                "######");
        assertNotNull(inside);
        assertTrue(inside.contains("no end facing space"), inside);
    }

    @Test
    void anEngineNotOnTheHullIsRefused() {
        String floating = refusal(
                "####   ",
                "#..#   ",
                "####  E",
                "      E");
        assertNotNull(floating);
        assertTrue(floating.contains("not mounted on the hull"), floating);
    }

    @Test
    void anEngineNeedsFiveClearTilesPastItsNozzle() {
        // A strut of hull four tiles aft of the nozzle, across its path
        assertNotNull(refusal(
                "      #####",
                "    EEE...#",
                "#   EEE...#",
                "      #...#",
                "      #####"));
        // Five tiles clear, and the grid's edge counts as space
        assertNull(refusal(
                "        #####",
                "      EEE...#",
                "#     EEE...#",
                "        #...#",
                "        #####"));
        assertNull(refusal(
                "  #####",
                "EEE...#",
                "EEE...#",
                "  #...#",
                "  #####"));
    }

    @Test
    void anotherEngineInTheExhaustIsRefused() {
        // A second engine, against the side of a leg of the hull, just past the first one's nozzle
        ChassisBlueprint bp = new ChassisBlueprint(
                "#########",
                "#.......#",
                "####E##.#",
                "    E #.#",
                "      #.#",
                "      ###");
        bp.mount(new MountFootprint(MountFootprint.MountKind.AUXILIARY, MountFootprint.MountSize.S, 4, 1, 1),
                SubsystemComponent.SubsystemType.ENGINE);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ChassisBlueprintLoader.load(bp));
        assertTrue(e.getMessage().contains("clear tiles past its nozzle"), e.getMessage());
    }

    @Test
    void theShipPowersAndPipesAnEngineFromTheDeckBehindItsFlange() {
        ChassisBlueprint bp = new ChassisBlueprint(
                "  ######",
                " EER..P#",
                "  #...P#",
                "  ######");
        for (int x = 3; x <= 6; x++) bp.pipe(x, 2);
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(bp);
        Engine engine = new Engine();
        engine.addSystem(new PowerRoutingSystem());
        Entity ship = new Entity();
        ShipSubsystemRegistryComponent registry = new ShipSubsystemRegistryComponent(ship);
        ship.add(r.grid);
        ship.add(registry);
        engine.addEntity(ship);
        SubsystemComponent drive = null, tank = null;
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            Entity e = new Entity();
            SubsystemComponent sub = new SubsystemComponent(p.footprint, p.type);
            p.component.configure(sub);
            e.add(sub);
            p.footprint.installed = e;
            engine.addEntity(e);
            registry.registerSubsystem(e);
            if (p.type == SubsystemComponent.SubsystemType.ENGINE) drive = sub;
            if (p.type == SubsystemComponent.SubsystemType.TANK) tank = sub;
        }
        r.grid.gridDirty = true;
        engine.update(0f);
        assertTrue(drive.powered, "power reaches it through its flange");
        assertTrue(drive.pipedTanks.contains(tank, true), "so does the propellant");
        assertTrue(EngineLayout.isCasing(r.grid, 1, 2));
        assertFalse(EngineLayout.isCasing(r.grid, 2, 2), "the flange is the wall");
    }
}
