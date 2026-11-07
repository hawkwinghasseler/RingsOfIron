package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.LifeSupportSystem;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.minimap.TileShade;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

import java.util.Locale;

/**
 * Engineering's Atmos tab (the Stations spec's ATMOS page, 9.2, at a console panel's size): every room, the
 * worst air first, with its pressure, its oxygen and its smoke; what life support does for them, which rooms
 * it puts air into and why not the rest; each life support unit with its state and its switch on a soft key,
 * the console's own selected as the panel opens; and the oxygen and nitrogen tanks, with life support's own
 * emergency oxygen once they are dry. The spec's hours of air are left out: the game has no figure for how
 * fast the air is used, and the page doesn't guess one.
 */
final class AtmosPage extends ListPage {
    static final int HEAD = 0, ROOM = 1, AIR = 2, UNIT = 3, TANK = 4;

    /** A row of the list. */
    static final class Line {
        int kind;
        Compartment room;
        String name = "";
        ShipSystems.Sys unit;
        Fluid gas;
    }

    private final ShipSystems systems = new ShipSystems();
    private final Array<SubsystemComponent> found = new Array<>(), units = new Array<>();
    final Array<Line> lines = new Array<>();
    private final Array<Line> pool = new Array<>();
    private final Array<Compartment> rooms = new Array<>(), fed = new Array<>(), venting = new Array<>();
    private final Array<String> roomList = new Array<>(), fedList = new Array<>(), ventList = new Array<>();
    private ShipGridComponent grid;
    private RoomNames names;
    private ShipSubsystemRegistryComponent registry;
    /** Life support's line: the rooms it puts air into, and why not the rest; whether a room goes short. */
    String air = "";
    private boolean airShort;
    private int bad;

    AtmosPage() {
        super("ATMOS", "ENG", ClassicWindow.CONSOLE);
    }

    @Override
    boolean offered() {
        return grid != null && grid.compartments.size > 0;
    }

    @Override
    boolean fitsSide() {
        return true;
    }

    /** Selects {@code s}'s row, if it is a life support unit: the one at its console. */
    void focus(SubsystemComponent s) {
        for (int i = 0; i < lines.size; i++) {
            if (lines.get(i).unit != null && lines.get(i).unit.s == s) {
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
        registry = ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class);
        names = own ? d.roomNames() : null;
        found.clear();
        units.clear();
        if (registry != null) {
            for (int i = 0; i < registry.subsystems.size; i++) {
                SubsystemComponent s = registry.subsystems.get(i).getComponent(SubsystemComponent.class);
                if (s == null) continue;
                found.add(s);
                if (s.type == SubsystemType.LIFE_SUPPORT) units.add(s);
            }
        }
        SubsystemComponent kept = sel >= 0 && sel < lines.size && lines.get(sel).unit != null ? lines.get(sel).unit.s : null;
        systems.read(found, grid, own ? d::roomAt : null, null);
        pool.addAll(lines);
        lines.clear();
        // The rooms, the worst air first; an airlock is pumped down on purpose, so not one the page talks about
        rooms.clear();
        if (grid != null) {
            Array<Compartment> all = names != null ? names.rooms(grid) : grid.compartments;
            for (int i = 0; i < all.size; i++) {
                Compartment c = all.get(i);
                if (c.tiles.size == 0 || name(c).startsWith("AIRLOCK")) continue;
                rooms.add(c);
            }
        }
        rooms.sort((a, c) -> Float.compare(score(a), score(c)));
        bad = 0;
        line(HEAD);
        for (int i = 0; i < rooms.size; i++) {
            Line l = line(ROOM);
            l.room = rooms.get(i);
            l.name = name(l.room);
            if (score(l.room) < 1f) bad++;
        }
        lifeSupport();
        line(AIR);
        for (int i = 0; i < systems.all.size; i++) if (systems.all.get(i).s.type == SubsystemType.LIFE_SUPPORT) line(UNIT).unit = systems.all.get(i);
        if (registry != null && TankSupply.hasTanks(registry, Fluid.OXYGEN)) line(TANK).gas = Fluid.OXYGEN;
        if (registry != null && TankSupply.hasTanks(registry, Fluid.NITROGEN)) line(TANK).gas = Fluid.NITROGEN;
        sel = -1;
        if (kept != null) for (int i = 0; i < lines.size && sel < 0; i++) if (lines.get(i).unit != null && lines.get(i).unit.s == kept) sel = i;
        keepSelection();
    }

    private Line line(int kind) {
        Line l = pool.size > 0 ? pool.pop() : new Line();
        l.kind = kind;
        l.room = null;
        l.name = "";
        l.unit = null;
        l.gas = null;
        lines.add(l);
        return l;
    }

    /**
     * A room's name, or where the ship's rooms have no names (a prize's) its number, by its place in the
     * grid's rooms so it keeps it as the list reorders.
     */
    private String name(Compartment c) {
        String n = names == null || grid == null ? "" : names.of(grid, c);
        return n == null || n.isEmpty() ? "ROOM " + (grid == null ? 0 : grid.compartments.indexOf(c, true) + 1) : n;
    }

    /** How breathable a room is, the worst lowest: under 1 is thin or smoky. */
    static float score(Compartment c) {
        float s = Math.min(c.pressureRatio() / ShipAlerts.LOW_PRESSURE, 1.2f);
        if (c.smokeDensity() > TileShade.THICK_SMOKE * 0.3f) s = Math.min(s, 0.9f);
        return s;
    }

    /** "THICK", "LIGHT" or a dash. */
    static String smoke(Compartment c) {
        float s = c.smokeDensity();
        return s > TileShade.THICK_SMOKE ? "THICK" : s > TileShade.THICK_SMOKE * 0.3f ? "LIGHT" : "—";
    }

    private void lifeSupport() {
        LifeSupportSystem.reach(grid, units, fed, venting);
        roomList.clear();
        fedList.clear();
        ventList.clear();
        for (int i = 0; i < rooms.size; i++) {
            Compartment c = rooms.get(i);
            String n = name(c);
            if (!roomList.contains(n, false)) roomList.add(n);
            if (fed.contains(c, true) && !fedList.contains(n, false)) fedList.add(n);
            if (venting.contains(c, true) && !ventList.contains(n, false)) ventList.add(n);
        }
        air = SystemsPage.air(units.size, roomList, fedList, ventList);
        airShort = air.startsWith("NO ") || air.contains(":");
    }

    /**
     * A gas's tanks: the share they hold and the kilograms of their capacity ("72%  432 OF 600 KG"); for
     * oxygen once they are dry, life support's own reserve ("DRY, RESERVE 42 KG").
     */
    String tank(Fluid gas) {
        if (registry == null) return "";
        Array<SubsystemComponent> tanks = TankSupply.tanks(registry, gas);
        double held = TankSupply.available(tanks, gas), cap = 0;
        for (int i = 0; i < tanks.size; i++) cap += Math.max(0, tanks.get(i).capacity);
        if (cap <= 0) return "NONE";
        if (held <= 0) {
            if (gas != Fluid.OXYGEN || units.size == 0) return "DRY";
            double reserve = 0;
            for (int i = 0; i < units.size; i++) reserve += TankSupply.reserve(units.get(i));
            return "DRY, RESERVE " + Math.round(reserve) + " KG";
        }
        return Math.round(held / cap * 100) + "%  " + Math.round(held) + " OF " + Math.round(cap) + " KG";
    }

    /** Whether a gas's tanks are low: under a sixth, or dry. */
    private boolean low(Fluid gas) {
        if (registry == null) return false;
        Array<SubsystemComponent> tanks = TankSupply.tanks(registry, gas);
        double held = TankSupply.available(tanks, gas), cap = 0;
        for (int i = 0; i < tanks.size; i++) cap += Math.max(0, tanks.get(i).capacity);
        return cap <= 0 || held / cap < 0.15;
    }

    @Override
    int count() {
        return lines.size;
    }

    @Override
    boolean selectable(int i) {
        return lines.get(i).unit != null;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        Line l = lines.get(i);
        switch (l.kind) {
            case HEAD:
                L.at(p, 1, r, "ROOM", DIM);
                L.right(p, 21, r, "KPA", DIM);
                L.right(p, 28, r, "O2", DIM);
                L.at(p, 31, r, "SMOKE", DIM);
                return;
            case ROOM: {
                Compartment c = l.room;
                float pr = c.pressureRatio();
                boolean vac = pr < ShipAlerts.VACUUM_PRESSURE, thin = !vac && pr < ShipAlerts.LOW_PRESSURE;
                String sm = smoke(c);
                Color ink = vac ? Yard.P_RED : thin || !sm.equals("—") ? AMBER : DIM;
                if (vac) L.warning(p, 0, r, Yard.P_RED);
                else if (thin || !sm.equals("—")) L.caution(p, 0, r, AMBER);
                L.at(p, 2, r, Ledger.cut(l.name, 14), ink);
                L.right(p, 21, r, String.valueOf(Math.round(c.pressure())), ink);
                L.right(p, 28, r, Math.round(c.oxygenFraction() * 100f) + "%", ink);
                L.at(p, 31, r, vac ? "VACUUM" : sm, ink);
                return;
            }
            case AIR:
                L.rule(p, r - 1);
                L.at(p, 1, r, Ledger.cut(air, L.cols - 2), airShort ? Yard.P_RED : AMBER);
                return;
            case UNIT: {
                ShipSystems.Sys y = l.unit;
                Color ink = y.state.bad() ? Yard.P_RED : y.state == ShipSystems.State.OFF ? DIM : AMBER;
                String name = "LIFE SUPPORT" + (y.where.isEmpty() ? "" : " " + y.where);
                put(p, s, 1, r, Ledger.cut(name, 24), ink);
                put(p, s, 27, r, Ledger.cut(PowerPage.word(y.state) + (Float.isNaN(y.timer) ? "" : " " + ShipSystems.seconds(y.timer)), L.cols - 28), ink);
                return;
            }
            default: {
                boolean o2 = l.gas == Fluid.OXYGEN;
                Color ink = low(l.gas) ? Yard.P_RED : AMBER;
                L.at(p, 1, r, o2 ? "O2 TANKS" : "N2 TANKS", ink);
                L.at(p, 12, r, Ledger.cut(tank(l.gas), L.cols - 13), ink);
            }
        }
    }

    private void put(YardPaint p, boolean s, float c, int r, String t, Color ink) {
        if (s) L.rev(p, c, r, t);
        else L.at(p, c, r, t, ink);
    }

    @Override
    void keys() {
        if (sel < 0 || sel >= lines.size || lines.get(sel).unit == null) return;
        softKey(lines.get(sel).unit.s.switchedOn ? "SWITCH OFF" : "SWITCH ON", null);
    }

    @Override
    void press(int k) {
        if (sel < 0 || sel >= lines.size || lines.get(sel).unit == null) return;
        ShipSystems.Sys y = lines.get(sel).unit;
        y.s.switchedOn = !y.s.switchedOn;
        say("Life support" + (y.where.isEmpty() ? "" : " (" + y.where.toLowerCase(Locale.ROOT) + ")") + " switched " + (y.s.switchedOn ? "on" : "off"));
    }

    @Override
    String empty() {
        return "NO ROOMS ABOARD";
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        float bx = x + 8f, by = y + 4f, bw = w - 16f, bh = h - 8f;
        BigConsole.bay(p, bx, by, bw, bh, null, false);
        int cols = Ledger.colsIn(bw - 36f), rows = Math.max(6, Ledger.rowsIn(bh - 44f));
        L.set(bx + 12f, by + 10f, cols, rows);
        L.under(p, false);
        title(p, "ATMOS", bad == 0 ? "ALL BREATHABLE" : bad + (bad == 1 ? " ROOM" : " ROOMS") + " BAD");
        drawList(p, hits, 1, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
    }
}
