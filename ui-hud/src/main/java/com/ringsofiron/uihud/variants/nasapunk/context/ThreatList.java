package com.ringsofiron.uihud.variants.nasapunk.context;

import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;
import java.util.ArrayList;
import java.util.List;

/**
 * F4, the threat list (section 7), at fire control and a gun: rows 24 tall, the inbound missiles
 * soonest first (the impact time, the ship that fired it in red, PD ON while point defense works
 * it, or DECOY when it will land before anything else stops it), then the hostiles in the fight
 * other than the Lock, with range and what they are doing.
 */
final class ThreatList extends NpDu {
    static final float W = 308f, ROW = 24f;
    static final int MAX = 6;
    /** A missile this close to impact with no point defense on it calls for the decoy, s. */
    static final float DECOY_AT = 8f;

    static final class Row {
        boolean missile, pd;
        String name = "", state = "";
        float time = Float.NaN, range = Float.NaN;
    }

    final List<Row> rows = new ArrayList<>();

    ThreatList(NpKit kit) {
        super(kit);
        bezel = 6f;
        setSize(W, 12f + ROW);
    }

    float heightFor() {
        return 12f + Math.max(1, Math.min(MAX, rows.size())) * ROW;
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float R = gx + gw - 6f, T = gx + 160f, D = gx + 170f;
        for (int i = 0; i < Math.min(MAX, rows.size()); i++) {
            Row r = rows.get(i);
            float top = gy + i * ROW, yy = top + 16.7f;
            if (i > 0) cv.line(gx + 4f, top, gx + gw - 4f, top, 1f, NpColors.GRATICULE);
            if (r.missile) {
                NpSymbols.chevron(cv, gx + 11f, yy - 5f, 0f, NpColors.RED, 1.5f, 5.5f);
                cv.text(gx + 24f, yy, "MSL", CtxDraw.B, true, NpColors.RED, 0.4f, NpCanvas.START);
                if (!Float.isNaN(r.time)) CtxDraw.valEnd(cv, T, yy, NpFormat.tMinus(r.time), null, CtxDraw.V, NpColors.WHITE);
                float end = R;
                if (r.pd) {
                    NpHardware.box(cv, R - 56f, yy - 14f, 56f, 18f, "PD ON", NpHardware.BoxState.ENGAGED, CtxDraw.LB, false);
                    end = R - 62f;
                } else if (!Float.isNaN(r.time) && r.time <= DECOY_AT) {
                    float dw = cv.text(R, yy, "DECOY", CtxDraw.B, true, NpColors.AMBER, 0.4f, NpCanvas.END);
                    end = R - dw - 6f;
                }
                cv.text(D, yy, CtxDraw.fitName(cv, r.name, end - D, CtxDraw.B, true, 0.4f), CtxDraw.B, true, NpColors.RED, 0.4f, NpCanvas.START);
            } else {
                NpSymbols.diamond(cv, gx + 11f, yy - 4.7f, 4.6f, NpColors.RED, 1.4f, false);
                cv.text(gx + 24f, yy, CtxDraw.fitName(cv, r.name, T - 62f - gx - 24f, CtxDraw.B, true, 0.4f), CtxDraw.B, true, NpColors.RED, 0.4f, NpCanvas.START);
                if (!Float.isNaN(r.range)) {
                    String[] v = NpFormat.range(r.range);
                    CtxDraw.valEnd(cv, T, yy, v[0], v[1], CtxDraw.V, NpColors.WHITE);
                }
                cv.text(D, yy, CtxDraw.fit(cv, r.state, R - D, CtxDraw.B, false, 0.3f), CtxDraw.B, false, NpColors.WHITE, 0.3f, NpCanvas.START);
            }
        }
    }
}
