package com.ringsofiron.interiorgrid;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;

/** Engines mount on the outside of the hull: where they may go, and how the ship reaches them. */
class EngineLayoutTest {

    private static ChassisBlueprintLoader.LoadResult load(ChassisBlueprint bp) {
        return ChassisBlueprintLoader.load(bp);
    }

    private static String refusal(ChassisBlueprint bp) {
        try {
            load(bp);
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
                new ChassisBlueprint("   #####",
                "   #...#",
                "   #...#",
                "   #...#",
                "   #####")
                .mount("engine", MountFootprint.MountSize.M, 0, 2, 1)
                .mount("reactor", MountFootprint.MountSize.S, 4, 3)
                .conduitRun(4, 3)).grid;
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
                new ChassisBlueprint("#####",
                "#...#",
                "#...#",
                "#####",
                "     ",
                "     ")
                .mount("engine", MountFootprint.MountSize.S, 1, 0, 2)).grid;
        MountFootprint m = engine(g);
        assertEquals(Side.BOTTOM, EngineLayout.nozzle(m));
        assertTrue(EngineLayout.isCasing(g, m, 1, 1), "the flange is outside too");
        assertTrue(EngineLayout.isFeedTile(g, m, 1, 3), "fed from the deck behind the wall");
    }

    @Test
    void anEngineMayNotOverlapTheHull() {
        // Its flange in place of a tile of the aft hull
        String inWall = refusal(
                new ChassisBlueprint("  #####",
                "  #...#",
                "  #...#",
                "  #...#",
                "  #####")
                .mount("engine", MountFootprint.MountSize.M, 0, 2, 1));
        assertNotNull(inWall);
        assertTrue(inWall.contains("wholly outside the hull"), inWall);
        // Through the aft hull and a tile on into the room
        String deep = refusal(
                new ChassisBlueprint(" ######",
                " #....#",
                " #....#",
                " #....#",
                " ######")
                .mount("engine", MountFootprint.MountSize.M, 0, 2, 1));
        assertNotNull(deep);
        assertTrue(deep.contains("wholly outside the hull"), deep);
        // Standing on the deck of a room
        String inside = refusal(
                new ChassisBlueprint("######",
                "#....#",
                "#....#",
                "#....#",
                "######")
                .mount("engine", MountFootprint.MountSize.S, 2, 2));
        assertNotNull(inside);
        assertTrue(inside.contains("wholly outside the hull"), inside);
    }

    @Test
    void anEngineNotOnTheHullIsRefused() {
        String floating = refusal(
                new ChassisBlueprint("####   ",
                "#..#   ",
                "####   ",
                "       ")
                .mount("engine", MountFootprint.MountSize.S, 6, 0));
        assertNotNull(floating);
        assertTrue(floating.contains("against the outer hull"), floating);
    }

    @Test
    void anEngineNeedsFiveClearTilesPastItsNozzleForEachTileOfItsWidth() {
        // A Small engine, one tile wide: a strut of hull three tiles past its nozzle is in the way, six is not
        assertNotNull(refusal(
                new ChassisBlueprint("     #####",
                "#    #...#",
                "     #...#",
                "     #####")
                .mount("engine", MountFootprint.MountSize.S, 3, 2, 1)));
        assertNull(refusal(
                new ChassisBlueprint("        #####",
                "#       #...#",
                "        #...#",
                "        #####")
                .mount("engine", MountFootprint.MountSize.S, 6, 2, 1)));
        // A Medium engine, two tiles wide, needs ten: nine is too few, eleven enough
        String medium = refusal(
                new ChassisBlueprint("            #####",
                "            #...#",
                "#           #...#",
                "            #...#",
                "            #####")
                .mount("engine", MountFootprint.MountSize.M, 9, 2, 1));
        assertNotNull(medium);
        assertTrue(medium.contains("needs 10 clear tiles"), medium);
        assertNull(refusal(
                new ChassisBlueprint("              #####",
                "              #...#",
                "#             #...#",
                "              #...#",
                "              #####")
                .mount("engine", MountFootprint.MountSize.M, 11, 2, 1)));
        // The grid's edge counts as space
        assertNull(refusal(
                new ChassisBlueprint("   #####",
                "   #...#",
                "   #...#",
                "   #...#",
                "   #####")
                .mount("engine", MountFootprint.MountSize.M, 0, 2, 1)));
    }

    @Test
    void anotherEngineInTheExhaustIsRefused() {
        // A second engine, against the side of a leg of the hull, just past the first one's nozzle
        ChassisBlueprint bp = new ChassisBlueprint(
                "#########",
                "#.......#",
                "#######.#",
                "      #.#",
                "      #.#",
                "      #.#",
                "      ###")
                .mount("engine", MountFootprint.MountSize.S, 4, 2, 2);
        bp.mount(new MountFootprint(MountFootprint.MountKind.AUXILIARY, MountFootprint.MountSize.S, 4, 1, 1),
                SubsystemComponent.SubsystemType.ENGINE);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ChassisBlueprintLoader.load(bp));
        assertTrue(e.getMessage().contains("clear tiles past its nozzle"), e.getMessage());
    }

    @Test
    void theShipPowersAndPipesAnEngineFromTheDeckBehindItsFlange() {
        ChassisBlueprint bp = new ChassisBlueprint(
                "   ######",
                "   #....#",
                "   #....#",
                "   ######")
                .mount("tank", MountFootprint.MountSize.S, 7, 1)
                .mount("engine", MountFootprint.MountSize.S, 1, 2, 1)
                .mount("reactor", MountFootprint.MountSize.S, 4, 2)
                .conduitRun(4, 2);
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
