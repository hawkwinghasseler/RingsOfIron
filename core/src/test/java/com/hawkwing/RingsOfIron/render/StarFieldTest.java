package com.hawkwing.RingsOfIron.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarFieldTest {
    @Test
    void aboutHalfTheCellsHoldAStarAndMostAreFaint() {
        int stars = 0, faint = 0, bright = 0, cells = 0;
        for (int i = -100; i < 100; i++) {
            for (int j = -100; j < 100; j++) {
                cells++;
                float b = StarField.brightness(i, j);
                assertTrue(b >= 0f && b <= 1f);
                if (b <= 0f) continue;
                stars++;
                if (b < 0.5f) faint++;
                if (b > 0.85f) bright++;
            }
        }
        assertEquals(StarField.FILL, stars / (float) cells, 0.02f);
        assertTrue(faint > stars * 0.6f, "most stars are faint: " + faint + " of " + stars);
        assertTrue(bright > 0 && bright < stars * 0.1f, "a few are bright: " + bright + " of " + stars);
    }

    @Test
    void theSameCellAlwaysHasTheSameStar() {
        assertEquals(StarField.brightness(12, -7), StarField.brightness(12, -7));
    }
}
