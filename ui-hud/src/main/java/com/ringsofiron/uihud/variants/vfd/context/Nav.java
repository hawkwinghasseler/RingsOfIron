package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrbitInfoProvider.OrbitInfo;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

/**
 * H2, the navigation block (functional-context 7, H2): a slab of labelled rows in the left column
 * under P5. Labels are legends, words dot matrix, numbers seven segments with their units as legends.
 * The full face at the helm in cruise: the orbit and its apsides, the Nav with its range, rate and
 * closest approach, the next event and who flies it, and the plan with what it leaves or how short it
 * falls. Under warp the orbit and the Nav take a line each and the next event carries its warp key;
 * in combat one line, the next event if it is due within 30 minutes, else the Nav. Docked it shows a
 * planned departure. Hidden in an approach: H3 names the port and has the distance.
 */
final class Nav {
    enum Face {
        NONE, FULL, WARP, COMPACT
    }

    /** The row kinds, in the order the full face stacks them. */
    static final int ORBIT = 0, AP = 1, PE = 2, NAV = 3, NAV_RATE = 4, NAV_CPA = 5, NEXT = 6, NEXT_BY = 7, PLAN = 8,
            ORBIT_LINE = 9, NAV_LINE = 10, NEXT_WARP = 11;
    /** Combat's one line shows the next event only when it is this close, s. */
    static final float SOON = 30 * 60f;
    static final int MAX_ROWS = 9;
    private static final String[] WHAT = {"H2 orbit", "H2 apoapsis", "H2 periapsis", "H2 nav", "H2 nav rate", "H2 closest",
            "H2 next", "H2 next by", "H2 plan", "H2 orbit line", "H2 nav line", "H2 next warp"};

    Face face = Face.NONE;
    final int[] rows = new int[MAX_ROWS];
    int count;

    String body = "", alt = "", ap = "", pe = "", altUnit = "KM", apUnit = "KM", peUnit = "KM";
    String nav = "", navRange = "", navUnit = "KM", rate = "", cpa = "", cpaUnit = "KM";
    boolean opening;
    float cpaIn = Float.NaN;
    String next = "", nextDv = "", by = "", warpKey = "";
    float nextIn = Float.NaN;
    boolean burning;
    String need = "", leaves = "";
    boolean shortfall;

    /** The slab's height on the grid, 0 when hidden. */
    float height() {
        return count == 0 ? 0f : count * Geo.ROW + 6f;
    }

    void update(HudData d, Situations s) {
        count = 0;
        face = face(s);
        if (face == Face.NONE) return;
        read(d);
        OrbitInfo o = d.orbitInfo == null ? null : d.orbitInfo.getPlayerOrbit();
        NavReadout n = d.navReadout;
        boolean hasNav = n.present && !nav.isEmpty();
        boolean hasNext = !next.isEmpty();
        boolean hasPlan = !need.isEmpty();
        switch (face) {
            case FULL:
                if (s.docked || s.latched) {
                    // Docked: only a planned departure
                    if (!hasNext) return;
                    add(NEXT);
                    add(NEXT_BY);
                    if (hasPlan) add(PLAN);
                    return;
                }
                if (o != null) {
                    add(ORBIT);
                    add(AP);
                    add(PE);
                }
                if (hasNav) {
                    add(NAV);
                    add(NAV_RATE);
                    if (!Double.isNaN(n.cpaDistance)) add(NAV_CPA);
                }
                if (hasNext) {
                    add(NEXT);
                    add(NEXT_BY);
                }
                if (hasPlan) add(PLAN);
                return;
            case WARP:
                if (o != null) add(ORBIT_LINE);
                if (hasNav) add(NAV_LINE);
                if (hasNext) add(NEXT_WARP);
                if (hasPlan) add(PLAN);
                return;
            case COMPACT:
                if (hasNext && !Float.isNaN(nextIn) && nextIn <= SOON) add(NEXT);
                else if (hasNav) add(NAV_LINE);
                return;
            default:
        }
    }

    private void add(int kind) {
        if (count < MAX_ROWS) rows[count++] = kind;
    }

    /** Which face H2 shows in this situation (4.6), or none. */
    static Face face(Situations s) {
        if (s.seat != Situations.Seat.HELM || s.fullScreen || s.approach) return Face.NONE;
        if (s.warp) return Face.WARP;
        if (s.combat) return Face.COMPACT;
        return Face.FULL;
    }

    private void read(HudData d) {
        OrbitInfo o = d.orbitInfo == null ? null : d.orbitInfo.getPlayerOrbit();
        if (o != null) {
            body = Fmt.name(o.bodyName, 7);
            alt = km(o.altitude, 5);
            altUnit = unit(o.altitude);
            ap = km(o.apoapsis, 5);
            apUnit = unit(o.apoapsis);
            pe = km(o.periapsis, 5);
            peUnit = unit(o.periapsis);
        }
        NavReadout n = d.navReadout;
        nav = n.present ? Fmt.name(n.name, 16) : "";
        navRange = Fmt.range(n.distance, 5);
        navUnit = Fmt.km(n.distance) ? "KM" : "M";
        opening = !Float.isNaN(n.closing) && n.closing < 0f;
        rate = Float.isNaN(n.closing) ? "" : Fmt.fit(Math.round(Math.abs(n.closing)), 4, 0);
        cpa = Fmt.range(n.cpaDistance, 4);
        cpaUnit = Fmt.km(n.cpaDistance) ? "KM" : "M";
        cpaIn = n.cpaIn;

        // NEXT: the next burn, the one being flown, or an impact with no burn to stop it
        FlightStatus f = d.flightStatus;
        burning = n.burning;
        next = "";
        nextIn = Float.NaN;
        nextDv = "";
        by = "";
        if (n.burnsLeft > 0 || n.burning) {
            int of = Math.max(n.burnsPlanned, n.burnNumber);
            next = "BURN " + Math.max(1, n.burnNumber) + (of > 1 ? "/" + of : "");
            nextIn = n.burning ? Float.NaN : n.burnStartIn;
            float dv = n.burning && !Float.isNaN(n.burnToGo) ? n.burnToGo : n.burnDeltaV;
            nextDv = Float.isNaN(dv) ? "" : Fmt.fit(Math.round(dv), 4, 0);
            by = by(d);
        } else if (o != null && o.impactWarning) {
            next = "IMPACT";
        }
        warpKey = d.key("WARP_TO_BURN");
        double planned = n.planDeltaV > 0 ? n.planDeltaV : f.deltaVPlanned;
        need = "";
        leaves = "";
        shortfall = false;
        if (planned > 0 && n.burnsLeft > 0) {
            need = Fmt.fit(Math.round(planned), 5, 0);
            if (!Double.isNaN(f.deltaVLeft) && !Double.isInfinite(f.deltaVLeft)) {
                double left = f.deltaVLeft - planned;
                shortfall = left < 0;
                leaves = Fmt.fit(Math.round(Math.abs(left)), 5, 0);
            }
        }
    }

    /** Who flies the next burn: the helm on its order, the autopilot, the captain, or nobody. */
    static String by(HudData d) {
        if (d.orderState.autopilot == Autopilot.ON) return "AUTOPILOT";
        if (d.orderState.helm == HelmOrder.FLY_BURNS) return "HELM FLIES IT";
        String who = d.ctx.helmsman;
        if (who.equalsIgnoreCase("you")) return "YOU FLY IT";
        if (who.isEmpty()) return "NOBODY AT HELM";
        return Fmt.cut(who, 13);
    }

    /** Metres above the surface as kilometres in {@code cells}: tenths under 100 km. */
    static String km(double meters, int cells) {
        if (Double.isNaN(meters)) return "";
        if (Math.abs(meters) < 1000) return Fmt.fit(Math.round(meters), cells, 0);
        double k = meters / 1000.0;
        return Fmt.fit(k, cells, Math.abs(k) < 100 ? 1 : 0);
    }

    static String unit(double meters) {
        return Double.isNaN(meters) || Math.abs(meters) >= 1000 ? "KM" : "M";
    }

    // ------------------------------------------------------------------ painting, on the grid

    /** Paints the slab with its top at {@code y} in the left column. */
    void paint(Filament f, float y) {
        if (count == 0) return;
        float x = Geo.LX;
        f.slab(x, y, Geo.CW, height(), 5f, 1, true);
        float ry = y + 3f;
        for (int i = 0; i < count; i++) {
            row(f, x, ry, rows[i]);
            ry += Geo.ROW;
        }
    }

    private void row(Filament f, float x, float ry, int kind) {
        float b = Geo.base(ry), vx = x + 52f, cx = vx, limit = x + Geo.CW - 2f;
        switch (kind) {
            case ORBIT:
            case ORBIT_LINE:
                label(f, x, b, "ORBIT");
                cx = Rows.txt(f, cx, b, body, 7);
                cx = Rows.lab(f, cx, b, "ALT");
                cx = Rows.num(f, cx, b, alt, 5) - 2f;
                cx = Rows.lab(f, cx, b, altUnit);
                break;
            case AP:
                cx = Rows.lab(f, cx, b, "AP");
                cx = Rows.num(f, cx, b, ap, 5) - 2f;
                cx = Rows.lab(f, cx, b, apUnit);
                break;
            case PE:
                cx = Rows.lab(f, cx, b, "PE");
                cx = Rows.num(f, cx, b, pe, 5) - 2f;
                cx = Rows.lab(f, cx, b, peUnit);
                break;
            case NAV:
                label(f, x, b, "NAV");
                cx = Rows.txt(f, cx, b, nav, 16);
                break;
            case NAV_LINE:
                label(f, x, b, "NAV");
                cx = Rows.txt(f, cx, b, nav, 16);
                cx = Rows.num(f, cx, b, navRange, 5) - 2f;
                cx = Rows.lab(f, cx, b, navUnit);
                break;
            case NAV_RATE:
                cx = Rows.num(f, cx, b, navRange, 5) - 2f;
                cx = Rows.lab(f, cx, b, navUnit) + 4f;
                cx = Rows.state(f, cx, b, "CLOSING", !opening && !rate.isEmpty(), Phosphor.ZINC) - 2f;
                cx = Rows.state(f, cx, b, "OPENING", opening, Phosphor.ZINC) - 2f;
                cx = Rows.num(f, cx, b, rate, 4) - 2f;
                cx = Rows.lab(f, cx, b, "M/S");
                break;
            case NAV_CPA:
                cx = Rows.lab(f, cx, b, "CLOSEST");
                cx = Rows.num(f, cx, b, cpa, 4) - 2f;
                cx = Rows.lab(f, cx, b, cpaUnit) + 6f;
                cx = Rows.time(f, cx, b, cpaIn);
                break;
            case NEXT:
                label(f, x, b, "NEXT");
                cx = Rows.txt(f, cx, b, next, 9);
                cx = burning ? Rows.state(f, cx, b, "BURNING", true, Phosphor.ZINC) : Rows.time(f, cx, b, nextIn);
                break;
            case NEXT_WARP:
                label(f, x, b, "NEXT");
                cx = Rows.txt(f, cx, b, next, 9);
                cx = burning ? Rows.state(f, cx, b, "BURNING", true, Phosphor.ZINC) : Rows.time(f, cx, b, nextIn);
                // Its warp-to key, on the row's end
                if (!warpKey.isEmpty() && !burning && !Float.isNaN(nextIn)) {
                    float kw = f.keycapWidth(warpKey, 16f, 11f);
                    float kx = x + Geo.CW - 8f - kw - 12f;
                    f.keycap(kx, ry + 2f, warpKey, 16f, 11f, false);
                    f.legend(kx + kw + 3f, b, "▶", f.o().dim());
                    limit = kx - 4f;
                }
                break;
            case NEXT_BY:
                if (!nextDv.isEmpty()) {
                    cx = Rows.num(f, cx, b, nextDv, 4) - 2f;
                    cx = Rows.lab(f, cx, b, burning ? "M/S TO GO" : "M/S") + 6f;
                }
                cx = Rows.txt(f, cx, b, by, 14, Phosphor.ZINC, by.startsWith("NOBODY") ? Drive.FULL : Drive.NORM);
                break;
            case PLAN:
                label(f, x, b, "PLAN");
                cx = Rows.lab(f, cx, b, "ΔV");
                cx = Rows.num(f, cx, b, need, 5) + 2f;
                cx = Rows.state(f, cx, b, "LEAVES", !shortfall && !leaves.isEmpty(), Phosphor.ZINC) - 2f;
                cx = Rows.state(f, cx, b, "SHORT", shortfall, Phosphor.AMBER) - 2f;
                cx = Rows.num(f, cx, b, leaves, 5, shortfall ? Phosphor.AMBER : Phosphor.ZINC, shortfall ? Drive.FULL : Drive.NORM) - 2f;
                cx = Rows.lab(f, cx, b, "M/S");
                break;
            default:
        }
        Kit.fits(cx, limit, WHAT[kind]);
    }

    private static void label(Filament f, float x, float b, String t) {
        f.legend(x + 6f, b, t, f.o().dim());
    }
}
