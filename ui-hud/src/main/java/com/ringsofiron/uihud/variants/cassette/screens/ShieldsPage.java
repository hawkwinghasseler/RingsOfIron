package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ShieldStatusProvider;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

import java.util.Locale;

/**
 * Engineering's Shields tab (the Stations spec's 5.5), on a hull with an emitter: the cloud protecting the
 * ship against what the emitters can hold, the mass aboard with what of it isn't protecting the ship
 * (stowed, scattered by hits, adrift), the trim; then each emitter by where it stands, with its state, its
 * heat against its trip and, tripped, when it starts again; its switch is on a soft key, Enter pressing it.
 * The cloud is the player's ship's, as the shield model has it, so a prize's console has no Shields tab.
 */
final class ShieldsPage extends ListPage {
    static final int CLOUD = 0, MASS = 1, TRIM = 2, STUN = 3, HEAD = 4, EMITTER = 5;
    /** Heat, of the trip's, past which an emitter's bar and its row go red. */
    static final float HOT = 0.85f;

    /** A row of the list. */
    static final class Line {
        int kind;
        ShipSystems.Sys sys;
        ShieldStatusProvider.Emitter emitter;
        /** An emitter's place in the list, counted from 1, for one with no room to name it by. */
        int number;
    }

    private final ShipSystems systems = new ShipSystems();
    private final Array<SubsystemComponent> found = new Array<>();
    final Array<Line> lines = new Array<>();
    private final Array<Line> pool = new Array<>();
    private ShieldStatus status;
    private int emitters, holding;

    ShieldsPage() {
        super("SHIELDS", "ENG", ClassicWindow.CONSOLE);
    }

    @Override
    boolean offered() {
        return status != null && status.present;
    }

    @Override
    boolean fitsSide() {
        return true;
    }

    @Override
    void refresh(Boards b) {
        HudData d = b.data;
        Entity ship = ConsolePanels.ship(d);
        boolean own = d != null && ship != null && ship == d.ctx.ship;
        status = own ? d.shieldStatus : null;
        ShipGridComponent grid = ship == null ? null : ship.getComponent(ShipGridComponent.class);
        ShipSubsystemRegistryComponent registry = ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class);
        found.clear();
        if (registry != null && status != null) {
            for (int i = 0; i < registry.subsystems.size; i++) {
                SubsystemComponent s = registry.subsystems.get(i).getComponent(SubsystemComponent.class);
                if (s != null && s.type == SubsystemType.SHIELD_EMITTER) found.add(s);
            }
        }
        SubsystemComponent kept = sel >= 0 && sel < lines.size && lines.get(sel).sys != null ? lines.get(sel).sys.s : null;
        systems.read(found, grid, own ? d::roomAt : null, null);
        pool.addAll(lines);
        lines.clear();
        emitters = holding = 0;
        if (!offered()) {
            keepSelection();
            return;
        }
        line(CLOUD);
        line(MASS);
        line(TRIM);
        if (status.powerStunned) line(STUN);
        line(HEAD);
        for (int i = 0; i < systems.all.size; i++) {
            ShipSystems.Sys y = systems.all.get(i);
            Line l = line(EMITTER);
            l.sys = y;
            l.emitter = emitter(status, y.s);
            l.number = ++emitters;
            if (l.emitter != null && l.emitter.working) holding++;
        }
        sel = -1;
        if (kept != null) for (int i = 0; i < lines.size && sel < 0; i++) if (lines.get(i).sys != null && lines.get(i).sys.s == kept) sel = i;
        keepSelection();
    }

    private Line line(int kind) {
        Line l = pool.size > 0 ? pool.pop() : new Line();
        l.kind = kind;
        l.sys = null;
        l.emitter = null;
        l.number = 0;
        lines.add(l);
        return l;
    }

    /** The shield model's row for the emitter on system {@code s}, or null where it has none. */
    static ShieldStatusProvider.Emitter emitter(ShieldStatus st, SubsystemComponent s) {
        if (st == null) return null;
        for (int i = 0; i < st.each.size; i++) {
            ShieldStatusProvider.Emitter e = st.each.get(i);
            if (e.system != null && e.system.getComponent(SubsystemComponent.class) == s) return e;
        }
        return null;
    }

    /** The shield's mode, as the DEFENSE page says it: tripped, stunned, raised, lowered to dock, stowing, lowered. */
    static String mode(ShieldStatus s) {
        if (s.emittersTripped > 0 && s.emittersWorking == 0) return "TRIPPED";
        if (s.powerStunned) return "STUNNED";
        if (s.raised) return "RAISED";
        if (s.loweredForDock) return "DOCKING";
        return s.cloud > 0.5f ? "STOWING" : "LOWERED";
    }

    /** Where the mass aboard that isn't protecting the ship is: "150 STOWED, 20 SCATTERED, 5 ADRIFT", the parts there are. */
    static String asideText(ShieldStatus s) {
        StringBuilder b = new StringBuilder();
        part(b, s.stowed, "STOWED");
        part(b, s.scattered, "SCATTERED");
        part(b, s.adrift, "ADRIFT");
        return b.toString();
    }

    private static void part(StringBuilder b, float kg, String what) {
        if (kg <= 0.5f) return;
        if (b.length() > 0) b.append(", ");
        b.append(Fmt.thousands(Math.round(kg))).append(' ').append(what);
    }

    /**
     * An emitter's state: its system's, in the gun chips' words, while that stops it; else tripped with
     * when it starts again, starved of power, holding its share of the cloud, or ready with the shield
     * down.
     */
    static String state(ShipSystems.Sys y, ShieldStatusProvider.Emitter e, boolean raised) {
        if (y.state != ShipSystems.State.ON) {
            String w = PowerPage.word(y.state);
            return Float.isNaN(y.timer) ? w : w + " " + ShipSystems.seconds(y.timer);
        }
        if (e == null) return "";
        if (e.tripped) return Float.isNaN(e.restartIn) ? "TRIPPED" : "TRIPPED " + ShipSystems.seconds(e.restartIn);
        if (e.starved) return "STARVED";
        if (!e.working) return "IDLE";
        return raised ? "HOLDING" : "READY";
    }

    /** What the selected emitter's row means, for the soft row: why it trips, or why it can't hold. */
    static String meaning(ShipSystems.Sys y, ShieldStatusProvider.Emitter e) {
        if (y.state != ShipSystems.State.ON || e == null) return null;
        if (e.tripped) return "TRIPPED HOT: IT STARTS AGAIN AT HALF ITS TRIP HEAT";
        if (e.starved) return "ITS CIRCUIT CAN'T PAY TO HOLD ITS SHARE";
        if (e.heat >= HOT) return "RUNNING HOT: IT TRIPS AT 100%";
        return null;
    }

    /** An emitter's name on the list: where it stands, else its number. */
    static String name(Line l) {
        return l.sys != null && !l.sys.where.isEmpty() ? l.sys.where : "EMITTER " + l.number;
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
        ShieldStatus st = status;
        switch (l.kind) {
            case CLOUD: {
                float share = st.share();
                Color ink = share < 0.25f ? Yard.P_RED : AMBER;
                L.at(p, 1, r, "CLOUD", ink);
                bar(p, 12, 10, r, share, ink);
                L.right(p, 27, r, Math.round(share * 100f) + "%", ink);
                L.at(p, 28, r, Ledger.cut(Fmt.thousands(Math.round(st.cloud)) + " OF " + Fmt.thousands(Math.round(st.capacity)) + " KG", L.cols - 29), ink);
                return;
            }
            case MASS: {
                Color ink = st.adrift > 0.5f ? Yard.P_RED : AMBER;
                String aside = asideText(st);
                L.at(p, 1, r, "ABOARD", ink);
                L.at(p, 12, r, Fmt.clauses(Fmt.thousands(Math.round(st.stock)) + " KG" + (aside.isEmpty() ? "" : ", " + aside), L.cols - 13), ink);
                return;
            }
            case TRIM:
                L.at(p, 1, r, "TRIM", AMBER);
                L.at(p, 12, r, st.trim.toUpperCase(Locale.ROOT), AMBER);
                return;
            case STUN:
                L.at(p, 1, r, Ledger.cut("ION STUN: THE EMITTERS' POWER IS CUT", L.cols - 2), Yard.P_RED);
                return;
            case HEAD:
                L.rule(p, r - 1);
                L.at(p, 1, r, "EMITTERS", DIM);
                L.right(p, L.cols - 1, r, holding + " OF " + emitters + " HOLDING", DIM);
                return;
            default: {
                ShipSystems.Sys y = l.sys;
                ShieldStatusProvider.Emitter e = l.emitter;
                String word = state(y, e, st.raised);
                float heat = e == null ? 0f : e.heat;
                Color ink = y.state.bad() || (e != null && e.tripped) ? Yard.P_RED : y.state == ShipSystems.State.OFF ? DIM : AMBER;
                put(p, s, 1, r, Ledger.cut(name(l), 11), ink);
                put(p, s, 13, r, Ledger.cut(word, 13), ink);
                Color heatInk = e != null && (e.tripped || heat >= HOT) ? Yard.P_RED : AMBER;
                bar(p, 27, 8, r, heat, heatInk);
                if (s) L.revRight(p, 39, r, Math.round(heat * 100f) + "%");
                else L.right(p, 39, r, Math.round(heat * 100f) + "%", heatInk);
            }
        }
    }

    /** A bar {@code cols} wide from column {@code c} on row {@code r}, {@code share} of it lit. */
    private void bar(YardPaint p, int c, int cols, int r, float share, Color ink) {
        float bx = L.cx(c), bw = cols * Ledger.COL, top = L.rowTop(r) + 5f;
        p.frame(bx, top, bw, 9f, 1f, Ledger.DIM, 0.9f);
        float f = Math.max(0f, Math.min(1f, share));
        if (f > 0f) p.rect(bx + 1.5f, top + 1.5f, (bw - 3f) * f, 6f, ink);
    }

    private void put(YardPaint p, boolean s, float c, int r, String t, Color ink) {
        if (s) L.rev(p, c, r, t);
        else L.at(p, c, r, t, ink);
    }

    @Override
    void keys() {
        if (sel < 0 || sel >= lines.size || lines.get(sel).sys == null) return;
        Line l = lines.get(sel);
        softKey(l.sys.s.switchedOn ? "SWITCH OFF" : "SWITCH ON", null);
        String n = meaning(l.sys, l.emitter);
        if (n != null) note(n);
    }

    @Override
    void press(int k) {
        if (sel < 0 || sel >= lines.size || lines.get(sel).sys == null) return;
        Line l = lines.get(sel);
        l.sys.s.switchedOn = !l.sys.s.switchedOn;
        say("Emitter " + (l.sys.where.isEmpty() ? String.valueOf(l.number) : "in " + Fmt.capital(l.sys.where.toLowerCase(Locale.ROOT)))
                + " switched " + (l.sys.s.switchedOn ? "on" : "off"));
    }

    @Override
    String empty() {
        return "NO SHIELD EMITTER ABOARD";
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        float bx = x + 8f, by = y + 4f, bw = w - 16f, bh = h - 8f;
        BigConsole.bay(p, bx, by, bw, bh, null, false);
        int cols = Ledger.colsIn(bw - 36f), rows = Math.max(6, Ledger.rowsIn(bh - 44f));
        L.set(bx + 12f, by + 10f, cols, rows);
        L.under(p, false);
        title(p, "SHIELDS", status == null ? null : mode(status));
        drawList(p, hits, 1, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
    }
}
