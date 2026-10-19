package com.ringsofiron.interiorgrid.editor;

import static org.junit.jupiter.api.Assertions.*;

import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipPartCatalog;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.WallLight;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/** Wall lights in layouts: placed by hand or auto-placed in the editor, saved, and loaded by the game. */
class EditorLightsTest {
    static final Path ASSETS = Paths.get("../assets");

    @TempDir
    Path temp;

    private Path assetsCopy() throws IOException {
        Path data = temp.resolve("assets/data");
        Files.createDirectories(data);
        try (Stream<Path> files = Files.list(ASSETS.resolve("data"))) {
            for (Path f : (Iterable<Path>) files::iterator) Files.copy(f, data.resolve(f.getFileName()));
        }
        return temp.resolve("assets");
    }

    private static ShipGridComponent grid(ChassisModel m, ShipPartCatalog catalog) {
        return ChassisBlueprintLoader.load(InteriorGridData.toBlueprint(m.toDef(catalog), catalog)).grid;
    }

    @Test
    void everyLayoutHangsItsLightsOnFloorTiles() {
        EditorProject project = EditorProject.load(ASSETS, null);
        ShipPartCatalog catalog = project.catalog();
        for (EditorProject.Item<ChassisModel> item : project.chassis.values()) {
            ChassisModel m = item.value;
            assertNotNull(m.lights, item.id + " places its lights");
            assertFalse(m.lights.isEmpty(), item.id);
            ShipGridComponent g = grid(m, catalog);
            assertEquals(m.lights.size(), g.wallLights.size, item.id + ": the game hangs every light");
            for (WallLight l : g.wallLights) assertTrue(g.getTile(l.x, l.y).isFloor(), item.id + " " + l);
        }
    }

    @Test
    void autoPlacingIsWhatTheDataHolds() {
        // The lights in chassis.toml came from the editor's auto-place; doing it again changes nothing
        EditorProject project = EditorProject.load(ASSETS, null);
        ShipPartCatalog catalog = project.catalog();
        for (EditorProject.Item<ChassisModel> item : project.chassis.values()) {
            ChassisDef before = item.value.toDef(catalog);
            assertNull(item.value.autoPlaceLights(catalog));
            ChassisDef after = item.value.toDef(catalog);
            assertEquals(before.lights.size(), after.lights.size(), item.id);
            for (int i = 0; i < before.lights.size(); i++) {
                ChassisDef.Light a = before.lights.get(i), b = after.lights.get(i);
                assertEquals(a.x + "," + a.y + " " + a.side, b.x + "," + b.y + " " + b.side, item.id);
            }
        }
    }

    @Test
    void aLightPlacedByHandSavesAndLoadsBack() throws IOException {
        Path assets = assetsCopy();
        EditorProject project = EditorProject.load(assets, null);
        EditorProject.Item<ChassisModel> corvette = project.chassis.get("corvette");
        int before = corvette.value.lights.size();
        assertNull(corvette.value.lightAt(6, 4, Side.TOP));
        assertTrue(corvette.value.toggleLight(6, 4, Side.TOP));
        corvette.dirty = true;
        project.save();

        EditorProject again = EditorProject.load(assets, null);
        ChassisModel back = again.chassis.get("corvette").value;
        assertEquals(before + 1, back.lights.size());
        assertNotNull(back.lightAt(6, 4, Side.TOP));
        assertTrue(grid(back, again.catalog()).wallLights.contains(new WallLight(6, 4, Side.TOP), false));

        back.toggleLight(6, 4, Side.TOP);
        assertNull(back.lightAt(6, 4, Side.TOP), "clicking it again takes it down");
        assertFalse(back.toggleLight(-1, 0, Side.TOP), "not off the grid");
    }

    @Test
    void aLightTakesNoRoomOnItsTile() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisModel m = ChassisModel.blank("test", 12, 10, catalog);
        m.toggleLight(4, 4, Side.TOP);
        assertNotNull(m.place("reactor", MountFootprint.MountSize.M, 4, 4, 0, catalog), "a system goes on the light's tile");
        assertNotNull(m.lightAt(4, 4, Side.TOP), "and the light stays");
        assertEquals(1, grid(m, catalog).wallLights.size);
    }

    @Test
    void lightsMoveWithTheGridAndUndo() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisModel m = ChassisModel.blank("test", 12, 10, catalog);
        m.toggleLight(1, 1, Side.LEFT);
        ChassisModel copy = m.copy();
        m.resize(Side.LEFT, 1, catalog);
        assertNotNull(m.lightAt(2, 1, Side.LEFT));
        assertNotNull(copy.lightAt(1, 1, Side.LEFT), "a snapshot keeps its own lights");
        m.resize(Side.BOTTOM, -1, catalog);
        m.resize(Side.BOTTOM, -1, catalog);
        assertTrue(m.lights.isEmpty(), "a light whose tile falls off the grid goes with it");
    }

    @Test
    void aLayoutWithoutLightsLeavesThemToTheGame() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisModel m = ChassisModel.blank("test", 12, 10, catalog);
        assertNull(m.toDef(catalog).lights);
        assertNull(grid(m, catalog).wallLights);
        assertFalse(Defs.chassis(m.toDef(catalog)).stream().anyMatch(e -> e.key.equals("lights")));
        assertNull(m.autoPlaceLights(catalog));
        assertFalse(m.lights.isEmpty());
        m.lights.clear();
        assertTrue(Defs.chassis(m.toDef(catalog)).stream().anyMatch(e -> e.key.equals("lights") && e.value.equals("[]")),
                "cleared is saved as none, not left to the game");
    }
}
