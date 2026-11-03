package com.ringsofiron.uihud.variants.nasapunk.task;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;

/**
 * TGT, the target card (task spec section 6, Z6): what the world can't show about the locked
 * target. Its name in its relation's colour and shape, its state, FIRING ON YOU when true, the hull
 * as a sensor estimate in four bands (cells lit, and the band in words: the sensors know no
 * number), range and closing side by side, and the closest approach while it is passing.
 */
final class TargetCard extends NpDu {
    private static final Color CELL_EDGE = NpColors.rgb(0x4C5864);

    String name = "", kindLine = "", state = "", footer;
    Color footerColor = NpColors.MAGENTA;
    NpSymbols.Kind kind = NpSymbols.Kind.NEUTRAL;
    boolean firingOnUs;
    float hull = Float.NaN, range = Float.NaN, closing = Float.NaN, cpa = Float.NaN, cpaTime = Float.NaN;

    /** Its size, units. */
    static final float W = 240f, H = 200f;

    TargetCard(NpKit kit) {
        super(kit);
        bezel = 8f;
        title = "TARGET";
        setSize(W, H);
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float L = gx + 8f, R = gx + gw - 8f;
        Color col = NpSymbols.colorOf(kind);
        NpSymbols.shape(cv, kind, L + 6f, gy + 14f, 4.8f, col, 1.5f, kind == NpSymbols.Kind.HOSTILE, 0f);
        cv.text(L + 18f, gy + 19f, Objectives.fit(cv, name, R - L - 18f), 14f, true, col, 0.4f, NpCanvas.START);
        if (!kindLine.isEmpty()) cv.text(L + 18f, gy + 37f, kindLine, 12f, true, NpColors.DIM, 0.3f, NpCanvas.START);
        if (firingOnUs) NpHardware.box(cv, L, gy + 45f, NpHardware.boxWidth(cv, "FIRING ON YOU", 12f, 7f), 18f, "FIRING ON YOU", NpHardware.BoxState.WARNING, 12f, true);
        if (!state.isEmpty()) cv.text(R, gy + 59f, state, 12f, true, NpColors.WHITE, 0.3f, NpCanvas.END);
        float y = gy + 86f;
        if (!Float.isNaN(hull)) {
            int lit = hull >= 0.75f ? 4 : hull >= 0.5f ? 3 : hull >= 0.25f ? 2 : 1;
            String band = lit == 4 ? "INTACT" : lit == 3 ? "DAMAGED" : lit == 2 ? "BATTERED" : "CRIPPLED";
            cv.text(L, y, "HULL", 12f, true, NpColors.DIM, 0.4f, NpCanvas.START);
            for (int i = 0; i < 4; i++) {
                if (i < lit) cv.fill(L + 46f + i * 25f, y - 10f, 22f, 11f, lit == 1 ? NpColors.AMBER : NpColors.WHITE);
                else cv.stroke(L + 46f + i * 25f, y - 10f, 22f, 11f, 1f, CELL_EDGE);
            }
            cv.text(R, y + 0.5f, band, 12.5f, true, lit == 1 ? NpColors.AMBER : NpColors.WHITE, 0.2f, NpCanvas.END);
        }
        cv.text(L, gy + 107f, "RNG", 12f, true, NpColors.DIM, 0.4f, NpCanvas.START);
        cv.text(L + 112f, gy + 107f, "CLOSING", 12f, true, NpColors.DIM, 0.4f, NpCanvas.START);
        if (!Float.isNaN(range)) {
            String[] r = NpFormat.range(range);
            cv.text(L, gy + 125f, r[0] + " " + r[1], 15f, true, NpColors.WHITE, 0f, NpCanvas.START);
        }
        if (!Float.isNaN(closing)) {
            String sign = closing < -0.05f ? NpFormat.MINUS : "";
            cv.text(L + 112f, gy + 125f, sign + NpFormat.tenths(Math.abs(closing)) + " M/S", 15f, true, NpColors.WHITE, 0f, NpCanvas.START);
        }
        if (!Float.isNaN(cpa) && !Float.isNaN(cpaTime) && cpaTime > 0f) {
            String[] c = NpFormat.range(cpa);
            cv.text(L, gy + 147f, "CPA", 12f, true, NpColors.DIM, 0.4f, NpCanvas.START);
            cv.text(L + 38f, gy + 147f, c[0] + " " + c[1] + "  " + NpFormat.tMinus(cpaTime), 12.5f, true, NpColors.WHITE, 0f, NpCanvas.START);
        }
        if (footer != null) {
            cv.line(gx + 4f, gy + 156f, gx + gw - 4f, gy + 156f, 1f, NpColors.GRATICULE);
            cv.text(L, gy + 174f, footer, 12f, true, footerColor, 0.4f, NpCanvas.START);
        }
    }
}
