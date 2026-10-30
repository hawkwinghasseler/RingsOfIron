package com.hawkwing.RingsOfIron.render;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.interiorgrid.HullPiece;
import com.ringsofiron.interiorgrid.WeightClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The deck edge's weights: 0 at the hull's edge, rising inward across the outermost tile, 1 deep in the decks. */
class DeckEdgeBlendTest {
    private static final int N = DeckEdgeBlend.SUBDIVISIONS;
    private DeckEdgeBlend.Fade was;

    /** The weights below are the sharp fade's, whatever the game's default. */
    @BeforeEach
    void sharpFade() {
        was = DeckEdgeBlend.fade;
        DeckEdgeBlend.fade = DeckEdgeBlend.Fade.LINEAR;
    }

    @AfterEach
    void restoreFade() {
        DeckEdgeBlend.fade = was;
    }

    /** A solid block of {@code w} by {@code h} tiles. */
    private static boolean[][] block(int w, int h) {
        boolean[][] on = new boolean[h][w];
        for (boolean[] row : on) java.util.Arrays.fill(row, true);
        return on;
    }

    private static float[] weights(boolean[][] on) {
        return new DeckEdgeBlend.Outline(on, new IntArray()).cornerWeights();
    }

    /** The weight at the corner {@code tilesX}, {@code tilesY} tiles from the grid's bottom-left (half tiles allowed). */
    private static float at(float[] w, int tilesWide, float tilesX, float tilesY) {
        int cw = tilesWide * N + 1;
        return w[Math.round(tilesY * N) * cw + Math.round(tilesX * N)];
    }

    private static HullPiece wedge(int x, int y, int w, int h, int rotation) {
        return new HullPiece(HullPiece.Shape.WEDGE, x, y, w, h, rotation, WeightClass.MEDIUM, "hull_steel");
    }

    @Test
    void acrossTheOuterTileTheWeightRisesFromTheEdgeToItsInnerSide() {
        float[] w = weights(block(6, 6));
        assertEquals(0f, at(w, 6, 0f, 3f), 1e-6f, "the hull's edge");
        assertEquals(0.5f, at(w, 6, 0.5f, 3f), 1e-6f, "halfway across the outer tile");
        assertEquals(1f, at(w, 6, 1f, 3f), 1e-6f, "the outer tile's inner side");
        assertEquals(1f, at(w, 6, 3f, 3f), 1e-6f, "deep in");
    }

    @Test
    void aHoleInTheFootprintIsAnEdgeToo() {
        boolean[][] on = block(7, 7);
        on[3][3] = false;
        float[] w = weights(on);
        assertEquals(0f, at(w, 7, 3f, 3f), 1e-6f, "the hole's corner");
        assertEquals(1f, at(w, 7, 2f, 3f), 1e-6f, "a tile from the hole and from the hull's edge");
        assertEquals(1f, at(w, 7, 2.5f, 2.5f), 1e-6f, "across from the hole's corner, half a tile across and half up");
        assertEquals(0.5f, at(w, 7, 3f, 2.5f), 1e-6f, "half a tile below the hole's corner");
    }

    @Test
    void aCornerTilesOuterCornersAreAllAtTheEdge() {
        float[] w = weights(block(4, 4));
        assertEquals(0f, at(w, 4, 0f, 0f), 1e-6f);
        assertEquals(0f, at(w, 4, 1f, 0f), 1e-6f);
        assertEquals(0f, at(w, 4, 0f, 1f), 1e-6f);
        assertEquals(1f, at(w, 4, 1f, 1f), 1e-6f, "the corner tile's inner corner");
    }

    @Test
    void aWedgesCornersRunRoundItWithTheSlopeFacingOut() {
        for (int[] size : new int[][]{{1, 1}, {1, 2}, {2, 1}}) {
            for (int r = 0; r < 4; r++) {
                HullPiece p = wedge(3, 5, size[0], size[1], r);
                int[] c = DeckEdgeBlend.wedge(p);
                // Counter-clockwise, its legs along the footprint's sides
                float area = (c[2] - c[0]) * (c[5] - c[1]) - (c[3] - c[1]) * (c[4] - c[0]);
                assertEquals(p.width() * p.height(), area, 1e-6f, "twice its area, turning left");
                assertTrue(c[0] == c[2] || c[1] == c[3], "a leg along a side");
                assertTrue(c[0] == c[4] || c[1] == c[5], "the other leg along a side");
                // The slope, b to c, faces out to its right: the way the collision shape faces
                Vector2 out = new Vector2(c[5] - c[3], -(c[4] - c[2])).nor();
                Vector2 face = p.faceNormal();
                assertEquals(face.x, out.x, 1e-5f, "rotation " + r + " " + size[0] + "x" + size[1]);
                assertEquals(face.y, out.y, 1e-5f, "rotation " + r + " " + size[0] + "x" + size[1]);
            }
        }
    }

    @Test
    void aSlopeIsAnEdgeAndItsLegsAgainstTheTilesAreNot() {
        // A block bevelled at its top right corner, the slope facing up and right
        boolean[][] on = block(3, 3);
        on[2][2] = false;
        IntArray wedges = new IntArray(DeckEdgeBlend.wedge(wedge(2, 2, 1, 1, 0)));
        DeckEdgeBlend.Outline edge = new DeckEdgeBlend.Outline(on, wedges);
        assertEquals(0f, edge.distance(2.5f, 2.5f), 1e-6f, "on the slope");
        float[] w = edge.cornerWeights();
        assertEquals(1f, at(w, 3, 2f, 2f), 1e-6f, "the corner behind the slope: the fade ends there");
        assertEquals(0.5f, at(w, 3, 2f, 2.5f), 1e-6f, "on a leg, nearer the slope than the hull's top");
    }

    @Test
    void aWallBlockTouchingASlopeAtOneCornerIsLitFromInsideFromItsMiddleOn() {
        // A staircase of slopes facing down and right, the wall block (1, 1) behind them touching
        // the edge at its bottom right corner alone
        boolean[][] on = block(4, 4);
        on[0][1] = on[1][2] = false;
        on[0][2] = on[0][3] = on[1][3] = false;
        IntArray wedges = new IntArray(DeckEdgeBlend.wedge(wedge(1, 0, 1, 1, 3)));
        wedges.addAll(DeckEdgeBlend.wedge(wedge(2, 1, 1, 1, 3)));
        DeckEdgeBlend.Outline edge = new DeckEdgeBlend.Outline(on, wedges);
        float[] w = edge.cornerWeights();
        assertEquals(0f, at(w, 4, 2f, 1f), 1e-6f, "the corner on the edge");
        assertEquals(1f, at(w, 4, 1.5f, 1.5f), 1e-6f, "its middle, half its diagonal in");
        assertEquals(1f, at(w, 4, 1f, 2f), 1e-6f, "its far corner");
    }

    @Test
    void theDistanceRunsAcrossPlusUp() {
        assertEquals(1f, DeckEdgeBlend.toSegment(0f, 0f, 1f, -1f, 1f, 1f), 1e-6f, "square to a side");
        assertEquals(1f, DeckEdgeBlend.toSegment(0f, 0f, 1f, 0f, 0f, 1f), 1e-6f, "to a slope: half a tile's diagonal");
        assertEquals(2f, DeckEdgeBlend.toSegment(0f, 0f, 1f, 1f, 2f, 1f), 1e-6f, "to an end, one across and one up");
    }

    @Test
    void softCornersKeepWhereTheFadeStartsAndEnds() {
        // A bevelled block with a notch: convex and concave corners, flat and sloped
        boolean[][] on = block(8, 6);
        on[0][0] = on[5][7] = false;
        on[3][0] = on[3][1] = false;
        IntArray wedges = new IntArray(DeckEdgeBlend.wedge(wedge(0, 0, 1, 1, 2)));
        wedges.addAll(DeckEdgeBlend.wedge(wedge(7, 5, 1, 1, 0)));
        DeckEdgeBlend.Outline edge = new DeckEdgeBlend.Outline(on, wedges);
        float[] linear = edge.cornerWeights();
        DeckEdgeBlend.fade = DeckEdgeBlend.Fade.SOFT;
        float[] soft = edge.cornerWeights();
        boolean rounded = false;
        int cw = 8 * N + 1;
        for (int i = 0; i < linear.length; i++) {
            // Where the whole tiles are drawn (the slopes' far sides are off the footprint)
            int vx = i % cw, vy = i / cw;
            boolean drawn = false;
            for (int ty = 0; ty < 6; ty++) {
                for (int tx = 0; tx < 8; tx++) {
                    drawn |= on[ty][tx] && vx >= tx * N && vx <= (tx + 1) * N && vy >= ty * N && vy <= (ty + 1) * N;
                }
            }
            if (!drawn) continue;
            assertEquals(linear[i] >= 1f, soft[i] >= 1f, "all inside at corner " + i);
            assertEquals(linear[i] <= 0f, soft[i] <= 0f, "all outside at corner " + i);
            rounded |= Math.abs(linear[i] - soft[i]) > 0.01f;
        }
        assertTrue(rounded, "rounded somewhere between");
    }

    @Test
    void twoWedgesBackToBackMakeNoEdgeBetweenThem() {
        boolean[][] on = block(3, 3);
        on[1][1] = false;
        IntArray wedges = new IntArray(DeckEdgeBlend.wedge(wedge(1, 1, 1, 1, 0)));
        wedges.addAll(DeckEdgeBlend.wedge(wedge(1, 1, 1, 1, 2)));
        DeckEdgeBlend.Outline edge = new DeckEdgeBlend.Outline(on, wedges);
        assertEquals(1.5f, edge.distance(1.5f, 1.5f), 1e-6f, "as far in as a whole tile's middle");
    }
}
