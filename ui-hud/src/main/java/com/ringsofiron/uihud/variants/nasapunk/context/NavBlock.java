package com.ringsofiron.uihud.variants.nasapunk.context;

import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;
import java.util.ArrayList;
import java.util.List;

/**
 * H2, the navigation block (section 7): label, value and time columns. ORBIT (the body, the height,
 * each apsis), NAV in magenta with its ring (range, rate, closest approach), NEXT with its diamond
 * (the next event and who flies it), PLAN (what the burns need and leave, or how short they fall).
 * Its numbers sit inside lines, so they take the line's 13, with units dim at 12. Under warp ORBIT
 * and NAV take a line each and NEXT lists the stops ahead, each with its warp-to key; in combat one
 * line, the most pressing.
 */
final class NavBlock extends NpDu {
    enum Face { FULL, WARP, COMPACT }

    static final float W = 308f, P = 17.5f;

    Face face = Face.FULL;
    /** ORBIT: the body ("" for none), altitude, apsides, m. */
    String body = "";
    double alt = Double.NaN, ap = Double.NaN, pe = Double.NaN;
    /** NAV: the name ("" for none), range, m, rate, m/s (+ closing), closest approach, m, and in, s. */
    String nav = "";
    double navRange = Double.NaN, cpa = Double.NaN;
    float navRate = Float.NaN, cpaIn = Float.NaN;
    /** NEXT: what ("" for none), in, s, its delta-v, m/s, and who flies it. */
    String next = "", nextWho = "";
    float nextIn = Float.NaN, nextDv = Float.NaN;
    /** PLAN: what the burns need and leave, m/s; NaN with no plan. */
    double planDv = Double.NaN, planLeaves = Double.NaN;
    /** Under warp: the stops ahead, as name, time and the key that warps to it ("" for none). */
    final List<String[]> stops = new ArrayList<>();
    final List<Float> stopIn = new ArrayList<>();

    NavBlock(NpKit kit) {
        super(kit);
        bezel = 6f;
        setSize(W, 60f);
    }

    boolean hasOrbit() {
        return !body.isEmpty();
    }

    boolean hasNav() {
        return !nav.isEmpty();
    }

    boolean hasNext() {
        return !next.isEmpty();
    }

    boolean hasPlan() {
        return !Double.isNaN(planDv);
    }

    /** Whether it has anything to show in its face. */
    boolean any() {
        switch (face) {
            case COMPACT: return hasNav() || hasNext() && !Float.isNaN(nextIn) && nextIn <= 1800f;
            default: return hasOrbit() || hasNav() || hasNext() || hasPlan();
        }
    }

    /** The height its face takes, units. */
    float heightFor() {
        if (face == Face.COMPACT) return 12f + 24f;
        // as the glass draws it: a group's rows a pitch apart, the next group 1 + 4 below its last
        float yy = 18f, last = 18f;
        boolean first = true;
        int[] groups = rows();
        for (int g = 0; g < groups.length; g++) {
            int n = groups[g];
            if (n == 0) continue;
            if (!first) yy += 4f;
            first = false;
            float pitch = face == Face.WARP && g == 2 ? 24f : P;
            last = yy + (n - 1) * pitch;
            yy = last + P + 1f;
        }
        return 12f + last + 7f;
    }

    /** Rows in each group, in order: ORBIT, NAV, NEXT, PLAN. */
    private int[] rows() {
        if (face == Face.WARP) {
            return new int[] {hasOrbit() ? 1 : 0, hasNav() ? 1 : 0, stops.size(), hasPlan() ? 1 : 0};
        }
        int orbit = hasOrbit() ? 1 + (Double.isNaN(ap) ? 0 : 1) + (Double.isNaN(pe) ? 0 : 1) : 0;
        int navRows = hasNav() ? 1 + (Float.isNaN(navRate) ? 0 : 1) + (Double.isNaN(cpa) ? 0 : 1) : 0;
        int nextRows = hasNext() ? 1 + (Float.isNaN(nextDv) && nextWho.isEmpty() ? 0 : 1) : 0;
        return new int[] {orbit, navRows, nextRows, hasPlan() ? 1 : 0};
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float L = gx + 8f, M = L + 56f, R = gx + gw - 8f;
        if (face == Face.COMPACT) {
            compact(cv, gx, gy, gw, L, M, R);
            return;
        }
        int[] groups = rows();
        float yy = gy + 18f;
        boolean first = true;
        for (int g = 0; g < 4; g++) {
            int n = groups[g];
            if (n == 0) continue;
            if (!first) {
                cv.line(gx + 4f, yy - 13f, gx + gw - 4f, yy - 13f, 1f, NpColors.GRATICULE);
                yy += 4f;
            }
            first = false;
            switch (g) {
                case 0: yy = orbit(cv, L, M, R, yy); break;
                case 1: yy = navRows(cv, L, M, R, yy); break;
                case 2: yy = face == Face.WARP ? stopRows(cv, L, M, R, yy) : nextRows(cv, L, M, R, yy); break;
                default: plan(cv, L, M, R, yy); yy += P; break;
            }
        }
    }

    private float right(NpCanvas cv, float R, float yy, String v, String unit) {
        return CtxDraw.valEnd(cv, R, yy, v, unit, CtxDraw.B, NpColors.WHITE);
    }

    private float orbit(NpCanvas cv, float L, float M, float R, float yy) {
        CtxDraw.lab(cv, L, yy, "ORBIT");
        String[] a = NpFormat.range(alt);
        float vx = Double.isNaN(alt) ? R : right(cv, R, yy, a[0], a[1]);
        if (!Double.isNaN(alt)) CtxDraw.labEnd(cv, vx - 6f, yy, "ALT");
        float room = (Double.isNaN(alt) ? R : vx - 6f - cv.width("ALT", CtxDraw.LB, true, 0.6f) - 8f) - M;
        cv.text(M, yy, CtxDraw.fit(cv, body, room, CtxDraw.B, true, 0.3f), CtxDraw.B, true, NpColors.WHITE, 0.3f, NpCanvas.START);
        if (face == Face.WARP) return yy + P + 1f;
        yy += P;
        if (!Double.isNaN(ap)) {
            CtxDraw.lab(cv, M, yy, "AP");
            String[] r = NpFormat.range(ap);
            CtxDraw.val(cv, M + 26f, yy, r[0], r[1], CtxDraw.B, NpColors.WHITE);
            yy += P;
        }
        if (!Double.isNaN(pe)) {
            CtxDraw.lab(cv, M, yy, "PE");
            String[] r = NpFormat.range(pe);
            CtxDraw.val(cv, M + 26f, yy, r[0], r[1], CtxDraw.B, pe < 0 ? NpColors.AMBER : NpColors.WHITE);
            yy += P;
        }
        return yy + 1f;
    }

    private float navRows(NpCanvas cv, float L, float M, float R, float yy) {
        CtxDraw.lab(cv, L, yy, "NAV");
        cv.ring(M + 5f, yy - 4.5f, 5f, 1.5f, NpColors.MAGENTA);
        float vx = R;
        if (!Double.isNaN(navRange)) {
            String[] r = NpFormat.range(navRange);
            vx = right(cv, R, yy, r[0], r[1]);
        }
        cv.text(M + 16f, yy, CtxDraw.fit(cv, nav, vx - 8f - M - 16f, CtxDraw.B, true, 0.3f), CtxDraw.B, true, NpColors.MAGENTA, 0.3f, NpCanvas.START);
        if (face == Face.WARP) return yy + P + 1f;
        yy += P;
        if (!Float.isNaN(navRate)) {
            CtxDraw.lab(cv, M, yy, navRate >= 0f ? "CLOSING" : "OPENING");
            String[] s = NpFormat.speed(Math.abs(navRate));
            right(cv, R, yy, s[0], s[1]);
            yy += P;
        }
        if (!Double.isNaN(cpa)) {
            CtxDraw.lab(cv, M, yy, "CPA");
            String[] r = NpFormat.range(cpa);
            CtxDraw.val(cv, M + 40f, yy, r[0], r[1], CtxDraw.B, NpColors.WHITE);
            if (!Float.isNaN(cpaIn) && cpaIn > 0f) right(cv, R, yy, NpFormat.tMinus(cpaIn), null);
            yy += P;
        }
        return yy + 1f;
    }

    private void nextHead(NpCanvas cv, float L, float M, float R, float yy, String what, float in) {
        CtxDraw.lab(cv, L, yy, "NEXT");
        NpSymbols.diamond(cv, M + 5f, yy - 4.5f, 4.4f, NpColors.MAGENTA, 1.5f, false);
        float vx = R;
        if (!Float.isNaN(in) && in > 0f) vx = right(cv, R, yy, NpFormat.tMinus(in), null);
        cv.text(M + 16f, yy, CtxDraw.fit(cv, what, vx - 8f - M - 16f, CtxDraw.B, true, 0.3f), CtxDraw.B, true, NpColors.MAGENTA, 0.3f, NpCanvas.START);
    }

    private float nextRows(NpCanvas cv, float L, float M, float R, float yy) {
        nextHead(cv, L, M, R, yy, next, nextIn);
        yy += P;
        if (!Float.isNaN(nextDv) || !nextWho.isEmpty()) {
            if (!Float.isNaN(nextDv)) CtxDraw.val(cv, M + 16f, yy, NpFormat.mps(nextDv), "M/S", CtxDraw.B, NpColors.WHITE);
            if (!nextWho.isEmpty()) cv.text(R, yy, nextWho, CtxDraw.B, false, NpColors.WHITE, 0.3f, NpCanvas.END);
            yy += P;
        }
        return yy + 1f;
    }

    /** Under warp, NEXT's stops on rows of 24, each with the key that warps to it. */
    private float stopRows(NpCanvas cv, float L, float M, float R, float yy) {
        for (int i = 0; i < stops.size(); i++) {
            String[] s = stops.get(i);
            float in = stopIn.get(i);
            if (i == 0) CtxDraw.lab(cv, L, yy, "NEXT");
            NpSymbols.diamond(cv, M + 5f, yy - 4.5f, 4.4f, NpColors.MAGENTA, 1.5f, false);
            float kx = R;
            if (!s[1].isEmpty()) kx = R - CtxDraw.capW(cv, s[1]);
            if (!s[1].isEmpty()) CtxDraw.capKey(cv, kx, yy - 13f, s[1], 17f);
            float tx = kx - 8f;
            if (!Float.isNaN(in) && in > 0f) tx = CtxDraw.valEnd(cv, tx, yy, NpFormat.tMinus(in), null, CtxDraw.B, NpColors.WHITE) - 8f;
            cv.text(M + 16f, yy, CtxDraw.fit(cv, s[0], tx - M - 16f, CtxDraw.B, true, 0.3f), CtxDraw.B, true, NpColors.MAGENTA, 0.3f, NpCanvas.START);
            yy += 24f;
        }
        return yy - 24f + P + 1f;
    }

    private void plan(NpCanvas cv, float L, float M, float R, float yy) {
        CtxDraw.lab(cv, L, yy, "PLAN");
        CtxDraw.lab(cv, M, yy, "ΔV");
        CtxDraw.val(cv, M + 22f, yy, NpFormat.mps(planDv), "M/S", CtxDraw.B, NpColors.WHITE);
        if (Double.isNaN(planLeaves)) return;
        boolean shortOf = planLeaves < 0;
        String[] v = {NpFormat.mps(Math.abs(planLeaves)), "M/S"};
        float vx = CtxDraw.valEnd(cv, R, yy, v[0], v[1], CtxDraw.B, shortOf ? NpColors.AMBER : NpColors.WHITE);
        if (shortOf) cv.text(vx - 6f, yy, "SHORT", CtxDraw.LB, true, NpColors.AMBER, 0.6f, NpCanvas.END);
        else CtxDraw.labEnd(cv, vx - 6f, yy, "LEAVES");
    }

    /** One line: the next event within 30 minutes, else the Nav and its range. */
    private void compact(NpCanvas cv, float gx, float gy, float gw, float L, float M, float R) {
        float yy = gy + 16.5f;
        if (hasNext() && !Float.isNaN(nextIn) && nextIn <= 1800f) {
            nextHead(cv, L, M, R, yy, next, nextIn);
            return;
        }
        float vx = R;
        CtxDraw.lab(cv, L, yy, "NAV");
        cv.ring(M + 5f, yy - 4.5f, 5f, 1.5f, NpColors.MAGENTA);
        if (!Double.isNaN(navRange)) {
            String[] r = NpFormat.range(navRange);
            vx = right(cv, R, yy, r[0], r[1]);
        }
        cv.text(M + 16f, yy, CtxDraw.fit(cv, nav, vx - 8f - M - 16f, CtxDraw.B, true, 0.3f), CtxDraw.B, true, NpColors.MAGENTA, 0.3f, NpCanvas.START);
    }
}
