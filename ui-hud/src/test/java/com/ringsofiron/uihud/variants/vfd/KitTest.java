package com.ringsofiron.uihud.variants.vfd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitTest {

    @Test
    void theScopesSectorsAreCentredOnTheirBearings() {
        // 16 sectors of 22.5 degrees, the first centred on the nose
        assertEquals(0, Kit.sector(0f, 16));
        assertEquals(0, Kit.sector(11f, 16));
        assertEquals(1, Kit.sector(12f, 16));
        assertEquals(0, Kit.sector(-11f, 16));
        assertEquals(15, Kit.sector(-12f, 16));
        assertEquals(0, Kit.sector(359f, 16));
        assertEquals(8, Kit.sector(180f, 16));
        assertEquals(4, Kit.sector(450f, 16));
    }

    @Test
    void edgeMarkersInEachOthersWaySpreadAlongTheEdge() {
        // Three labels pointing straight down from the middle of a 1,248-unit edge
        float[] pos = {640f, 630f, 650f, 100f};
        float[] half = {48f, 90f, 60f, 30f};
        Kit.spread(pos, half, 4, 16f, 1264f, 10f);
        // In order along the edge, none overlapping the next, the one out of the way untouched
        assertEquals(100f, pos[0]);
        for (int i = 1; i < 4; i++) assertTrue(pos[i] - half[i] >= pos[i - 1] + half[i - 1] + 10f - 1e-3f, "marker " + i + " overlaps");
        assertEquals(630f, pos[1]);

        // At the end of the edge they move back inside it
        float[] end = {1250f, 1255f};
        float[] w = {40f, 40f};
        Kit.spread(end, w, 2, 16f, 1264f, 10f);
        assertEquals(1224f, end[1]);
        assertEquals(1134f, end[0]);
    }

    @Test
    void theRomHoldsTheCharactersTheHudsPrint() {
        String printed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,:;!?'\"-+/%()[]#&*<>=_|@Δ▶▲♦·°…";
        for (int i = 0; i < printed.length(); i++) {
            char c = printed.charAt(i);
            assertTrue(Rom.has(c), "the ROM has no glyph for '" + c + "'");
        }
    }
}
