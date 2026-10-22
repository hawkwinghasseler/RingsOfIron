package com.ringsofiron.interiorgrid.editor;

import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.RcsThruster;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipPartCatalog;
import com.ringsofiron.interiorgrid.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** RCS blocks in layouts: placed in the editor, saved, and loaded by the game. */
class EditorRcsTest {
    static final Path ASSETS = Paths.get("../assets");

    @TempDir
    Path temp;

    private static ShipGridComponent grid(ChassisModel m, ShipPartCatalog catalog) {
        return ChassisBlueprintLoader.load(InteriorGridData.toBlueprint(m.toDef(catalog), catalog)).grid;
    }

    @Test
    void everyShipBoltsItsBlocksOnHullEdgesThatFaceSpace() {
        EditorProject project = EditorProject.load(ASSETS, null);
        ShipPartCatalog catalog = project.catalog();
        for (EditorProject.Item<ChassisModel> item : project.chassis.values()) {
            ShipGridComponent g = grid(item.value, catalog);
            assertEquals(item.value.rcs.size(), g.rcsThrusters.size, item.id);
            for (RcsThruster r : g.rcsThrusters) {
                assertNull(RcsThruster.placement(g, r.x, r.y, r.side), item.id + " " + r);
                assertTrue(r.works(g));
            }
        }
        assertFalse(project.chassis.get("corvette").value.rcs.isEmpty(), "the corvette has RCS");
    }

    @Test
    void placingTakingOffAndSwappingABlock() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisModel m = ChassisModel.blank("test", 12, 10, catalog);
        assertNotNull(m.toggleRcs(4, 4, Side.TOP, "QUAD", catalog), "not inside the hull");
        assertNotNull(m.toggleRcs(-1, 0, Side.TOP, "QUAD", catalog), "not off the grid");
        assertTrue(m.rcs.isEmpty());

        int top = topHullRow(m, catalog);
        assertNull(m.toggleRcs(4, top, Side.TOP, "QUAD", catalog));
        assertEquals("QUAD", m.rcsAt(4, top, Side.TOP).kind);
        assertNull(m.toggleRcs(4, top, Side.TOP, "LINEAR", catalog), "one of another kind swaps it");
        assertEquals(1, m.rcs.size());
        assertEquals("LINEAR", m.rcsAt(4, top, Side.TOP).kind);
        assertEquals(1, grid(m, catalog).rcsThrusters.size);
        assertNull(m.toggleRcs(4, top, Side.TOP, "LINEAR", catalog));
        assertTrue(m.rcs.isEmpty(), "the same kind again takes it off");
    }

    @Test
    void aBlockPlacedByHandSavesAndLoadsBack() throws IOException {
        Path data = temp.resolve("assets/data");
        Files.createDirectories(data);
        try (Stream<Path> files = Files.list(ASSETS.resolve("data"))) {
            for (Path f : (Iterable<Path>) files::iterator) Files.copy(f, data.resolve(f.getFileName()));
        }
        Path assets = temp.resolve("assets");
        EditorProject project = EditorProject.load(assets, null);
        EditorProject.Item<ChassisModel> corvette = project.chassis.get("corvette");
        int before = corvette.value.rcs.size();
        int top = topHullRow(corvette.value, project.catalog());
        assertNull(corvette.value.toggleRcs(5, top, Side.TOP, "LINEAR", project.catalog()));
        corvette.dirty = true;
        project.save();

        EditorProject again = EditorProject.load(assets, null);
        ChassisModel back = again.chassis.get("corvette").value;
        assertEquals(before + 1, back.rcs.size());
        assertNotNull(back.rcsAt(5, top, Side.TOP));
        assertTrue(grid(back, again.catalog()).rcsThrusters.contains(new RcsThruster(5, top, Side.TOP, RcsThruster.Kind.LINEAR), false));
        assertTrue(back.rcs.stream().anyMatch(r -> r.thrust > 0), "the tuned blocks keep their thrust");
    }

    @Test
    void blocksMoveWithTheGrid() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisModel m = ChassisModel.blank("test", 12, 10, catalog);
        int top = topHullRow(m, catalog);
        m.toggleRcs(4, top, Side.TOP, "QUAD", catalog);
        ChassisModel copy = m.copy();
        m.resize(Side.LEFT, 1, catalog);
        assertNotNull(m.rcsAt(5, top, Side.TOP));
        assertNotNull(copy.rcsAt(4, top, Side.TOP), "a snapshot keeps its own blocks");
    }

    /** The row of the hull's top edge at x = 5. */
    private static int topHullRow(ChassisModel m, ShipPartCatalog catalog) {
        ShipGridComponent g = grid(m, catalog);
        for (int y = g.height - 1; y >= 0; y--) {
            if (RcsThruster.placement(g, 5, y, Side.TOP) == null) return y;
        }
        throw new AssertionError("no top hull edge");
    }
}
