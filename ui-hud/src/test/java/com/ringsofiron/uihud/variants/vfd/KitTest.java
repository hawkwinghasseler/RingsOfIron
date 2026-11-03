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
    void theRomHoldsTheCharactersTheHudsPrint() {
        String printed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,:;!?'\"-+/%()[]#&*<>=_|@Δ▶▲♦·°…";
        for (int i = 0; i < printed.length(); i++) {
            char c = printed.charAt(i);
            assertTrue(Rom.has(c), "the ROM has no glyph for '" + c + "'");
        }
    }
}
