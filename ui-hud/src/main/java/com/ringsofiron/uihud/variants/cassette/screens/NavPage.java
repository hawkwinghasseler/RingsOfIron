package com.ringsofiron.uihud.variants.cassette.screens;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrbitInfoProvider.OrbitInfo;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardText;

/**
 * The helm panel's Navigation tab (the Stations spec's 10.2, NAV and PLAN): the orbit and the body it is
 * about, an impact in red; the navigation target with its range, closing and closest approach; then the
 * plan, its next burn, its delta-v against what the tanks hold and what is left to reach the nearest port
 * after it. Its soft key opens the plot, where burns are planned and a transfer accepted.
 */
final class NavPage extends FactsPage {
    /** Puts the map up: set by the windows. */
    Runnable openPlot;
    private HudData data;
    private boolean own;
    private String right;

    NavPage() {
        super("NAV", "NAV");
    }

    @Override
    boolean offered() {
        return own;
    }

    @Override
    void refresh(Boards b) {
        data = b.data;
        own = data != null && ConsolePanels.ship(data) == data.ctx.ship;
        begin();
        if (!own) return;
        OrbitInfo o = data.orbitInfo == null ? null : data.orbitInfo.getPlayerOrbit();
        NavReadout n = data.navReadout;
        right = n.proposal ? "PROPOSAL" : n.burnsLeft > 0 ? n.burnsLeft + (n.burnsLeft == 1 ? " BURN" : " BURNS") : null;

        if (o == null) head("ORBIT", "NONE");
        else {
            boolean escape = escape(o);
            head("ORBIT", Fmt.upper(o.bodyName));
            fact("ALTITUDE", dist(o.altitude), AMBER);
            fact("APOAPSIS", escape ? "ESCAPE" : dist(o.apoapsis), AMBER);
            fact("PERIAPSIS", dist(o.periapsis), o.impactWarning ? Yard.P_RED : AMBER);
            fact("PERIOD", escape ? "--" : YardText.clock(o.period), AMBER);
            if (o.impactWarning) text("ON COURSE TO IMPACT", Yard.P_RED);
        }

        head("TARGET", n.present ? Fmt.upper(n.name) : "NONE");
        if (n.present) {
            fact("RANGE", dist(n.distance), AMBER);
            if (!Float.isNaN(n.closing)) fact("CLOSING", mps(n.closing), AMBER);
            if (!Double.isNaN(n.cpaDistance)) fact("CPA", dist(n.cpaDistance) + (Float.isNaN(n.cpaIn) ? "" : " IN " + YardText.clock(n.cpaIn)), AMBER);
        } else text("NO NAVIGATION TARGET", DIM);

        head("PLAN", right == null ? "NO PLAN" : right);
        if (n.burning) text("BURN " + n.burnNumber + " BURNING, " + mps(n.burnToGo) + " TO GO", AMBER);
        else if (!Float.isNaN(n.burnIn)) {
            text("BURN " + n.burnNumber + (n.burnsPlanned > 1 ? " OF " + n.burnsPlanned : "") + " IN " + YardText.clock(n.burnIn)
                    + (Float.isNaN(n.burnDeltaV) ? "" : ", " + mps(n.burnDeltaV)), AMBER);
        } else text(n.proposal ? "A TRANSFER IS PROPOSED" : "NO BURNS PLANNED", DIM);
        double left = data.flightStatus.deltaVLeft, need = n.planDeltaV;
        if (need > 0 && !Double.isNaN(left)) {
            boolean shortOf = !Double.isInfinite(left) && need > left;
            fact("NEEDS", TanksPage.mps(need) + " OF " + TanksPage.mps(left), shortOf ? Yard.P_RED : AMBER);
            double home = data.flightStatus.deltaVHome;
            if (!Double.isNaN(home) && !Double.isInfinite(left)) {
                double after = left - need;
                fact("AFTER", TanksPage.mps(after) + (after < home ? ", SHORT OF A PORT" : ""), after < home ? Yard.P_RED : DIM);
            }
        } else if (!Double.isNaN(left)) fact("DELTA-V", TanksPage.mps(left), DIM);
        keepSelection();
    }

    static boolean escape(OrbitInfo o) {
        return Double.isInfinite(o.period) || Double.isNaN(o.apoapsis) || Double.isInfinite(o.apoapsis) || o.apoapsis < 0;
    }

    /** "850 M", "2.8 KM", "1,204 KM". */
    static String dist(double m) {
        if (Double.isNaN(m) || Double.isInfinite(m)) return "--";
        double a = Math.abs(m);
        String sign = m < 0 ? "-" : "";
        if (a < 1000) return sign + Math.round(a) + " M";
        if (a < 100_000) return sign + String.format(java.util.Locale.ROOT, "%.1f KM", a / 1000);
        return sign + YardText.thousands(a / 1000) + " KM";
    }

    /** "3.9 M/S", "210 M/S". */
    static String mps(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "--";
        return Math.abs(v) < 100 ? String.format(java.util.Locale.ROOT, "%.1f M/S", v) : Math.round(v) + " M/S";
    }

    @Override
    String titleRight() {
        return own ? right : null;
    }

    @Override
    void keys() {
        if (!own) return;
        softKey("OPEN PLOT", openPlot == null ? "THE PLOT CAN'T BE OPENED HERE" : null);
        if (data.navReadout.proposal) note("THE PLOT ACCEPTS THE TRANSFER");
    }

    @Override
    void press(int k) {
        if (openPlot != null) openPlot.run();
    }
}
