package com.ringsofiron.uihud.variants.nasapunk.task;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * WPN, the weapons cluster (task spec section 6, Z7). Expanded at a gunnery post: a header saying
 * what is in hand, then one row per mount (where, what, who works it, its state, and a gun's time
 * of flight or a launcher's rounds), then the decoy and, on a ship with an emitter, the shields.
 * Compact at the helm: a pip per mount, the launcher's rounds, the decoy and the shields.
 */
final class WeaponsDisplay extends NpDu {
    /** One row: where, what, who, the state in words and as a box state (null: plain text). */
    static final class Row {
        String place = "", weapon = "", who = "", state = "", extra = "", extraUnit = "";
        Color whoColor = NpColors.DIM, stateColor = NpColors.WHITE;
        NpHardware.BoxState box;
        /** Compact: 0 ready, 1 waiting, 2 not working; -1 for a row that is not a mount (decoy, shields). */
        int pip = -1;
        boolean extraIsTof;
    }

    final List<Row> rows = new ArrayList<>();
    String header = "";
    boolean compact;
    /** Compact summary: launcher rounds ("3/4"), decoy ("READY" or seconds), shields ("UP", "DOWN") or null. */
    String rounds, decoy, shields;

    /** The expanded display at gunnery, and the compact one at the helm, units. */
    static final float W = 360f, H = 166f, COMPACT_W = 196f, COMPACT_H = 72f;

    WeaponsDisplay(NpKit kit) {
        super(kit);
        bezel = 8f;
        title = "WEAPONS";
        setSize(W, H);
    }

    void setCompact(boolean c) {
        compact = c;
        setSize(c ? COMPACT_W : W, c ? COMPACT_H : H);
    }

    /** {@code s}, or as many of its words as fit in {@code max} units (HARPOON LAUNCHER is HARPOON), else cut. */
    static String fitWords(NpCanvas cv, String s, float max) {
        if (cv.width(s, 12f, false, 0f) <= max) return s;
        String[] w = s.split(" ");
        String fit = "";
        for (String word : w) {
            String next = fit.isEmpty() ? word : fit + " " + word;
            if (cv.width(next, 12f, false, 0f) > max) break;
            fit = next;
        }
        return fit.isEmpty() ? Objectives.fit(cv, s, max) : fit;
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float L = gx + 8f, R = gx + gw - 8f;
        if (compact) {
            // a pip per mount (ready, waiting, not working) and the launcher's rounds; then the decoy and shields
            float x = L;
            for (Row r : rows) {
                if (r.pip < 0) continue;
                Color c = r.pip == 0 ? NpColors.WHITE : r.pip == 1 ? NpColors.DIM : NpColors.AMBER;
                if (r.pip == 2) cv.stroke(x + 1f, gy + 9f, 10f, 10f, 1.2f, c);
                else cv.fill(x + 1f, gy + 9f, 10f, 10f, c);
                x += 15f;
            }
            if (rounds != null) cv.text(R, gy + 19f, "MSL " + rounds, 12f, true, NpColors.WHITE, 0.3f, NpCanvas.END);
            if (decoy != null) cv.text(L, gy + 40f, "DECOY " + decoy, 12f, true, NpColors.WHITE, 0.3f, NpCanvas.START);
            if (shields != null) cv.text(R, gy + 40f, "SHIELDS " + shields, 12f, true, NpColors.WHITE, 0.3f, NpCanvas.END);
            return;
        }
        float mx = L, wx = L + 72f, bx = L + 166f, sx = L + 210f;
        if (!header.isEmpty()) {
            cv.text(L, gy + 15f, header, 12f, true, NpColors.DIM, 0.3f, NpCanvas.START);
            cv.line(gx + 4f, gy + 22f, gx + gw - 4f, gy + 22f, 1f, NpColors.GRATICULE);
        }
        int n = rows.size();
        float pitch = n <= 6 ? 19f : Math.max(15f, (gh - 30f) / n);
        for (int i = 0; i < n; i++) {
            Row r = rows.get(i);
            float yy = gy + 39f + i * pitch;
            cv.text(mx, yy, Objectives.fit(cv, r.place, 68f), 12f, true, NpColors.WHITE, 0.2f, NpCanvas.START);
            cv.text(wx, yy, fitWords(cv, r.weapon, bx - wx - 6f), 12f, false, NpColors.WHITE, 0f, NpCanvas.START);
            cv.text(bx, yy, r.who, 12f, true, r.whoColor, 0.2f, NpCanvas.START);
            float stW;
            if (r.box == null) {
                stW = cv.text(sx, yy, r.state, 12f, true, r.stateColor, 0.2f, NpCanvas.START);
            } else {
                stW = NpHardware.boxWidth(cv, r.state, 12f, 5f);
                NpHardware.box(cv, sx - 3f, yy - 13f, stW, 17f, r.state, r.box, 12f, false);
            }
            if (!r.extra.isEmpty()) {
                if (r.extraIsTof) {
                    float vw = cv.width(r.extra + " " + r.extraUnit, 15f, true, 0f);
                    cv.text(R - vw - 7f, yy, "TOF", 12f, true, NpColors.DIM, 0.3f, NpCanvas.END);
                    cv.text(R, yy + 0.5f, r.extra + " " + r.extraUnit, 15f, true, NpColors.WHITE, 0f, NpCanvas.END);
                } else {
                    cv.text(sx + stW + 8f, yy + 0.5f, r.extra, 15f, true, NpColors.WHITE, 0f, NpCanvas.START);
                }
            }
        }
    }
}
