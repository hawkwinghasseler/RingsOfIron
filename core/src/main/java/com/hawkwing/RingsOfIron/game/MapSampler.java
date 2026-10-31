package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.utils.FloatArray;
import com.ringsofiron.uihud.providers.OrbitPathProvider;

/**
 * Samples a curve for the orbit map: evenly in its parameter first, then, where a span passes through
 * the map's view, halved again and again until its chord lies within {@link #TOLERANCE} of a pixel of
 * the curve. Zoomed in on an orbit hundreds of thousands of kilometres round, the even samples alone
 * are chords that cut tens of kilometres inside it, and they swing about as the orbit's periapsis does
 * (a near-circular orbit's periapsis wanders every frame), so the line jumped about; refined, it stays
 * on the orbit wherever it is drawn.
 *
 * <p>Points go out relative to the path's {@link OrbitPathProvider.OrbitPath#originX origin}, so floats
 * keep their precision zoomed in far from the body. With no view (a path asked for outside a map) the
 * origin is the body and nothing is refined.
 */
final class MapSampler {
    /** How far a chord may stray from the curve, pixels. */
    static final double TOLERANCE = 0.25;
    /** How many times a span may be halved: a 128th of an orbit to well under a pixel at the closest zoom. */
    static final int MAX_DEPTH = 24;
    /** Points one curve may add by refining, a guard against pathological curves. */
    static final int MAX_EXTRA = 4096;
    /** How far past the view's edge spans are refined, as a share of its reach: the view moves on a little before it is drawn. */
    static final double MARGIN = 1.5;

    /** A curve through the map's frame: its position at parameter {@code u}, meters, into {@code xy}. */
    interface Curve {
        void at(double u, double[] xy);
    }

    /** Never refines; points relative to the body. */
    static final MapSampler NONE = new MapSampler();

    private double viewX, viewY, metersPerPixel, reach;
    private double originX, originY;
    private int budget;
    private final double[] xy = new double[2];

    /** Takes the map's view from {@code path} and sets its origin to the view's middle. */
    void setView(OrbitPathProvider.OrbitPath path) {
        if (path.viewScale > 0 && Double.isFinite(path.viewX) && Double.isFinite(path.viewY)) {
            metersPerPixel = path.viewScale;
            viewX = path.viewX;
            viewY = path.viewY;
            reach = Math.max(1, path.viewReach) * metersPerPixel * MARGIN;
            originX = viewX;
            originY = viewY;
        } else {
            metersPerPixel = 0;
            originX = originY = 0;
        }
        path.originX = originX;
        path.originY = originY;
    }

    double originX() {
        return originX;
    }

    double originY() {
        return originY;
    }

    /** Adds the point (x, y), meters in the map's frame, relative to the origin. */
    void add(double x, double y, FloatArray out) {
        out.add((float) (x - originX), (float) (y - originY));
    }

    /**
     * {@code c} from {@code u0} to {@code u1} in {@code segments} even steps, each refined where it
     * crosses the view, into {@code out}. The even samples are always there, so a path asked for
     * without a view has {@code segments + 1} points.
     */
    void sample(Curve c, double u0, double u1, int segments, FloatArray out) {
        budget = MAX_EXTRA;
        c.at(u0, xy);
        double pu = u0, px = xy[0], py = xy[1];
        add(px, py, out);
        for (int i = 1; i <= segments; i++) {
            double u = u0 + (u1 - u0) * i / segments;
            c.at(u, xy);
            double x = xy[0], y = xy[1];
            if (metersPerPixel > 0) refine(c, pu, px, py, u, x, y, 0, out);
            add(x, y, out);
            pu = u;
            px = x;
            py = y;
        }
    }

    /** The points between (ax, ay) at {@code ua} and (bx, by) at {@code ub}, not those two, as needed. */
    private void refine(Curve c, double ua, double ax, double ay, double ub, double bx, double by, int depth, FloatArray out) {
        if (depth >= MAX_DEPTH || budget <= 0) return;
        double um = (ua + ub) / 2;
        c.at(um, xy);
        double mx = xy[0], my = xy[1];
        double dx = bx - ax, dy = by - ay;
        double len = Math.hypot(dx, dy);
        double cx = (ax + bx) / 2, cy = (ay + by) / 2;
        // The span lies within a circle about its chord's middle that takes in the chord and its bulge
        // (twice the midpoint's, with room: a conic's arc between samples bends one way)
        double bound = len / 2 + 2 * Math.hypot(mx - cx, my - cy);
        if (Math.hypot(cx - viewX, cy - viewY) > reach + bound) return;
        double off = len > 0 ? Math.abs(dx * (my - ay) - dy * (mx - ax)) / len : Math.hypot(mx - ax, my - ay);
        if (off <= TOLERANCE * metersPerPixel) return;
        budget--;
        refine(c, ua, ax, ay, um, mx, my, depth + 1, out);
        add(mx, my, out);
        refine(c, um, mx, my, ub, bx, by, depth + 1, out);
    }
}
