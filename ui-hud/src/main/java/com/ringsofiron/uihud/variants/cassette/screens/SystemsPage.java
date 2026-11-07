package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.LifeSupportSystem;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.RcsThruster;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.ConditionLog;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.PowerStatusProvider;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

import java.util.Locale;

/**
 * The ship's systems (Context's M2 Systems, v15): the ship's plan as a mimic board, a lamp in each
 * system lit by its state; every system on a ledger tube with its health, its power and its state,
 * and its switch on a soft key (the reactor's under a guard, since switching it off darkens the ship;
 * a group's switching the group, and opening to list each with its own; the RCS blocks with none);
 * the damage control board, every hazard and repair job the game raises with who has it, a fire by
 * its tiles burning and whether it is spreading or shrinking (Task v16); the power: each circuit's
 * reactors, output against rating and store, and what no reactor reaches; and life support: which
 * rooms it puts air into and why not the rest, and the oxygen tanks.
 * The switches are the classic console window's ({@code SubsystemComponent.switchedOn}).
 *
 * <p>In a side panel it is two of Engineering's tabs (the Stations spec's 5.5, {@link Side}): Systems, the
 * list with its switches, narrowed to the panel; and Damage, the plan over the damage control board.
 */
final class SystemsPage extends ListPage {
    /** Where it draws: across the ship's console, or as one of Engineering's tabs in a side panel. */
    enum Side {
        /** The ship's console's Systems tab, across the screen (Context's M2). */
        NONE,
        /** Engineering's Systems tab: every system, its health and state, with its switch. */
        LIST,
        /** Engineering's Damage tab (the DAMAGE page, 9.2): the ship's plan over the damage control board. */
        PLAN
    }

    private static final Color MIMIC_TOP = Yard.hex("3F4B43"), MIMIC_FOOT = Yard.hex("333D37");
    private static final Color MIMIC_RIM = Yard.hex("1F2420"), FLOOR = Yard.hex("56655A");
    private static final Color PAINT = Yard.hex("EFE8D6"), BLOCK = Yard.hex("232823");
    private static final Color RED = Yard.P_RED;
    private final ShipSystems systems = new ShipSystems();
    private final Array<SubsystemComponent> found = new Array<>();
    private final Array<AlertRegistry.Alert> jobs = new Array<>();
    /** The worst job in each room, by the room's name: the plan tints the room. */
    private final ObjectMap<String, AlertRegistry.Level> troubled = new ObjectMap<>();
    private HudData data;
    private final Ledger D = new Ledger(), W = new Ledger();
    private final FireTrend fires;
    private final Array<Compartment> fed = new Array<>(), venting = new Array<>();
    private final Array<String> roomList = new Array<>(), fedList = new Array<>(), ventList = new Array<>();
    /** Life support's line, and whether it says a room gets no air. */
    private String air = "";
    private boolean airShort;
    private Boards b;
    private ShipGridComponent grid;
    // The plan as last drawn: its origin, a tile's size, and whether it stands bow up (else bow to the right)
    private float ox, oy, px;
    private boolean turned;
    private int gw, gh;
    private final Side side;
    /** Whether the list drew narrowed to a side panel last frame: no power column, the others closer. */
    private boolean narrow;

    SystemsPage(FireTrend fires) {
        this(fires, Side.NONE);
    }

    SystemsPage(FireTrend fires, Side side) {
        super(side == Side.PLAN ? "DAMAGE" : "SYSTEMS", side == Side.NONE ? "SHIP" : "ENG",
                side == Side.NONE ? ClassicWindow.CREW : ClassicWindow.CONSOLE);
        this.fires = fires;
        this.side = side;
    }

    @Override
    boolean fitsSide() {
        return side != Side.NONE;
    }

    @Override
    boolean offered() {
        return b != null && grid != null && systems.all.size > 0;
    }

    @Override
    void refresh(Boards boards) {
        b = boards;
        HudData d = b.data;
        found.clear();
        // Engineering's tabs read the ship whose console it is, which may be a prize's; the crew's jobs,
        // the rooms' names and the guns are the player's own ship's
        Entity ship = d == null ? null : side == Side.NONE ? d.ctx.ship : ConsolePanels.ship(d);
        boolean own = d != null && ship == d.ctx.ship;
        grid = ship == null ? null : ship.getComponent(ShipGridComponent.class);
        ShipSubsystemRegistryComponent registry = ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry != null) {
            for (int i = 0; i < registry.subsystems.size; i++) {
                SubsystemComponent s = registry.subsystems.get(i).getComponent(SubsystemComponent.class);
                if (s != null) found.add(s);
            }
        }
        systems.read(found, grid, own ? d::roomAt : null, own ? d.weaponFrame.mounts : null);
        keepSelection();
        jobs(own ? d.alerts : null, jobs);
        data = d;
        lifeSupport(own ? d.roomNames() : null);
        troubled.clear();
        for (int i = 0; i < jobs.size; i++) {
            AlertRegistry.Alert a = jobs.get(i);
            if (a.room.isEmpty() || a.id.startsWith("sys:")) continue;
            AlertRegistry.Level had = troubled.get(a.room);
            if (had == null || a.level.ordinal() > had.ordinal()) troubled.put(a.room, a.level);
        }
    }

    /**
     * Selects {@code s}'s row, opening its group so it has one of its own: E at the system's console
     * opens the tab on it (Context 4.3). Nothing when it isn't listed.
     */
    void focus(SubsystemComponent s) {
        int i = s == null ? -1 : systems.rowOf(s);
        if (i < 0) return;
        ShipSystems.Row r = systems.rows.get(i);
        if (r.kind == ShipSystems.Row.GROUP) {
            systems.open.add(r.systems.first().name);
            if (b != null) refresh(b);
            i = systems.rowOf(s);
        }
        select(i);
    }

    /** The selected row's systems, for tests: empty with none selected. */
    Array<SubsystemComponent> selected() {
        Array<SubsystemComponent> out = new Array<>();
        if (sel < 0 || sel >= systems.rows.size) return out;
        ShipSystems.Row r = systems.rows.get(sel);
        for (int k = 0; k < r.systems.size; k++) out.add(r.systems.get(k).s);
        return out;
    }

    /**
     * The damage control board's jobs: what the game raises about the hull, the air, fire, systems,
     * power and the crew, warnings first. Combat, flight and the captain's own state are other widgets'.
     * An advisory is no one's job (a system switched off, crew outside), and a room's air is one job,
     * as P2 shows it: its breach while one is open, else its vacuum or its low pressure.
     */
    static void jobs(AlertRegistry alerts, Array<AlertRegistry.Alert> out) {
        out.clear();
        if (alerts == null) return;
        Array<AlertRegistry.Alert> all = alerts.all();
        for (int lv = AlertRegistry.Level.values().length - 1; lv > AlertRegistry.Level.ADVISORY.ordinal(); lv--) {
            for (int i = 0; i < all.size; i++) {
                AlertRegistry.Alert a = all.get(i);
                if (a.level.ordinal() != lv) continue;
                AlertRegistry.Category c = a.category;
                if (c != AlertRegistry.Category.ENGINEERING && c != AlertRegistry.Category.LIFE_SUPPORT
                        && c != AlertRegistry.Category.CREW && c != AlertRegistry.Category.SHIP) continue;
                if (a.id.startsWith("air:") && alerts.get("breach:" + a.id.substring("air:".length())) != null) continue;
                out.add(a);
            }
        }
    }

    /** Life support's line from the ship as it is now: the rooms it reaches, by name. */
    private void lifeSupport(RoomNames names) {
        int fitted = 0;
        for (int i = 0; i < found.size; i++) if (found.get(i).type == SubsystemComponent.SubsystemType.LIFE_SUPPORT) fitted++;
        LifeSupportSystem.reach(grid, found, fed, venting);
        roomList.clear();
        fedList.clear();
        ventList.clear();
        if (grid != null && names != null) {
            Array<Compartment> rooms = names.rooms(grid);
            for (int i = 0; i < rooms.size; i++) named(names, rooms.get(i), roomList);
            for (int i = 0; i < fed.size; i++) named(names, fed.get(i), fedList);
            for (int i = 0; i < venting.size; i++) named(names, venting.get(i), ventList);
        }
        air = air(fitted, roomList, fedList, ventList);
        airShort = air.startsWith("NO ") || air.contains(":");
    }

    /** Adds {@code c}'s name to {@code out}: a room the screen talks about, so not an airlock, pumped down on purpose. */
    private void named(RoomNames names, Compartment c, Array<String> out) {
        String n = names.of(grid, c);
        if (!n.isEmpty() && !n.startsWith("AIRLOCK") && !out.contains(n, false)) out.add(n);
    }

    /**
     * What life support does for the ship's rooms (Task v16), with {@code fitted} units aboard: of
     * {@code rooms}, the ones it puts air into ({@code fed}) and those it reaches that vent to space
     * ({@code venting}); any other is behind a shut door, since it reaches only through open ones.
     * "AIR TO BRIDGE AND HOLD"; "AIR TO THE HOLD ONLY: BRIDGE VENTING"; "AIR TO THE BRIDGE ONLY:
     * HOLD BEHIND A SHUT DOOR"; "NO AIR: LIFE SUPPORT DOWN" with no unit working.
     */
    static String air(int fitted, Array<String> rooms, Array<String> fed, Array<String> venting) {
        if (fitted == 0) return "NO LIFE SUPPORT ABOARD";
        if (fed.size == 0 && venting.size == 0) return "NO AIR: LIFE SUPPORT DOWN";
        Array<String> shut = new Array<>();
        for (int i = 0; i < rooms.size; i++) {
            String r = rooms.get(i);
            if (!fed.contains(r, false) && !venting.contains(r, false)) shut.add(r);
        }
        if (venting.size == 0 && shut.size == 0) {
            if (fed.size == 1) return "AIR TO THE " + fed.first();
            return fed.size <= 3 ? "AIR TO " + list(fed) : "AIR TO ALL " + fed.size + " ROOMS";
        }
        String head = fed.size == 0 ? "NO AIR" : fed.size == 1 ? "AIR TO THE " + fed.first() + " ONLY" : "AIR TO " + list(fed) + " ONLY";
        StringBuilder why = new StringBuilder(head).append(": ");
        if (venting.size > 0) why.append(list(venting)).append(" VENTING");
        if (shut.size > 0) {
            if (venting.size > 0) why.append(", ");
            why.append(list(shut)).append(shut.size == 1 ? " BEHIND A SHUT DOOR" : " BEHIND SHUT DOORS");
        }
        return why.toString();
    }

    /** "BRIDGE", "BRIDGE AND HOLD", "BRIDGE, HOLD AND ENGINES". */
    static String list(Array<String> names) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size; i++) {
            if (i > 0) sb.append(i == names.size - 1 ? " AND " : ", ");
            sb.append(names.get(i));
        }
        return sb.toString();
    }

    /** A job in the board's words: "FIRE, 3 TILES", "HULL BREACH", a system by its name and state ("ENGINE SHORTED"). */
    static String task(AlertRegistry.Alert a, ShipSystems systems) {
        if (a.id.startsWith("fire:")) return "FIRE, " + Math.max(1, a.count) + (a.count > 1 ? " TILES" : " TILE");
        if (a.id.startsWith("sys:")) {
            ShipSystems.Sys y = systems == null ? null : systems.at(a.tileX, a.tileY);
            return y != null ? y.name + " " + y.state.label : Fmt.upper(a.text.isEmpty() ? a.code : a.text);
        }
        return Fmt.upper(ConditionLog.longForm(a));
    }

    /** Where a job is: its room, the casualty's name, or a system outside every room by its side. */
    static String place(AlertRegistry.Alert a, ShipSystems systems) {
        if (a.id.startsWith("casualty:")) return Fmt.upper(ConditionLog.where(a));
        if (!a.room.isEmpty()) return a.room;
        ShipSystems.Sys y = a.id.startsWith("sys:") && systems != null ? systems.at(a.tileX, a.tileY) : null;
        return y != null && !y.where.isEmpty() ? y.where : "—";
    }

    /** A row's state cut to {@code room}: a group's last counts go first ("1 SHORTED · 1 ON" to "1 SHORTED"). */
    static String fit(String state, int room) {
        String s = state;
        while (s.length() > room) {
            int cut = s.lastIndexOf(" · ");
            if (cut < 0) break;
            s = s.substring(0, cut);
        }
        return Ledger.cut(s, room);
    }

    /**
     * Who has a job: the one of the crew on it the game names, with how many more ("ADA", "ADA +1");
     * "2 CREW" where it names nobody; "UNASSIGNED"; or a dash where it can't say.
     */
    static String who(AlertRegistry.Alert a) {
        if (a.handling < 0) return "—";
        if (a.handling == 0) return "UNASSIGNED";
        if (a.who.isEmpty()) return a.handling + " CREW";
        String name = Fmt.upper(a.who);
        return a.handling > 1 ? name + " +" + (a.handling - 1) : name;
    }

    @Override
    int count() {
        return systems.rows.size;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        ShipSystems.Row row = systems.rows.get(i);
        Color ink = row.worst.bad() ? RED : row.worst == ShipSystems.State.OFF ? DIM : AMBER;
        if (row.kind == ShipSystems.Row.GROUP) put(p, s, 1, r, row.open ? "-" : "+", ink);
        // Narrowed to a side panel the power column goes (Power has it), the rest close up
        int nameAt = row.kind == ShipSystems.Row.MEMBER ? 5 : 3, whereAt = narrow ? 18 : 22, healthAt = narrow ? 30 : 39;
        int stateAt = narrow ? 32 : 47;
        put(p, s, nameAt, r, Ledger.cut(row.name, whereAt - nameAt), ink);
        put(p, s, whereAt, r, Ledger.cut(row.where, narrow ? 8 : 11), ink);
        String health = Float.isNaN(row.health) ? "—" : Math.round(row.health * 100f) + "%";
        if (s) L.revRight(p, healthAt, r, health);
        else L.right(p, healthAt, r, health, ink);
        if (!narrow) put(p, s, 41, r, row.power, row.power.equals("NONE") ? RED : ink);
        put(p, s, stateAt, r, fit(row.state, Math.max(4, L.cols - stateAt - 1)), ink);
    }

    private void put(YardPaint p, boolean s, float c, int r, String t, Color ink) {
        if (s) L.rev(p, c, r, t);
        else L.at(p, c, r, t, ink);
    }

    @Override
    void keys() {
        // The plan's tab has no switches: they are on Systems beside it
        if (side == Side.PLAN || sel < 0 || sel >= systems.rows.size) return;
        ShipSystems.Row row = systems.rows.get(sel);
        switch (row.kind) {
            case ShipSystems.Row.RCS:
                softKey("SWITCH", "RCS BLOCKS HAVE NO SWITCH: EACH FIRES WHILE ITS HULL TILE STANDS");
                return;
            case ShipSystems.Row.GROUP:
                if (row.allOn && row.guarded) guardedKey("ALL OFF", null, "Switch every reactor off? Press again: the ship goes dark");
                else softKey(row.allOn ? "ALL OFF" : "ALL ON", null);
                softKey(row.open ? "SHUT" : "OPEN", null);
                return;
            default:
                if (row.allOn && row.guarded) guardedKey("SWITCH OFF", null, "Switch the reactor off? Press again: what it feeds goes dark");
                else softKey(row.allOn ? "SWITCH OFF" : "SWITCH ON", null);
        }
    }

    @Override
    void press(int k) {
        if (sel < 0 || sel >= systems.rows.size) return;
        ShipSystems.Row row = systems.rows.get(sel);
        if (row.kind == ShipSystems.Row.GROUP && k == 1) {
            systems.openOrShut(sel);
            return;
        }
        String said = systems.toggle(sel);
        if (said != null) say(said);
    }

    @Override
    String empty() {
        return "NO SYSTEMS ABOARD";
    }

    // --- Drawing -----------------------------------------------------------------------------

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        if (side == Side.LIST) {
            list(p, hits, x + 8f, y + 4f, w - 16f, h - 8f);
            return;
        }
        if (side == Side.PLAN) {
            // The plan over the board, half the body each
            float bx = x + 8f, by = y + 4f, bw = w - 16f, bh = h - 8f, ph = Math.round((bh - 8f) * 0.5f);
            plan(p, hits, bx, by, bw, ph);
            board(p, bx, by + ph + 8f, bw, bh - ph - 8f);
            return;
        }
        float gap = 8f, bx = x + 8f, by = y + 6f, bw = w - 16f, bh = h - 12f;
        float lw = Math.round((bw - gap) * 0.5f), rw = bw - gap - lw;
        float th = Math.round((bh - gap) * 0.58f), fh = bh - gap - th;
        float rx = bx + lw + gap, fy = by + th + gap;
        plan(p, hits, bx, by, lw, th);
        list(p, hits, rx, by, rw, th);
        board(p, bx, fy, lw, fh);
        power(p, rx, fy, rw, fh);
    }

    /** The systems on their tube, the selected one's switch on the soft keys under it. */
    private void list(YardPaint p, Hits hits, float x, float y, float w, float h) {
        BigConsole.bay(p, x, y, w, h, null, false);
        narrow = side != Side.NONE;
        int cols = narrow ? Ledger.colsIn(w - 38f) : Math.max(56, Ledger.colsIn(w - 38f)), rows = Math.max(8, Ledger.rowsIn(h - 52f));
        L.set(x + 12f, y + 10f, cols, rows);
        L.under(p, false);
        int down = 0;
        for (int i = 0; i < systems.all.size; i++) if (systems.all.get(i).state.bad()) down++;
        String count = systems.all.size + " SYSTEMS" + (down > 0 ? " · " + down + " DOWN" : "");
        title(p, "SYSTEMS", count);
        L.at(p, 3, 1, "SYSTEM", DIM);
        L.at(p, narrow ? 18 : 22, 1, "WHERE", DIM);
        L.right(p, narrow ? 30 : 39, 1, "HEALTH", DIM);
        if (!narrow) L.at(p, 41, 1, "POWER", DIM);
        L.at(p, narrow ? 32 : 47, 1, "STATE", DIM);
        drawList(p, hits, 2, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
    }

    /** The damage control board: each job, where it is and who has it. */
    private void board(YardPaint p, float x, float y, float w, float h) {
        BigConsole.bay(p, x, y, w, h, null, false);
        int cols = Math.max(40, Ledger.colsIn(w - 24f)), rows = Math.max(4, Ledger.rowsIn(h - 20f));
        D.set(x + 12f, y + 10f, cols, rows);
        D.under(p, false);
        D.bar(p, 0);
        D.rev(p, 1, 0, "DAMAGE CONTROL");
        D.revRight(p, cols - 1, 0, jobs.size == 0 ? "NO JOBS" : jobs.size == 1 ? "1 JOB" : jobs.size + " JOBS");
        int whoAt = cols - 12, whereAt = whoAt - 12;
        if (jobs.size == 0) {
            D.at(p, 1, 2, "NOTHING FOR THE CREW TO DO", DIM);
        } else {
            D.at(p, 3, 1, "TASK", DIM);
            D.at(p, whereAt, 1, "WHERE", DIM);
            D.at(p, whoAt, 1, "WHO", DIM);
            int room = rows - 2;
            for (int k = 0; k < jobs.size && k < room; k++) {
                int r = 2 + k;
                if (k == room - 1 && jobs.size > room) {
                    D.at(p, 3, r, "+" + (jobs.size - k) + " MORE", DIM);
                    break;
                }
                AlertRegistry.Alert a = jobs.get(k);
                boolean warn = a.level == AlertRegistry.Level.WARNING;
                Color ink = warn ? RED : a.level == AlertRegistry.Level.CAUTION ? AMBER : DIM;
                if (warn) D.warning(p, 1, r, RED);
                else if (a.level == AlertRegistry.Level.CAUTION) D.caution(p, 1, r, AMBER);
                String t = task(a, systems);
                if (fires != null && a.id.startsWith("fire:")) {
                    String course = fires.trend(a.id, a.count, data == null ? 0 : data.ctx.simSeconds);
                    if (!course.isEmpty()) t = t + " · " + Fmt.upper(course);
                }
                D.at(p, 3, r, Ledger.cut(t, whereAt - 4), ink);
                D.at(p, whereAt, r, Ledger.cut(place(a, systems), 11), ink);
                String who = who(a);
                D.at(p, whoAt, r, Ledger.cut(who, 11), a.handling == 0 ? RED : ink);
            }
        }
        D.over(p);
    }

    /** The power board: each circuit's reactors, output of rating and store; what no reactor reaches. */
    private void power(YardPaint p, float x, float y, float w, float h) {
        BigConsole.bay(p, x, y, w, h, null, false);
        int cols = Math.max(40, Ledger.colsIn(w - 24f)), rows = Math.max(4, Ledger.rowsIn(h - 20f));
        W.set(x + 12f, y + 10f, cols, rows);
        W.under(p, false);
        PowerStatusProvider.PowerFrame f = b == null || b.data == null ? null : b.data.powerFrame;
        W.bar(p, 0);
        W.rev(p, 1, 0, "POWER");
        if (f != null && f.circuits.size > 0) W.revRight(p, cols - 1, 0, watts(f.outputWatts) + " OF " + watts(f.ratedWatts));
        // The last two rows are life support's
        int last = rows - 3;
        lifeSupport(p, rows - 2, cols);
        int r = 1;
        if (f == null || f.circuits.size == 0) {
            W.at(p, 1, Math.min(2, last), "NO REACTOR FEEDS THE SHIP", RED);
            W.over(p);
            return;
        }
        for (int i = 0; i < f.circuits.size && r <= last; i++, r++) {
            PowerStatusProvider.Circuit c = f.circuits.get(i);
            Color ink = c.reactors == 0 || c.outputWatts <= 0 ? RED : AMBER;
            W.at(p, 1, r, "CIRCUIT " + (i + 1), ink);
            W.at(p, 12, r, c.reactors == 1 ? "REACTOR" : c.reactors + " REACTORS", ink);
            W.at(p, 24, r, watts(c.outputWatts) + " OF " + watts(c.ratedWatts), ink);
            // The store as a bar on the tube, its share at the end
            float share = c.charge();
            int c0 = 41, c1 = Math.max(c0 + 4, cols - 6);
            if (c1 - c0 >= 4) {
                float bx = W.cx(c0), bw = (c1 - c0) * Ledger.COL - 4f, top = W.rowTop(r) + 5f;
                p.frame(bx, top, bw, 9f, 1f, Ledger.DIM, 0.9f);
                if (share > 0f) p.rect(bx + 1.5f, top + 1.5f, (bw - 3f) * Math.min(1f, share), 6f, share < 0.25f ? RED : AMBER);
                W.right(p, cols, r, Math.round(share * 100f) + "%", share < 0.25f ? RED : ink);
            }
        }
        // What isn't working for want of power, or tripped or stunned off its circuit
        boolean any = false;
        for (int i = 0; i < f.faults.size && r <= last; i++) {
            PowerStatusProvider.SystemRow s = f.faults.get(i);
            String why = why(s);
            if (why == null) continue;
            if (!any) {
                W.rule(p, r - 1);
                any = true;
            }
            Color ink = s.fault == PowerStatusProvider.Fault.NO_POWER ? RED : AMBER;
            W.at(p, 1, r, faultName(s.fault), ink);
            W.at(p, 12, r, Ledger.cut(s.system + (s.room.isEmpty() ? "" : " (" + s.room + ")"), 22), ink);
            W.at(p, 35, r, Ledger.cut(why, cols - 36), ink);
            r++;
        }
        if (!any && r <= last) W.at(p, 1, r, "EVERY SYSTEM SWITCHED ON HAS POWER", DIM);
        W.over(p);
    }

    /** Life support on rows {@code r} and the one under it: its bar with the oxygen tanks, then the rooms it puts air into. */
    private void lifeSupport(YardPaint p, int r, int cols) {
        W.bar(p, r);
        W.rev(p, 1, r, "LIFE SUPPORT");
        HudData d = b == null ? null : b.data;
        String tanks = d == null ? "" : tanks(d.shipStatus.oxygen, d.shipStatus.emergencyOxygenKg);
        if (!tanks.isEmpty()) W.revRight(p, cols - 1, r, tanks);
        W.at(p, 1, r + 1, Ledger.cut(air, cols - 2), airShort ? RED : AMBER);
    }

    /** The oxygen tanks on the life support bar: "O2 TANKS 72%", or "EMERGENCY O2 42 KG" once they are dry. */
    static String tanks(float oxygen, double emergencyKg) {
        if (!Double.isNaN(emergencyKg)) return "EMERGENCY O2 " + Math.round(emergencyKg) + " KG";
        if (Float.isNaN(oxygen)) return "";
        return "O2 TANKS " + Math.round(oxygen * 100f) + "%";
    }

    /** What the power board says of a system not working, or null for what isn't its business. */
    static String why(PowerStatusProvider.SystemRow s) {
        switch (s.fault) {
            case NO_POWER:
                return "NO REACTOR REACHES IT";
            case SHORTED:
                return Float.isNaN(s.timer) ? "RESETS SOON" : "RECLOSES IN " + ShipSystems.seconds(s.timer);
            case STUNNED:
                return Float.isNaN(s.timer) ? "BACK SOON" : "BACK IN " + ShipSystems.seconds(s.timer);
            default:
                return null;
        }
    }

    static String faultName(PowerStatusProvider.Fault f) {
        switch (f) {
            case NO_POWER:
                return "CUT OFF";
            case SHORTED:
                return "SHORTED";
            case STUNNED:
                return "STUNNED";
            case OFF:
                return "OFF";
            case DISABLED:
                return "DAMAGED";
            default:
                return "WRECKED";
        }
    }

    /** "3.0 MW", "850 KW", "40 W". */
    static String watts(double w) {
        if (w >= 1e6) return String.format(Locale.ROOT, "%.1f MW", w / 1e6);
        if (w >= 1e3) return String.format(Locale.ROOT, "%.0f KW", w / 1e3);
        return String.format(Locale.ROOT, "%.0f W", Math.max(0, w));
    }

    // --- The mimic board ---------------------------------------------------------------------

    /** The ship's plan painted on the enamel, a lamp in each system, its hazards marked. */
    private void plan(YardPaint p, Hits hits, float x, float y, float w, float h) {
        BigConsole.bay(p, x, y, w, h, "SHIP'S PLAN", false);
        float mx = x + 12f, my = y + 24f, mw = w - 24f, mh = h - 24f - 30f;
        p.rrect(mx - 2f, my - 2f, mw + 4f, mh + 4f, 4f, MIMIC_RIM);
        p.vgrad(mx, my, mw, mh, MIMIC_TOP, MIMIC_FOOT);
        legendRow(p, x + 14f, y + h - 12f, w - 28f);
        ShipGridComponent g = grid;
        if (g == null || g.width == 0 || g.height == 0) {
            p.textC(YardFonts.Face.LEGEND, 12f, "NO SHIP", mx + mw / 2f, my + mh / 2f, Yard.INK_LIGHT);
            return;
        }
        gw = g.width;
        gh = g.height;
        // Bow to the right, or bow up where that gives the tiles more room; the bow's end engraved,
        // with room kept for the word
        float pad = 18f, aw = mw - 2f * pad, ah = mh - 2f * pad, word = p.width(YardFonts.Face.LEGEND, 10f, "BOW");
        float straight = Math.min((aw - word - 10f) / gw, ah / gh), side = Math.min(aw / gh, (ah - 16f) / gw);
        turned = side > straight * 1.08f;
        // At large scale (Context's M2), with a cap so a small ship's tiles don't grow coarse
        px = Math.min(38f, turned ? side : straight);
        float pw = (turned ? gh : gw) * px, ph = (turned ? gw : gh) * px;
        ox = mx + (mw - pw - (turned ? 0f : word + 10f)) / 2f;
        oy = my + (mh - ph + (turned ? 16f : 0f)) / 2f;
        if (turned) p.text(YardFonts.Face.LEGEND, 10f, "BOW", ox + pw / 2f - word / 2f, oy - 7f, Yard.INK_LIGHT, 0.7f);
        else p.text(YardFonts.Face.LEGEND, 10f, "BOW", ox + pw + 10f, oy + ph / 2f + 3.5f, Yard.INK_LIGHT, 0.7f);
        float t = p.time();
        boolean blink = (t % 1f) < 0.55f;
        // The decks: each floor tile, a room with a job in it washed red for a warning (fire, breach,
        // vacuum), mustard for a caution (low pressure, smoke)
        for (int ty = 0; ty < gh; ty++) {
            for (int tx = 0; tx < gw; tx++) {
                Tile tile = g.getTile(tx, ty);
                if (tile == null || !tile.isFloor()) continue;
                float l = left(tx, ty, 1, 1), tp = top(tx, ty, 1, 1);
                p.rect(l, tp, px, px, FLOOR, 0.6f);
                AlertRegistry.Level trouble = troubled.size == 0 || data == null ? null : troubled.get(data.roomAt(tx, ty));
                if (trouble == AlertRegistry.Level.WARNING) p.rect(l, tp, px, px, RED, blink ? 0.34f : 0.22f);
                else if (trouble == AlertRegistry.Level.CAUTION) p.rect(l, tp, px, px, Yard.MUSTARD, 0.22f);
            }
        }
        // The hull and walls painted as lines through their tiles
        float lw = Math.max(1.5f, Math.min(4f, px * 0.32f));
        for (int ty = 0; ty < gh; ty++) {
            for (int tx = 0; tx < gw; tx++) {
                Tile tile = g.getTile(tx, ty);
                if (tile == null || !tile.isWall()) continue;
                Color c = tile.isHullBreach() ? RED : tile.isIntactWall() ? PAINT : Yard.MUSTARD;
                float cx = cx(tx + 0.5f, ty + 0.5f), cy = cy(tx + 0.5f, ty + 0.5f);
                p.circle(cx, cy, lw / 2f, c, 0.92f);
                if (wall(g, tx + 1, ty)) p.line(cx, cy, cx(tx + 1.5f, ty + 0.5f), cy(tx + 1.5f, ty + 0.5f), lw, c, 0.92f);
                if (wall(g, tx, ty + 1)) p.line(cx, cy, cx(tx + 0.5f, ty + 1.5f), cy(tx + 0.5f, ty + 1.5f), lw, c, 0.92f);
                // A diagonal hull where no square path joins the two
                if (wall(g, tx + 1, ty + 1) && !wall(g, tx + 1, ty) && !wall(g, tx, ty + 1))
                    p.line(cx, cy, cx(tx + 1.5f, ty + 1.5f), cy(tx + 1.5f, ty + 1.5f), lw, c, 0.92f);
                if (wall(g, tx - 1, ty + 1) && !wall(g, tx - 1, ty) && !wall(g, tx, ty + 1))
                    p.line(cx, cy, cx(tx - 0.5f, ty + 1.5f), cy(tx - 0.5f, ty + 1.5f), lw, c, 0.92f);
            }
        }
        edges(p, g, lw * 0.6f);
        // The systems: each footprint a block, a lamp at its heart
        int selRow = sel;
        for (int i = 0; i < systems.all.size; i++) {
            ShipSystems.Sys y0 = systems.all.get(i);
            MountFootprint f = y0.s.footprint;
            if (f == null) continue;
            float l = left(f.originX, f.originY, f.width(), f.height()), tp = top(f.originX, f.originY, f.width(), f.height());
            float bw = (turned ? f.height() : f.width()) * px, bh = (turned ? f.width() : f.height()) * px;
            float inset = Math.min(2f, px * 0.12f);
            p.rect(l + inset, tp + inset, bw - 2f * inset, bh - 2f * inset, BLOCK, 0.85f);
            p.frame(l + inset, tp + inset, bw - 2f * inset, bh - 2f * inset, 1f, PAINT, 0.45f);
            ShipSystems.State st = ShipSystems.state(y0.s);
            float r = Math.max(2.2f, Math.min(7f, Math.min(bw, bh) * 0.24f));
            lamp(p, l + bw / 2f, tp + bh / 2f, r, st, blink);
            int row = systems.rowOf(y0.s);
            if (row >= 0) {
                if (row == selRow) {
                    p.frame(l - 2f, tp - 2f, bw + 4f, bh + 4f, 2f, Yard.ORANGE, 0.95f);
                }
                hits.add(p, l, tp, bw, bh, this, ROW + row);
            }
        }
        // The RCS blocks on the hull: a pip each, lit while it fires
        for (int i = 0; i < g.rcsThrusters.size; i++) {
            RcsThruster r = g.rcsThrusters.get(i);
            boolean works = r.works(g);
            p.lamp(cx(r.x + 0.5f, r.y + 0.5f), cy(r.x + 0.5f, r.y + 0.5f), Math.max(1.6f, Math.min(3.4f, px * 0.18f)),
                    works ? Yard.GREEN_LAMP : Yard.RED_LAMP, works || blink);
        }
        // Fire over everything, a system's block too: a flicker and a hot core on each burning tile
        for (int ty = 0; ty < gh; ty++) {
            for (int tx = 0; tx < gw; tx++) {
                Tile tile = g.getTile(tx, ty);
                if (tile == null || !tile.isBurning()) continue;
                float cx = cx(tx + 0.5f, ty + 0.5f), cy = cy(tx + 0.5f, ty + 0.5f), core = px * 0.42f;
                p.glow(cx, cy, px * 1.1f, Yard.ORANGE, blink ? 0.85f : 0.55f);
                p.rect(cx - core / 2f, cy - core / 2f, core, core, Yard.ORANGE, blink ? 0.95f : 0.7f);
            }
        }
    }

    /** The thin walls between tiles; a bulkhead's door picked out in mustard. */
    private void edges(YardPaint p, ShipGridComponent g, float lw) {
        if (g.edgeH != null) {
            for (int ty = 0; ty < g.edgeH.length; ty++) {
                for (int tx = 0; tx < g.edgeH[ty].length; tx++) {
                    EdgeWall e = g.edgeH[ty][tx];
                    if (e == null || e.isDestroyed()) continue;
                    edge(p, tx, ty, tx + 1, ty, e, lw);
                }
            }
        }
        if (g.edgeV != null) {
            for (int ty = 0; ty < g.edgeV.length; ty++) {
                for (int tx = 0; tx < g.edgeV[ty].length; tx++) {
                    EdgeWall e = g.edgeV[ty][tx];
                    if (e == null || e.isDestroyed()) continue;
                    edge(p, tx, ty, tx, ty + 1, e, lw);
                }
            }
        }
    }

    private void edge(YardPaint p, float gx0, float gy0, float gx1, float gy1, EdgeWall e, float lw) {
        float x0 = cx(gx0, gy0), y0 = cy(gx0, gy0), x1 = cx(gx1, gy1), y1 = cy(gx1, gy1);
        if (!e.hasBulkhead) {
            p.line(x0, y0, x1, y1, lw, PAINT, 0.7f);
            return;
        }
        // A door: the frame's ends in paint, the leaf in mustard
        float mx0 = x0 + (x1 - x0) * 0.25f, my0 = y0 + (y1 - y0) * 0.25f, mx1 = x0 + (x1 - x0) * 0.75f, my1 = y0 + (y1 - y0) * 0.75f;
        p.line(x0, y0, mx0, my0, lw, PAINT, 0.7f);
        p.line(mx1, my1, x1, y1, lw, PAINT, 0.7f);
        p.line(mx0, my0, mx1, my1, lw, Yard.MUSTARD, e.bulkheadSealed ? 0.95f : 0.45f);
    }

    private static boolean wall(ShipGridComponent g, int x, int y) {
        if (x < 0 || y < 0 || x >= g.width || y >= g.height) return false;
        Tile t = g.getTile(x, y);
        return t != null && t.isWall();
    }

    /** A system's lamp: green while it works, amber for a while (stunned, shorted) or cut off, red when damage has it. */
    static void lamp(YardPaint p, float cx, float cy, float r, ShipSystems.State st, boolean blink) {
        switch (st) {
            case ON:
                p.lamp(cx, cy, r, Yard.GREEN_LAMP, true);
                return;
            case OFF:
                p.lamp(cx, cy, r, Yard.WHITE_LAMP, false);
                return;
            case STUNNED:
            case SHORTED:
                p.lamp(cx, cy, r, Yard.AMBER_LAMP, blink);
                return;
            case CUT_OFF:
                p.lamp(cx, cy, r, Yard.AMBER_LAMP, true);
                return;
            default:
                p.lamp(cx, cy, r, Yard.RED_LAMP, true);
        }
    }

    /** The lamps' meanings, engraved under the board. */
    private void legendRow(YardPaint p, float x, float baseline, float w) {
        String[] names = {"WORKING", "OFF", "STUNNED, SHORTED", "CUT OFF", "DAMAGED", "BLOCK"};
        ShipSystems.State[] states = {ShipSystems.State.ON, ShipSystems.State.OFF, ShipSystems.State.STUNNED,
                ShipSystems.State.CUT_OFF, ShipSystems.State.DAMAGED, null};
        float cx = x;
        for (int i = 0; i < names.length; i++) {
            if (states[i] == null) p.lamp(cx + 4f, baseline - 4f, 3f, Yard.GREEN_LAMP, true);
            else lamp(p, cx + 4f, baseline - 4f, 3f, states[i], true);
            String label = i == names.length - 1 ? "RCS " + names[i] : names[i];
            float tw = p.legend(label, cx + 12f, baseline, Yard.INK);
            cx += 12f + tw + 18f;
            if (cx > x + w - 40f) break;
        }
    }

    // A point of the grid on the board. The grid's +x is the bow and its +y is port (Box2D's body
    // axes): bow to the right puts port up, and bow up puts port to the left
    private float cx(float gx, float gy) {
        return turned ? ox + (gh - gy) * px : ox + gx * px;
    }

    private float cy(float gx, float gy) {
        return turned ? oy + (gw - gx) * px : oy + (gh - gy) * px;
    }

    /** The left and top of the tiles from ({@code x}, {@code y}), {@code w} by {@code h} of them. */
    private float left(int x, int y, int w, int h) {
        return Math.min(cx(x, y), cx(x + w, y + h));
    }

    private float top(int x, int y, int w, int h) {
        return Math.min(cy(x, y), cy(x + w, y + h));
    }
}
