package com.hawkwing.RingsOfIron.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The deck edge's weights: 0 at the hull's edge, rising inward across the mode's width, 1 deep in the decks. */
class DeckEdgeBlendTest {
    private static final int N = DeckEdgeBlend.SUBDIVISIONS;

    /** A solid block of {@code w} by {@code h} tiles. */
    private static boolean[][] block(int w, int h) {
        boolean[][] on = new boolean[h][w];
        for (boolean[] row : on) java.util.Arrays.fill(row, true);
        return on;
    }

    /** The weight at the corner {@code tilesX}, {@code tilesY} tiles from the grid's bottom-left (half tiles allowed). */
    private static float at(float[] w, int tilesWide, float tilesX, float tilesY) {
        int cw = tilesWide * N + 1;
        return w[Math.round(tilesY * N) * cw + Math.round(tilesX * N)];
    }

    @Test
    void acrossTheOuterTileTheWeightRisesFromTheEdgeToItsInnerSide() {
        float[] w = DeckEdgeBlend.cornerWeights(block(6, 6), 0f, 1f);
        assertEquals(0f, at(w, 6, 0f, 3f), 1e-6f, "the hull's edge");
        assertEquals(0.5f, at(w, 6, 0.5f, 3f), 1e-6f, "halfway across the outer tile");
        assertEquals(1f, at(w, 6, 1f, 3f), 1e-6f, "the outer tile's inner side");
        assertEquals(1f, at(w, 6, 3f, 3f), 1e-6f, "deep in");
    }

    @Test
    void halfAndWideAndInsetSetWhereTheGradientLies() {
        float[] half = DeckEdgeBlend.cornerWeights(block(8, 8), 0f, 0.5f);
        assertEquals(1f, at(half, 8, 0.5f, 4f), 1e-6f, "half: the outer tile's inner half is all inside light");
        float[] wide = DeckEdgeBlend.cornerWeights(block(8, 8), 0f, 2f);
        assertEquals(0.5f, at(wide, 8, 1f, 4f), 1e-6f, "wide: halfway after one tile");
        float[] inset = DeckEdgeBlend.cornerWeights(block(8, 8), 1f, 1f);
        assertEquals(0f, at(inset, 8, 1f, 4f), 1e-6f, "inset: the outer tile all outside light");
        assertEquals(0.5f, at(inset, 8, 1.5f, 4f), 1e-6f);
        assertEquals(1f, at(inset, 8, 2f, 4f), 1e-6f);
    }

    @Test
    void aHoleInTheFootprintIsAnEdgeToo() {
        boolean[][] on = block(7, 7);
        on[3][3] = false;
        float[] w = DeckEdgeBlend.cornerWeights(on, 0f, 1f);
        assertEquals(0f, at(w, 7, 3f, 3f), 1e-6f, "the hole's corner");
        assertEquals(1f, at(w, 7, 2f, 3f), 1e-6f, "a tile from the hole and from the hull's edge");
    }

    @Test
    void aCornerTilesOuterCornersAreAllAtTheEdge() {
        float[] w = DeckEdgeBlend.cornerWeights(block(4, 4), 0f, 1f);
        assertEquals(0f, at(w, 4, 0f, 0f), 1e-6f);
        assertEquals(0f, at(w, 4, 1f, 0f), 1e-6f);
        assertEquals(0f, at(w, 4, 0f, 1f), 1e-6f);
        assertEquals(1f, at(w, 4, 1f, 1f), 1e-6f, "the corner tile's inner corner");
    }
}
