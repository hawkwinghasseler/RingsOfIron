package com.ringsofiron.interiorgrid.editor;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.HullPiece;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipPartCatalog;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.WeightClass;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The ship editor's hull piece tool (flip, and replacing what a new piece overlaps) and a layout
 * edited every way the editor can, saved and loaded back.
 */
class EditorHullPiecesTest {
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

    /**
     * The corners of a wedge's triangle in its footprint's own tiles: legs along the left and
     * bottom edges at rotation 0, turned {@code rotation} quarter turns counter-clockwise.
     */
    private static Set<String> corners(int w, int h, int rotation) {
        List<int[]> pts = new ArrayList<>(List.of(new int[]{0, 0}, new int[]{w, 0}, new int[]{0, h}));
        for (int r = 0; r < rotation; r++) {
            for (int[] p : pts) {
                int x = p[0];
                p[0] = -p[1];
                p[1] = x;
            }
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        for (int[] p : pts) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
        }
        Set<String> out = new HashSet<>();
        for (int[] p : pts) out.add((p[0] - minX) + "," + (p[1] - minY));
        return out;
    }

    private static Set<String> mirrored(Set<String> corners, int footprintWidth) {
        Set<String> out = new HashSet<>();
        for (String c : corners) {
            String[] xy = c.split(",");
            out.add((footprintWidth - Integer.parseInt(xy[0])) + "," + xy[1]);
        }
        return out;
    }

    /**
     * The editor's flip swaps width and height and steps the rotation the other way
     * ({@code rotation ^ 1}), so the data needs no flip field. That is only right if it gives
     * exactly the mirror image, for every size and every turn.
     */
    @Test
    void flippingAWedgeIsItsMirrorImage() {
        for (int[] size : new int[][]{{1, 2}, {2, 1}, {1, 3}, {2, 3}, {1, 1}}) {
            for (int r = 0; r < 4; r++) {
                int w = size[0], h = size[1];
                HullPiece piece = new HullPiece(HullPiece.Shape.WEDGE, 0, 0, w, h, r, WeightClass.MEDIUM, "hull_steel");
                HullPiece flipped = new HullPiece(HullPiece.Shape.WEDGE, 0, 0, h, w, r ^ 1, WeightClass.MEDIUM, "hull_steel");
                String what = w + "x" + h + " at " + r;
                assertEquals(piece.width(), flipped.width(), what + ": same footprint");
                assertEquals(piece.height(), flipped.height(), what);
                assertEquals(mirrored(corners(w, h, r), piece.width()), corners(h, w, r ^ 1), what);
                // Its sloped face looks the mirrored way too.
                Vector2 n = piece.faceNormal(), m = flipped.faceNormal();
                assertEquals(-n.x, m.x, 1e-5f, what);
                assertEquals(n.y, m.y, 1e-5f, what);
            }
        }
    }

    /** A new piece only replaces the pieces it really overlaps, turned or not. */
    @Test
    void onlyOverlappedPiecesAreReplaced() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisModel m = ChassisModel.blank("test", 12, 10, catalog);
        ChassisDef.HullPiece a = m.addHullPiece("WEDGE", 0, 0, 1, 2, 0, "MEDIUM");     // (0,0)-(0,1)
        ChassisDef.HullPiece b = m.addHullPiece("WEDGE", 1, 0, 1, 2, 1, "MEDIUM");     // turned: (1,0)-(2,0)
        ChassisDef.HullPiece c = m.addHullPiece("BLOCK", 5, 5, 2, 2, 0, "HEAVY");
        assertEquals(3, m.hullPieces.size());

        // Touching b's right edge but not overlapping it
        m.addHullPiece("BLOCK", 3, 0, 1, 1, 0, "MEDIUM");
        assertTrue(m.hullPieces.contains(b), "a neighbour is kept");
        // Over the second tile of the turned wedge only: b goes, a stays
        m.addHullPiece("BLOCK", 2, 0, 1, 1, 0, "MEDIUM");
        assertFalse(m.hullPieces.contains(b), "turned, it covers (2,0)");
        assertTrue(m.hullPieces.contains(a));
        // One big block over two pieces at once
        m.addHullPiece("BLOCK", 0, 0, 3, 3, 0, "LIGHT");
        assertFalse(m.hullPieces.contains(a));
        assertTrue(m.hullPieces.contains(c));
        assertNotNull(m.hullPieceAt(1, 1));
        assertEquals("LIGHT", m.hullPieceAt(1, 1).weightClass);
        assertEquals(m.hullPieceAt(0, 0), m.hullPieceAt(2, 2), "one piece over all nine tiles");
    }

    @Test
    void aPieceThatWouldLeaveTheGridIsNotPlacedAndChangesNothing() {
        ShipPartCatalog catalog = ShipPartCatalog.builtIn();
        ChassisModel m = ChassisModel.blank("test", 6, 4, catalog);
        ChassisDef.HullPiece kept = m.addHullPiece("WEDGE", 5, 2, 1, 2, 0, "MEDIUM");
        assertNotNull(kept);
        assertNull(m.addHullPiece("WEDGE", 5, 2, 1, 2, 1, "MEDIUM"), "turned it is two wide at the right edge");
        assertNull(m.addHullPiece("WEDGE", -1, 0, 1, 2, 0, "MEDIUM"), "off the left");
        assertNull(m.addHullPiece("WEDGE", 0, 3, 1, 2, 0, "MEDIUM"), "off the top");
        assertEquals(List.of(kept), m.hullPieces, "a refused piece replaces nothing");
    }

    /** The game builds a flipped wedge as the mirror of the original, tile for tile. */
    @Test
    void theGameBuildsAFlippedWedgeOnTheSameTiles() {
        EditorProject project = EditorProject.load(ASSETS, null);
        ShipPartCatalog catalog = project.catalog();
        ChassisModel corvette = project.chassis.get("corvette").value;
        corvette.resize(Side.RIGHT, 2, catalog);
        int x = corvette.width() - 2;
        assertNotNull(corvette.addHullPiece("WEDGE", x, 0, 1, 2, 1, "MEDIUM"));
        ShipGridComponent before = grid(corvette, catalog);
        assertNotNull(corvette.addHullPiece("WEDGE", x, 0, 2, 1, 0, "MEDIUM"));
        assertNull(project.check(corvette).error);
        ShipGridComponent after = grid(corvette, catalog);
        HullPiece p = after.getTile(x, 0).piece;
        assertNotNull(p);
        assertEquals(before.getTile(x, 0).piece.width(), p.width());
        assertEquals(before.getTile(x, 0).piece.height(), p.height());
        assertSame(p, after.getTile(x + 1, 0).piece, "both tiles are the one piece");
        assertEquals(-before.getTile(x, 0).piece.faceNormal().x, p.faceNormal().x, 1e-5f);
    }

    /** A layout edited every way the editor can comes back from disk the same, and saves again the same. */
    @Test
    void anEditedLayoutSavesAndLoadsBackExactly() throws IOException {
        Path assets = assetsCopy();
        EditorProject project = EditorProject.load(assets, null);
        ShipPartCatalog catalog = project.catalog();
        ChassisModel m = ChassisModel.blank("roundtrip", 12, 8, catalog);
        m.name = "Round Trip";
        m.place("reactor", MountFootprint.MountSize.M, 1, 1, 0, catalog);
        m.place("life_support", MountFootprint.MountSize.S, 5, 1, 1, catalog);
        m.place("docking_port", MountFootprint.MountSize.S, 4, 7, 0, catalog);
        m.mounts.get(m.mounts.size() - 1).active = true;
        m.toggleEdgeWall(7, 3, Side.RIGHT, "LIGHT", true);
        m.toggleEdgeWall(7, 4, Side.RIGHT, "LIGHT", false);
        m.toggleEdgeConduit(3, 3, Side.TOP);
        m.addCovering("CARPET", 8, 2, 10, 5);
        assertNull(m.autoPlaceLights(catalog));
        m.toggleLight(9, 5, Side.TOP);
        m.resize(Side.RIGHT, 1, catalog);
        assertNotNull(m.addHullPiece("WEDGE", 12, 0, 1, 2, 0, "MEDIUM"));
        assertNotNull(m.addHullPiece("WEDGE", 12, 6, 2, 1, 1, "MEDIUM"), "a flipped wedge");
        assertNull(project.check(m).error, project.check(m).error);
        project.addChassis(m);
        project.save();
        Path file = project.chassis.get("roundtrip").file;
        String written = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        EditorProject again = EditorProject.load(assets, null);
        assertTrue(again.problems.isEmpty(), again.problems.toString());
        ChassisModel back = again.chassis.get("roundtrip").value;
        assertEquals("Round Trip", back.name);
        assertEquals(m.width(), back.width());
        assertEquals(m.height(), back.height());
        for (int y = 0; y < m.height(); y++) assertArrayEquals(m.tiles[y], back.tiles[y], "row " + y);
        assertEquals(describe(m.toDef(catalog)), describe(back.toDef(again.catalog())));

        // Saving the loaded layout again writes the same file.
        again.chassis.get("roundtrip").dirty = true;
        again.save();
        assertEquals(written, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    /** Everything about a chassis def the game reads, as text to compare. */
    private static String describe(ChassisDef d) {
        StringBuilder s = new StringBuilder();
        s.append(d.id).append(' ').append(d.name).append(' ').append(d.station).append('\n');
        for (String row : d.rows) s.append(row).append('\n');
        for (ChassisDef.HullPiece p : d.hullPieces) {
            s.append("piece ").append(p.shape).append(' ').append(p.x).append(',').append(p.y).append(' ')
                    .append(p.width).append('x').append(p.height).append(" r").append(p.rotation).append(' ')
                    .append(p.weightClass).append(' ').append(p.material).append('\n');
        }
        for (ChassisDef.Mount mt : d.mounts) s.append("mount ").append(mt.type).append(' ').append(mt.component).append(' ').append(mt.size)
                .append(' ').append(mt.x).append(',').append(mt.y).append(" r").append(mt.rotation).append('\n');
        for (ChassisDef.Port p : d.ports) s.append("port ").append(p.x).append(',').append(p.y).append(' ').append(p.active).append('\n');
        for (ChassisDef.EdgeWall w : d.edgeWalls) s.append("edge ").append(w.x).append(',').append(w.y).append(' ').append(w.side)
                .append(' ').append(w.bulkhead).append('\n');
        for (ChassisDef.EdgeConduit c : d.edgeConduits) s.append("conduit ").append(c.x).append(',').append(c.y).append(' ').append(c.side).append('\n');
        for (ChassisDef.Covering c : d.coverings) s.append("cover ").append(c.covering).append(' ').append(c.x).append(',').append(c.y)
                .append(' ').append(c.width).append('x').append(c.height).append('\n');
        for (ChassisDef.Light l : d.lights) s.append("light ").append(l.x).append(',').append(l.y).append(' ').append(l.side).append('\n');
        return s.toString();
    }
}
