package com.ringsofiron.uihud.variants.nasapunk.task;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;

/**
 * CHR, the mission clock (task spec section 6): a small hardware block with the day and time large,
 * the warp rate under them, and at the right the next stop or, when warp is held, why.
 */
final class Chronometer extends NpInstrument {
    String day = "DAY 0", time = "00:00:00", warp = "1×", note;
    Color noteColor = NpColors.WHITE;

    /** Its size, units. */
    static final float W = 272f, H = 48f;

    Chronometer(NpKit kit) {
        super(kit);
        setSize(W, H);
    }

    @Override
    protected void paint(NpCanvas cv) {
        float w = getWidth(), h = getHeight();
        NpHardware.bezel(cv, 0f, 0f, w, h, 3f);
        NpHardware.fastener(cv, 6f, 6f);
        NpHardware.fastener(cv, w - 6f, h - 6f);
        cv.fill(10f, 6f, w - 20f, h - 12f, NpColors.GLASS);
        float dw = cv.text(18f, 22f, day, 12f, true, NpColors.DIM, 0.6f, NpCanvas.START);
        cv.text(Math.max(72f, 18f + dw + 12f), 23f, time, 16f, true, NpColors.WHITE, 0f, NpCanvas.START);
        float ww = cv.text(18f, 38.5f, "WARP", 12f, true, NpColors.DIM, 0.6f, NpCanvas.START);
        cv.text(Math.max(64f, 18f + ww + 8f), 38.5f, warp, 12f, true, NpColors.WHITE, 0f, NpCanvas.START);
        if (note != null && !note.isEmpty()) cv.text(w - 16f, 38.5f, note, 12f, true, noteColor, 0.2f, NpCanvas.END);
    }
}
