package com.ringsofiron.moddingdata;

import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipPartCatalog;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.WeightClass;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.MaterialDef;
import com.ringsofiron.moddingdata.defs.StationTypeDef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the chassis, tile, component and material data feed interior-grid's real loader.
 * interior-grid is only a test dependency here, so modding-data stays independent of it.
 */
class InteriorGridDataTest {

    static ModContext ctx;

    @BeforeAll
    static void load() {
        ctx = new ModdingBootstrap(ModdingBootstrapTest.ASSETS, ModdingBootstrapTest.EXAMPLE_MODS).load(new GameEngine());
    }

    /** The blueprint for a chassis, drawn with the loaded data's tile types and components. */
    static ChassisBlueprint toBlueprint(ChassisDef def) {
        return InteriorGridData.toBlueprint(def, ShipPartCatalog.from(ctx.defs));
    }

    static MaterialTable toMaterialTable(DefRegistry defs) {
        MaterialTable table = new MaterialTable();
        for (MaterialDef m : defs.all(MaterialDef.class)) {
            table.define(m.id, stats(m.light), stats(m.medium), stats(m.heavy));
        }
        return table;
    }

    private static MaterialTable.ClassStats stats(MaterialDef.ClassStats s) {
        return new MaterialTable.ClassStats(s.mass, s.hitPoints, s.armor);
    }

    @Test
    void everyDataChassisBuildsAValidGrid() {
        assertFalse(ctx.defs.ids("chassis").isEmpty());
        for (ChassisDef def : ctx.defs.all(ChassisDef.class)) {
            ChassisBlueprintLoader.LoadResult result = ChassisBlueprintLoader.load(toBlueprint(def));
            assertEquals(def.rows.length, result.grid.height, def.id);
            assertTrue(result.subsystems.size > 0, def.id + " has systems");
        }
    }

    @Test
    void corvetteHasATurretPerWeaponAndAWeaponsConsole() {
        ChassisDef corvette = ctx.defs.require("chassis", "corvette", ChassisDef.class);
        ChassisBlueprintLoader.LoadResult result = ChassisBlueprintLoader.load(toBlueprint(corvette));
        int reactors = 0, turrets = 0, consoles = 0, bays = 0, ports = 0, engines = 0;
        for (ChassisBlueprintLoader.SubsystemPlacement p : result.subsystems) {
            if (p.type == SubsystemComponent.SubsystemType.REACTOR) reactors++;
            if (p.type == SubsystemComponent.SubsystemType.TURRET_MOUNT) turrets++;
            if (p.type == SubsystemComponent.SubsystemType.WEAPONS) consoles++;
            if (p.type == SubsystemComponent.SubsystemType.CARGO) bays++;
            if (p.type == SubsystemComponent.SubsystemType.DOCKING_PORT) ports++;
            if (p.type == SubsystemComponent.SubsystemType.ENGINE) engines++;
        }
        assertEquals(1, reactors);
        assertEquals(5, turrets, "nose, both beams, the launcher and the laser");
        assertEquals(1, consoles);
        assertEquals(4, bays, "two in the aft hold, two small ones forward");
        assertEquals(1, ports, "a Small docking port beside the hold");
        assertEquals(2, engines, "two Small engines in the stern");
        assertEquals(16, result.subsystems.size,
                "helm, reactor, life support, 4 cargo bays, weapons console, 5 turrets, docking port, 2 engines");
        assertEquals(16, result.grid.width);
        assertTrue(result.grid.getTile(10, 3).isFlammable(), "the crew spaces are carpeted");
        assertFalse(result.grid.getTile(6, 3).isFlammable(), "the hold's walkway is bare deck");
    }

    @Test
    void stationTypesAreBuiltFromStationBlueprintsWithPortsOfEverySize() {
        // The base game's types; a mod's type may leave it out and get the default outpost
        for (String id : new String[]{"ice_mine", "agri_dome", "ore_mine", "foundry", "shipyard", "fuel_depot"}) {
            StationTypeDef type = ctx.defs.require("station_type", id, StationTypeDef.class);
            assertNotNull(type.blueprint, type.id + " names a blueprint");
            ChassisDef chassis = ctx.defs.require("chassis", type.blueprint, ChassisDef.class);
            assertTrue(chassis.station, type.blueprint + " is marked as a station");
            ChassisBlueprintLoader.LoadResult result = ChassisBlueprintLoader.load(toBlueprint(chassis));
            java.util.EnumSet<MountFootprint.MountSize> sizes = java.util.EnumSet.noneOf(MountFootprint.MountSize.class);
            for (ChassisBlueprintLoader.SubsystemPlacement p : result.subsystems) {
                if (p.type == SubsystemComponent.SubsystemType.DOCKING_PORT) sizes.add(p.footprint.size);
            }
            assertEquals(java.util.EnumSet.of(MountFootprint.MountSize.S, MountFootprint.MountSize.M,
                    MountFootprint.MountSize.L), sizes, type.blueprint + " can berth any ship");
            assertEquals(portCount(result), chassis.ports.size(), "every station port is listed as active");
            assertTrue(chassis.ports.stream().allMatch(p -> p.active));
        }
        assertTrue(ctx.report.entries().stream().noneMatch(e -> e.message.contains("blueprint")), ctx.report.toString());
    }

    private static int portCount(ChassisBlueprintLoader.LoadResult result) {
        int n = 0;
        for (ChassisBlueprintLoader.SubsystemPlacement p : result.subsystems) {
            if (p.type == SubsystemComponent.SubsystemType.DOCKING_PORT) n++;
        }
        return n;
    }

    @Test
    void cargoCapacityIsTheVolumeOfTheBays() {
        // 2.5 m3 a tile: the corvette's 16 bay tiles and the freighter's 120
        assertEquals(40.0, InteriorGridData.cargoBayVolume(ctx.defs, "corvette_patrol"), 1e-9);
        assertEquals(300.0, InteriorGridData.cargoBayVolume(ctx.defs, "freighter_hauler"), 1e-9);
    }

    @Test
    void materialTableComesFromData() {
        MaterialTable table = toMaterialTable(ctx.defs);
        MaterialTable.ClassStats heavySteel = table.get("hull_steel", WeightClass.HEAVY);
        assertEquals(5000f, heavySteel.mass);
        assertEquals(1.2e7f, heavySteel.hitPoints);
        assertEquals(1.2e7f, table.get("composite_armor", WeightClass.HEAVY).armor);
    }
}
