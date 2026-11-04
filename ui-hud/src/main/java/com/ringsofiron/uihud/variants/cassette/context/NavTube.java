package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrbitInfoProvider.OrbitInfo;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * H2, the navigation tube on the left rail: where the ship is going and what happens next. One amber tube
 * with its own row names in dim phosphor (ORBIT, NAV, NEXT, PLAN), so a face of one line still says what it
 * is. The full face has the orbit, the nav target with its distance, rate and closest approach, the next
 * burn with who flies it, and the plan's delta-v against what is left; under warp each shrinks to a line;
 * in combat it is one line, the most pressing of the next burn and the nav target.
 */
final class NavTube extends YardPart {
    enum Mode {
        FULL, WARP, COMPACT
    }

    static final float W = 308f, LINE = 20f, PAD = 8f;
    private static final float NAME_W = 56f;

    /** One row of the tube: its name (only on a group's first line) and its text, and a caution mark. */
    static final class Row {
        final String name, text;
        final boolean caution;

        Row(String name, String text, boolean caution) {
            this.name = name;
            this.text = text;
            this.caution = caution;
        }
    }

    private final ContextFrame f;
    Mode mode = Mode.FULL;
    final List<Row> rows = new ArrayList<>();
    /** The PLAN line says the plan is short: the caution board leaves PLAN SHORT to it. */
    boolean planShort;

    NavTube(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** This frame's rows for {@code mode}; returns the height, 0 with nothing to show. */
    float measure(HudData d, Mode mode, int budget) {
        this.mode = mode;
        rows.clear();
        planShort = false;
        OrbitInfo o = d.orbitInfo == null ? null : d.orbitInfo.getPlayerOrbit();
        NavReadout n = d.navReadout;
        if (mode == Mode.COMPACT) {
            String next = nextLine(d, n);
            float due = burnDue(n);
            if (!next.isEmpty() && !Float.isNaN(due) && due <= 30 * 60) rows.add(new Row("NEXT", next, false));
            else if (n.present) rows.add(new Row("NAV", navLine(n, budget), false));
            return rows.isEmpty() ? 0f : PAD * 2f + LINE;
        }
        if (o != null && !o.bodyName.isEmpty()) {
            rows.add(new Row("ORBIT", o.bodyName.toUpperCase(Locale.ROOT) + " · ALT " + Kit.dist(o.altitude).toUpperCase(Locale.ROOT), o.impactWarning));
            if (mode == Mode.FULL) rows.add(new Row("", apsides(o), o.impactWarning));
        }
        if (n.present) {
            if (mode == Mode.WARP) rows.add(new Row("NAV", navLine(n, budget), false));
            else {
                rows.add(new Row("NAV", YardText.fit(n.name, budget), false));
                String rate = Float.isNaN(n.closing) ? "" : " · " + Kit.rate(n.closing).toUpperCase(Locale.ROOT);
                rows.add(new Row("", Kit.dist(n.distance).toUpperCase(Locale.ROOT) + rate, false));
                if (!Double.isNaN(n.cpaDistance) && !Float.isNaN(n.cpaIn) && n.cpaIn > 0f) {
                    rows.add(new Row("", "CLOSEST " + Kit.dist(n.cpaDistance).toUpperCase(Locale.ROOT) + " " + Kit.tMinus(n.cpaIn).toUpperCase(Locale.ROOT), false));
                }
            }
        }
        String next = nextLine(d, n);
        if (!next.isEmpty()) {
            rows.add(new Row("NEXT", next, false));
            String who = burnWho(d, n);
            if (mode == Mode.FULL && !who.isEmpty()) rows.add(new Row("", who, false));
        } else if (o != null && o.impactWarning && n.burnsLeft == 0) {
            rows.add(new Row("NEXT", "IMPACT · " + o.bodyName.toUpperCase(Locale.ROOT), true));
        }
        if (n.planDeltaV > 0) {
            double left = d.flightStatus.deltaVLeft;
            String need = "ΔV " + YardText.thousands(n.planDeltaV);
            if (Double.isNaN(left) || Double.isInfinite(left)) rows.add(new Row("PLAN", need, false));
            else if (left >= n.planDeltaV) rows.add(new Row("PLAN", need + " · LEAVES " + YardText.thousands(left - n.planDeltaV) + " M/S", false));
            else {
                planShort = true;
                rows.add(new Row("PLAN", need + " · SHORT " + YardText.thousands(n.planDeltaV - left) + " M/S", true));
            }
        }
        if (rows.isEmpty()) return 0f;
        return PAD * 2f + rows.size() * LINE;
    }

    private static String apsides(OrbitInfo o) {
        boolean escape = Double.isInfinite(o.period) || Double.isNaN(o.apoapsis) || Double.isInfinite(o.apoapsis) || o.apoapsis < 0;
        String ap = escape ? "ESCAPE" : "AP " + Kit.dist(o.apoapsis).toUpperCase(Locale.ROOT);
        return ap + " · PE " + Kit.dist(o.periapsis).toUpperCase(Locale.ROOT);
    }

    /** "SHACKLETON ICE · 1,240 KM", the name giving way first. */
    static String navLine(NavReadout n, int budget) {
        String rest = Double.isNaN(n.distance) ? "" : " · " + Kit.dist(n.distance).toUpperCase(Locale.ROOT);
        return Kit.squeeze(n.name, budget, rest);
    }

    /** Seconds to the next burn's start, NaN with none. */
    static float burnDue(NavReadout n) {
        if (n.burnsLeft == 0) return Float.NaN;
        if (n.burning) return 0f;
        return Float.isNaN(n.burnStartIn) ? n.burnIn : n.burnStartIn;
    }

    /** "BURN 1/2 · T-12:05", or "BURN 1/2 · 120 M/S TO GO" while it burns; "" with no burn. */
    static String nextLine(HudData d, NavReadout n) {
        if (n.burnsLeft == 0 || n.burnNumber <= 0) return "";
        String which = "BURN " + n.burnNumber + "/" + Math.max(n.burnNumber, n.burnsPlanned);
        if (n.burning) {
            return Float.isNaN(n.burnToGo) ? which + " · BURNING" : which + " · " + YardText.thousands(n.burnToGo) + " M/S TO GO";
        }
        float due = burnDue(n);
        return Float.isNaN(due) ? which : which + " · " + Kit.tMinus(due).toUpperCase(Locale.ROOT);
    }

    /** The burn's size and who flies it: "420 M/S · HELM FLIES IT". */
    static String burnWho(HudData d, NavReadout n) {
        String size = Float.isNaN(n.burnDeltaV) ? "" : YardText.thousands(n.burnDeltaV) + " M/S";
        String who;
        if (d.orderState.autopilot == Autopilot.ON) who = "AUTOPILOT FLIES IT";
        else if (d.orderState.helm == HelmOrder.FLY_BURNS) who = "HELM FLIES IT";
        else if (d.ctx.helmsman.equalsIgnoreCase("you")) who = "YOU FLY IT";
        else who = "";
        if (size.isEmpty()) return who;
        return who.isEmpty() ? size : size + " · " + who;
    }

    @Override
    protected void draw(YardPaint p) {
        Kit.slideRail(p, -x, true, 0f, h);
        Kit.housing(p, 0f, 0f, w, h, false);
        float tx = 6f, ty = 5f, tw = w - 12f, th = h - 10f;
        p.tube(tx, ty, tw, th, 3f, 2f, false);
        float vx = tx + NAME_W, vw = tw - NAME_W - 8f;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float base = ty + PAD - 3f + i * LINE + 15f;
            if (!r.name.isEmpty()) p.tube(r.name, tx + 8f, base, 13f, Kit.ROW_NAME, false);
            Color c = i == 0 || !r.name.isEmpty() ? Yard.P_AMBER : Yard.P_DIM;
            float lx = vx;
            if (r.caution) {
                Kit.mark(p, false, lx + 4f, base - 4.5f, 4.2f, Yard.P_AMBER);
                lx += 12f;
            }
            Kit.tubeFit(p, r.text, lx, base, 13f, c, vw - (lx - vx));
        }
        p.tubeOver(tx, ty, tw, th);
    }

    /** The characters a row's text has, after its name. */
    static int budget(YardPaint p) {
        return Kit.chars(p, W - 12f - NAME_W - 8f, 13f);
    }
}
