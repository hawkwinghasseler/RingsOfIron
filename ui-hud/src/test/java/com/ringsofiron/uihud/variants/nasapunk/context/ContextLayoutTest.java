package com.ringsofiron.uihud.variants.nasapunk.context;

import com.ringsofiron.uihud.variants.nasapunk.context.ContextLayout.Zone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The context spec's zones (section 5.1): the table at 1280 x 800, and how they grow on a bigger screen. */
class ContextLayoutTest {
    @Test
    void theZonesAreTheSpecsTableAtTheDesignSize() {
        float w = 1280f, h = 800f;
        assertArrayEquals(new float[] {12f, 38f, 308f, 150f}, ContextLayout.zone(Zone.LTOP, w, h));
        assertArrayEquals(new float[] {12f, 194f, 308f, 358f}, ContextLayout.zone(Zone.LMID, w, h));
        assertArrayEquals(new float[] {12f, 558f, 308f, 230f}, ContextLayout.zone(Zone.LBOT, w, h));
        assertArrayEquals(new float[] {390f, 38f, 500f, 174f}, ContextLayout.zone(Zone.TOPC, w, h));
        assertArrayEquals(new float[] {360f, 220f, 560f, 370f}, ContextLayout.zone(Zone.CENTRE, w, h));
        assertArrayEquals(new float[] {330f, 598f, 620f, 190f}, ContextLayout.zone(Zone.BOTC, w, h));
        assertArrayEquals(new float[] {960f, 38f, 308f, 150f}, ContextLayout.zone(Zone.RTOP, w, h));
        assertArrayEquals(new float[] {960f, 194f, 308f, 358f}, ContextLayout.zone(Zone.RMID, w, h));
        assertArrayEquals(new float[] {960f, 558f, 308f, 230f}, ContextLayout.zone(Zone.RBOT, w, h));
    }

    @Test
    void noZoneSharesAnyAreaWithAnother() {
        for (float[] size : new float[][] {{1280f, 800f}, {1920f, 1080f}, {1440f, 800f}}) {
            Zone[] zones = Zone.values();
            for (int i = 0; i < zones.length; i++) {
                for (int j = i + 1; j < zones.length; j++) {
                    float[] a = ContextLayout.zone(zones[i], size[0], size[1]), b = ContextLayout.zone(zones[j], size[0], size[1]);
                    assertFalse(ContextLayout.overlap(a, b), zones[i] + " and " + zones[j] + " at " + size[0] + " x " + size[1]);
                }
            }
        }
    }

    @Test
    void widerTheColumnsKeepToTheEdgesAndTallerTheBottomRowToTheFoot() {
        float w = 1920f, h = 1080f;
        float[] right = ContextLayout.zone(Zone.RMID, w, h);
        assertEquals(w - 12f, right[0] + right[2], 0.001f);
        float[] bottom = ContextLayout.zone(Zone.BOTC, w, h);
        assertEquals(h - 12f, bottom[1] + bottom[3], 0.001f);
        assertEquals(w / 2f, bottom[0] + bottom[2] / 2f, 0.001f);
        // the middle row takes the extra height, the centre the extra width
        assertEquals(358f + 280f, ContextLayout.zone(Zone.LMID, w, h)[3], 0.001f);
        assertEquals(560f + 640f, ContextLayout.zone(Zone.CENTRE, w, h)[2], 0.001f);
    }
}
