package com.ringsofiron.interiorgrid.editor;

import static org.junit.jupiter.api.Assertions.*;

import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipPartCatalog;
import com.ringsofiron.interiorgrid.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Conduits along tile edges in layouts: placed in the editor, saved, and loaded by the game. */
class EditorConduitsTest {

    @TempDir
    Path temp;

    private Path assetsCopy() throws IOException {
        Path data = temp.resolve("assets/data");
        Files.createDirectories(data);
        try (Stream<Path> files = Files.list(EditorLightsTest.ASSETS.resolve("data"))) {
            for (Path f : (Iterable<Path>) files::iterator) Files.copy(f, data.resolve(f.getFileName()));
        }
        return temp.resolve("assets");
    }

    private static ShipGridComponent grid(ChassisModel m, ShipPartCatalog catalog) {
        return ChassisBlueprintLoader.load(InteriorGridData.toBlueprint(m.toDef(catalog), catalog)).grid;
    }

    @Test
    void anEdgeConduitIsTheSameFromEitherTile() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisModel m = ChassisModel.blank("test", 8, 6, catalog);
        assertTrue(m.toggleEdgeConduit(2, 2, Side.RIGHT));
        assertNotNull(m.edgeConduitAt(3, 2, Side.LEFT));
        assertTrue(m.toggleEdgeConduit(3, 2, Side.LEFT));
        assertTrue(m.edgeConduits.isEmpty(), "clicking it from the other tile takes it up");
        assertFalse(m.toggleEdgeConduit(-1, 0, Side.TOP), "not off the grid");
    }

    @Test
    void edgeConduitsRunInsideWallsAndLoad() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisModel m = ChassisModel.blank("test", 8, 6, catalog);
        // Along the hull's inner face, and inside a thin wall
        m.toggleEdgeConduit(0, 2, Side.RIGHT);
        m.toggleEdgeWall(4, 2, Side.LEFT, "MEDIUM", false);
        m.toggleEdgeConduit(4, 2, Side.LEFT);
        ShipGridComponent g = grid(m, catalog);
        assertNotNull(g.getConduit(1, 2, Side.LEFT));
        assertNotNull(g.getConduit(4, 2, Side.LEFT));
        assertNotNull(g.getEdge(4, 2, Side.LEFT), "the wall is still there");
        assertTrue(Defs.chassis(m.toDef(catalog)).stream().anyMatch(e -> e.key.equals("edge_conduits")));
    }

    @Test
    void edgeConduitsMoveWithTheGridAndUndo() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisModel m = ChassisModel.blank("test", 8, 6, catalog);
        m.toggleEdgeConduit(1, 1, Side.BOTTOM);
        ChassisModel copy = m.copy();
        m.resize(Side.LEFT, 1, catalog);
        assertNotNull(m.edgeConduitAt(2, 1, Side.BOTTOM));
        assertNotNull(copy.edgeConduitAt(1, 1, Side.BOTTOM), "a snapshot keeps its own conduits");
        m.resize(Side.BOTTOM, -1, catalog);
        m.resize(Side.BOTTOM, -1, catalog);
        assertTrue(m.edgeConduits.isEmpty(), "a conduit whose tile falls off the grid goes with it");
    }

    @Test
    void anEdgeConduitSavesAndLoadsBack() throws IOException {
        Path assets = assetsCopy();
        EditorProject project = EditorProject.load(assets, null);
        EditorProject.Item<ChassisModel> corvette = project.chassis.get("corvette");
        assertTrue(corvette.value.toggleEdgeConduit(6, 4, Side.TOP));
        corvette.dirty = true;
        project.save();

        EditorProject again = EditorProject.load(assets, null);
        ChassisModel back = again.chassis.get("corvette").value;
        assertNotNull(back.edgeConduitAt(6, 4, Side.TOP));
        assertNotNull(grid(back, again.catalog()).getConduit(6, 4, Side.TOP));
    }
}
