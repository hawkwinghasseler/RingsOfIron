package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * Symbology: contact shapes, lock brackets, the flight path markers, the lead reticle and the rest
 * of what the mockups draw over the world and on the scopes (lib.js {@code sym} and kit.js
 * {@code W}). The {@code halo} forms draw a dark outline first, so a symbol reads over stars and
 * planets alike; the plain forms are for the glass.
 */
public final class NpSymbols {
    private static final float[] PTS = new float[16];

    private NpSymbols() {}

    /** What a contact is, which picks its shape and colour. */
    public enum Kind { HOSTILE, FRIENDLY, NEUTRAL, UNKNOWN, STATION, DERELICT, MISSILE, OWN_MISSILE }

    public static Color colorOf(Kind k) {
        switch (k) {
            case HOSTILE: case MISSILE: return NpColors.RED;
            case FRIENDLY: case OWN_MISSILE: return NpColors.CYAN;
            case UNKNOWN: return NpColors.AMBER;
            case DERELICT: return NpColors.DIM;
            default: return NpColors.WHITE;
        }
    }

    // ---- shapes, one stroke ---------------------------------------------------------------------

    public static void circle(NpCanvas cv, float x, float y, float r, Color c, float w, boolean filled) {
        if (filled) cv.disc(x, y, 2f * r, c);
        cv.ring(x, y, r, w, c);
    }

    public static void square(NpCanvas cv, float x, float y, float r, Color c, float w, boolean filled) {
        if (filled) cv.fill(x - r, y - r, 2f * r, 2f * r, c);
        cv.stroke(x - r, y - r, 2f * r, 2f * r, w, c);
    }

    public static void diamond(NpCanvas cv, float x, float y, float r, Color c, float w, boolean filled) {
        float d = r * 1.25f;
        float[] p = {x, y - d, x + d, y, x, y + d, x - d, y};
        if (filled) cv.fillPoly(p, c);
        cv.poly(p, true, w, c);
    }

    public static void quatrefoil(NpCanvas cv, float x, float y, float r, Color c, float w) {
        float q = r * 0.55f;
        cv.arc(x, y - q, q, 270f, 450f, w, c, 1f);
        cv.arc(x + q, y, q, 0f, 180f, w, c, 1f);
        cv.arc(x, y + q, q, 90f, 270f, w, c, 1f);
        cv.arc(x - q, y, q, 180f, 360f, w, c, 1f);
    }

    public static void station(NpCanvas cv, float x, float y, float r, Color c, float w) {
        cv.stroke(x - r, y - r, 2f * r, 2f * r, w, c);
        cv.disc(x, y, 3.2f, c);
    }

    /** A dashed square: a derelict, or a ship that has struck. */
    public static void derelict(NpCanvas cv, float x, float y, float r, Color c, float w) {
        cv.strokeDashed(x - r, y - r, 2f * r, 2f * r, w, c, 3f, 2.5f);
    }

    /** An arrowhead pointing along bearing {@code a}, {@code s} from its centre to its tip. */
    public static void chevron(NpCanvas cv, float x, float y, float a, Color c, float w, float s) {
        float ca = MathUtils.cosDeg(a), sa = MathUtils.sinDeg(a);
        float[] p = {-s, 0.7f * s, 0f, -s, s, 0.7f * s, 0f, 0.25f * s};
        float[] out = new float[8];
        for (int i = 0; i < 4; i++) {
            float dx = p[i * 2], dy = p[i * 2 + 1];
            out[i * 2] = x + dx * ca - dy * sa;
            out[i * 2 + 1] = y + dx * sa + dy * ca;
        }
        cv.poly(out, true, w, c);
    }

    /** A filled arrowhead, for one's own ship on a scope. */
    public static void chevronFilled(NpCanvas cv, float x, float y, float a, Color c, float s) {
        float ca = MathUtils.cosDeg(a), sa = MathUtils.sinDeg(a);
        float[] p = {0f, -s, s, 0.7f * s, 0f, 0.25f * s, -s, 0.7f * s};
        float[] out = new float[8];
        for (int i = 0; i < 4; i++) {
            float dx = p[i * 2], dy = p[i * 2 + 1];
            out[i * 2] = x + dx * ca - dy * sa;
            out[i * 2 + 1] = y + dx * sa + dy * ca;
        }
        // concave: two triangles from the tip
        cv.fillPoly(new float[] {out[0], out[1], out[2], out[3], out[4], out[5]}, c);
        cv.fillPoly(new float[] {out[0], out[1], out[4], out[5], out[6], out[7]}, c);
    }

    /** The shape of a contact of kind {@code k}, {@code r} across its middle. */
    public static void shape(NpCanvas cv, Kind k, float x, float y, float r, Color c, float w, boolean filled, float heading) {
        switch (k) {
            case HOSTILE: diamond(cv, x, y, r, c, w, filled); break;
            case FRIENDLY: circle(cv, x, y, r, c, w, filled); break;
            case UNKNOWN: quatrefoil(cv, x, y, r * 9f / 7f, c, w); break;
            case STATION: station(cv, x, y, r, c, w); break;
            case DERELICT: derelict(cv, x, y, r, c, w); break;
            case MISSILE: case OWN_MISSILE: chevron(cv, x, y, heading, c, w, r * 6f / 7f); break;
            default: square(cv, x, y, r, c, w, filled); break;
        }
    }

    /** Lock brackets: four corners {@code r} out from the centre, arms {@code k} long. */
    public static void brackets(NpCanvas cv, float x, float y, float r, float k, Color c, float w) {
        corner(cv, x - r, y - r + k, x - r, y - r, x - r + k, y - r, c, w);
        corner(cv, x + r - k, y - r, x + r, y - r, x + r, y - r + k, c, w);
        corner(cv, x + r, y + r - k, x + r, y + r, x + r - k, y + r, c, w);
        corner(cv, x - r + k, y + r, x - r, y + r, x - r, y + r - k, c, w);
    }

    private static void corner(NpCanvas cv, float x1, float y1, float x2, float y2, float x3, float y3, Color c, float w) {
        PTS[0] = x1; PTS[1] = y1; PTS[2] = x2; PTS[3] = y2; PTS[4] = x3; PTS[5] = y3;
        cv.poly(PTS, 6, false, w, c);
    }

    /** The prograde marker: a ring, three ticks and a dot. */
    public static void prograde(NpCanvas cv, float x, float y, Color c, float w) {
        cv.ring(x, y, 7f, w, c);
        cv.line(x, y - 7f, x, y - 13f, w, c);
        cv.line(x - 7f, y, x - 13f, y, w, c);
        cv.line(x + 7f, y, x + 13f, y, w, c);
        cv.disc(x, y, 2.4f, c);
    }

    /** The retrograde marker: a ring crossed. */
    public static void retrograde(NpCanvas cv, float x, float y, Color c, float w) {
        cv.ring(x, y, 7f, w, c);
        cv.line(x - 5f, y - 5f, x + 5f, y + 5f, w, c);
        cv.line(x - 5f, y + 5f, x + 5f, y - 5f, w, c);
    }

    /** The lead reticle: a ring with four ticks, where the gun's solution puts the shot. */
    public static void reticle(NpCanvas cv, float x, float y, Color c, float w) {
        cv.ring(x, y, 11.5f, w, c);
        cv.line(x - 15f, y, x - 11.5f, y, w, c);
        cv.line(x + 11.5f, y, x + 15f, y, w, c);
        cv.line(x, y - 15f, x, y - 11.5f, w, c);
        cv.line(x, y + 11.5f, x, y + 15f, w, c);
    }

    /** Where a round will land, or landed: a ring crossed. */
    public static void impact(NpCanvas cv, float x, float y, float r, Color c, float w) {
        float d = r * 0.62f;
        cv.line(x - d, y - d, x + d, y + d, w, c);
        cv.line(x - d, y + d, x + d, y - d, w, c);
        cv.ring(x, y, r, w, c);
    }

    /** A collision warning: a diamond with a cross in it. */
    public static void collision(NpCanvas cv, float x, float y, Color c, float w) {
        cv.poly(new float[] {x, y - 10f, x + 10f, y, x, y + 10f, x - 10f, y}, true, w, c);
        cv.line(x - 4f, y, x + 4f, y, w, c);
        cv.line(x, y - 4f, x, y + 4f, w, c);
    }

    /** The point a planned burn starts at: an open diamond (B612 has no lozenge glyph). */
    public static void burnMark(NpCanvas cv, float x, float y, float r, Color c, float w, boolean filled) {
        float[] p = {x, y - r, x + r, y, x, y + r, x - r, y};
        if (filled) cv.fillPoly(p, c);
        cv.poly(p, true, w, c);
    }

    /** A filled triangle pointing up (B612 has no triangle glyph either). */
    public static void triangle(NpCanvas cv, float x, float y, float r, Color c) {
        cv.fillPoly(new float[] {x, y - r, x + r, y + r * 0.8f, x - r, y + r * 0.8f}, c);
    }

    // ---- haloed, for the world ------------------------------------------------------------------

    private static final float HALO_W = 2f;

    public static void haloShape(NpCanvas cv, Kind k, float x, float y, float r, Color c, float w, boolean filled, float heading) {
        shape(cv, k, x, y, r, NpColors.HALO, w + HALO_W, filled, heading);
        shape(cv, k, x, y, r, c, w, filled, heading);
    }

    public static void haloBrackets(NpCanvas cv, float x, float y, float r, float k, Color c, float w) {
        brackets(cv, x, y, r, k, NpColors.HALO, w + HALO_W);
        brackets(cv, x, y, r, k, c, w);
    }

    public static void haloRing(NpCanvas cv, float x, float y, float r, Color c, float w) {
        cv.ring(x, y, r, w + HALO_W, NpColors.HALO);
        cv.ring(x, y, r, w, c);
    }

    public static void haloPrograde(NpCanvas cv, float x, float y, Color c) {
        prograde(cv, x, y, NpColors.HALO, 1.5f + HALO_W);
        prograde(cv, x, y, c, 1.5f);
    }

    public static void haloRetrograde(NpCanvas cv, float x, float y, Color c) {
        retrograde(cv, x, y, NpColors.HALO, 1.5f + HALO_W);
        retrograde(cv, x, y, c, 1.5f);
    }

    public static void haloReticle(NpCanvas cv, float x, float y, Color c) {
        reticle(cv, x, y, NpColors.HALO, 1.5f + HALO_W);
        reticle(cv, x, y, c, 1.5f);
    }

    public static void haloImpact(NpCanvas cv, float x, float y, float r) {
        impact(cv, x, y, r, NpColors.HALO, 1.8f + HALO_W);
        impact(cv, x, y, r, NpColors.RED, 1.8f);
    }

    public static void haloChevron(NpCanvas cv, float x, float y, float a, Color c, float s) {
        chevron(cv, x, y, a, NpColors.HALO, 1.5f + HALO_W, s);
        chevron(cv, x, y, a, c, 1.5f, s);
    }

    public static void haloCollision(NpCanvas cv, float x, float y) {
        collision(cv, x, y, NpColors.HALO, 1.5f + HALO_W);
        collision(cv, x, y, NpColors.AMBER, 1.5f);
    }

    /** An arrow from ({@code x}, {@code y}) along bearing {@code deg}, {@code len} long. */
    public static void haloArrow(NpCanvas cv, float x, float y, float deg, float len, Color c, float w) {
        float r = deg * MathUtils.degreesToRadians, ex = x + len * MathUtils.sin(r), ey = y - len * MathUtils.cos(r);
        float ax = ex - 10f * MathUtils.sin(r + 0.45f), ay = ey + 10f * MathUtils.cos(r + 0.45f);
        float bx = ex - 10f * MathUtils.sin(r - 0.45f), by = ey + 10f * MathUtils.cos(r - 0.45f);
        for (int pass = 0; pass < 2; pass++) {
            Color cc = pass == 0 ? NpColors.HALO : c;
            float ww = pass == 0 ? w + HALO_W : w;
            cv.line(x, y, ex, ey, ww, cc);
            cv.poly(new float[] {ax, ay, ex, ey, bx, by}, false, ww, cc);
        }
    }

    /** An edge arrow: a chevron at the screen's edge pointing along bearing {@code deg}. */
    public static void haloEdge(NpCanvas cv, float x, float y, float deg, Color c) {
        float ca = MathUtils.cosDeg(deg), sa = MathUtils.sinDeg(deg);
        float[] p = {-7f, 6f, 0f, -6f, 7f, 6f};
        float[] out = new float[6];
        for (int i = 0; i < 3; i++) {
            out[i * 2] = x + p[i * 2] * ca - p[i * 2 + 1] * sa;
            out[i * 2 + 1] = y + p[i * 2] * sa + p[i * 2 + 1] * ca;
        }
        cv.poly(out, false, 2f + HALO_W, NpColors.HALO);
        cv.poly(out, false, 2f, c);
    }
}
