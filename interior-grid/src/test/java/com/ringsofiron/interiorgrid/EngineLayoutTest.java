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
    void anEngineAgainstTheAftHullPointsAftAndLiesWhollyOutsideIt() {
        ShipGridComponent g = load(
                "   #####",
                "EEE#R..#",
                "EEE#...#",
                "   #...#",
                "   #####").grid;
        MountFootprint m = engine(g);
        assertEquals(Side.LEFT, EngineLayout.nozzle(m));
        for (int y = 2; y <= 3; y++) {
            assertEquals(Tile.WallType.HULL, g.getTile(3, y).wallType, "the hull behind it is whole");
            assertFalse(EngineLayout.isCasing(g, m, 3, y));
            for (int x = 0; x <= 2; x++) {
                assertEquals(Tile.WallType.HULL, g.getTile(x, y).wallType, "the casing is hull");
                assertEquals(EngineLayout.CASING, g.getTile(x, y).weightClass);
                assertTrue(EngineLayout.isCasing(g, m, x, y), "the flange is outside too");
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
        assertTrue(EngineLayout.isCasing(g, m, 1, 1), "the flange is outside too");
        assertTrue(EngineLayout.isFeedTile(g, m, 1, 3), "fed from the deck behind the wall");
    }

    @Test
    void anEngineMayNotOverlapTheHull() {
        // Its flange in place of a tile of the aft hull
        String inWall = refusal(
                "  #####",
                "EEE...#",
                "EEE...#",
                "  #...#",
                "  #####");
        assertNotNull(inWall);
        assertTrue(inWall.contains("wholly outside the hull"), inWall);
        // Through the aft hull and a tile on into the room
        String deep = refusal(
                " ######",
                "EEE...#",
                "EEE...#",
                " #....#",
                " ######");
        assertNotNull(deep);
        assertTrue(deep.contains("wholly outside the hull"), deep);
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
    void anEngineNeedsFiveClearTilesPastItsNozzleForEachTileOfItsWidth() {
        // A Small engine, one tile wide: a strut of hull three tiles past its nozzle is in the way, six is not
        assertNotNull(refusal(
                "     #####",
                "#  EE#...#",
                "     #...#",
                "     #####"));
        assertNull(refusal(
                "        #####",
                "#     EE#...#",
                "        #...#",
                "        #####"));
        // A Medium engine, two tiles wide, needs ten: nine is too few, eleven enough
        String medium = refusal(
                "            #####",
                "         EEE#...#",
                "#        EEE#...#",
                "            #...#",
                "            #####");
        assertNotNull(medium);
        assertTrue(medium.contains("needs 10 clear tiles"), medium);
        assertNull(refusal(
                "              #####",
                "           EEE#...#",
                "#          EEE#...#",
                "              #...#",
                "              #####"));
        // The grid's edge counts as space
        assertNull(refusal(
                "   #####",
                "EEE#...#",
                "EEE#...#",
                "   #...#",
                "   #####"));
    }

    @Test
    void anotherEngineInTheExhaustIsRefused() {
        // A second engine, against the side of a leg of the hull, just past the first one's nozzle
        ChassisBlueprint bp = new ChassisBlueprint(
                "#########",
                "#.......#",
                "#######.#",
                "    E #.#",
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
                "   ######",
                " EE#R..P#",
                "   #...P#",
                "   ######");
        for (int x = 4; x <= 7; x++) bp.pipe(x, 2);
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
        assertTrue(drive.powered, "power reaches it through the hull behind its flange");
        assertTrue(drive.pipedTanks.contains(tank, true), "so does the propellant");
        assertTrue(EngineLayout.isCasing(r.grid, 2, 2), "the flange is outside the hull");
        assertFalse(EngineLayout.isCasing(r.grid, 3, 2), "the hull behind it is not the engine's");
    }
}
