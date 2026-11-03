package com.ringsofiron.uihud.variants.nasapunk.task;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * OBJ and NTF (task spec section 6): contract rows on slips with a kapton tab (magenta for the
 * navigation target's row, which carries a second line only when it has a next step), then the
 * news on comm-loop slips under them.
 */
final class Objectives extends NpInstrument {
    private static final Color SLIP_EDGE = NpColors.rgb(0x2A333D);

    static final class Row {
        String title = "", left = "", sub;
        boolean nav, late;
    }

    /** A news line: its loop tag, the words, and the repeat count ({@code ×3}) or null. */
    static final class News {
        String tag = "", msg = "", count;
    }

    final List<Row> rows = new ArrayList<>();
    final List<News> news = new ArrayList<>();

    /** Its size, units. */
    static final float W = 240f, H = 220f;

    Objectives(NpKit kit) {
        super(kit);
        setSize(W, H);
    }

    @Override
    protected void paint(NpCanvas cv) {
        float w = getWidth(), y = 0f;
        for (Row r : rows) {
            float h = r.sub != null ? 42f : 26f;
            NpHardware.slip(cv, 0f, y, w, h);
            cv.stroke(0f, y, w, h, 1f, SLIP_EDGE);
            cv.fill(0f, y, 4f, h, r.nav ? NpColors.MAGENTA : NpColors.KAPTON);
            float lw = cv.width(r.left, 12.5f, true, 0f);
            cv.text(w - 8f, y + 17.5f, r.left, 12.5f, true, r.late ? NpColors.AMBER : NpColors.WHITE, 0f, NpCanvas.END);
            cv.text(12f, y + 17.5f, fit(cv, r.title, w - 12f - lw - 18f), 12.5f, true, NpColors.WHITE, 0f, NpCanvas.START);
            if (r.sub != null) cv.text(24f, y + 35f, r.sub, 12f, true, r.nav ? NpColors.MAGENTA : NpColors.DIM, 0.4f, NpCanvas.START);
            y += h + 6f;
        }
        for (News n : news) {
            NpHardware.callout(cv, 0f, y, w, n.tag, fit(cv, n.msg, w - cv.width(n.tag, 10f, true, 0.6f) - 40f), NpColors.WHITE, true, 12f, n.count);
            y += 24f;
        }
    }

    /** Cuts {@code s} with an ellipsis to fit {@code max} units at body size. */
    static String fit(NpCanvas cv, String s, float max) {
        if (s == null) return "";
        if (cv.width(s, 12.5f, true, 0f) <= max) return s;
        int n = s.length();
        while (n > 1 && cv.width(s.substring(0, n) + "…", 12.5f, true, 0f) > max) n--;
        return s.substring(0, n).trim() + "…";
    }
}
