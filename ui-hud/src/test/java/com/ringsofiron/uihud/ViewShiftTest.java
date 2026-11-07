package com.ringsofiron.uihud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The view's shift off the screen's middle, which a HUD asks for to keep the ship or the avatar in the
 * middle of the world its units leave in view (the Stations spec's 6.2): eased as the zoom is, from
 * wherever it is, and put back at once as a HUD goes.
 */
class ViewShiftTest {

    @Test
    void theShiftEasesInTheZoomsTimeFromWhereItIs() {
        ViewportManager v = new ViewportManager();
        assertEquals(0f, v.getViewShiftX(), "the middle, as the game has it");
        assertEquals(0f, v.getViewShiftY());
        v.easeViewShift(-240f, 92f);
        assertEquals(0f, v.getViewShiftX(), "nothing until a frame moves it on");
        v.update(ZoomEase.SECONDS / 2f);
        assertEquals(-120f, v.getViewShiftX(), 0.01f, "half way at half the time, eased in and out");
        assertEquals(46f, v.getViewShiftY(), 0.01f);
        v.update(ZoomEase.SECONDS);
        assertEquals(-240f, v.getViewShiftX(), 1e-4f);
        assertEquals(92f, v.getViewShiftY(), 1e-4f);
        // Asked again for where it is already heading: nothing starts over
        v.easeViewShift(-240f, 92f);
        v.update(0.01f);
        assertEquals(-240f, v.getViewShiftX(), 1e-4f);
        // A panel closed while the band stays: only x goes back, from where it is
        v.easeViewShift(0f, 92f);
        v.update(ZoomEase.SECONDS / 2f);
        assertEquals(-120f, v.getViewShiftX(), 0.01f);
        assertEquals(92f, v.getViewShiftY(), 1e-4f);
        // Turned round half way: it eases back from there, not from either end
        v.easeViewShift(-240f, 92f);
        v.update(ZoomEase.SECONDS);
        assertEquals(-240f, v.getViewShiftX(), 1e-4f);
    }

    @Test
    void aHudGoingPutsTheMiddleBackAtOnce() {
        ViewportManager v = new ViewportManager();
        v.easeViewShift(-240f, 92f);
        v.update(ZoomEase.SECONDS / 3f);
        v.setViewShift(0f, 0f);
        assertEquals(0f, v.getViewShiftX());
        assertEquals(0f, v.getViewShiftY());
        v.update(ZoomEase.SECONDS);
        assertEquals(0f, v.getViewShiftX(), "the ease it had going is over");
        assertEquals(0f, v.getViewShiftY());
    }
}
