package com.ringsofiron.uihud.variants.nasapunk.task;

import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * ALR, the alert band (task spec sections 6 and 9.1): two rows that keep their places. The top row
 * is the one warning on a red-framed glass: a lens, the number that matters large, the action on a
 * key cap. The second is up to three amber strips fitted to their text, each naming who handles it
 * (NOBODY in amber), then the reserved +n slot at the row's end. Empty, it draws nothing.
 */
final class AlertBand extends NpInstrument {
    /** The one warning: its lens legend (two lines), its large line, and its second line. */
    static final class Warning {
        String tag = "", big = "", big2, line2;
        /** Keys and what they do, as pairs. */
        final List<String[]> actions = new ArrayList<>();
        /** The game actions whose keys it shows, on a cap or in its words, so the hint line can leave them out. */
        final List<String> named = new ArrayList<>();
    }

    /** A caution: its code, where, and who handles it. */
    static final class Caution {
        final String code, where, who;

        Caution(String code, String where, String who) {
            this.code = code;
            this.where = where;
            this.who = who;
        }
    }

    Warning warning;
    final List<Caution> cautions = new ArrayList<>();
    /** The count of cautions past the third, as {@code +2}; null for none. */
    String more;
    /** Seconds since the warning appeared, for its 3 s pulse. */
    float warningAge = 99f;

    /** Its size, units. */
    static final float W = 640f, H = 76f;

    AlertBand(NpKit kit) {
        super(kit);
        setSize(W, H);
    }

    @Override
    protected void paint(NpCanvas cv) {
        float w = getWidth(), wh = 40f, cy = 46f, ch = 30f;
        if (warning != null) {
            Warning W0 = warning;
            // a new warning pulses for its first 3 s: its frame and lens beat at 2 Hz
            boolean dim = warningAge < 3f && ((int) (warningAge * 4f)) % 2 == 1;
            cv.fillRound(-2f, -2f, w + 4f, wh + 4f, 3f, NpColors.BEZEL_SHADOW, 1f);
            cv.fill(0f, 0f, w, wh, NpColors.GLASS);
            cv.stroke(0f, 0f, w, wh, 2f, NpColors.RED, dim ? 0.45f : 1f);
            NpHardware.lens(cv, 5f, 5f, 74f, wh - 10f, W0.tag, dim ? NpColors.LENS_LEGEND : NpColors.RED, false, 12f);
            float bw = cv.text(90f, 20f, W0.big, 20f, true, NpColors.RED, 0.5f, NpCanvas.START);
            if (W0.big2 != null) cv.text(90f + bw + 16f, 20f, W0.big2, 15f, true, NpColors.WHITE, 0.3f, NpCanvas.START);
            if (W0.line2 != null) cv.text(90f, 34.5f, W0.line2, 12f, true, NpColors.WHITE, 0.3f, NpCanvas.START);
            float kx = w - 12f;
            for (int i = W0.actions.size() - 1; i >= 0; i--) {
                String[] a = W0.actions.get(i);
                float vw = cv.text(kx, 25f, a[1], 12.5f, true, NpColors.WHITE, 0.4f, NpCanvas.END);
                kx -= vw + 7f;
                float kw = cv.width(a[0], 12f, true, 0f) + 14f;
                NpHardware.softKey(cv, kx - kw, 10f, kw, 20f, false);
                cv.text(kx - kw / 2f, 24.5f, a[0], 12f, true, NpColors.STENCIL, 0f, NpCanvas.MIDDLE);
                kx -= kw + 18f;
            }
        }
        float cx = 0f, nx = w - 44f;
        for (int i = 0; i < Math.min(3, cautions.size()); i++) {
            Caution c = cautions.get(i);
            float cw0 = cv.width(c.code, 12f, true, 0.6f), ww = cv.width(c.where, 12f, false, 0f), hw = cv.width(c.who, 12f, true, 0.2f);
            float cw = 14f + cw0 + 10f + ww + 14f + hw + 10f;
            if (cx + cw > nx - 8f) break;
            cv.fillRound(cx - 1f, cy - 1f, cw + 2f, ch + 2f, 2f, NpColors.BEZEL_SHADOW, 1f);
            cv.fill(cx, cy, cw, ch, NpColors.GLASS);
            cv.stroke(cx, cy, cw, ch, 1.4f, NpColors.AMBER);
            cv.fill(cx, cy, 6f, ch, NpColors.AMBER);
            cv.text(cx + 14f, cy + 19.5f, c.code, 12f, true, NpColors.AMBER, 0.6f, NpCanvas.START);
            cv.text(cx + 14f + cw0 + 10f, cy + 19.5f, c.where, 12f, false, NpColors.WHITE, 0f, NpCanvas.START);
            cv.text(cx + cw - 10f, cy + 19.5f, c.who, 12f, true, "NOBODY".equals(c.who) ? NpColors.AMBER : NpColors.WHITE, 0.2f, NpCanvas.END);
            cx += cw + 8f;
        }
        if (more != null) {
            cv.fillRound(nx - 1f, cy - 1f, 46f, ch + 2f, 2f, NpColors.BEZEL_SHADOW, 1f);
            cv.fill(nx, cy, 44f, ch, NpColors.GLASS);
            cv.stroke(nx, cy, 44f, ch, 1.4f, NpColors.AMBER);
            cv.text(nx + 22f, cy + 19.5f, more, 12f, true, NpColors.AMBER, 0.2f, NpCanvas.MIDDLE);
        }
    }
}
