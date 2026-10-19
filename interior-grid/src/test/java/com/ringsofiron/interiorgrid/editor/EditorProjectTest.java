package com.ringsofiron.interiorgrid.editor;

import static org.junit.jupiter.api.Assertions.*;

import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipPartCatalog;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.LoadReport;
import com.ringsofiron.moddingdata.ModdingBootstrap;
import com.ringsofiron.moddingdata.TomlDataLoader;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.ShipComponentDef;
import com.ringsofiron.moddingdata.defs.TileTypeDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

class EditorProjectTest {

    static final Path ASSETS = Paths.get("../assets");

    @TempDir
    Path temp;

    /** A copy of the game's assets folder to save into. */
    private Path assetsCopy() throws IOException {
        Path data = temp.resolve("assets/data");
        Files.createDirectories(data);
        try (Stream<Path> files = Files.list(ASSETS.resolve("data"))) {
            for (Path f : (Iterable<Path>) files::iterator) Files.copy(f, data.resolve(f.getFileName()));
        }
        return temp.resolve("assets");
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static DefRegistry loadDefs(Path assets) {
        DefRegistry defs = new DefRegistry();
        ModdingBootstrap.registerBuiltInTypes(defs);
        new TomlDataLoader(defs, new LoadReport()).loadDirectory(assets.resolve("data"), "base", true);
        return defs;
    }

    private static ChassisBlueprintLoader.LoadResult build(DefRegistry defs, String id) {
        ShipPartCatalog catalog = ShipPartCatalog.from(defs);
        return ChassisBlueprintLoader.load(InteriorGridData.toBlueprint(defs.require("chassis", id, ChassisDef.class), catalog));
    }

    /** Two grids hold the same tiles, walls and mounts. */
    private static void assertSameLayout(ChassisBlueprintLoader.LoadResult a, ChassisBlueprintLoader.LoadResult b, String id) {
        ShipGridComponent ga = a.grid, gb = b.grid;
        assertEquals(ga.width, gb.width, id);
        assertEquals(ga.height, gb.height, id);
        assertEquals(ga.gridOriginLocal, gb.gridOriginLocal, id);
        for (int y = 0; y < ga.height; y++) {
            for (int x = 0; x < ga.width; x++) {
                Tile ta = ga.getTile(x, y), tb = gb.getTile(x, y);
                String at = id + " " + x + "," + y;
                assertEquals(ta.wallType, tb.wallType, at);
                assertEquals(ta.floorType, tb.floorType, at);
                assertEquals(ta.weightClass, tb.weightClass, at);
                assertEquals(ta.covering, tb.covering, at);
                for (Side s : Side.values()) assertEquals(ga.getEdge(x, y, s) == null, gb.getEdge(x, y, s) == null, at);
            }
        }
        assertEquals(a.subsystems.size, b.subsystems.size, id);
        outer:
        for (ChassisBlueprintLoader.SubsystemPlacement pa : a.subsystems) {
            for (ChassisBlueprintLoader.SubsystemPlacement pb : b.subsystems) {
                MountFootprint fa = pa.footprint, fb = pb.footprint;
                if (pa.component.id.equals(pb.component.id) && fa.originX == fb.originX && fa.originY == fb.originY
                        && fa.size == fb.size && fa.rotation == fb.rotation) continue outer;
            }
            fail(id + ": " + pa.component.id + " at " + pa.footprint.originX + "," + pa.footprint.originY + " is missing");
        }
    }

    @Test
    void everyChassisSavesBackUnchanged() throws IOException {
        Path assets = assetsCopy();
        DefRegistry before = loadDefs(assets);
        EditorProject project = EditorProject.load(assets, null);
        assertTrue(project.problems.isEmpty(), project.problems.toString());
        assertFalse(project.chassis.isEmpty());
        for (EditorProject.Item<ChassisModel> c : project.chassis.values()) c.dirty = true;
        project.save();
        DefRegistry after = loadDefs(assets);
        for (String id : before.ids("chassis")) {
            assertSameLayout(build(before, id), build(after, id), id);
            ChassisDef a = before.require("chassis", id, ChassisDef.class), b = after.require("chassis", id, ChassisDef.class);
            assertEquals(a.name, b.name);
            assertEquals(a.station, b.station);
            assertEquals(a.ports.size(), b.ports.size(), id + " ports");
        }
    }

    @Test
    void savingKeepsCommentsAndOtherSections() throws IOException {
        Path assets = assetsCopy();
        Path file = assets.resolve("data/chassis.toml");
        String original = read(file);
        EditorProject project = EditorProject.load(assets, null);
        EditorProject.Item<ChassisModel> corvette = project.chassis.get("corvette");
        corvette.value.name = "Patrol Corvette Mk2";
        corvette.dirty = true;
        project.save();
        String saved = read(file);
        assertTrue(saved.contains("name = \"Patrol Corvette Mk2\""));
        assertTrue(saved.contains("# Carpet through the crew spaces, so a fire here has something to burn\ncoverings"),
                "a comment stays above its key");
        assertTrue(saved.contains("# A turret mount under each hardpoint"));
        int start = original.indexOf("[chassis.freighter]");
        assertEquals(original.substring(start - 1), saved.substring(saved.indexOf("[chassis.freighter]") - 1),
                "the sections after it are untouched");
        assertTrue(saved.startsWith(original.substring(0, original.indexOf("[chassis.corvette]"))), "and the header comment");
    }

    @Test
    void untouchedFilesAreNotWritten() throws IOException {
        Path assets = assetsCopy();
        EditorProject project = EditorProject.load(assets, null);
        assertFalse(project.hasUnsavedChanges());
        assertTrue(project.save().isEmpty());
    }

    @Test
    void newPartsAndLayoutsLoadInTheGame() throws IOException {
        Path assets = assetsCopy();
        EditorProject project = EditorProject.load(assets, null);

        TileTypeDef armor = Defs.copy(project.tiles.get("hull_heavy").value, TileTypeDef.class);
        armor.id = "armor_plate";
        armor.name = "Armor plate";
        armor.symbol = String.valueOf(project.freeSymbol("X"));
        armor.material = "composite_armor";
        project.addTile(armor);

        ShipComponentDef core = Defs.copy(project.components.get("reactor").value, ShipComponentDef.class);
        core.id = "fusion_core";
        core.name = "Fusion core";
        core.symbol = "F";
        core.sizes.add("M");
        core.powerOutput.put("M", 4.0e6);
        project.addComponent(core);

        ShipPartCatalog catalog = project.catalog();
        assertTrue(project.catalogProblems().isEmpty(), project.catalogProblems().toString());
        ChassisModel m = ChassisModel.blank("skiff", 8, 6, catalog);
        m.paint(0, 0, armor.symbol.charAt(0), catalog);
        m.place("fusion_core", MountFootprint.MountSize.M, 1, 1, 0, catalog);
        m.place("life_support", MountFootprint.MountSize.S, 4, 1, 0, catalog);
        m.place("cargo", MountFootprint.MountSize.S, 5, 1, 0, catalog);
        m.place("cargo", MountFootprint.MountSize.S, 6, 1, 0, catalog);   // touches the first bay: must be listed
        m.place("docking_port", MountFootprint.MountSize.S, 2, 5, 0, catalog);
        m.mounts.get(m.mounts.size() - 1).active = true;
        m.toggleEdgeWall(3, 3, Side.RIGHT, "LIGHT", true);
        m.addCovering("CARPET", 3, 3, 4, 4);
        EditorProject.Check check = project.check(m);
        assertNull(check.error, check.error);
        assertEquals(5, check.systems);
        assertTrue(check.poweredSystems >= 2, "the reactor powers itself and its neighbours");
        project.addChassis(m);
        project.save();

        DefRegistry defs = loadDefs(assets);
        ChassisDef saved = defs.require("chassis", "skiff", ChassisDef.class);
        assertEquals(1, saved.mounts.size(), "only the touching bay is listed");
        assertEquals("cargo", saved.mounts.get(0).type.toLowerCase());
        assertEquals(1, saved.ports.size());
        ChassisBlueprintLoader.LoadResult r = build(defs, "skiff");
        assertEquals("composite_armor", r.grid.getTile(0, 0).material);
        assertEquals(5, r.subsystems.size);
        boolean foundCore = false;
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            if (p.component.id.equals("fusion_core")) {
                foundCore = true;
                assertEquals(4.0e6, p.component.ratedOutput(p.footprint.size), 1e-6);
            }
        }
        assertTrue(foundCore);
        assertTrue(read(assets.resolve("data/components.toml")).contains("[component.fusion_core]"));
        assertTrue(read(assets.resolve("data/tiles.toml")).contains("[tile.armor_plate]"));
    }

    @Test
    void renamingATileSymbolRedrawsLayouts() {
        EditorProject project = EditorProject.load(ASSETS, null);
        EditorProject.Item<TileTypeDef> hull = project.tiles.get("hull");
        assertNull(project.setTileSymbol(hull, 'h'));
        ChassisModel corvette = project.chassis.get("corvette").value;
        assertTrue(corvette.usesSymbol('h'));
        assertFalse(corvette.usesSymbol('#'));
        assertTrue(project.chassis.get("corvette").dirty);
        assertNull(project.check(corvette).error);
        assertNotNull(project.setTileSymbol(project.tiles.get("floor"), 'R'), "R is the reactor's");
    }

    @Test
    void partsInUseCannotBeDeleted() {
        EditorProject project = EditorProject.load(ASSETS, null);
        assertNotNull(project.delete(project.components.get("reactor")));
        assertNotNull(project.delete(project.tiles.get("conduit")));
        assertNull(project.delete(project.tiles.get("hull_medium")), "nothing draws with '2'");
    }

    @Test
    void undoRestoresEdits() {
        EditorProject project = EditorProject.load(ASSETS, null);
        EditorProject.Snapshot s = project.snapshot();
        ChassisModel corvette = project.chassis.get("corvette").value;
        ShipPartCatalog catalog = project.catalog();
        corvette.erase(13, 3, catalog);
        project.chassis.get("corvette").dirty = true;
        project.restore(s);
        assertFalse(project.chassis.get("corvette").dirty);
        assertNotNull(project.chassis.get("corvette").value.mountAt(13, 3, catalog), "the reactor is back");
    }

    @Test
    void resizingShiftsEverything() {
        EditorProject project = EditorProject.load(ASSETS, null);
        ShipPartCatalog catalog = project.catalog();
        ChassisModel corvette = project.chassis.get("corvette").value;
        int w = corvette.width();
        float ox = corvette.originX;
        corvette.resize(Side.LEFT, 1, catalog);
        assertEquals(w + 1, corvette.width());
        assertEquals(ox - 1f, corvette.originX, 1e-6f);
        assertNotNull(corvette.mountAt(14, 3, catalog), "the reactor moved right with its tiles");
        assertNull(project.check(corvette).error);
        corvette.resize(Side.LEFT, -1, catalog);
        assertNotNull(corvette.mountAt(13, 3, catalog));
        corvette.resize(Side.TOP, -1, catalog);
        assertEquals(5, corvette.height());
        assertNull(corvette.mountAt(4, 5, catalog), "the docking port on the top row is gone");
    }

    @Test
    void rotatingAComponentSavesItsFacing() {
        EditorProject project = EditorProject.load(ASSETS, null);
        ShipPartCatalog catalog = project.catalog();
        ChassisModel corvette = project.chassis.get("corvette").value;
        ChassisModel.Mount reactor = corvette.mountAt(13, 3, catalog);
        int x = reactor.x, y = reactor.y;
        assertNull(corvette.rotate(reactor, catalog));
        assertEquals(1, reactor.rotation);
        assertEquals(x, reactor.x, "a square mount turns in place");
        assertEquals(y, reactor.y);
        assertNull(project.check(corvette).error);

        // The loader reads a drawn square mount as unturned, so the turned one is listed
        ChassisDef def = corvette.toDef(catalog);
        ChassisDef.Mount listed = def.mounts.stream().filter(m -> m.x == x && m.y == y).findFirst().orElse(null);
        assertNotNull(listed);
        assertEquals(1, listed.rotation);
        ChassisModel back = ChassisModel.fromDef(def, catalog);
        assertEquals(1, back.mountAt(13, 3, catalog).rotation);

        ChassisModel.Mount port = corvette.mounts.stream().filter(m -> ChassisModel.isPort(m, catalog)).findFirst().get();
        assertNotNull(corvette.rotate(port, catalog), "ports face out of the hull");
    }

    @Test
    void aRectangularMountTurnsAboutItsMiddle() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisModel m = ChassisModel.blank("test", 16, 12, catalog);
        ChassisModel.Mount cargo = m.place("cargo", MountFootprint.MountSize.L, 4, 4, 0, catalog);
        MountFootprint f = cargo.footprint(catalog);
        assertNotEquals(f.width(), f.height(), "the test needs a rectangular slot");
        for (int turn = 1; turn <= 4; turn++) {
            assertNull(m.rotate(cargo, catalog));
            MountFootprint t = cargo.footprint(catalog);
            assertEquals(turn % 2 == 0 ? f.width() : f.height(), t.width());
        }
        assertEquals(4, cargo.x, "four turns end where they began");
        assertEquals(4, cargo.y);
        assertEquals(0, cargo.rotation);

        ChassisModel.Mount edge = m.place("cargo", MountFootprint.MountSize.L, 0, 0, 0, catalog);
        MountFootprint ef = edge.footprint(catalog);
        if (ef.width() > ef.height()) assertNotNull(m.rotate(edge, catalog), "it would leave the grid");
    }

    @Test
    void aHullPieceReplacesThePiecesItOverlaps() {
        EditorProject project = EditorProject.load(ASSETS, null);
        ShipPartCatalog catalog = project.catalog();
        ChassisModel corvette = project.chassis.get("corvette").value;
        corvette.resize(Side.RIGHT, 1, catalog);
        int x = corvette.width() - 1;
        assertNotNull(corvette.addHullPiece("WEDGE", x, 0, 1, 2, 0, "MEDIUM"));
        assertNull(project.check(corvette).error);
        // The same spot again, as the mirrored wedge: it takes the old one's place
        assertNotNull(corvette.addHullPiece("WEDGE", x, 0, 2, 1, 1, "MEDIUM"));
        assertEquals(1, corvette.hullPieces.size());
        assertNull(project.check(corvette).error);
        assertNull(corvette.addHullPiece("WEDGE", x, 0, 2, 1, 0, "MEDIUM"), "two wide off the right edge");
        assertEquals(1, corvette.hullPieces.size());
    }
}
