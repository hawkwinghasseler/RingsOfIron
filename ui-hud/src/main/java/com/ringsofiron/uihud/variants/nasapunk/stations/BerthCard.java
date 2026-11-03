package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * The berth card (section 12, Docked): for a few seconds after the ports latch, under the ticker,
 * where the ship is and the station's services with their keys: "DOCKED AT HIGHPORT  [T] MARKET
 * [Y] YARD [J] MISSIONS". The COMMS page keeps them after it goes.
 */
final class BerthCard extends NpInstrument {
    static final float H = StationsLayout.BERTH_H;

    String title = "";
    /** Key, word pairs. */
    final List<String[]> keys = new ArrayList<>();

    BerthCard(NpKit kit) {
        super(kit);
        setSize(StationsLayout.COLUMN_W, H);
    }

    @Override
    protected void paint(NpCanvas cv) {
        float tw = cv.width(title, 13f, true, 0.6f), w = tw + 28f;
        for (String[] k : keys) w += StDraw.hintW(cv, k[0], k[1]) + 14f;
        float x = getWidth() / 2f - w / 2f;
        NpHardware.slip(cv, x, 0f, w, H);
        cv.fill(x, 0f, 2f, H, NpColors.GREEN);
        cv.text(x + 12f, 18.5f, title, 13f, true, NpColors.WHITE, 0.6f, NpCanvas.START);
        float hx = x + 12f + tw + 16f;
        for (String[] k : keys) hx += StDraw.hint(cv, hx, 18.5f, k[0], k[1], NpColors.DIM) + 14f;
    }
}
