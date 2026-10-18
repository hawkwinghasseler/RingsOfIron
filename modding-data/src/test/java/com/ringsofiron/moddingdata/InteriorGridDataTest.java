package com.ringsofiron.moddingdata;

import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.HullPiece;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.WeightClass;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.MaterialDef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the chassis and material data feed interior-grid's real loader. The two adapter
 * methods here are what interior-grid needs to read its blueprints and MaterialTable
 * from data; they live in this test so modding-data stays independent of interior-grid.
 */
class InteriorGridDataTest {

    static ModContext ctx;

    @BeforeAll
    static void load() {
        ctx = new ModdingBootstrap(ModdingBootstrapTest.ASSETS, ModdingBootstrapTest.EXAMPLE_MODS).load(new GameEngine());
    }

    static ChassisBlueprint toBlueprint(ChassisDef def) {
        ChassisBlueprint bp = new ChassisBlueprint(def.rows).tileSize(def.tileSize).origin(def.originX, def.originY);
        for (ChassisDef.EdgeWall w : def.edgeWalls) {
            bp.edgeWalls.add(new ChassisBlueprint.EdgeWallDef(w.x, w.y, Side.valueOf(w.side),
                    WeightClass.valueOf(w.weightClass), w.material, w.bulkhead));
        }
        for (ChassisDef.HullPiece p : def.hullPieces) {
            bp.hullPiece(new HullPiece(HullPiece.Shape.valueOf(p.shape), p.x, p.y, p.width, p.height, p.rotation,
                    WeightClass.valueOf(p.weightClass), p.material));
        }
        for (ChassisDef.Mount m : def.mounts) {
            SubsystemComponent.SubsystemType type = SubsystemComponent.SubsystemType.valueOf(m.type);
            bp.mount(new MountFootprint(type.mountKind, MountFootprint.MountSize.valueOf(m.size), m.x, m.y, m.rotation), type);
        }
        for (ChassisDef.Covering c : def.coverings) {
            bp.covering(FloorCovering.valueOf(c.covering), c.x, c.y, c.width, c.height);
        }
        return bp;
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
        int reactors = 0, turrets = 0, consoles = 0, bays = 0;
        for (ChassisBlueprintLoader.SubsystemPlacement p : result.subsystems) {
            if (p.type == SubsystemComponent.SubsystemType.REACTOR) reactors++;
            if (p.type == SubsystemComponent.SubsystemType.TURRET_MOUNT) turrets++;
            if (p.type == SubsystemComponent.SubsystemType.WEAPONS) consoles++;
            if (p.type == SubsystemComponent.SubsystemType.CARGO) bays++;
        }
        assertEquals(1, reactors);
        assertEquals(5, turrets, "nose, both beams, the launcher and the laser");
        assertEquals(1, consoles);
        assertEquals(4, bays, "two in the aft hold, two small ones forward");
        assertEquals(13, result.subsystems.size, "helm, reactor, life support, 4 cargo bays, weapons console, 5 turrets");
        assertEquals(14, result.grid.width);
        assertTrue(result.grid.getTile(8, 3).isFlammable(), "the crew spaces are carpeted");
        assertFalse(result.grid.getTile(4, 3).isFlammable(), "the hold's walkway is bare deck");
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
