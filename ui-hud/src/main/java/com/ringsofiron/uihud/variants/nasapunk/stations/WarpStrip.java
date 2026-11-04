package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;

/**
 * The warp strip (section 7.5), 720 x 20 under the annunciator while time runs above 1×: the
 * level and the rails, then when warp drops out and for what ("DROPS OUT IN 4:12 FOR BURN 2"), or,
 * held back, why, in amber ("HELD: CREW OUTSIDE").
 */
final class WarpStrip extends NpInstrument {
    static final float W = 720f, H = 20f;

    /** "WARP 100×", with RAILS after it on the rails. */
    String level = "";
    /** What comes next, or why warp is held; "" for neither. */
    String rest = "";
    boolean held;

    WarpStrip(NpKit kit) {
        super(kit);
        setSize(W, H);
    }

    @Override
    protected void paint(NpCanvas cv) {
        float lw = cv.width(level, 13f, true, 0.6f), gap = rest.isEmpty() ? 0f : 14f;
        float rw = rest.isEmpty() ? 0f : cv.width(rest, 12f, true, 0.4f);
        float w = lw + gap + rw + 20f, x = W / 2f - w / 2f;
        NpHardware.slip(cv, x, 0f, w, H);
        cv.fill(x, 0f, 2f, H, held ? NpColors.AMBER : NpColors.CYAN);
        cv.text(x + 10f, 14.5f, level, 13f, true, NpColors.WHITE, 0.6f, NpCanvas.START);
        if (!rest.isEmpty()) cv.text(x + 10f + lw + gap, 14.5f, rest, 12f, true, held ? NpColors.AMBER : NpColors.DIM, 0.4f, NpCanvas.START);
    }
}
