package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.LifeSupportSystem;
import com.ringsofiron.interiorgrid.PowerCircuit;
import com.ringsofiron.interiorgrid.PowerRoutingSystem;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.HudData.ConsoleAction;
import com.ringsofiron.uihud.data.HudData.ConsoleView;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.systems.InteractPromptSystem;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;
import com.ringsofiron.uihud.variants.cassette.screens.ShipSystems;
import com.ringsofiron.uihud.variants.cassette.screens.ShipSystems.State;

import java.util.Locale;

/**
 * CON, the console panel (spec CON): E at a system's console on deck hangs a small enamel panel beside
 * the console, an aluminium leader run to it, rather than a window in the middle of the screen, and it
 * closes as the captain walks away or sits down. Every console gives the system's name on Dymo tape (a
 * gun by its weapon, with its mount), its state on a lamp, its health and its power, and its one switch:
 * a toggle worked by Enter or a click, the reactor's under a guard, since switching it off darkens what it
 * feeds. Under those, what the console is for: the reactor's output, its circuit's draw and store; life
 * support's rooms, each lit for the air it gets or saying why not (behind a shut door, since it reaches
 * only through open ones, or venting, since it puts none into a room open to space), and the oxygen and
 * nitrogen tanks piped to it; a gun's key for taking it, and at a point-defense gun what that costs, typed
 * on a slip as the crisis placard types a way out's cost. The helm and the weapons console open none: E
 * sits there, as V does ({@link #seat}).
 */
final class ConsolePanel extends YardPart {
    /** Wide enough to type a point-defense gun's cost, "Point defense: in hand, it hunts no missile", on one line. */
    static final float W = 336f;
    /** Seconds the reactor's guard stays lifted for the second press. */
    static final float GUARD_SECONDS = 3f;
    /**
     * Tiles from its console past which the captain has walked away from the panel: a little past the
     * reach E has there, so it doesn't flicker shut at the edge.
     */
    static final float WALK_AWAY = InteractPromptSystem.INTERACT_RADIUS + 0.5f;
    /** Seconds the panel waits for the game to say how far the captain is before it gives up on them. */
    private static final float REACH_WAIT = 0.5f;
    /** Rooms life support's list shows; past that its last row counts the rest. */
    static final int ROOMS = 5;
    private static final float HEAD = 40f, ROWS = 52f, FOOT = 46f, ROOM_ROW = 15f, TANK_ROW = 18f, NOTE_LINE = 14f;
    private static final float SLIP_LINE = 14f, LEADER_RING = 9f;
    /** Room the reactor's tube leaves over the foot, where its switch's guard stands when lifted ({@code YardPaint.toggle}). */
    private static final float GUARD_ROOM = 16f;
    private static final float[][] HEALTH_ZONES = {{0f, SubsystemComponent.DISABLE_THRESHOLD}};
    private static final float[][] TANK_ZONES = {{0f, 0.15f}};
    private static final Color[] RED_ZONE = {Yard.RED};

    /** What a unit of life support does for one room, as its row says. */
    enum Air {
        AIR("AIR"), OTHER("AIR FROM ANOTHER UNIT"), SHUT("BEHIND A SHUT DOOR"), VENTING("VENTING");

        final String label;

        Air(String label) {
            this.label = label;
        }
    }

    /** One of life support's tank rows: the gas, how full its tanks are, what that says, and whether it is bad news. */
    static final class TankRow {
        String gas = "", words = "";
        float share;
        boolean on, bad;
    }

    private final TaskFrame f;
    private Entity entity;
    private SubsystemComponent s;
    private InteractableConsoleComponent console;
    /** The game's action at this console ("Take the gun"), or null. */
    private ConsoleAction take;
    /** Seconds the reactor's guard stays lifted. */
    private float armed;
    /** Seconds since it opened, and whether the game has said yet how far the captain is. */
    private float openFor;
    private boolean reachSeen;
    /** The circuit's count of what was drawn from it since its last reroute, last frame; and the draw that gives, W. */
    private double lastDrawn, drawW;
    /** The console's point on the canvas, design units, for the leader; NaN where the game can't say. */
    private float anchorX = Float.NaN, anchorY = Float.NaN;

    // This frame's reading
    private String name = "", where = "", stateWords = "", power = "";
    private State state = State.ON;
    private boolean powerBad;
    private Mount gun;
    final Array<String> roomNames = new Array<>();
    final Array<Air> roomAir = new Array<>();
    private String noAir = "";
    final TankRow o2 = new TankRow(), n2 = new TankRow();
    private final Array<String> cost = new Array<>();
    private final Array<String> slip = new Array<>();
    private final Array<String> note = new Array<>();
    private final Array<Compartment> fed = new Array<>(), venting = new Array<>(), fedAll = new Array<>(), ventAll = new Array<>();
    private final Array<SubsystemComponent> units = new Array<>(), one = new Array<>();
    private final RoomNames ownNames = new RoomNames();
    private Entity ownNamesShip;
    private final float[] ring = new float[40];

    ConsolePanel(TaskFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** The helm and the weapons console open no panel: E seats the captain there, as V does (spec CON). */
    static boolean seat(String consoleId) {
        return "helm".equals(consoleId) || "weapons".equals(consoleId);
    }

    boolean isOpen() {
        return entity != null;
    }

    /** Opens on the console E found. */
    void open(ConsoleView v) {
        entity = v.console;
        console = v.data;
        s = entity.getComponent(SubsystemComponent.class);
        take = v.actions.size > 0 ? v.actions.first() : null;
        armed = 0f;
        openFor = 0f;
        reachSeen = false;
        PowerCircuit c = s == null ? null : s.circuit;
        lastDrawn = c == null ? 0 : c.drawnThisTick;
        drawW = 0;
    }

    void close() {
        entity = null;
        console = null;
        s = null;
        take = null;
        armed = 0f;
        visible = false;
    }

    /**
     * Whether the captain has walked away, {@code reach} tiles from the console now (NaN while the game
     * can't say, as off its ship): past {@link #WALK_AWAY}, or off the ship once the game has said where
     * they are, or never said within {@link #REACH_WAIT}.
     */
    boolean walkedAway(float reach, float delta) {
        openFor += delta;
        if (Float.isNaN(reach)) return reachSeen || openFor > REACH_WAIT;
        reachSeen = true;
        return reach > WALK_AWAY;
    }

    /** Reads this frame's figures, {@code delta} seconds after the last. */
    void update(HudData d, float delta) {
        armed = Math.max(0f, armed - delta);
        PowerCircuit c = s == null ? null : s.circuit;
        double now = c == null ? 0 : c.drawnThisTick;
        // The circuit counts what is drawn from it since its last reroute, once a second: the count just
        // before it starts again is the last second's draw
        if (c == null) drawW = 0;
        else if (now < lastDrawn - 1e-6) drawW = lastDrawn / PowerRoutingSystem.ROUTING_TICK;
        lastDrawn = now;
        roomNames.clear();
        roomAir.clear();
        noAir = "";
        o2.on = n2.on = false;
        cost.clear();
        if (s == null) {
            name = console == null ? "CONSOLE" : console.title.toUpperCase(Locale.ROOT);
            where = stateWords = power = "";
            state = State.ON;
            return;
        }
        gun = s.type == SubsystemType.TURRET_MOUNT ? ShipSystems.mount(s, d.weaponFrame.mounts) : null;
        name = ShipSystems.name(s, gun);
        where = gun != null && !gun.name.isEmpty() ? gun.name.toUpperCase(Locale.ROOT) : "";
        state = ShipSystems.state(s);
        stateWords = stateWords(state, ShipSystems.timer(s, state));
        ShipGridComponent grid = console.ship == null ? null : console.ship.getComponent(ShipGridComponent.class);
        power = power(s, grid);
        powerBad = s.circuit == null && state != State.OFF;
        if (s.type == SubsystemType.LIFE_SUPPORT) lifeSupport(d, grid);
        if (gun != null) gunCost(gun, d.weaponFrame.mounts, cost);
    }

    /** The head's state: "ON", "OFF", "SHORTED 32 S", "STUNNED 8 S", "CUT OFF", "DAMAGED", "WRECKED". */
    static String stateWords(State st, float timer) {
        return Float.isNaN(timer) ? st.label : st.label + " " + ShipSystems.seconds(timer);
    }

    /**
     * Its power: the circuit feeding it, numbered as the power board numbers them ("CIRCUIT 1"), or none;
     * a system switched on and working but on no circuit says why.
     */
    static String power(SubsystemComponent s, ShipGridComponent grid) {
        if (s.circuit != null) {
            int n = grid == null ? 0 : grid.circuits.indexOf(s.circuit, true) + 1;
            return n > 0 ? "CIRCUIT " + n : "ON A CIRCUIT";
        }
        if (s.type != SubsystemType.REACTOR && ShipSystems.state(s) == State.CUT_OFF) return "NONE: NO REACTOR REACHES IT";
        return "NONE";
    }

    /** The reactor's tube: what it makes of what it is rated for, its circuit's draw, and the circuit's store. */
    static String[] reactorLines(SubsystemComponent s, double drawW) {
        boolean works = ShipSystems.state(s) == State.ON;
        double rated = s.ratedOutput > 0 ? s.ratedOutput : s.footprint == null ? 0 : PowerRoutingSystem.standardOutput(s.footprint.size);
        double out = works ? PowerRoutingSystem.reactorOutput(s) : 0;
        PowerCircuit c = s.circuit;
        return new String[]{
                "OUTPUT " + watts(out) + " OF " + watts(rated),
                "DRAW   " + (c == null ? "NONE, NO CIRCUIT" : watts(drawW)),
                "STORE  " + (c == null ? "NONE, NO CIRCUIT" : joules(c.stored) + ", " + Math.round(c.charge() * 100f) + "%")};
    }

    /** "3.0 MW", "850 KW", "40 W". */
    static String watts(double w) {
        if (w >= 1e6) return String.format(Locale.ROOT, "%.1f MW", w / 1e6);
        if (w >= 1e3) return String.format(Locale.ROOT, "%.0f KW", w / 1e3);
        return String.format(Locale.ROOT, "%.0f W", Math.max(0, w));
    }

    /** "6.0 MJ", "850 KJ", "40 J". */
    static String joules(double j) {
        if (j >= 1e6) return String.format(Locale.ROOT, "%.1f MJ", j / 1e6);
        if (j >= 1e3) return String.format(Locale.ROOT, "%.0f KJ", j / 1e3);
        return String.format(Locale.ROOT, "%.0f J", Math.max(0, j));
    }

    // --- Life support ------------------------------------------------------------------------

    private void lifeSupport(HudData d, ShipGridComponent grid) {
        Entity ship = console.ship;
        ShipSubsystemRegistryComponent registry = ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class);
        units.clear();
        if (registry != null) {
            for (int i = 0; i < registry.subsystems.size; i++) {
                SubsystemComponent u = registry.subsystems.get(i).getComponent(SubsystemComponent.class);
                if (u != null && u.type == SubsystemType.LIFE_SUPPORT) units.add(u);
            }
        }
        one.clear();
        one.add(s);
        LifeSupportSystem.reach(grid, one, fed, venting);
        LifeSupportSystem.reach(grid, units, fedAll, ventAll);
        noAir = noAir(state, fed.size + venting.size > 0);
        RoomNames names = names(d, ship, grid);
        if (noAir.isEmpty() && names != null) {
            Array<Compartment> rooms = names.rooms(grid);
            for (int i = 0; i < rooms.size; i++) {
                Compartment c = rooms.get(i);
                String n = names.of(grid, c);
                // An airlock is pumped down on purpose: not a room the panel talks about
                if (n.isEmpty() || n.startsWith("AIRLOCK") || roomNames.contains(n, false)) continue;
                roomNames.add(n);
                roomAir.add(air(fed.contains(c, true), venting.contains(c, true) || ventAll.contains(c, true), fedAll.contains(c, true)));
            }
        }
        double full = LifeSupportSystem.EMERGENCY_OXYGEN_KG * s.throughput;
        tank(o2, "O2", TankSupply.available(s.pipedTanks, Fluid.OXYGEN), capacity(s.pipedTanks, Fluid.OXYGEN),
                TankSupply.reserve(s), full, registry != null && TankSupply.hasTanks(registry, Fluid.OXYGEN));
        tank(n2, "N2", TankSupply.available(s.pipedTanks, Fluid.NITROGEN), capacity(s.pipedTanks, Fluid.NITROGEN),
                0, 0, registry != null && TankSupply.hasTanks(registry, Fluid.NITROGEN));
    }

    private RoomNames names(HudData d, Entity ship, ShipGridComponent grid) {
        if (grid == null) return null;
        if (ship == d.ctx.ship && d.roomNames() != null) return d.roomNames();
        if (ship != ownNamesShip) {
            ownNames.reset();
            ownNamesShip = ship;
        }
        return ownNames;
    }

    /** What this unit does for a room: puts air into it, or not, since it vents or is behind a shut door, though another unit may. */
    static Air air(boolean fed, boolean venting, boolean fedByAny) {
        if (fed) return Air.AIR;
        if (venting) return Air.VENTING;
        return fedByAny ? Air.OTHER : Air.SHUT;
    }

    /** Why this unit puts air into no room, from its state, or "" when it does ({@code reaches}). */
    static String noAir(State st, boolean reaches) {
        switch (st) {
            case OFF:
                return "NO AIR FROM IT WHILE IT IS SWITCHED OFF";
            case SHORTED:
                return "NO AIR FROM IT WHILE IT IS SHORTED";
            case STUNNED:
                return "NO AIR FROM IT WHILE IT IS STUNNED";
            case CUT_OFF:
                return "NO AIR FROM IT WHILE NO REACTOR REACHES IT";
            case DAMAGED:
            case WRECKED:
                return "NO AIR FROM IT UNTIL IT IS REPAIRED";
            default:
                return reaches ? "" : "IT STANDS IN NO ROOM, SO IT PUTS AIR INTO NONE";
        }
    }

    /** Kilograms the tanks of {@code fluid} among {@code tanks} hold full. */
    static double capacity(Array<SubsystemComponent> tanks, Fluid fluid) {
        double sum = 0;
        for (int i = 0; i < tanks.size; i++) if (tanks.get(i).fluid == fluid) sum += Math.max(0, tanks.get(i).capacity);
        return sum;
    }

    /**
     * A tank row for {@code gas}: the tanks piped to the unit hold {@code held} of {@code capacity} kg;
     * oxygen's own emergency reserve has {@code reserveKg} of {@code reserveFull} left. No row for a gas
     * the ship has no tank of ({@code shipHasTanks} false: a layout from before tanks, which life support
     * never runs short of).
     */
    static void tank(TankRow out, String gas, double held, double capacity, double reserveKg, double reserveFull, boolean shipHasTanks) {
        out.gas = gas;
        out.on = capacity > 0 || shipHasTanks;
        out.bad = false;
        if (!out.on) return;
        if (capacity <= 0) {
            out.share = 0f;
            out.words = "NONE PIPED TO IT";
            out.bad = true;
        } else if (held <= 0 && reserveFull > 0) {
            out.share = (float) MathUtils.clamp(reserveKg / reserveFull, 0, 1);
            out.words = "DRY, RESERVE " + Math.round(reserveKg) + " KG";
            out.bad = true;
        } else if (held <= 0) {
            out.share = 0f;
            out.words = "DRY";
            out.bad = true;
        } else {
            out.share = (float) MathUtils.clamp(held / capacity, 0, 1);
            out.words = Math.round(out.share * 100f) + "%";
            out.bad = out.share < 0.15f;
        }
    }

    // --- A gun -------------------------------------------------------------------------------

    /**
     * What taking {@code gun} in hand costs, a phrase a line (spec CON): a point-defense gun hunts no
     * missile while the captain holds it, and its side may then have none to cover it, as on the starter
     * corvette, where each autocannon's arc is blind across the hull. Nothing for any other gun.
     */
    static void gunCost(Mount gun, Array<Mount> mounts, Array<String> out) {
        out.clear();
        if (gun.kind != Kind.POINT_DEFENSE) return;
        out.add("Point defense: in hand, it hunts no missile");
        if (!covered(gun, mounts)) out.add("No other point defense covers its side");
    }

    /** Whether another working point-defense mount's arc takes in the way {@code gun} faces. */
    static boolean covered(Mount gun, Array<Mount> mounts) {
        for (int i = 0; i < mounts.size; i++) {
            Mount m = mounts.get(i);
            if (m == gun || m.kind != Kind.POINT_DEFENSE || !m.working()) continue;
            float off = Math.abs(((m.facingDeg - gun.facingDeg) % 360f + 540f) % 360f - 180f);
            if (off <= m.arcDeg / 2f + 1e-3f) return true;
        }
        return false;
    }

    // --- The switch --------------------------------------------------------------------------

    /** Switching it off asks twice: the reactor, which darkens what it feeds. */
    boolean guarded() {
        return s != null && s.type == SubsystemType.REACTOR && s.switchedOn;
    }

    /** The switch, by Enter or a click: the guard's first press lifts it. Returns whether it did anything. */
    boolean flip() {
        if (s == null) return false;
        if (guarded() && armed <= 0f) {
            armed = GUARD_SECONDS;
            return true;
        }
        s.switchedOn = !s.switchedOn;
        armed = 0f;
        return true;
    }

    /** The words beside the switch's key. */
    String switchWords() {
        if (s == null) return "";
        if (!s.switchedOn) return "SWITCH ON";
        if (!guarded()) return "SWITCH OFF";
        return armed > 0f ? "AGAIN TO SWITCH OFF" : "TWICE TO SWITCH OFF";
    }

    private String takeKey() {
        String k = take == null ? "" : f.data.key(take.action);
        return (k.isEmpty() ? "V" : k).toUpperCase(Locale.ROOT);
    }

    // --- Size and place ----------------------------------------------------------------------

    /** Breaks this frame's long lines for the width and returns the panel's height. */
    float measure(YardPaint p) {
        slip.clear();
        for (int i = 0; i < cost.size; i++) CrisisPlacard.wrapInto(p, cost.get(i), W - 48f, slip);
        note.clear();
        if (!noAir.isEmpty()) wrapLegend(p, noAir, W - 32f, note);
        return height();
    }

    float height() {
        return HEAD + ROWS + block() + FOOT;
    }

    private float block() {
        if (s == null) return 0f;
        switch (s.type) {
            case REACTOR:
                // The tube, and room over the foot for the switch's guard to lift into
                return 62f + GUARD_ROOM;
            case LIFE_SUPPORT: {
                float rooms = noAir.isEmpty() ? Math.min(roomNames.size, ROOMS) * ROOM_ROW + 6f : Math.max(1, note.size) * NOTE_LINE + 8f;
                return 2f + rooms + ((o2.on ? 1 : 0) + (n2.on ? 1 : 0)) * TANK_ROW + 4f;
            }
            case TURRET_MOUNT:
                return 26f + (slip.size > 0 ? 10f + slip.size * SLIP_LINE + 8f : 0f);
            default:
                return 0f;
        }
    }

    /** Where its console stands on the canvas, design units, for the leader; NaN for unknown. */
    void anchor(float ax, float ay) {
        anchorX = ax;
        anchorY = ay;
    }

    private static void wrapLegend(YardPaint p, String s, float width, Array<String> out) {
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            if (line.length() > 0 && p.width(Face.LEGEND, 12f, line + " " + word) > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
    }

    // --- Drawing -----------------------------------------------------------------------------

    @Override
    protected void draw(YardPaint p) {
        Yard.Finish fin = Yard.Finish.YARD;
        leader(p);
        p.unit(0f, 0f, W, h, fin, "");

        // The head: the system's name on Dymo tape, where a gun is mounted, and its state on a lamp
        float tw = p.dymo(14f, 13f, YardText.cut(name, 22));
        if (!where.isEmpty()) p.legendFit(where, 22f + tw, 25f, W - 140f - tw, fin.dim);
        Yard.Lamp lamp = state == State.ON ? Yard.GREEN_LAMP : state.bad() ? Yard.RED_LAMP : state == State.OFF ? Yard.WHITE_LAMP : Yard.AMBER_LAMP;
        p.lamp(W - 22f, 20.5f, 6f, lamp, state != State.OFF);
        p.textR(Face.LEGEND, 12f, stateWords, W - 36f, 25f, state.bad() ? Yard.RED : fin.ink);
        p.rect(12f, 36f, W - 24f, 1f, fin.lo, 0.8f);
        if (s == null) {
            foot(p, fin);
            return;
        }

        // Health and power
        p.legend("HEALTH", 14f, 58f, fin.ink);
        p.level(80f, 49f, 150f, 10f, s.health, HEALTH_ZONES, RED_ZONE);
        float dw = p.drums(244f, 44f, YardText.count(Math.round(s.health * 100f), 3).trim(), 11f, 18f, 15f, false);
        p.legend("%", 248f + dw, 58f, fin.ink);
        p.legend("POWER", 14f, 82f, fin.ink);
        p.legendFit(power, 80f, 82f, W - 96f, powerBad ? Yard.RED : fin.ink);

        float by = HEAD + ROWS;
        switch (s.type) {
            case REACTOR:
                reactor(p, by);
                break;
            case LIFE_SUPPORT:
                lifeSupport(p, by, fin);
                break;
            case TURRET_MOUNT:
                gun(p, by, fin);
                break;
            default:
                break;
        }
        foot(p, fin);
    }

    /**
     * The aluminium leader from the panel's near edge to its console, and a ring round the console: from
     * a side when the console is beside the panel, else from the top or bottom, as when the panel stands
     * off the ship's deck under or over it. None while the panel is over its console.
     */
    private void leader(YardPaint p) {
        if (Float.isNaN(anchorX)) return;
        float ax = anchorX - x, ay = anchorY - y, ex, ey;
        if (ax < 0f || ax > W) {
            ex = ax < 0f ? 0f : W;
            ey = MathUtils.clamp(ay, 16f, h - 16f);
        } else if (ay < 0f || ay > h) {
            ex = MathUtils.clamp(ax, 16f, W - 16f);
            ey = ay < 0f ? 0f : h;
        } else {
            return;
        }
        p.line(ex, ey + 1.5f, ax, ay + 1.5f, 3f, Color.BLACK, 0.35f);
        p.line(ex, ey, ax, ay, 2f, Yard.ALU);
        for (int i = 0; i < 20; i++) {
            float a = i * MathUtils.PI2 / 20f;
            ring[2 * i] = ax + LEADER_RING * MathUtils.cos(a);
            ring[2 * i + 1] = ay + LEADER_RING * MathUtils.sin(a);
        }
        p.polyline(ring, true, 2f, Yard.ALU, 1f);
    }

    /** The reactor's tube: output, the circuit's draw, the store. */
    private void reactor(YardPaint p, float y) {
        float tx = 18f, ty = y + 5f, tw = W - 36f, th = 50f;
        p.tube(tx, ty, tw, th, 3f, 3f, false);
        String[] lines = reactorLines(s, drawW);
        for (int i = 0; i < lines.length; i++) p.tube(lines[i], tx + 8f, ty + 15f + i * 15f, 13f, Yard.P_AMBER, false);
        p.tubeOver(tx, ty, tw, th);
    }

    /** Life support: each room it reaches lit for its air or saying why not, then its tanks. */
    private void lifeSupport(YardPaint p, float y, Yard.Finish fin) {
        float ry = y + 2f;
        if (!noAir.isEmpty()) {
            for (int i = 0; i < note.size; i++) p.legend(note.get(i), 16f, ry + 12f + i * NOTE_LINE, Yard.RED);
            ry += Math.max(1, note.size) * NOTE_LINE + 8f;
        } else {
            int n = Math.min(roomNames.size, ROOMS);
            for (int i = 0; i < n; i++) {
                if (i == ROOMS - 1 && roomNames.size > ROOMS) {
                    p.legend("AND " + (roomNames.size - i) + " MORE ROOMS", 34f, ry + 11f, fin.dim);
                } else {
                    Air a = roomAir.get(i);
                    p.lamp(22f, ry + 7f, 3.6f, a == Air.VENTING ? Yard.RED_LAMP : Yard.GREEN_LAMP, a != Air.SHUT);
                    p.legend(roomNames.get(i), 34f, ry + 11f, fin.ink);
                    p.textR(Face.LEGEND, 12f, a.label, W - 16f, ry + 11f, a == Air.VENTING ? Yard.RED : a == Air.SHUT ? fin.dim : fin.ink);
                }
                ry += ROOM_ROW;
            }
            ry += 6f;
        }
        ry = tankRow(p, ry, o2, fin);
        tankRow(p, ry, n2, fin);
    }

    private float tankRow(YardPaint p, float y, TankRow t, Yard.Finish fin) {
        if (!t.on) return y;
        p.legend(t.gas, 16f, y + 13f, fin.ink);
        p.level(48f, y + 4f, 130f, 9f, t.share, TANK_ZONES, RED_ZONE);
        p.legendFit(t.words, 192f, y + 13f, W - 208f, t.bad ? Yard.RED : fin.ink);
        return y + TANK_ROW;
    }

    /** A gun: its key for taking it in hand, and what that costs on a typed slip. */
    private void gun(YardPaint p, float y, Yard.Finish fin) {
        if (take != null) {
            float kw = p.key(16f, y + 5f, takeKey());
            p.legend(take.label.toUpperCase(Locale.ROOT), 24f + kw, y + 17f, fin.ink);
        } else {
            p.legend("NO SEAT AT THIS GUN", 16f, y + 17f, fin.dim);
        }
        if (slip.size > 0) CrisisPlacard.slip(p, 16f, y + 28f, W - 32f, 10f + slip.size * SLIP_LINE, slip, false);
    }

    /** The switch: a toggle, the reactor's under its guard, with Enter beside it; and E to close. */
    private void foot(YardPaint p, Yard.Finish fin) {
        float fy = h - FOOT;
        p.rect(12f, fy, W - 24f, 1f, fin.lo, 0.8f);
        if (s != null) {
            p.toggle(34f, fy + 24f, s.switchedOn, guarded(), armed > 0f);
            float kw = p.key(60f, fy + 16f, "ENTER");
            p.legend(switchWords(), 66f + kw, fy + 28f, fin.ink);
        }
        float ck = p.keyWidth("E"), cw = p.width(Face.LEGEND, 12f, "CLOSE"), cx = W - 16f - cw - 6f - ck;
        p.key(cx, fy + 16f, "E");
        p.legend("CLOSE", cx + ck + 6f, fy + 28f, fin.ink);
    }

    // --- The pointer -------------------------------------------------------------------------

    private enum Spot { NONE, HEAD, HEALTH, POWER, BLOCK, TAKE, SWITCH, CLOSE }

    private Spot spot(float px, float py) {
        float lx = localX(px), ly = localY(py);
        if (lx < 0f || lx > W || ly < 0f || ly > h) return Spot.NONE;
        float fy = h - FOOT;
        if (ly >= fy) {
            if (lx >= W - 70f) return Spot.CLOSE;
            return s == null ? Spot.NONE : lx < 230f ? Spot.SWITCH : Spot.NONE;
        }
        if (ly < 37f) return Spot.HEAD;
        if (s == null) return Spot.NONE;
        if (ly < 66f) return Spot.HEALTH;
        if (ly < HEAD + ROWS) return Spot.POWER;
        if (s.type == SubsystemType.TURRET_MOUNT && take != null && ly < HEAD + ROWS + 26f) return Spot.TAKE;
        return Spot.BLOCK;
    }

    @Override
    public boolean pressable(float px, float py) {
        Spot sp = spot(px, py);
        return sp == Spot.SWITCH || sp == Spot.CLOSE || sp == Spot.TAKE;
    }

    @Override
    public boolean press(float px, float py) {
        switch (spot(px, py)) {
            case SWITCH:
                return flip();
            case CLOSE:
                close();
                return true;
            case TAKE:
                if (take == null || take.run == null) return false;
                take.run.run();
                return true;
            default:
                return false;
        }
    }

    @Override
    public String hover(float px, float py) {
        if (s == null) return spot(px, py) == Spot.CLOSE ? "Close: E or Esc, or walk away" : null;
        String what = Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase(Locale.ROOT);
        switch (spot(px, py)) {
            case HEAD:
                return what + ": " + stateHover(state, ShipSystems.timer(s, state));
            case HEALTH:
                return "Health: below " + Math.round(SubsystemComponent.DISABLE_THRESHOLD * 100f) + "% it stops working until the crew repair it";
            case POWER:
                return s.circuit != null ? "Fed by " + power.toLowerCase(Locale.ROOT) + ", as the power board numbers the circuits"
                        : "No power reaches it";
            case BLOCK:
                return blockHover();
            case TAKE:
                return "Take the gun: " + takeKey() + ". The pointer aims it, the fire key or a click fires"
                        + (gun != null && gun.kind == Kind.POINT_DEFENSE ? "; it hunts no missile while you hold it" : "");
            case SWITCH:
                if (!s.switchedOn) return "Switch it on: Enter or a click";
                return guarded() ? "Switch the reactor off: Enter twice, or two clicks. What it feeds goes dark"
                        : "Switch it off: Enter or a click";
            case CLOSE:
                return "Close: E or Esc, or walk away";
            default:
                return null;
        }
    }

    /** What the head's state means, for the hover. */
    static String stateHover(State st, float timer) {
        switch (st) {
            case OFF:
                return "switched off. Enter switches it on";
            case SHORTED:
                return "shorted. The breaker recloses by itself" + (Float.isNaN(timer) ? " soon" : " in " + Math.max(0, Math.round(timer)) + " s")
                        + ", or the crew reset it sooner";
            case STUNNED:
                return "stunned by an ion hit. It comes back by itself" + (Float.isNaN(timer) ? " soon" : " in " + Math.max(0, Math.round(timer)) + " s");
            case CUT_OFF:
                return "cut off. It is switched on, but no reactor's circuit reaches it";
            case DAMAGED:
                return "damaged below working order. The crew repair it";
            case WRECKED:
                return "wrecked. The crew repair it, from nothing";
            default:
                return "working";
        }
    }

    private String blockHover() {
        switch (s.type) {
            case REACTOR:
                return "Output: what it makes now, less as it is damaged. Draw: what the guns and shields on its circuit took in the last second. Store: what the circuit holds for them";
            case LIFE_SUPPORT:
                return "It puts air only into the rooms it reaches through open doors, and none into a room open to space. Its tanks are the ones piped to it";
            case TURRET_MOUNT:
                return gun != null && gun.kind == Kind.POINT_DEFENSE ? "In your hand it fires where you aim, so it shoots down no missile by itself" : null;
            default:
                return null;
        }
    }
}
