package com.ringsofiron.uihud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoomEaseTest {
    @Test
    void itGetsThereInItsTimeAndNoSooner() {
        ZoomEase e = new ZoomEase();
        e.start(300f, 40f);
        assertEquals(300f, e.meters(), 1e-2f);
        assertEquals(40f, e.target(), 1e-3f);
        float last = 300f;
        for (int i = 0; i < 9; i++) {
            e.update(ZoomEase.SECONDS / 10f);
            assertTrue(e.isEasing());
            assertTrue(e.meters() < last && e.meters() > 40f, "on its way: " + e.meters());
            last = e.meters();
        }
        e.update(ZoomEase.SECONDS / 10f);
        assertFalse(e.isEasing());
        assertEquals(40f, e.meters(), 1e-3f);
        assertEquals(1f, e.progress());
    }

    @Test
    void halfwayInTimeIsHalfwayInRatio() {
        ZoomEase e = new ZoomEase();
        e.start(400f, 25f);
        e.update(ZoomEase.SECONDS / 2f);
        // Each step the same ratio of the view: halfway from 400 to 25 is 100
        assertEquals(100f, e.meters(), 0.1f);
        assertEquals(0.5f, e.progress(), 1e-6f);
    }

    @Test
    void itStartsAndEndsGently() {
        ZoomEase e = new ZoomEase();
        e.start(40f, 300f);
        e.update(ZoomEase.SECONDS * 0.1f);
        float early = e.progress();
        e.update(ZoomEase.SECONDS * 0.4f);
        float mid = e.progress();
        e.update(ZoomEase.SECONDS * 0.1f);
        assertTrue(early < 0.1f, "slower than linear at first: " + early);
        assertTrue(e.progress() - mid > early, "fastest in the middle");
    }

    @Test
    void theWheelMidEaseMovesWhereItIsHeadingToo() {
        ZoomEase e = new ZoomEase();
        e.start(40f, 300f);
        e.update(ZoomEase.SECONDS / 3f);
        float before = e.meters();
        e.scale(2f, 20f, 8000f);
        assertEquals(2f * before, e.meters(), 0.1f);
        assertEquals(600f, e.target(), 0.1f);
        e.scale(100f, 20f, 8000f);
        assertEquals(8000f, e.target(), 1f);
    }
}
