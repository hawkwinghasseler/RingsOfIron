package com.hawkwing.RingsOfIron.art;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hull art's collision layer, read straight from the SVG into body-frame convex polygons. */
class CollisionLayerTest {
    private static final Path ASSETS = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
    private static final float EPS = 1e-4f;

    @TempDir
    Path dir;

    private Path svg(String layers) throws IOException {
        Path file = dir.resolve("hull.svg");
        Files.write(file, ("<svg xmlns=\"http://www.w3.org/2000/svg\" "
                + "xmlns:inkscape=\"http://www.inkscape.org/namespaces/inkscape\" viewBox=\"-256 -256 512 512\">"
                + "<g id=\"base\"><rect x=\"-64\" y=\"-64\" width=\"128\" height=\"128\"/></g>"
                + layers + "</svg>").getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static float area(List<float[]> pieces) {
        float a = 0f;
        for (float[] p : pieces) {
            assertTrue(p.length >= 6 && p.length <= 2 * CollisionLayer.MAX_VERTICES, "3 to 8 vertices");
            assertTrue(CollisionLayer.isConvex(p), "convex and counter-clockwise");
            a += CollisionLayer.signedArea(p);
        }
        return a;
    }

    @Test
    void noLayerOrNoFileIsNull() throws IOException {
        assertNull(CollisionLayer.read(svg("")));
        assertNull(CollisionLayer.read(dir.resolve("missing.svg")));
    }

    @Test
    void aPolygonComesOutInBodyMetersCounterClockwise() throws IOException {
        // Counter-clockwise on screen, so clockwise once y is flipped: it comes out turned round
        List<float[]> pieces = CollisionLayer.read(svg(
                "<g id=\"collision\"><polygon points=\"-64,64 128,64 128,-64 -64,-64\"/></g>"));
        assertEquals(1, pieces.size());
        assertEquals(6f, area(pieces), EPS);
        float[] p = pieces.get(0);
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < p.length; i += 2) {
            minX = Math.min(minX, p[i]);
            maxX = Math.max(maxX, p[i]);
            maxY = Math.max(maxY, p[i + 1]);
        }
        assertEquals(-1f, minX, EPS);
        assertEquals(2f, maxX, EPS);
        assertEquals(1f, maxY, EPS);
    }

    @Test
    void rectsPathsAndTransformsAreRead() throws IOException {
        List<float[]> pieces = CollisionLayer.read(svg(
                "<g inkscape:groupmode=\"layer\" inkscape:label=\"collision\" transform=\"translate(64,0)\">"
                        + "<rect x=\"0\" y=\"0\" width=\"64\" height=\"64\"/>"
                        + "<path d=\"m 0,-64 h 64 v -64 H 0 z\"/></g>"));
        assertEquals(2, pieces.size());
        // The rect: x 1..2 m, y -1..0 m (SVG y down)
        assertArrayEquals(new float[]{1f, -1f, 2f, -1f, 2f, 0f, 1f, 0f}, sorted(pieces.get(0)), EPS);
        assertEquals(2f, area(pieces), EPS);
    }

    @Test
    void aConcaveShapeIsCutIntoConvexPieces() throws IOException {
        // An L of three tiles
        List<float[]> pieces = CollisionLayer.read(svg(
                "<g id=\"collision\"><polygon points=\"0,0 128,0 128,-64 64,-64 64,-128 0,-128\"/></g>"));
        assertTrue(pieces.size() >= 2);
        assertEquals(3f, area(pieces), EPS);
    }

    @Test
    void aConvexShapeWithManyCornersIsSplitInAFan() throws IOException {
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            double a = 2 * Math.PI * i / 20;
            points.append((float) (128 * Math.cos(a))).append(',').append((float) (128 * Math.sin(a))).append(' ');
        }
        List<float[]> pieces = CollisionLayer.read(svg("<g id=\"collision\"><polygon points=\"" + points + "\"/></g>"));
        assertTrue(pieces.size() >= 3);
        assertEquals(0.5f * 20 * 4f * (float) Math.sin(2 * Math.PI / 20), area(pieces), 1e-3f);
    }

    @Test
    void aCurveIsRefused() throws IOException {
        Path file = svg("<g id=\"collision\"><path d=\"M 0,0 C 10,10 20,10 30,0 Z\"/></g>");
        assertThrows(IllegalArgumentException.class, () -> CollisionLayer.read(file));
    }

    @Test
    void theCorvettesLayerHasItsBow() {
        List<float[]> pieces = CollisionLayer.read(ASSETS.resolve("art/hulls/corvette.svg"));
        assertNotNull(pieces);
        assertEquals(1, pieces.size());
        assertArrayEquals(new float[]{-9f, -3f, 5f, -3f, 6.5f, 0f, 5f, 3f, -9f, 3f}, pieces.get(0), EPS);
        assertNull(CollisionLayer.read(ASSETS.resolve("art/hulls/freighter.svg")), "the freighter collides on its tiles");
    }

    /** A box's four corners, starting from its bottom-left and going counter-clockwise. */
    private static float[] sorted(float[] box) {
        int start = 0;
        for (int i = 2; i < box.length; i += 2) {
            if (box[i] + box[i + 1] < box[start] + box[start + 1]) start = i;
        }
        float[] out = new float[box.length];
        for (int i = 0; i < box.length; i++) out[i] = box[(start + i) % box.length];
        return out;
    }
}
