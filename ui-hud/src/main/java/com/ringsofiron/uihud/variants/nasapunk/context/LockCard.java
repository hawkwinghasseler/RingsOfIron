package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;

/**
 * The lock card (section 7): H6's compact face at the helm, F1's full one at fire control and a
 * gun. The name in the allegiance colour with the lock brackets; its state as a box and FIRING ON
 * YOU as a lit red flag while it fires on the ship; then range and rate, and at the helm the shot's
 * time of flight and the best gun's hit chance (F2 has each gun's at fire control), or in full the
 * relative speed and course and what is known of its condition.
 */
final class LockCard extends NpDu {
    static final float W = 308f, COMPACT_H = 116f, FULL_H = 148f;

    boolean full;
    String name = "", state = "", known = "";
    NpSymbols.Kind kind = NpSymbols.Kind.NEUTRAL;
    NpHardware.BoxState stateBox = NpHardware.BoxState.NORMAL;
    boolean firing;
    float range = Float.NaN, closing = Float.NaN, rel = Float.NaN, course = Float.NaN, shot = Float.NaN, hit = Float.NaN;
    String hitWord = "";

    LockCard(NpKit kit) {
        super(kit);
        bezel = 6f;
        setSize(W, COMPACT_H);
    }

    void face(boolean asFull) {
        full = asFull;
        setSize(W, asFull ? FULL_H : COMPACT_H);
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float L = gx + 8f, R = gx + gw - 8f, M = gx + 154f;
        Color c = NpSymbols.colorOf(kind);
        NpSymbols.shape(cv, kind, L + 6f, gy + 14f, 4.6f, c, 1.4f, kind == NpSymbols.Kind.HOSTILE, 0f);
        NpSymbols.brackets(cv, L + 6f, gy + 14f, 9f, 4f, c, 1.2f);
        float nameEnd = R;
        if (full && !Float.isNaN(shot) && shot > 0f) nameEnd = shot(cv, R, gy + 20f) - 10f;
        cv.text(L + 22f, gy + 20f, CtxDraw.fit(cv, name, nameEnd - L - 22f, CtxDraw.V, true, 0.5f), CtxDraw.V, true, c, 0.5f, NpCanvas.START);
        float bw = Math.max(78f, NpHardware.boxWidth(cv, state, CtxDraw.B, 6f));
        NpHardware.box(cv, L, gy + 30f, bw, 20f, state, stateBox, CtxDraw.B, false);
        if (firing) {
            float fx = L + bw + 8f;
            cv.fillRound(fx, gy + 30f, R - fx, 20f, 1.5f, NpColors.RED, 1f);
            String t = "FIRING ON YOU";
            float tw = cv.width(t, CtxDraw.B, true, 0.6f), sx = (fx + R) / 2f - (tw + 14f) / 2f;
            NpSymbols.diamond(cv, sx + 4f, gy + 40f, 3.8f, NpColors.INK, 1f, true);
            cv.text(sx + 14f, gy + 44.7f, t, CtxDraw.B, true, NpColors.INK, 0.6f, NpCanvas.START);
        }
        cv.line(gx + 4f, gy + 58f, gx + gw - 4f, gy + 58f, 1f, NpColors.GRATICULE);
        float yy = gy + 77f;
        CtxDraw.lab(cv, L, yy, "RNG");
        if (!Float.isNaN(range)) {
            String[] r = NpFormat.range(range);
            CtxDraw.val(cv, L + 44f, yy, r[0], r[1], CtxDraw.V, NpColors.WHITE);
        }
        if (!Float.isNaN(closing)) {
            CtxDraw.lab(cv, M, yy, closing >= 0f ? "CLOSING" : "OPENING");
            String[] s = NpFormat.speed(Math.abs(closing));
            CtxDraw.valEnd(cv, R, yy, s[0], s[1], CtxDraw.V, NpColors.WHITE);
        }
        yy += 19f;
        if (!full) {
            // the shot and the best gun's chance of a hit, at the helm
            float x = L;
            if (!Float.isNaN(shot) && shot > 0f) {
                x += CtxDraw.lab(cv, x, yy, "SHOT") + 6f;
                x += CtxDraw.val(cv, x, yy, NpFormat.tenths(shot), "S", CtxDraw.V, NpColors.WHITE) + 14f;
            } else {
                x += cv.text(x, yy, "NO SOLUTION", CtxDraw.B, true, NpColors.DIM, 0.4f, NpCanvas.START) + 14f;
            }
            if (!Float.isNaN(hit)) {
                CtxDraw.lab(cv, M, yy, "HIT");
                float vx = CtxDraw.valEnd(cv, R, yy, hitWord, null, CtxDraw.B, NpColors.CYAN);
                CtxDraw.valEnd(cv, vx - 8f, yy, NpFormat.percent(hit), null, CtxDraw.V, NpColors.CYAN);
            }
            return;
        }
        CtxDraw.lab(cv, L, yy, "REL");
        if (!Float.isNaN(rel)) {
            String[] s = NpFormat.speed(rel);
            CtxDraw.val(cv, L + 44f, yy, s[0], s[1], CtxDraw.V, NpColors.WHITE);
        }
        if (!Float.isNaN(course)) {
            CtxDraw.lab(cv, M, yy, "COURSE");
            CtxDraw.valEnd(cv, R, yy, Math.round(((course % 360f) + 360f) % 360f) + "°", null, CtxDraw.V, NpColors.WHITE);
        }
        yy += 19f;
        if (!known.isEmpty()) {
            CtxDraw.lab(cv, L, yy, "KNOWN");
            cv.text(L + 60f, yy, CtxDraw.fit(cv, known, R - L - 60f, CtxDraw.B, false, 0.3f), CtxDraw.B, false, NpColors.WHITE, 0.3f, NpCanvas.START);
        }
    }

    /** SHOT and its time, ending at {@code R}; returns the left edge. */
    private float shot(NpCanvas cv, float R, float y) {
        float vx = CtxDraw.valEnd(cv, R, y, NpFormat.tenths(shot), "S", CtxDraw.V, NpColors.WHITE);
        return vx - 6f - CtxDraw.labEnd(cv, vx - 6f, y, "SHOT");
    }
}
