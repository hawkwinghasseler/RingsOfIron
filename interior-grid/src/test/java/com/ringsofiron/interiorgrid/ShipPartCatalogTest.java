package com.ringsofiron.interiorgrid;

import static org.junit.jupiter.api.Assertions.*;

import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.ModdingBootstrap;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

class ShipPartCatalogTest {

    private static DefRegistry defs() {
        DefRegistry defs = new DefRegistry();
        ModdingBootstrap.registerBuiltInTypes(defs);
        Map<String, Object> armor = new LinkedHashMap<>();
        armor.put("name", "Armor plate");
        armor.put("symbol", "X");
        armor.put("base", "HULL");
        armor.put("weight_class", "HEAVY");
        armor.put("material", "composite_armor");
        defs.define("tile", "armor_plate", armor);
        Map<String, Object> carpet = new LinkedHashMap<>();
        carpet.put("symbol", "c");
        carpet.put("base", "FLOOR");
        carpet.put("covering", "CARPET");
        defs.define("tile", "carpet", carpet);
        Map<String, Object> core = new LinkedHashMap<>();
        core.put("name", "Fusion core");
        core.put("symbol", "F");
        core.put("behavior", "REACTOR");
        core.put("sizes", Arrays.asList("M"));
        Map<String, Object> output = new HashMap<>();
        output.put("M", 4.0e6);
        core.put("power_output", output);
        defs.define("component", "fusion_core", core);
        Map<String, Object> scrubber = new LinkedHashMap<>();
        scrubber.put("symbol", "Z");
        scrubber.put("behavior", "LIFE_SUPPORT");
        scrubber.put("throughput", 2.5);
        defs.define("component", "big_scrubber", scrubber);
        return defs;
    }

    private static ChassisDef chassis(String... rows) {
        ChassisDef def = new ChassisDef();
        def.id = "test";
        def.rows = rows;
        return def;
    }

    @Test
    void builtInsKeepTheirCharacters() {
        ShipPartCatalog c = ShipPartCatalog.builtIn();
        assertEquals(ShipPartCatalog.TileBase.HULL, c.tileFor('#').base);
        assertEquals(WeightClass.HEAVY, c.tileFor('3').weightClass);
        assertEquals(SubsystemComponent.SubsystemType.REACTOR, c.componentFor('R').behavior);
        assertNull(c.tileFor('R'));
        assertSame(c.component("cargo"), c.standardComponent(SubsystemComponent.SubsystemType.CARGO));
    }

    @Test
    void newTileTypesBuildTheirTiles() {
        ShipPartCatalog c = ShipPartCatalog.from(defs());
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(InteriorGridData.toBlueprint(chassis(
                "XXXX",
                "X.cX",
                "XXXX"), c));
        Tile armor = r.grid.getTile(0, 0);
        assertEquals(Tile.WallType.HULL, armor.wallType);
        assertEquals(WeightClass.HEAVY, armor.weightClass);
        assertEquals("composite_armor", armor.material);
        assertEquals(FloorCovering.CARPET, r.grid.getTile(2, 1).covering);
        assertEquals(FloorCovering.NONE, r.grid.getTile(1, 1).covering);
    }

    @Test
    void newComponentsInstallWithTheirSettings() {
        DefRegistry defs = defs();
        ShipPartCatalog c = ShipPartCatalog.from(defs);
        ChassisDef def = chassis(
                "######",
                "#FF.Z#",
                "#FF.Z#",
                "######");
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(InteriorGridData.toBlueprint(def, c));
        assertEquals(2, r.subsystems.size);
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            SubsystemComponent sub = new SubsystemComponent(p.footprint, p.type);
            p.component.configure(sub);
            if (p.type == SubsystemComponent.SubsystemType.REACTOR) {
                assertEquals("fusion_core", sub.component);
                assertEquals(4.0e6, PowerRoutingSystem.reactorOutput(sub), 1e-6);
            } else {
                assertEquals("big_scrubber", sub.component);
                assertEquals(2.5f, sub.throughput, 1e-6f);
            }
        }
    }

    @Test
    void explicitMountsCanNameAComponent() {
        ShipPartCatalog c = ShipPartCatalog.from(defs());
        ChassisDef def = chassis(
                "######",
                "#....#",
                "#....#",
                "######");
        ChassisDef.Mount m = new ChassisDef.Mount();
        m.component = "fusion_core";
        m.size = "M";
        m.x = 1;
        m.y = 1;
        def.mounts.add(m);
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(InteriorGridData.toBlueprint(def, c));
        assertEquals("fusion_core", r.subsystems.first().component.id);
        m.size = "S";
        assertThrows(IllegalArgumentException.class,
                () -> ChassisBlueprintLoader.load(InteriorGridData.toBlueprint(def, c)), "fusion core comes in M only");
    }

    @Test
    void catalogIsCachedUntilTheDefsChange() {
        DefRegistry defs = defs();
        ShipPartCatalog c = InteriorGridData.catalog(defs);
        assertSame(c, InteriorGridData.catalog(defs));
        Map<String, Object> more = new LinkedHashMap<>();
        more.put("symbol", "Q");
        more.put("behavior", "CARGO");
        defs.define("component", "crate_rack", more);
        assertNotSame(c, InteriorGridData.catalog(defs));
        assertNotNull(InteriorGridData.catalog(defs).componentFor('Q'));
    }

    @Test
    void symbolsMustBeUnique() {
        DefRegistry defs = defs();
        Map<String, Object> clash = new LinkedHashMap<>();
        clash.put("symbol", "R");
        clash.put("base", "FLOOR");
        defs.define("tile", "red_floor", clash);
        assertThrows(IllegalArgumentException.class, () -> ShipPartCatalog.from(defs));
    }

    @Test
    void unknownCharactersAreErrors() {
        assertThrows(IllegalArgumentException.class, () -> ChassisBlueprintLoader.load(new String[]{"#?#"}, 1f));
    }

    @Test
    void theGamesDataRestatesTheBuiltIns() throws Exception {
        DefRegistry defs = new DefRegistry();
        ModdingBootstrap.registerBuiltInTypes(defs);
        new com.ringsofiron.moddingdata.TomlDataLoader(defs, new com.ringsofiron.moddingdata.LoadReport())
                .loadDirectory(java.nio.file.Paths.get("../assets/data"), "base", true);
        ShipPartCatalog data = ShipPartCatalog.from(defs);
        ShipPartCatalog builtIn = ShipPartCatalog.builtIn();
        assertEquals(builtIn.tiles().size(), data.tiles().size());
        // Every built-in, and the components only data has (the freighter's freight drive)
        assertTrue(data.components().size() >= builtIn.components().size());
        for (ShipPartCatalog.TileType t : builtIn.tiles()) {
            ShipPartCatalog.TileType d = data.tile(t.id);
            assertEquals(t.symbol, d.symbol, t.id);
            assertEquals(t.base, d.base, t.id);
            assertEquals(t.weightClass, d.weightClass, t.id);
        }
        for (ShipPartCatalog.ComponentType t : builtIn.components()) {
            ShipPartCatalog.ComponentType d = data.component(t.id);
            assertEquals(t.symbol, d.symbol, t.id);
            assertEquals(t.behavior, d.behavior, t.id);
            assertEquals(t.sizes, d.sizes, t.id);
        }
    }
}
