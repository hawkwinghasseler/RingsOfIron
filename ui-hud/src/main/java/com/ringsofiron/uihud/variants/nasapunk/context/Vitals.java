package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;
import java.util.ArrayList;
import java.util.List;

/**
 * D1, the captain's vitals (section 7), on deck: the suit's own chest display (the DCM), a
 * suit-grey bezel with an orange tab around a small glass. YOU (the captain's condition, and the
 * bleed-out), SUIT (on or off, and its air while it is the captain's air), HERE (the air where they
 * stand) and CARRY (what they hold, if anything).
 */
final class Vitals extends NpInstrument {
    static final float W = 308f, PITCH = 19f;

    /** Each row: its label, value and colour, and whether the value carries the hazard's shape. */
    static final class Row {
        String label = "", value = "", right, rightLabel;
        Color color = NpColors.WHITE, rightColor = NpColors.WHITE;
        boolean hazard;
    }

    final List<Row> rows = new ArrayList<>();

    Vitals(NpKit kit) {
        super(kit);
        setSize(W, 90f);
    }

    float heightFor() {
        return 33f + Math.max(3, rows.size()) * PITCH;
    }

    @Override
    protected void paint(NpCanvas cv) {
        float w = getWidth(), h = getHeight();
        cv.fillRound(0f, 0f, w, h, 6f, NpColors.SUIT, 1f);
        cv.line(6f, 0.5f, w - 6f, 0.5f, 1f, NpColors.SUIT_EDGE);
        cv.line(6f, h - 0.5f, w - 6f, h - 0.5f, 1f, NpColors.SUIT_EDGE);
        cv.text(12f, 14f, "SUIT", CtxDraw.LB, true, NpColors.SUIT_INK, 1.4f, NpCanvas.START);
        cv.fillRound(w - 34f, 6f, 22f, 8f, 1f, NpColors.ORANGE, 1f);
        cv.fill(8f, 19f, w - 16f, h - 25f, NpColors.GLASS);
        float L = 18f, M = 78f, R = w - 18f;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float yy = 38f + i * PITCH;
            CtxDraw.lab(cv, L, yy, r.label);
            float vx = M;
            if (r.hazard) {
                NpSymbols.diamond(cv, M + 5f, yy - 5f, 4.4f, r.color, 1.2f, true);
                vx = M + 16f;
            }
            float end = R;
            if (r.right != null) {
                end = CtxDraw.valEnd(cv, R, yy, r.right, null, CtxDraw.V, r.rightColor) - 6f;
                if (r.rightLabel != null) end = end - CtxDraw.labEnd(cv, end, yy, r.rightLabel) - 8f;
            }
            cv.text(vx, yy, CtxDraw.fit(cv, r.value, end - vx, CtxDraw.V, true, 0.4f), CtxDraw.V, true, r.color, 0.4f, NpCanvas.START);
        }
    }
}
