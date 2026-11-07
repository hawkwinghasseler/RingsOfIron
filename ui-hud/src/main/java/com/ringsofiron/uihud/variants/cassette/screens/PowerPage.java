package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.interiorgrid.PowerCircuit;
import com.ringsofiron.interiorgrid.PowerRoutingSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

import java.util.Locale;

/**
 * Engineering's Power tab (the Stations spec's POWER page, 9.2, at a console panel's size): each reactor's
 * output against its rating, which falls with its damage, and each circuit's store with its charge and how
 * soon it is full; then only the systems that are not working, in the order they come back, those that
 * come back by themselves first, soonest at the top, then those that need work, so the top row is the next.
 * Each says its state in the gun chips' words and what clears it (walkthrough F). In a blackout the
 * reactors lead, since the rest come back with them.
 *
 * <p>A reactor's row and a row of what is not working carry that system's switch on a soft key, Enter
 * pressing it; a reactor's is under a guard, since switching it off darkens what it feeds. The console's
 * own system is selected as the panel opens. A console's switch works whatever the power, as the game's
 * console window has it (5.1, rule 4).
 */
final class PowerPage extends ListPage {
    static final int REACTOR = 0, STORE = 1, HEAD = 2, DOWN = 3, WELL = 4;
    /** Seconds of the store's rise a time to full is read over: the store fills in steps, a routing tick each. */
    static final double RATE_SPAN = 2.0;

    /** A row of the list. */
    static final class Line {
        int kind;
        ShipSystems.Sys sys;
        PowerCircuit circuit;
        /** A store's circuit, counted from 1. */
        int number;
    }

    /** A circuit's store as last measured, for its rate of fill: when, how full, and the rate then, J/s. */
    private static final class Fill {
        double at = Double.NaN, stored, rate = Double.NaN;
    }

    private final ShipSystems systems = new ShipSystems();
    private final Array<SubsystemComponent> found = new Array<>();
    final Array<Line> lines = new Array<>();
    private final Array<Line> pool = new Array<>();
    private final Array<ShipSystems.Sys> down = new Array<>();
    private final ObjectMap<PowerCircuit, Fill> fills = new ObjectMap<>();
    private ShipGridComponent grid;
    private boolean blackout;
    private int reactors;
    private double rated, output;

    PowerPage() {
        super("POWER", "ENG", ClassicWindow.CONSOLE);
    }

    @Override
    boolean offered() {
        return grid != null && systems.all.size > 0;
    }

    @Override
    boolean fitsSide() {
        return true;
    }

    /** Selects {@code s}'s row, if it has one: the reactor, at its console. */
    void focus(SubsystemComponent s) {
        for (int i = 0; i < lines.size; i++) {
            if (lines.get(i).sys != null && lines.get(i).sys.s == s) {
                select(i);
                return;
            }
        }
    }

    @Override
    void refresh(Boards b) {
        HudData d = b.data;
        Entity ship = ConsolePanels.ship(d);
        boolean own = d != null && ship == d.ctx.ship;
        grid = ship == null ? null : ship.getComponent(ShipGridComponent.class);
        ShipSubsystemRegistryComponent registry = ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class);
        found.clear();
        if (registry != null) {
            for (int i = 0; i < registry.subsystems.size; i++) {
                SubsystemComponent s = registry.subsystems.get(i).getComponent(SubsystemComponent.class);
                if (s != null) found.add(s);
            }
        }
        // The row selected stays on its system as the list is read again
        SubsystemComponent kept = sel >= 0 && sel < lines.size && lines.get(sel).sys != null ? lines.get(sel).sys.s : null;
        systems.read(found, grid, own ? d::roomAt : null, own ? d.weaponFrame.mounts : null);
        pool.addAll(lines);
        lines.clear();
        rated = output = 0;
        reactors = 0;
        for (int i = 0; i < systems.all.size; i++) {
            ShipSystems.Sys y = systems.all.get(i);
            if (y.s.type != SubsystemType.REACTOR) continue;
            reactors++;
            rated += rated(y.s);
            output += output(y);
            line(REACTOR).sys = y;
        }
        blackout = output <= 0;
        double now = d == null ? 0 : d.ctx.simSeconds;
        if (grid != null) {
            for (int i = 0; i < grid.circuits.size; i++) {
                Line l = line(STORE);
                l.circuit = grid.circuits.get(i);
                l.number = i + 1;
                measure(l.circuit, now);
            }
        }
        // A circuit a reroute replaced takes its rate with it
        if (fills.size > (grid == null ? 0 : grid.circuits.size)) {
            for (PowerCircuit c : fills.keys().toArray()) if (grid == null || !grid.circuits.contains(c, true)) fills.remove(c);
        }
        down.clear();
        for (int i = 0; i < systems.all.size; i++) {
            ShipSystems.Sys y = systems.all.get(i);
            if (y.s.type.needsPower() && y.state != ShipSystems.State.ON) down.add(y);
        }
        down.sort(blackout ? PowerPage::reactorFirst : PowerPage::comeBack);
        if (down.size == 0) line(WELL);
        else {
            line(HEAD);
            for (int i = 0; i < down.size; i++) line(DOWN).sys = down.get(i);
        }
        sel = -1;
        if (kept != null) for (int i = 0; i < lines.size && sel < 0; i++) if (lines.get(i).sys != null && lines.get(i).sys.s == kept) sel = i;
        keepSelection();
    }

    private Line line(int kind) {
        Line l = pool.size > 0 ? pool.pop() : new Line();
        l.kind = kind;
        l.sys = null;
        l.circuit = null;
        l.number = 0;
        lines.add(l);
        return l;
    }

    /** Reads how fast {@code c}'s store rises, over {@link #RATE_SPAN} seconds of the game's time to {@code now}. */
    private void measure(PowerCircuit c, double now) {
        Fill f = fills.get(c);
        if (f == null) fills.put(c, f = new Fill());
        if (Double.isNaN(f.at) || now < f.at) {
            f.at = now;
            f.stored = c.stored;
            return;
        }
        if (now - f.at < RATE_SPAN) return;
        f.rate = (c.stored - f.stored) / (now - f.at);
        f.at = now;
        f.stored = c.stored;
    }

    /** What reactor {@code s} is rated for, W: its component's, else its mount size's. */
    static double rated(SubsystemComponent s) {
        if (s.ratedOutput > 0) return s.ratedOutput;
        return s.footprint == null ? 0 : PowerRoutingSystem.standardOutput(s.footprint.size);
    }

    /** What a reactor makes now, W: its rating less its damage, nothing while it isn't working. */
    static double output(ShipSystems.Sys y) {
        return y.state == ShipSystems.State.ON ? PowerRoutingSystem.reactorOutput(y.s) : 0;
    }

    /** The order systems come back in: those that recover by themselves first, soonest at the top; then the rest. */
    static int comeBack(ShipSystems.Sys a, ShipSystems.Sys b) {
        boolean ta = !Float.isNaN(a.timer), tb = !Float.isNaN(b.timer);
        if (ta != tb) return ta ? -1 : 1;
        if (ta) return Float.compare(a.timer, b.timer);
        return rest(a.state) - rest(b.state);
    }

    /** A blackout's order: the reactors first, then as {@link #comeBack}. */
    static int reactorFirst(ShipSystems.Sys a, ShipSystems.Sys b) {
        boolean ra = a.s.type == SubsystemType.REACTOR, rb = b.s.type == SubsystemType.REACTOR;
        if (ra != rb) return ra ? -1 : 1;
        return comeBack(a, b);
    }

    private static int rest(ShipSystems.State s) {
        switch (s) {
            case CUT_OFF:
                return 0;
            case OFF:
                return 1;
            case SHORTED:
            case STUNNED:
                return 2;
            case DAMAGED:
                return 3;
            default:
                return 4;
        }
    }

    /** A state in the gun chips' words (the Stations spec's 8.3): what the power page says of a system down. */
    static String word(ShipSystems.State s) {
        switch (s) {
            case CUT_OFF:
                return "NO POWER";
            case DAMAGED:
                return "DISABLED";
            case WRECKED:
                return "DESTROYED";
            default:
                return s.label;
        }
    }

    /**
     * What brings a system back: the seconds left on its breaker or its stun, a repair and how far it has
     * to go, its switch, its conduit; in a blackout, a reactor.
     */
    static String clears(ShipSystems.Sys y, boolean blackout) {
        if (!Float.isNaN(y.timer)) return "BACK IN " + ShipSystems.seconds(y.timer);
        switch (y.state) {
            case OFF:
                return "SWITCH ON";
            case CUT_OFF:
                return blackout ? "A REACTOR RUNNING" : "CONDUIT CUT";
            case DAMAGED:
                return Math.round(y.s.health * 100f) + "%, REPAIR";
            case WRECKED:
                return "A YARD'S REPAIR";
            default:
                return "";
        }
    }

    /** A system's name on the list: a gun by its mount ("AFT STBD GUN"), else its kind, and its room where there are more. */
    String label(ShipSystems.Sys y) {
        if (y.s.type == SubsystemType.TURRET_MOUNT && !y.where.isEmpty()) return y.where + " GUN";
        int same = 0;
        for (int i = 0; i < systems.all.size; i++) if (systems.all.get(i).name.equals(y.name)) same++;
        return same > 1 && !y.where.isEmpty() ? y.name + " " + y.where : y.name;
    }

    /** A store's state: full, how soon it is, or draining. */
    String fill(PowerCircuit c) {
        if (c.capacity <= 0) return "NO STORE";
        if (c.charge() >= 0.995f) return "FULL";
        Fill f = fills.get(c);
        double rate = f == null ? Double.NaN : f.rate;
        if (Double.isNaN(rate)) return "";
        if (rate < -1e-6) return "DRAINING";
        if (rate <= 1e-6) return "NOT FILLING";
        return "FULL IN " + ShipSystems.seconds((float) ((c.capacity - c.stored) / rate));
    }

    @Override
    int count() {
        return lines.size;
    }

    @Override
    boolean selectable(int i) {
        return lines.get(i).sys != null;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        Line l = lines.get(i);
        switch (l.kind) {
            case REACTOR: {
                ShipSystems.Sys y = l.sys;
                Color ink = y.state.bad() ? Yard.P_RED : y.state == ShipSystems.State.OFF ? DIM : AMBER;
                put(p, s, 1, r, Ledger.cut(reactors > 1 && !y.where.isEmpty() ? "REACTOR " + y.where : "REACTOR", 16), ink);
                put(p, s, 18, r, watts(output(y)) + " OF " + watts(rated(y.s)), ink);
                String state = y.state != ShipSystems.State.ON ? word(y.state)
                        : y.s.health < 0.995f ? "DAMAGED, " + Math.round(y.s.health * 100f) + "%" : "";
                put(p, s, 33, r, Ledger.cut(state, L.cols - 34), ink);
                return;
            }
            case STORE: {
                PowerCircuit c = l.circuit;
                float share = c.charge();
                Color ink = share < 0.25f ? Yard.P_RED : AMBER;
                L.at(p, 1, r, grid != null && grid.circuits.size > 1 ? "STORE " + l.number : "STORE", AMBER);
                float bx = L.cx(9), bw = 10 * Ledger.COL, top = L.rowTop(r) + 5f;
                p.frame(bx, top, bw, 9f, 1f, Ledger.DIM, 0.9f);
                if (share > 0f) p.rect(bx + 1.5f, top + 1.5f, (bw - 3f) * Math.min(1f, share), 6f, ink);
                L.right(p, 24, r, Math.round(share * 100f) + "%", ink);
                L.at(p, 25, r, Ledger.cut(fill(c), L.cols - 26), ink);
                return;
            }
            case HEAD:
                L.rule(p, r - 1);
                L.at(p, 1, r, "NOT WORKING", DIM);
                L.right(p, L.cols - 1, r, down.size == 1 ? "1 SYSTEM" : down.size + " SYSTEMS", DIM);
                return;
            case DOWN: {
                ShipSystems.Sys y = l.sys;
                Color ink = y.state == ShipSystems.State.DAMAGED || y.state == ShipSystems.State.WRECKED ? Yard.P_RED
                        : y.state == ShipSystems.State.OFF ? DIM : AMBER;
                put(p, s, 1, r, Ledger.cut(label(y), 15), ink);
                put(p, s, 17, r, word(y.state), ink);
                put(p, s, 27, r, Ledger.cut(clears(y, blackout), L.cols - 28), ink);
                return;
            }
            default:
                L.rule(p, r - 1);
                L.at(p, 1, r, blackout ? "NO REACTOR IS RUNNING" : "EVERY SYSTEM IS WORKING", blackout ? Yard.P_RED : DIM);
        }
    }

    private void put(YardPaint p, boolean s, float c, int r, String t, Color ink) {
        if (s) L.rev(p, c, r, t);
        else L.at(p, c, r, t, ink);
    }

    @Override
    void keys() {
        if (sel < 0 || sel >= lines.size || lines.get(sel).sys == null) return;
        SubsystemComponent s = lines.get(sel).sys.s;
        if (!s.switchedOn) softKey("SWITCH ON", null);
        else if (s.type == SubsystemType.REACTOR) guardedKey("SWITCH OFF", null, "Switch the reactor off? Press again: what it feeds goes dark");
        else softKey("SWITCH OFF", null);
    }

    @Override
    void press(int k) {
        if (sel < 0 || sel >= lines.size || lines.get(sel).sys == null) return;
        ShipSystems.Sys y = lines.get(sel).sys;
        y.s.switchedOn = !y.s.switchedOn;
        say(Fmt.capital(label(y).toLowerCase(Locale.ROOT)) + " switched " + (y.s.switchedOn ? "on" : "off"));
    }

    @Override
    String empty() {
        return "NO SYSTEMS ABOARD";
    }

    static String watts(double w) {
        return SystemsPage.watts(w);
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        float bx = x + 8f, by = y + 4f, bw = w - 16f, bh = h - 8f;
        BigConsole.bay(p, bx, by, bw, bh, null, false);
        int cols = Ledger.colsIn(bw - 36f), rows = Math.max(6, Ledger.rowsIn(bh - 44f));
        L.set(bx + 12f, by + 10f, cols, rows);
        L.under(p, false);
        title(p, "POWER", blackout ? (reactors == 0 ? "NO REACTOR" : "BLACKOUT") : watts(output) + " OF " + watts(rated));
        drawList(p, hits, 1, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
    }
}
