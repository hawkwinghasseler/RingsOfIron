package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstruments;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * H4, ship condition (section 7). Compact: the status lights in a small bezel, dark while nominal
 * (the dark cockpit), lit by severity with the severity's shape; three on a ship with no shield
 * emitter. Expanded, while a light is lit and for 10 s after: each lens heads a row with its
 * reading, its figure at 15 and a tape. The ship's plan is left out (on deck the deck is the plan).
 */
final class ConditionPanel extends NpDu {
    static final float W = 308f, COMPACT_H = 40f, ROW = 40f;

    /** One light's row. */
    static final class Row {
        String name = "", label = "", left, right, rightUnit;
        Level level;
        Color labelColor = NpColors.DIM, rightColor = NpColors.WHITE;
        float frac = Float.NaN;
        final NpInstruments.Tape tape = new NpInstruments.Tape();
    }

    final List<Row> rows = new ArrayList<>();
    boolean expanded;

    ConditionPanel(NpKit kit) {
        super(kit);
        bezel = 6f;
        setSize(W, COMPACT_H);
    }

    float heightFor() {
        return expanded ? 12f + rows.size() * ROW + 2f : COMPACT_H;
    }

    @Override
    protected void paint(NpCanvas cv) {
        if (expanded) {
            super.paint(cv);
            return;
        }
        float w = getWidth();
        NpHardware.bezel(cv, 0f, 0f, w, COMPACT_H, 3f);
        int n = rows.size();
        if (n == 0) return;
        float gap = 6f, lw = (w - 16f - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            Row r = rows.get(i);
            CtxDraw.sevLens(cv, 8f + i * (lw + gap), 8f, lw, 24f, r.name, r.level);
        }
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float bx = gx + 100f, R = gx + gw - 8f;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float top = gy + i * ROW + 1f, yy = top + 16f;
            if (i > 0) cv.line(gx + 4f, top - 1f, gx + gw - 4f, top - 1f, 1f, NpColors.GRATICULE);
            CtxDraw.sevLens(cv, gx + 6f, top + 7f, 84f, 24f, r.name, r.level);
            float lw = cv.text(bx, yy, r.label, CtxDraw.B, true, r.labelColor, 0.4f, NpCanvas.START);
            if (r.left != null) CtxDraw.val(cv, bx + lw + 8f, yy, r.left, null, CtxDraw.V, NpColors.WHITE);
            if (r.right != null) {
                float ux = R;
                if (r.rightUnit != null) ux = R - cv.width(r.rightUnit, CtxDraw.LB, true, 0.3f) - 4f;
                if (r.rightUnit != null) cv.text(R, yy, r.rightUnit, CtxDraw.LB, true, NpColors.DIM, 0.3f, NpCanvas.END);
                cv.text(ux, yy, r.right, CtxDraw.V, true, r.rightColor, 0f, NpCanvas.END);
            }
            if (!Float.isNaN(r.frac)) {
                r.tape.pointer = r.level == Level.WARNING ? NpColors.RED : r.level == Level.CAUTION ? NpColors.AMBER : NpColors.WHITE;
                NpInstruments.tape(cv, bx, top + 30f, R - bx, r.frac, r.tape);
            }
        }
    }
}
