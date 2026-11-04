package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.task.Catalogue.Entry;

/**
 * ALR, the message centre at the top in the middle (functional-task 6, ALR; 9): a window behind red
 * glass for the one warning, and a row behind amber glass hung under it for three cautions and a
 * narrow cell counting the rest. The rows keep their places: with no warning the red window stays,
 * dark. The warning gives its title in fourteen segments, what it is in dot matrix, the one number
 * that matters in large seven segments with its label and unit, then the action that fits the post
 * behind its key cap and a note. A caution cell is two lines of 19: what and where, then who is on
 * it or the key, each the full text's part when it fits and the short form's when it does not.
 */
final class Band {
    /** A new warning pulses: lit this long, then dimmed as long (under 3 a second). */
    static final float PULSE = 0.3f;

    /** The whole band, from the middle group's origin. */
    static void paint(Filament f, Catalogue c, float time) {
        f.plate(Geo.BX, Geo.WARN_Y, Geo.BW, Geo.CAUT_Y + Geo.CAUT_H - Geo.WARN_Y, 6f, 8f);
        boolean dim = c.pulse && (time % (2f * PULSE)) >= PULSE;
        warning(f, c.warning, dim);
        cautions(f, c);
    }

    private static void warning(Filament f, Entry e, boolean dim) {
        float x = Geo.BX, y = Geo.WARN_Y, w = Geo.BW, h = Geo.WARN_H, base = y + 32f;
        f.module(x, y, w, h, f.o().filaments(2));
        float full = dim ? Drive.DIM : Drive.FULL, norm = dim ? Drive.DIM : Drive.NORM;
        String title = e == null ? "" : e.title, detail = e == null ? "" : e.detail, label = e == null ? "" : e.label,
                number = e == null ? "" : e.number, unit = e == null ? "" : e.unit;
        // The first line stands on one baseline: the title, what it is, then the one number, biggest, at the end
        f.seg14(x + 8f, base - 16f, 16f, title, f.o().cells(10).red().level(full));
        float ux = x + w - 8f - f.dmWidth(1, f.o().pitch(2f));
        f.dm(ux, base - 14f, unit, f.o().pitch(2f).cells(1).red().level(norm));
        float nx = ux - 6f - f.seg7Width(28f, 3, f.o().colons(1));
        f.seg7(nx, base - 28f, 28f, number, f.o().cells(3).right().red().level(full));
        float lx = nx - 8f - f.dmWidth(7, f.o().pitch(Geo.P));
        f.dm(lx, base - 10.5f, label, f.o().pitch(Geo.P).cells(7).right().red().level(norm));
        float dx = x + 8f + f.seg14Width(16f, 10, f.o()) + 12f;
        f.dm(dx, base - 14f, Words.cut(detail, Geo.fit(dx, lx - 8f, 2f)), f.o().pitch(2f).cells(Geo.fit(dx, lx - 8f, 2f)).red().level(norm));
        // The second line: the action that fits the post, with its key, then what the ship is doing about it
        float ax = x + 8f;
        String key = e == null ? "" : e.key, action = e == null ? "" : e.action, note = e == null ? "" : e.note;
        if (!key.isEmpty()) ax += f.keycap(ax, y + 37f, key, 16f, 12f, true) + 6f;
        int ac = Math.max(Geo.fit(ax, x + 230f), Math.min(action.length(), Geo.fit(ax, x + w - 8f)));
        f.dm(ax, y + 40f, action, f.o().pitch(Geo.P).cells(ac).red().level(full));
        float x2 = ax + ac * Geo.CELL + 8f;
        int nc = Geo.fit(x2, x + w - 8f);
        if (nc > 0) f.dm(x2, y + 40f, Words.cut(note, nc), f.o().pitch(Geo.P).cells(nc).red().dim());
    }

    private static void cautions(Filament f, Catalogue c) {
        float x = Geo.BX, y = Geo.CAUT_Y, w = Geo.BW, h = Geo.CAUT_H, cw = Geo.CELL_W, gap = Geo.CELL_GAP;
        f.module(x, y, w, h, f.o().filaments(1));
        for (int i = 0; i < Catalogue.SLOTS; i++) {
            Entry e = i < c.row.size ? c.row.get(i) : null;
            float cx = x + 4f + i * (cw + gap);
            if (i > 0) f.rectLit(cx - gap / 2f - 0.4f, y + 5f, 0.8f, h - 10f, f.o().amber().ghostOnly().k(2f));
            // A warning pushed down into the row keeps its red
            boolean red = e != null && e.level == com.ringsofiron.uihud.data.AlertRegistry.Level.WARNING;
            Filament.Opt o1 = f.o().pitch(Geo.P).cells(Catalogue.LINE).full(), o2 = f.o().pitch(Geo.P).cells(Catalogue.LINE).norm();
            if (red) {
                o1.red();
                o2.red();
            } else {
                o1.amber();
                o2.amber();
            }
            f.dm(cx + 4f, y + 5f, e == null ? "" : e.line(0), o1);
            f.dm(cx + 4f, y + 19f, e == null ? "" : e.line(1), o2);
        }
        float mx = x + 4f + 3f * (cw + gap);
        f.rectLit(mx - gap / 2f - 0.4f, y + 5f, 0.8f, h - 10f, f.o().amber().ghostOnly().k(2f));
        f.dm(mx + 4f, y + 5f, c.more > 0 ? "+" + Math.min(9, c.more) : "", f.o().pitch(Geo.P).cells(2).amber().full());
        f.dm(mx + 4f, y + 19f, c.more > 0 ? "MORE" : "", f.o().pitch(Geo.P).cells(4).amber().norm());
        Kit.fits(mx + 4f + f.dmWidth(4, f.o().pitch(Geo.P)), x + w - 4f, "caution row");
    }
}
