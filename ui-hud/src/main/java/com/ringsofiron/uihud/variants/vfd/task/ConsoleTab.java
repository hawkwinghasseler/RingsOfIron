package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.AirflowEdge;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.DamageStates;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.PowerCircuit;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.HudData.ConsoleAction;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Circuit;
import com.ringsofiron.uihud.providers.StationBoardProvider.Hand;
import com.ringsofiron.uihud.providers.StationBoardProvider.Seat;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Phosphor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.ShipSystems;

import java.util.Locale;

/**
 * The one page of a console's panel (functional-task 7, CON): the console's system and, under it, what
 * that kind of system has to say. Every console: its power (its circuit counted from 1, or none), its
 * state in the power board's words ("SHORTED 32 S") and who works it; its one switch is the soft key, a
 * click alone (so Enter never switches a system off), pressed twice for a reactor, which darkens the
 * ship. The reactor: the circuit's output of its rating, the circuit's draw over the last second (the
 * guns' and the shields' bursts out of its store), the store, and how many systems it feeds. Life
 * support: the rooms it puts air into, and why not the rest, as the game's life support decides it
 * (only through open doors, and never into a room venting to space), then the oxygen and nitrogen in
 * the tanks piped to it and its own emergency oxygen. A gun: its weapon, how it stands and who works
 * it, then the game's order there with its key ({@code V  TAKE THE GUN}) and, for point defense, what
 * taking it costs.
 */
final class ConsoleTab extends Tab {
    /** Click ids: the switch's soft key, one of the game's orders. */
    static final int KEY = Windows.TAB_IDS, ORDER = Windows.TAB_IDS + 20;
    static final int SWITCH = 0, MAX_ORDERS = 3;
    /** A value's column after its label; a line's pitch; a head's and an order's rows. */
    static final float VALUE = 92f, LINE = SectionTab.LINE, HEAD_STEP = 20f, ORDER_ROW = 28f;
    /** How long the reactor's switch waits for its second press. */
    static final float CONFIRM_FOR = 3f;
    /** The rows' kinds. */
    static final int FIELD = 0, HEAD = 1, ORDER_KIND = 2, NOTE = 3;

    /** One row of the page: a labelled value (wrapped under itself), a section's head, an order, a note across. */
    static final class Row {
        int kind;
        String label = "", value = "";
        float level = Drive.NORM;
        boolean amber;
        int order = -1;
    }

    /** The console open and its system, null with none. */
    Entity console;
    SubsystemComponent sys;
    String consoleId = "";
    /** The header: the system's name, its room, its health 0..1 (NaN with no system). */
    String title = "", room = "";
    float health = Float.NaN;
    final Array<Row> rows = new Array<>();
    private final Array<Row> pool = new Array<>();
    /** The game's orders at it: their words and the caps of their keys. */
    final String[] orderWord = new String[MAX_ORDERS], orderCap = new String[MAX_ORDERS];
    int orders;
    /** The reactor's switch pressed once: seconds left for the second press. */
    float armLeft;
    /** The circuit's draw: what it had drawn at the last look, what has been drawn this second, and over how long. */
    private PowerCircuit drawCircuit;
    private double lastDrawn, drawn;
    private float drawTime;
    /** Watts drawn over the last whole second, NaN until a second has been seen. */
    double drawWatts = Double.NaN;
    private final RoomNames rooms = new RoomNames();
    private ShipGridComponent roomsGrid;
    private final Array<Compartment> served = new Array<>();
    private final String[] lines = new String[6];
    private final String[] one = new String[1];
    /** Where each order's row was painted, for its click box; NaN when it didn't fit. */
    private final float[] orderY = new float[MAX_ORDERS];
    /** How tall the page's rows stand, as last measured. */
    float rowsHeight;

    ConsoleTab() {
        super("CONSOLE", ClassicWindow.CONSOLE, "E");
        keys.define(1, 1).cap(SWITCH, "");
    }

    @Override
    void shown() {
        armLeft = 0f;
        drawCircuit = null;
        drawWatts = Double.NaN;
    }

    // ------------------------------------------------------------------ reading

    @Override
    void refresh(Boards b, HudData d) {
        read(d);
        boolean can = sys != null && !sys.isDestroyed();
        String word = sys == null ? "" : sys.switchedOn ? "SWITCH OFF" : "SWITCH ON";
        keys.set(SWITCH, word, can);
    }

    /** The console as it is now, into the header and the rows. */
    void read(HudData d) {
        HudData.ConsoleView v = d.console;
        console = v.console;
        consoleId = v.data == null ? "" : v.data.consoleId;
        sys = console == null ? null : console.getComponent(SubsystemComponent.class);
        clear();
        Entity ship = v.data != null && v.data.ship != null ? v.data.ship : d.ctx.ship;
        ShipGridComponent grid = ship == null ? null : ship.getComponent(ShipGridComponent.class);
        if (grid != roomsGrid) {
            rooms.reset();
            roomsGrid = grid;
        }
        Mount gun = sys != null && sys.type == SubsystemType.TURRET_MOUNT && v.data != null ? mountAt(d, v.data.tileX, v.data.tileY) : null;
        String fallback = v.data == null || v.data.title == null ? "CONSOLE" : v.data.title.toUpperCase(Locale.ROOT);
        title = sys == null ? fallback : ShipSystems.name(sys, gun);
        room = grid == null || v.data == null ? "" : rooms.at(grid, v.data.tileX, v.data.tileY).toUpperCase(Locale.ROOT);
        if (gun != null && !gun.name.isEmpty()) {
            // Where the gun sits, less its kind, which the title says: "PORT MSL" is PORT
            String at = Fmt.place(gun.name);
            room = at.isEmpty() ? gun.name.toUpperCase(Locale.ROOT) : at;
        }
        health = sys == null ? Float.NaN : sys.health;
        orders = 0;
        if (sys == null) {
            note("NO SYSTEM HERE", Drive.DIM);
        } else {
            system(d, grid);
            if (sys.type == SubsystemType.REACTOR) reactor(d, grid);
            else if (sys.type == SubsystemType.LIFE_SUPPORT) lifeSupport(grid, ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class));
            else if (sys.type == SubsystemType.TURRET_MOUNT) gun(d, gun);
        }
        rowsHeight = measure(Glass.cells(Geo.CON_W - 28f - VALUE), Glass.cells(Geo.CON_W - 28f));
    }

    /** Every console's own lines: its power, its state, who works it. */
    private void system(HudData d, ShipGridComponent grid) {
        int circuit = grid == null || sys.circuit == null ? 0 : grid.circuits.indexOf(sys.circuit, true) + 1;
        String power;
        boolean noPower = false;
        if (sys.type == SubsystemType.REACTOR) power = circuit > 0 ? "CIRCUIT " + circuit + ", ITS OWN" : "FEEDS NO CIRCUIT";
        else if (!sys.type.needsPower()) power = "NEEDS NONE";
        else if (circuit > 0) power = "CIRCUIT " + circuit;
        else {
            power = "NONE: NO REACTOR'S CIRCUIT REACHES IT";
            noPower = true;
        }
        field("POWER", power, Drive.NORM, noPower);
        ShipSystems.State st = ShipSystems.state(sys);
        field("STATE", stateText(st, sys), st == ShipSystems.State.OFF ? Drive.DIM : Drive.NORM, st != ShipSystems.State.ON && st != ShipSystems.State.OFF);
        // A gun says who has it under THE GUN, from the mount
        Seat seat = sys.type == SubsystemType.TURRET_MOUNT ? null : seat(d);
        if (seat != null) field("WORKED BY", who(seat), seat.hand == Hand.NOBODY ? Drive.DIM : Drive.NORM, false);
    }

    /** How a system stands, in the power board's words, with when it comes back by itself: "SHORTED, BACK IN 32 S OR ON A RESET". */
    static String stateText(ShipSystems.State st, SubsystemComponent s) {
        switch (st) {
            case SHORTED:
                return "SHORTED, BACK IN " + ShipSystems.seconds(Math.max(0f, DamageStates.AUTO_RESET - s.shortedFor)) + " OR ON A RESET";
            case STUNNED:
                return "STUNNED, BACK IN " + ShipSystems.seconds(s.stunned);
            case CUT_OFF:
                return "CUT OFF: NO POWER REACHES IT";
            default:
                return st.label;
        }
    }

    /** The reactor: the circuit's output of its rating, its draw over the last second, the store, what it feeds. */
    private void reactor(HudData d, ShipGridComponent grid) {
        head("ITS CIRCUIT");
        int at = grid == null || sys.circuit == null ? -1 : grid.circuits.indexOf(sys.circuit, true);
        Circuit c = at >= 0 && at < d.powerFrame.circuits.size ? d.powerFrame.circuits.get(at) : null;
        if (c == null) {
            note("IT FEEDS NOTHING WHILE IT IS DOWN", Drive.DIM);
            return;
        }
        field("OUTPUT", ShipTab.kw(c.outputWatts) + " OF " + ShipTab.kw(c.ratedWatts) + " KW"
                + (c.reactors > 1 ? ", " + c.reactors + " REACTORS" : ""), Drive.NORM, c.outputWatts < c.ratedWatts * 0.999);
        field("DRAW", Double.isNaN(drawWatts) ? "MEASURING" : ShipTab.kw(drawWatts) + " KW, THE LAST SECOND",
                Double.isNaN(drawWatts) ? Drive.DIM : Drive.NORM, false);
        String store = c.capacity > 0
                ? String.format(Locale.ROOT, "%d%%, %s OF %s KJ", Math.round(c.charge() * 100f), Glass.thousands(c.stored / 1000.0), Glass.thousands(c.capacity / 1000.0))
                : "NONE";
        field("STORE", store, Drive.NORM, c.capacity > 0 && c.charge() < 0.25f);
        field("FEEDS", c.systems == 1 ? "1 SYSTEM" : c.systems + " SYSTEMS", Drive.NORM, false);
    }

    /**
     * Life support: the rooms it airs, as {@code LifeSupportSystem} serves them (its own room and every
     * room through open openings, none venting), why the rest get none, and its gases.
     */
    private void lifeSupport(ShipGridComponent grid, ShipSubsystemRegistryComponent registry) {
        head("AIR");
        if (grid == null) return;
        if (!sys.isOperational || sys.isDestroyed()) {
            note("IT AIRS NO ROOM WHILE IT IS DOWN", Drive.NORM).amber = true;
        } else {
            served(grid, sys, served);
            Array<Compartment> all = rooms.rooms(grid);
            StringBuilder to = new StringBuilder();
            for (int i = 0; i < all.size; i++) {
                Compartment c = all.get(i);
                if (!served.contains(c, true) || venting(grid, c)) continue;
                if (to.length() > 0) to.append(", ");
                to.append(rooms.of(grid, c).toUpperCase(Locale.ROOT));
            }
            field("AIR TO", to.length() == 0 ? "NO ROOM" : to.toString(), Drive.NORM, to.length() == 0);
            for (int i = 0; i < all.size; i++) {
                Compartment c = all.get(i);
                boolean in = served.contains(c, true);
                if (in && !venting(grid, c)) continue;
                String name = rooms.of(grid, c).toUpperCase(Locale.ROOT);
                field("NONE TO", name + (in ? ": VENTING TO SPACE" : ": BEHIND A SHUT DOOR"), Drive.NORM, in);
            }
        }
        gas("OXYGEN", Fluid.OXYGEN, registry);
        gas("NITROGEN", Fluid.NITROGEN, registry);
        double reserve = TankSupply.reserve(sys);
        field("RESERVE", String.format(Locale.ROOT, "%s KG OF OXYGEN, ITS OWN", Glass.units(reserve)), Drive.DIM, reserve <= 0);
    }

    /**
     * One gas in the tanks piped to it: kilograms of full. With none piped it has none but its own
     * reserve, as when damage has cut the pipes, unless the ship has no tank of it at all, which leaves
     * it unlimited ({@link TankSupply#hasTanks}).
     */
    private void gas(String label, Fluid fluid, ShipSubsystemRegistryComponent registry) {
        double in = 0, full = 0;
        int tanks = 0;
        for (int i = 0; i < sys.pipedTanks.size; i++) {
            SubsystemComponent t = sys.pipedTanks.get(i);
            if (t.fluid != fluid) continue;
            tanks++;
            if (t.canSupply()) in += Math.max(0, t.contents);
            full += t.capacity;
        }
        if (tanks == 0) {
            if (registry != null && TankSupply.hasTanks(registry, fluid)) field(label, "NONE: NO TANK'S PIPE REACHES IT", Drive.NORM, true);
            else field(label, "NOT LIMITED: NO TANK OF IT ABOARD", Drive.DIM, false);
            return;
        }
        boolean low = full > 0 && in < full * 0.25;
        field(label, Glass.units(in) + " OF " + Glass.units(full) + " KG" + (tanks > 1 ? ", " + tanks + " TANKS" : ""), Drive.NORM, low);
    }

    /** A gun: its weapon and how it stands, then the game's orders here, and what taking point defense costs. */
    private void gun(HudData d, Mount m) {
        head("THE GUN");
        if (m != null) {
            String state = m.state.name().replace('_', ' ');
            if (m.state == WeaponStatusProvider.State.RELOADING && m.cooldown > 0f) state += " " + ShipSystems.seconds(m.cooldown);
            String ammo = m.magazine > 0 ? ", " + m.ammo + " OF " + m.magazine + " LEFT" : m.ammo >= 0 ? ", " + m.ammo + " LEFT" : "";
            field("WEAPON", (m.weapon.isEmpty() ? "NONE FITTED" : m.weapon.toUpperCase(Locale.ROOT)) + ", " + state + ammo, Drive.NORM, !m.working());
            field("WORKED BY", handled(m, seat(d)), m.controller == Controller.NONE ? Drive.DIM : Drive.NORM, false);
        }
        orders(d);
        if (m != null && m.kind == Kind.POINT_DEFENSE) note("POINT DEFENSE: IN HAND, IT HUNTS NO MISSILE", Drive.DIM);
    }

    /**
     * Who has the gun (functional-task C9): the captain's hand, its gunner by name from the station
     * board's post at it, the weapons console's crew at half skill, its own point defense, or nobody,
     * when it fires only on the captain's trigger.
     */
    static String handled(Mount m, Seat post) {
        switch (m.controller) {
            case PLAYER:
                return "YOU, IN HAND";
            case CREW:
                return post != null && post.hand == Hand.CREW && post.who != null && !post.who.isEmpty()
                        ? post.who.toUpperCase(Locale.ROOT) + ", AT THE GUN" : "A GUNNER, AT THE GUN";
            case CONSOLE:
                return "THE WEAPONS CONSOLE, AT HALF SKILL";
            case AUTO:
                return "NOBODY: POINT DEFENSE, AUTOMATIC";
            default:
                return "NOBODY: IT FIRES ON YOUR TRIGGER";
        }
    }

    /** The game's orders at the console, with the caps of their keys. */
    private void orders(HudData d) {
        Array<ConsoleAction> actions = d.console.actions;
        for (int i = 0; i < actions.size && orders < MAX_ORDERS; i++) {
            ConsoleAction a = actions.get(i);
            orderWord[orders] = a.label.toUpperCase(Locale.ROOT);
            orderCap[orders] = a.action.isEmpty() ? "" : MapPlan.cap(d.key(a.action));
            Row r = row(ORDER_KIND);
            r.order = orders;
            orders++;
        }
    }

    /** The mount whose system sits at {@code x, y} on the grid, or null. */
    static Mount mountAt(HudData d, int x, int y) {
        Array<Mount> mounts = d.weaponFrame.mounts;
        for (int i = 0; i < mounts.size; i++) {
            Mount m = mounts.get(i);
            if (m.tileX == x && m.tileY == y) return m;
        }
        return null;
    }

    /** The station board's post at this console, or null. */
    private Seat seat(HudData d) {
        if (console == null) return null;
        Array<Seat> seats = d.stations.seats;
        for (int i = 0; i < seats.size; i++) if (seats.get(i).console == console) return seats.get(i);
        return null;
    }

    /** Who works a post: "YOU", the crew member, "ADA, ON THE WAY", or "NOBODY". */
    static String who(Seat s) {
        String name = s.who == null ? "" : s.who.toUpperCase(Locale.ROOT);
        switch (s.hand) {
            case PLAYER:
                return "YOU";
            case CREW:
                return name.isEmpty() ? "CREW" : name;
            case COMING:
                return (name.isEmpty() ? "CREW" : name) + ", ON THE WAY";
            default:
                return "NOBODY";
        }
    }

    /**
     * The rooms a life support unit serves, as the game's life support finds them: the room its
     * footprint stands in (or one beside it), and every room through an opening a tile wide or more.
     */
    static void served(ShipGridComponent grid, SubsystemComponent unit, Array<Compartment> out) {
        out.clear();
        Compartment home = home(grid, unit);
        if (home == null) return;
        out.add(home);
        for (int i = 0; i < out.size; i++) {
            Compartment c = out.get(i);
            for (int j = 0; j < grid.airflow.size; j++) {
                AirflowEdge e = grid.airflow.get(j);
                if (e.room != c || e.other == null || e.width < 1f) continue;
                if (!out.contains(e.other, true)) out.add(e.other);
            }
        }
    }

    /** The room the unit stands in, or one next to its footprint. */
    static Compartment home(ShipGridComponent grid, SubsystemComponent unit) {
        if (unit.footprint == null) return null;
        Compartment beside = null;
        int x0 = unit.footprint.originX, y0 = unit.footprint.originY;
        for (int y = y0 - 1; y <= y0 + unit.footprint.height(); y++) {
            for (int x = x0 - 1; x <= x0 + unit.footprint.width(); x++) {
                if (!grid.inBounds(x, y)) continue;
                Compartment c = grid.compartmentAt(x, y);
                if (unit.footprint.covers(x, y)) {
                    if (c != null) return c;
                } else if (beside == null && c != null) {
                    beside = c;
                }
            }
        }
        return beside;
    }

    /** Whether air leaves room {@code c} for space faster than a trickle, as life support judges it. */
    static boolean venting(ShipGridComponent grid, Compartment c) {
        for (int i = 0; i < grid.airflow.size; i++) {
            AirflowEdge e = grid.airflow.get(i);
            if (e.room == c && e.other == null && e.flow > ShipGridComponent.VENTING_HAZARD_FLOW) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ the rows

    private void clear() {
        pool.addAll(rows);
        rows.clear();
    }

    private Row row(int kind) {
        Row r = pool.size > 0 ? pool.pop() : new Row();
        r.kind = kind;
        r.label = "";
        r.value = "";
        r.level = Drive.NORM;
        r.amber = false;
        r.order = -1;
        rows.add(r);
        return r;
    }

    private void field(String label, String value, float level, boolean amber) {
        Row r = row(FIELD);
        r.label = label;
        r.value = value;
        r.level = level;
        r.amber = amber;
    }

    private void head(String label) {
        row(HEAD).label = label;
    }

    private Row note(String text, float level) {
        Row r = row(NOTE);
        r.value = text;
        r.level = level;
        return r;
    }

    /** How tall the rows stand, a value wrapping in {@code valueCells} and a note in {@code noteCells}. */
    float measure(int valueCells, int noteCells) {
        float h = 0f;
        for (int i = 0; i < rows.size; i++) {
            Row r = rows.get(i);
            switch (r.kind) {
                case HEAD:
                    h += (i > 0 ? 6f : 0f) + HEAD_STEP;
                    break;
                case ORDER_KIND:
                    h += ORDER_ROW;
                    break;
                case NOTE:
                    h += Math.max(1, Glass.wrap(r.value, noteCells, lines, lines.length)) * LINE + 2f;
                    break;
                default:
                    h += Math.max(1, Glass.wrap(r.value, valueCells, lines, lines.length)) * LINE + 2f;
            }
        }
        return h;
    }

    // ------------------------------------------------------------------ each frame

    @Override
    void update(Boards b, HudData d, float delta) {
        if (armLeft > 0f && (armLeft -= delta) <= 0f) {
            armLeft = 0f;
            refresh(b, d);
        }
        draw(delta);
    }

    /**
     * The reactor's circuit's draw: what has been taken from its store since the last look, summed
     * over a second. The routing zeroes the count each second, so a fall is a new second begun.
     */
    private void draw(float delta) {
        PowerCircuit c = sys == null || sys.type != SubsystemType.REACTOR ? null : sys.circuit;
        if (c != drawCircuit) {
            drawCircuit = c;
            lastDrawn = c == null ? 0 : c.drawnThisTick;
            drawn = 0;
            drawTime = 0f;
            drawWatts = Double.NaN;
            return;
        }
        if (c == null) return;
        double now = c.drawnThisTick;
        drawn += now >= lastDrawn ? now - lastDrawn : now;
        lastDrawn = now;
        drawTime += delta;
        if (drawTime >= 1f) {
            drawWatts = drawn / drawTime;
            drawn = 0;
            drawTime = 0f;
        }
    }

    // ------------------------------------------------------------------ paint

    @Override
    void paint(Filament f, float time) {
        float x0 = at.lx, x1 = at.dr, y = at.ly;
        int valueCells = Glass.cells(x1 - x0 - VALUE), noteCells = Glass.cells(x1 - x0);
        for (int i = 0; i < MAX_ORDERS; i++) orderY[i] = Float.NaN;
        for (int i = 0; i < rows.size; i++) {
            Row r = rows.get(i);
            if (y + Glass.RH > at.lb) break;
            switch (r.kind) {
                case HEAD:
                    if (i > 0) y += 6f;
                    Glass.head(f, x0, y, x1, r.label);
                    y += HEAD_STEP;
                    break;
                case ORDER_KIND:
                    if (y + Glass.KEY_H > at.lb) break;
                    one[0] = orderCap[r.order];
                    Glass.capWord(f, x0, y, one, Glass.cut(orderWord[r.order], noteCells - 6), Drive.NORM);
                    orderY[r.order] = y;
                    y += ORDER_ROW;
                    break;
                case NOTE: {
                    int n = Glass.wrap(r.value, noteCells, lines, lines.length);
                    for (int j = 0; j < n && y + Glass.RH <= at.lb; j++) {
                        text(f, x0, y, lines[j], r.level, r.amber);
                        y += LINE;
                    }
                    y += 2f;
                    break;
                }
                default: {
                    Glass.label(f, x0, y + 9.5f, r.label);
                    int n = Glass.wrap(r.value, valueCells, lines, lines.length);
                    for (int j = 0; j < n && y + Glass.RH <= at.lb; j++) {
                        text(f, x0 + VALUE, y, lines[j], r.level, r.amber);
                        y += LINE;
                    }
                    y += 2f;
                }
            }
        }
    }

    private static void text(Filament f, float x, float y, String s, float level, boolean amber) {
        if (amber) Glass.text(f, x, y, s, Drive.NORM, Phosphor.AMBER);
        else Glass.text(f, x, y, s, level);
    }

    @Override
    void boxes(FilamentActor a) {
        keys.boxes(a, KEY);
        for (int i = 0; i < orders; i++) {
            if (Float.isNaN(orderY[i])) continue;
            a.box(ORDER + i, at.lx - 2f, orderY[i] - 3f, at.dr - at.lx + 4f, ORDER_ROW - 2f);
        }
    }

    @Override
    void clicked(int id) {
        if (id >= KEY && id < KEY + keys.count()) {
            if (keys.can(id - KEY)) press(id - KEY);
        } else if (id >= ORDER && id < ORDER + MAX_ORDERS) {
            order(id - ORDER);
        }
    }

    /** The switch: on or off, and a reactor's off only on the second press within {@link #CONFIRM_FOR} seconds. */
    void press(int k) {
        if (k != SWITCH || sys == null) return;
        if (sys.type == SubsystemType.REACTOR && sys.switchedOn && armLeft <= 0f) {
            armLeft = CONFIRM_FOR;
            windows.say("Press again to switch the reactor off: what it feeds goes dark");
            windows.refresh();
            return;
        }
        armLeft = 0f;
        sys.switchedOn = !sys.switchedOn;
        windows.say(capital(title.toLowerCase(Locale.ROOT)) + (sys.switchedOn ? " switched on" : " switched off"));
        windows.refresh();
    }

    /** The game's order {@code i} at the console, as the button in the game's window. */
    void order(int i) {
        HudData d = windows.data();
        if (d == null || i < 0 || i >= d.console.actions.size) return;
        d.console.actions.get(i).run.run();
        windows.refresh();
    }

    static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
