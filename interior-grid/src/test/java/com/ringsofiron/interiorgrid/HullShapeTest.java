package com.ringsofiron.interiorgrid;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HullShapeTest {
    private static final float EPS = 1e-5f;

    private static ShipGridComponent grid(ChassisBlueprint bp) {
        return ChassisBlueprintLoader.load(bp).grid;
    }

    private static float area(List<float[]> pieces) {
        float a = 0f;
        for (float[] p : pieces) {
            float pa = 0f;
            for (int i = 0; i < p.length; i += 2) {
                int j = (i + 2) % p.length;
                pa += 0.5f * (p[i] * p[j + 1] - p[j] * p[i + 1]);
            }
            assertTrue(pa > 0f, "every piece is counter-clockwise");
            a += pa;
        }
        return a;
    }

    @Test
    void aSolidLayoutIsOneBoxAtItsOrigin() {
        ShipGridComponent g = grid(new ChassisBlueprint(new String[]{
                "####",
                "#..#",
                "####",
        }).origin(-2f, -1.5f));
        List<float[]> pieces = HullShape.pieces(g);
        assertEquals(1, pieces.size());
        assertArrayEquals(new float[]{-2f, -1.5f, 2f, -1.5f, 2f, 1.5f, -2f, 1.5f}, pieces.get(0), EPS);
    }

    @Test
    void voidTilesAreLeftOut() {
        ShipGridComponent g = grid(new ChassisBlueprint(new String[]{
                " ###",
                "####",
        }));
        List<float[]> pieces = HullShape.pieces(g);
        assertEquals(2, pieces.size());
        assertEquals(7f, area(pieces), EPS);
        assertArrayEquals(new float[]{0f, 0f, 4f, 0f, 4f, 1f, 0f, 1f}, pieces.get(0), EPS);
        assertArrayEquals(new float[]{1f, 1f, 4f, 1f, 4f, 2f, 1f, 2f}, pieces.get(1), EPS);
    }

    @Test
    void aWedgeIsItsTriangle() {
        ShipGridComponent g = grid(new ChassisBlueprint(new String[]{
                "##",
                "##",
        }).hullPiece(new HullPiece(HullPiece.Shape.WEDGE, 0, 0, 1, 2, 0, WeightClass.MEDIUM, MaterialTable.DEFAULT_MATERIAL)));
        List<float[]> pieces = HullShape.pieces(g);
        assertEquals(2, pieces.size());
        assertArrayEquals(new float[]{1f, 0f, 2f, 0f, 2f, 2f, 1f, 2f}, pieces.get(0), EPS);
        // Legs on the left and bottom edges, the slope facing +x/+y
        assertArrayEquals(new float[]{0f, 0f, 1f, 0f, 0f, 2f}, pieces.get(1), EPS);
        assertEquals(3f, area(pieces), EPS);
    }

    @Test
    void aTurnedWedgeTurnsCounterClockwise() {
        ShipGridComponent g = grid(new ChassisBlueprint(new String[]{
                "##",
        }).hullPiece(new HullPiece(HullPiece.Shape.WEDGE, 0, 0, 1, 2, 1, WeightClass.MEDIUM, MaterialTable.DEFAULT_MATERIAL)));
        List<float[]> pieces = HullShape.pieces(g);
        assertEquals(1, pieces.size());
        // Legs along the bottom and right edges, the slope facing up and to the left like its face normal
        assertArrayEquals(new float[]{2f, 0f, 2f, 1f, 0f, 0f}, pieces.get(0), EPS);
        assertEquals(1f, area(pieces), EPS);
    }

    @Test
    void aBlockHullPieceIsPartOfTheBoxes() {
        ShipGridComponent g = grid(new ChassisBlueprint(new String[]{
                "###",
        }).hullPiece(new HullPiece(HullPiece.Shape.BLOCK, 1, 0, 2, 1, 0, WeightClass.HEAVY, MaterialTable.DEFAULT_MATERIAL)));
        List<float[]> pieces = HullShape.pieces(g);
        assertEquals(1, pieces.size());
        assertEquals(3f, area(pieces), EPS);
    }
}
