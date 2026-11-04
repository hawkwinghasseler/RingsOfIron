package com.ringsofiron.uihud.variants.nasapunk.task;

import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;

/**
 * HLM, the helm status (task spec section 6): who has the helm and the standing order, as an
 * autopilot mode strip, FMA style: the order boxed in green while it flies, white while it holds,
 * and dim words before it for what it is not doing (NO ORDER).
 */
final class HelmStrip extends NpInstrument {
    String who = "NOBODY", order = "", note;
    NpHardware.BoxState state = NpHardware.BoxState.NORMAL;

    /** Its width alone, and over the flight display, whose width it takes; its height. */
    static final float W = 380f, W_OVER_FLIGHT = 440f, H = 24f;

    HelmStrip(NpKit kit) {
        super(kit);
        setSize(W, H);
    }

    @Override
    protected void paint(NpCanvas cv) {
        float w = getWidth();
        cv.fillRound(-1f, -1f, w + 2f, 26f, 2f, NpColors.BEZEL_SHADOW, 1f);
        cv.fillRound(0f, 0f, w, 24f, 1.5f, NpColors.GLASS, 1f);
        cv.text(10f, 16.5f, "HELM", 12f, true, NpColors.DIM, 0.6f, NpCanvas.START);
        cv.text(58f, 16.5f, who, 12.5f, true, NpColors.WHITE, 0.4f, NpCanvas.START);
        if (order == null || order.isEmpty()) return;
        float ow = NpHardware.boxWidth(cv, order, 12f, 8f);
        NpHardware.box(cv, w - ow - 4f, 3f, ow, 18f, order, state, 12f, false);
        if (note != null) cv.text(w - ow - 14f, 16.5f, note, 12f, true, NpColors.DIM, 0.4f, NpCanvas.END);
    }
}
