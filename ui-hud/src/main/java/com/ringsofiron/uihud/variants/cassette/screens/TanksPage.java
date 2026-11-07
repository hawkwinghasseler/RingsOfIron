package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

/**
 * Engineering's Tanks &amp; propulsion tab (the Stations spec's 5.5): what the tanks hold of each fluid, its
 * share and its kilograms; the delta-v the propellant still makes, what reaching the nearest station that
 * takes the ship needs, and what the plan needs, with how far short it falls; then every tank by what it
 * holds and where, a leaking one in amber and a burst one in red. The propulsion rows are the player's
 * ship's, as the flight readout has them, so a prize's tanks show without them.
 */
final class TanksPage extends ListPage {
    static final int GAS = 0, DV = 1, HEAD = 2, TANK = 3;
    /** The delta-v rows: what is left, to the nearest port, the plan. */
    static final int LEFT = 0, HOME = 1, PLANNED = 2;
    private static final Fluid[] ORDER = {Fluid.PROPELLANT, Fluid.OXYGEN, Fluid.NITROGEN};

    /** A row of the list. */
    static final class Line {
        int kind, which;
        Fluid gas;
        ShipSystems.Sys tank;
    }

    private final ShipSystems systems = new ShipSystems();
    private final Array<SubsystemComponent> found = new Array<>();
    final Array<Line> lines = new Array<>();
    private final Array<Line> pool = new Array<>();
    private ShipSubsystemRegistryComponent registry;
    private FlightStatus flight;
    private int tanks;

    TanksPage() {
        super("TANKS", "ENG", ClassicWindow.CONSOLE);
    }

    @Override
    boolean offered() {
        return tanks > 0;
    }

    @Override
    boolean fitsSide() {
        return true;
    }

    @Override
    void refresh(Boards b) {
        HudData d = b.data;
        Entity ship = ConsolePanels.ship(d);
        boolean own = d != null && ship == d.ctx.ship;
        ShipGridComponent grid = ship == null ? null : ship.getComponent(ShipGridComponent.class);
        registry = ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class);
        flight = own ? d.flightStatus : null;
        found.clear();
        if (registry != null) {
            for (int i = 0; i < registry.subsystems.size; i++) {
                SubsystemComponent s = registry.subsystems.get(i).getComponent(SubsystemComponent.class);
                if (s != null) found.add(s);
            }
        }
        systems.read(found, grid, own ? d::roomAt : null, null);
        pool.addAll(lines);
        lines.clear();
        tanks = 0;
        for (int i = 0; i < systems.all.size; i++) if (systems.all.get(i).s.type == SubsystemType.TANK) tanks++;
        for (Fluid f : ORDER) if (registry != null && TankSupply.hasTanks(registry, f)) line(GAS).gas = f;
        if (flight != null && !Double.isNaN(flight.deltaVLeft)) {
            line(DV).which = LEFT;
            if (!Double.isNaN(flight.deltaVHome)) line(DV).which = HOME;
            if (flight.deltaVPlanned > 0) line(DV).which = PLANNED;
        }
        if (tanks > 0) {
            line(HEAD);
            for (Fluid f : ORDER) {
                for (int i = 0; i < systems.all.size; i++) {
                    ShipSystems.Sys y = systems.all.get(i);
                    if (y.s.type == SubsystemType.TANK && y.s.fluid == f) line(TANK).tank = y;
                }
            }
            // A tank of something else, should a mod fit one
            for (int i = 0; i < systems.all.size; i++) {
                ShipSystems.Sys y = systems.all.get(i);
                if (y.s.type != SubsystemType.TANK) continue;
                boolean listed = false;
                for (Fluid f : ORDER) listed |= y.s.fluid == f;
                if (!listed) line(TANK).tank = y;
            }
        }
        keepSelection();
    }

    private Line line(int kind) {
        Line l = pool.size > 0 ? pool.pop() : new Line();
        l.kind = kind;
        l.which = 0;
        l.gas = null;
        l.tank = null;
        lines.add(l);
        return l;
    }

    /** What the tanks of {@code gas} can give, kg, and what they hold full. */
    private double[] held(Fluid gas) {
        double held = 0, cap = 0;
        if (registry != null) {
            Array<SubsystemComponent> list = TankSupply.tanks(registry, gas);
            held = TankSupply.available(list, gas);
            for (int i = 0; i < list.size; i++) cap += Math.max(0, list.get(i).capacity);
        }
        return new double[]{held, cap};
    }

    /** "OXYGEN" and the like, as a row names its fluid. */
    static String gas(Fluid f) {
        return f == null ? "TANK" : f.name().replace('_', ' ');
    }

    /** A tank's state: burst once wrecked, leaking under the leak's threshold of health, else nothing. */
    static String state(SubsystemComponent t) {
        if (t.isDestroyed()) return "BURST";
        if (t.health < TankSupply.LEAK_THRESHOLD) return "LEAKING";
        return "";
    }

    /** "1,850 M/S"; "FREE" for thrust that costs nothing. */
    static String mps(double v) {
        if (Double.isInfinite(v)) return "FREE";
        return Fmt.thousands(Math.max(0, Math.round(v))) + " M/S";
    }

    @Override
    int count() {
        return lines.size;
    }

    @Override
    boolean selectable(int i) {
        return lines.get(i).kind == TANK;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        Line l = lines.get(i);
        switch (l.kind) {
            case GAS: {
                double[] h = held(l.gas);
                float share = h[1] <= 0 ? 0f : (float) Math.min(1.0, h[0] / h[1]);
                Color ink = share < 0.15f ? Yard.P_RED : AMBER;
                L.at(p, 1, r, gas(l.gas), ink);
                float bx = L.cx(12), bw = 10 * Ledger.COL, top = L.rowTop(r) + 5f;
                p.frame(bx, top, bw, 9f, 1f, Ledger.DIM, 0.9f);
                if (share > 0f) p.rect(bx + 1.5f, top + 1.5f, (bw - 3f) * share, 6f, ink);
                L.right(p, 27, r, Math.round(share * 100f) + "%", ink);
                L.at(p, 28, r, Ledger.cut(Fmt.thousands(Math.round(h[0])) + " OF " + Fmt.thousands(Math.round(h[1])) + " KG", L.cols - 29), ink);
                return;
            }
            case DV: {
                if (l.which == LEFT) {
                    L.rule(p, r - 1);
                    L.at(p, 1, r, "ΔV LEFT", AMBER);
                    L.at(p, 12, r, mps(flight.deltaVLeft), AMBER);
                } else if (l.which == HOME) {
                    boolean shortOf = flight.deltaVHome > flight.deltaVLeft;
                    Color ink = shortOf ? Yard.P_RED : AMBER;
                    L.at(p, 1, r, "ΔV HOME", ink);
                    L.at(p, 12, r, mps(flight.deltaVHome), ink);
                    L.at(p, 24, r, Ledger.cut(flight.deltaVHome <= 0 ? "DOCKED" : "NEAREST PORT", L.cols - 25), DIM);
                } else {
                    boolean shortOf = flight.deltaVPlanned > flight.deltaVLeft;
                    Color ink = shortOf ? Yard.P_RED : AMBER;
                    L.at(p, 1, r, "ΔV PLAN", ink);
                    L.at(p, 12, r, mps(flight.deltaVPlanned), ink);
                    if (shortOf) L.at(p, 24, r, Ledger.cut("SHORT BY " + mps(flight.deltaVPlanned - flight.deltaVLeft), L.cols - 25), ink);
                }
                return;
            }
            case HEAD:
                L.rule(p, r - 1);
                L.at(p, 1, r, "TANKS", DIM);
                L.right(p, L.cols - 1, r, tanks == 1 ? "1 TANK" : tanks + " TANKS", DIM);
                return;
            default: {
                ShipSystems.Sys y = l.tank;
                SubsystemComponent t = y.s;
                String st = state(t);
                Color ink = st.equals("BURST") ? Yard.P_RED : !st.isEmpty() ? AMBER : AMBER;
                float share = t.capacity <= 0 ? 0f : (float) Math.min(1.0, Math.max(0, t.contents) / t.capacity);
                put(p, s, 1, r, Ledger.cut(gas(t.fluid), 11), ink);
                put(p, s, 13, r, Ledger.cut(y.where, 9), ink);
                if (s) L.revRight(p, 27, r, Math.round(share * 100f) + "%");
                else L.right(p, 27, r, Math.round(share * 100f) + "%", ink);
                put(p, s, 28, r, Ledger.cut(st.isEmpty() ? Fmt.thousands(Math.round(Math.max(0, t.contents))) + " KG" : st, L.cols - 29), ink);
            }
        }
    }

    private void put(YardPaint p, boolean s, float c, int r, String t, Color ink) {
        if (s) L.rev(p, c, r, t);
        else L.at(p, c, r, t, ink);
    }

    @Override
    void keys() {
    }

    @Override
    void press(int k) {
    }

    @Override
    String empty() {
        return "NO TANKS ABOARD";
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        float bx = x + 8f, by = y + 4f, bw = w - 16f, bh = h - 8f;
        BigConsole.bay(p, bx, by, bw, bh, null, false);
        int cols = Ledger.colsIn(bw - 36f), rows = Math.max(6, Ledger.rowsIn(bh - 24f));
        L.set(bx + 12f, by + 10f, cols, rows);
        L.under(p, false);
        String right = flight != null && !Double.isNaN(flight.deltaVLeft) ? "ΔV " + mps(flight.deltaVLeft) : tanks == 1 ? "1 TANK" : tanks + " TANKS";
        title(p, "TANKS", right);
        drawList(p, hits, 1, rows - 1);
        L.over(p);
    }
}
