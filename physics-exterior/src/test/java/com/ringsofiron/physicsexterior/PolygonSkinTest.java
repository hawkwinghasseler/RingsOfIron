package com.ringsofiron.physicsexterior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PolygonSkinTest {

    @Test
    void insetMovesEveryEdgeInAndOutsetPutsItBack() {
        // Counter-clockwise triangle and a clockwise square
        float[][] shapes = {{0f, 0f, 4f, 0f, 0f, 3f}, {-1f, -1f, -1f, 1f, 1f, 1f, 1f, -1f}};
        for (float[] v : shapes) {
            float[] in = PolygonSkin.inset(v, 0.1f);
            float[] back = PolygonSkin.outset(in, 0.1f);
            for (int i = 0; i < v.length; i++) assertEquals(v[i], back[i], 1e-4f);
        }
        float[] square = PolygonSkin.inset(shapes[1], 0.1f);
        assertEquals(-0.9f, square[0], 1e-5f);
        assertEquals(0.9f, square[3], 1e-5f);
    }
}
