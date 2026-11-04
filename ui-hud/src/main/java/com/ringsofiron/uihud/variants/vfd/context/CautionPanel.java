package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;

/**
 * P2 drawn (functional-context 6, P2; the mockup's p2): a slab at the top of the left column, six rows
 * of 24 in fixed fields, a cell apart where a full code meets its detail. Severity in two filtered
 * columns (a red diamond, an amber triangle), the code in 11 cells, the detail at L1 in 10, handling in
 * 3 (YOU inverse; a dot per crew member on it up to three, from four a dot and the count; or BACK and
 * the recovery time across handling and timer), and the timer in seven segments. The sixth row is
 * "+n MORE:" with the codes that fit. A new warning pulses for 2 s, a time-critical one until it is
 * acknowledged.
 */
final class CautionPanel {
    private CautionPanel() {
    }

    static final float RH = Geo.LIST_ROW;

    /** The slab's height for {@code c}'s rows, 0 with none. */
    static float height(Cautions c) {
        int n = c.panel.size + (c.more.isEmpty() ? 0 : 1);
        return n == 0 ? 0f : n * RH + 4f;
    }

    /** Paints P2 with its top at {@code y}; {@code now} is sim seconds, {@code time} real seconds for the pulse. */
    static void paint(Filament f, Cautions c, float y, double now, float time) {
        float h = height(c);
        if (h == 0f) return;
        float x = Geo.LX;
        f.slab(x, y, Geo.CW, h, 5f, 1, true);
        boolean pulseOn = (time % 0.5f) < 0.3f;
        for (int i = 0; i < c.panel.size; i++) {
            Cautions.Row r = c.panel.get(i);
            float ry = y + 2f + i * RH, ty = ry + (RH - 7f * Geo.P) / 2f, sy = ry + (RH - 12f) / 2f;
            boolean dark = c.pulsing(r, now) && !pulseOn;
            glyph(f, x + 5f, ty, r.level, dark);
            f.dm(x + 27f, ty, r.code, f.o().pitch(Geo.P).cells(11).level(r.level == Level.WARNING ? Drive.FULL : Drive.NORM).lit(!dark));
            f.dm(x + 133.5f, ty, r.detail, f.o().pitch(Geo.P).cells(10).dim());
            float hx = x + 226.5f, tx = x + 256f;
            String timer = timer(r);
            switch (r.handling) {
                case YOU:
                    f.dm(hx, ty, "YOU", f.o().pitch(Geo.P).cells(3).inverse(0, 3).norm());
                    break;
                case CREW:
                    f.dm(hx, ty, crew(r.crew), f.o().pitch(Geo.P).cells(3).norm());
                    break;
                case BACK:
                    f.legend(hx, sy + 12f, "BACK", f.o().size(12f).dim());
                    break;
                default:
                    f.dm(hx, ty, "", f.o().pitch(Geo.P).cells(3));
            }
            float tw = f.seg7(tx, sy, 12f, timer, f.o().cells(4).right().level(r.handling == Cautions.Handling.BACK ? Drive.DIM : Drive.NORM));
            Kit.fits(tx + tw, x + Geo.CW - 4f, "P2 timer");
        }
        if (!c.more.isEmpty()) {
            float ty = y + 2f + c.panel.size * RH + (RH - 7f * Geo.P) / 2f;
            f.dm(x + 27f, ty, c.more, f.o().pitch(Geo.P).cells(30).dim());
        }
    }

    /** The severity columns: a red diamond for a warning, then an amber triangle for a caution. */
    static void glyph(Filament f, float x, float ty, Level level, boolean dark) {
        f.dm(x, ty, level == Level.WARNING && !dark ? "♦" : "", f.o().pitch(Geo.P).cells(1).red().full());
        f.dm(x + Geo.CELL, ty, level == Level.CAUTION ? "▲" : "", f.o().pitch(Geo.P).cells(1).amber().full());
    }

    /** A dot per crew member on it, from four a dot and the count. */
    static String crew(int n) {
        if (n <= 0) return "";
        if (n > 3) return "●" + Math.min(n, 99);
        return "●".repeat(n);
    }

    /** The row's timer as m:ss in four cells: to harm, or to recovery for BACK. */
    static String timer(Cautions.Row r) {
        if (Float.isNaN(r.timer)) return "";
        return Fmt.mmss(Math.min(r.timer, 3599f));
    }
}
