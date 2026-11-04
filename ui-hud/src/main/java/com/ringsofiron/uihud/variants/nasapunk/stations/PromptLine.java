package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * The prompt line's learning hints (section 7.3): at most three key caps with their verbs, quiet,
 * centred 8 above the band when seated and near the bottom edge on deck.
 */
final class PromptLine extends NpInstrument {
    static final int MOST = 3;

    /** Key, verb pairs. */
    final List<String[]> hints = new ArrayList<>();

    PromptLine(NpKit kit) {
        super(kit);
        setSize(StationsLayout.COLUMN_W, StationsLayout.PROMPT_H);
    }

    @Override
    protected void paint(NpCanvas cv) {
        float total = -18f;
        for (String[] h : hints) total += width(cv, h) + 18f;
        float x = getWidth() / 2f - total / 2f;
        for (String[] h : hints) {
            float kw = StDraw.capW(cv, h[0]);
            NpHardware.softKey(cv, x, 3f, kw, 17f, false);
            cv.text(x + kw / 2f, 16f, h[0], 12f, true, NpColors.STENCIL, 0f, NpCanvas.MIDDLE);
            cv.text(x + kw + 6f, 16f, h[1], 12f, true, NpColors.DIM, 0.5f, NpCanvas.START);
            x += width(cv, h) + 18f;
        }
    }

    private static float width(NpCanvas cv, String[] h) {
        return StDraw.capW(cv, h[0]) + 6f + cv.width(h[1], 12f, true, 0.5f);
    }
}
