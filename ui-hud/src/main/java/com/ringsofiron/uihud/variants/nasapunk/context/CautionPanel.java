package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * P2, the caution and warning panel (section 6): an EICAS list of rows 24 tall, five fields at
 * fixed places so they line up from row to row: the severity's shape, the code, the detail, the
 * handling (a cyan YOU flag, a white dot per crew member on it, or BACK with the time it recovers
 * in), the timer. Harm timers are white, recovery timers dim. Past six rows, the sixth is "+n MORE"
 * with the hidden codes. A new warning's row pulses for 2 s. Hidden with nothing wrong.
 */
final class CautionPanel extends NpDu {
    static final float W = 308f, ROW = 24f;
    private static final Color PULSE = NpColors.RED;

    final List<CautionRows.Row> rows = new ArrayList<>();
    /** The hidden codes, past the fifth row; empty with none hidden. */
    final List<String> more = new ArrayList<>();
    /** Sim seconds now, for the pulse. */
    double now;

    CautionPanel(NpKit kit) {
        super(kit);
        bezel = 6f;
        setSize(W, 12f + ROW);
    }

    /** The panel's height for its rows. */
    float heightFor() {
        int n = rows.size() + (more.isEmpty() ? 0 : 1);
        return 12f + Math.max(1, n) * ROW;
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        for (int i = 0; i < rows.size(); i++) row(cv, rows.get(i), gx, gy + i * ROW, gw, i);
        if (!more.isEmpty()) {
            float top = gy + rows.size() * ROW, yy = top + 16.7f;
            cv.line(gx + 4f, top, gx + gw - 4f, top, 1f, NpColors.GRATICULE);
            StringBuilder b = new StringBuilder("+").append(more.size()).append(" MORE: ");
            String line = CtxDraw.fit(cv, b.append(String.join(", ", more)).toString(), gw - 22f - 22f, CtxDraw.B, true, 0.3f);
            cv.text(gx + 22f, yy, line, CtxDraw.B, true, NpColors.DIM, 0.3f, NpCanvas.START);
            cv.poly(new float[] {gx + gw - 14f, top + 7f, gx + gw - 9f, top + 12f, gx + gw - 14f, top + 17f}, false, 1.6f, NpColors.DIM);
        }
    }

    private void row(NpCanvas cv, CautionRows.Row r, float gx, float top, float gw, int i) {
        float yy = top + 16.7f;
        if (i > 0) cv.line(gx + 4f, top, gx + gw - 4f, top, 1f, NpColors.GRATICULE);
        Color col = CtxDraw.color(r.level);
        // a new warning pulses for its first 2 s, twice a second
        double age = now - r.since;
        if (r.level == Level.WARNING && !r.acknowledged && age >= 0 && age < 2.0 && (age * 2.0) % 1.0 < 0.5) {
            cv.fill(gx + 1f, top + 1f, gw - 2f, ROW - 2f, PULSE, 0.22f);
        }
        CtxDraw.severity(cv, gx + 11f, yy, r.level, col);
        // the codes run to eleven letters (SYSTEM DOWN): a long one pushes its detail along
        float cw = cv.text(gx + 22f, yy, CtxDraw.fit(cv, r.code, 116f, CtxDraw.B, true, 0.3f), CtxDraw.B, true, col, 0.3f, NpCanvas.START);
        float hx = gx + 206f;
        String timer = Float.isNaN(r.timer) || r.timer <= 0f ? "" : r.timer >= 3600f ? (int) (r.timer / 3600f) + " H" : NpFormat.clock(r.timer);
        if (!r.detail.isEmpty()) {
            float dx = Math.max(gx + 126f, gx + 22f + cw + 8f);
            float end = r.handling != CautionRows.Handling.NONE ? hx - 4f
                    : gx + gw - 6f - (timer.isEmpty() ? 0f : cv.width(timer, CtxDraw.B, true, 0f) + 8f);
            cv.text(dx, yy, CtxDraw.fit(cv, r.detail, end - dx, CtxDraw.B, false, 0.2f), CtxDraw.B, false, NpColors.WHITE, 0.2f, NpCanvas.START);
        }
        switch (r.handling) {
            case YOU:
                cv.stroke(hx, top + 4f, 38f, 16f, 1.2f, NpColors.CYAN);
                cv.text(hx + 19f, yy - 0.5f, "YOU", CtxDraw.LB, true, NpColors.CYAN, 0.4f, NpCanvas.MIDDLE);
                break;
            case BACK:
                cv.text(hx, yy, "BACK", CtxDraw.LB, true, NpColors.DIM, 0.6f, NpCanvas.START);
                break;
            case CREW:
                if (r.crew >= 4) {
                    cv.disc(hx + 6f, yy - 4.5f, 7.6f, NpColors.WHITE);
                    cv.text(hx + 13f, yy, Integer.toString(r.crew), CtxDraw.B, true, NpColors.WHITE, 0f, NpCanvas.START);
                } else {
                    for (int k = 0; k < r.crew; k++) cv.disc(hx + 6f + k * 11f, yy - 4.5f, 7.6f, NpColors.WHITE);
                }
                break;
            default:
                break;
        }
        if (!timer.isEmpty()) cv.text(gx + gw - 6f, yy, timer, CtxDraw.B, true, r.harm ? NpColors.WHITE : NpColors.DIM, 0f, NpCanvas.END);
    }
}
