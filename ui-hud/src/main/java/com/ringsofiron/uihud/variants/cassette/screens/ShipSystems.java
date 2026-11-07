package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.DamageStates;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The ship's systems as the ship console's Systems tab lists them (Context 10.2, v15): every system
 * in the ship's registry, by kind, each with its health, its power, its state and its switch. A kind
 * the ship has more than one of is a group: its row switches them all, and opening it lists each one
 * with its own switch. Each gun is its own row, named by its weapon and its mount. The RCS blocks are
 * hull parts rather than systems: one row with no switch, counting those that fire. Read from the
 * components, as the classic console window reads the one it shows and switches. The Task HUD's
 * console panel names a system and its state by the same rules ({@link #name}, {@link #state}).
 */
public final class ShipSystems {
    /** Why a system is or isn't working, mildest first. */
    public enum State {
        ON("ON"), OFF("OFF"), STUNNED("STUNNED"), SHORTED("SHORTED"), CUT_OFF("CUT OFF"), DAMAGED("DAMAGED"), WRECKED("WRECKED");

        public final String label;

        State(String label) {
            this.label = label;
        }

        /** Whether it stops the system and needs the crew or a yard: red on the tube and the board. */
        public boolean bad() {
            return this == DAMAGED || this == WRECKED || this == CUT_OFF;
        }
    }

    /** The room a tile of the ship is in ({@code RoomNames}), or "". */
    interface Rooms {
        String at(int x, int y);
    }

    /** One system as read this time. */
    static final class Sys {
        SubsystemComponent s;
        String name = "", where = "";
        State state = State.ON;
        /** Seconds until it comes back by itself (a short's reclose, a stun), NaN for none. */
        float timer = Float.NaN;
        /** Its circuit, counted from 1 as the power board counts them; 0 when none reaches it. */
        int circuit;
    }

    /** A row of the list: a group, a system on its own, a member of an open group, or the RCS. */
    static final class Row {
        static final int GROUP = 0, ONE = 1, MEMBER = 2, RCS = 3;
        int kind;
        String name = "", where = "";
        /** The worst health of its systems, 0 to 1; NaN for the RCS. */
        float health = Float.NaN;
        String power = "—", state = "";
        /** Its systems are all switched on; some are. */
        boolean allOn, anyOn;
        /** Switching it off asks twice: a reactor, which darkens the ship. */
        boolean guarded;
        boolean open;
        State worst = State.ON;
        final Array<Sys> systems = new Array<>();
    }

    /** The order the kinds are listed in; the RCS row follows the engines. */
    private static final SubsystemType[] ORDER = {
            SubsystemType.REACTOR, SubsystemType.ENGINE, SubsystemType.SHIELD_EMITTER, SubsystemType.TURRET_MOUNT,
            SubsystemType.WEAPONS, SubsystemType.HELM, SubsystemType.LIFE_SUPPORT, SubsystemType.TANK,
            SubsystemType.AIRLOCK, SubsystemType.DOCKING_PORT, SubsystemType.ESCAPE_POD, SubsystemType.CARGO};

    final Array<Sys> all = new Array<>();
    final Array<Row> rows = new Array<>();
    /** The groups shown open, by name: kept from one read to the next. */
    final Set<String> open = new HashSet<>();
    int rcsBlocks, rcsFiring;
    private final Array<Sys> sysPool = new Array<>();
    private final Array<Row> rowPool = new Array<>();
    private final Array<Sys> kind = new Array<>();
    private final Array<String> names = new Array<>();
    private ShipGridComponent grid;

    /** Reads the systems again: {@code systems} the ship's, in its registry's order. */
    void read(Array<SubsystemComponent> systems, ShipGridComponent grid, Rooms rooms, Array<Mount> mounts) {
        this.grid = grid;
        sysPool.addAll(all);
        all.clear();
        for (int i = 0; i < rows.size; i++) rows.get(i).systems.clear();
        rowPool.addAll(rows);
        rows.clear();
        for (int i = 0; i < systems.size; i++) {
            SubsystemComponent s = systems.get(i);
            Sys y = sysPool.size > 0 ? sysPool.pop() : new Sys();
            y.s = s;
            Mount m = s.type == SubsystemType.TURRET_MOUNT ? mount(s, mounts) : null;
            y.name = name(s, m);
            y.where = m != null && !m.name.isEmpty() ? m.name.toUpperCase(Locale.ROOT)
                    : rooms == null || s.footprint == null ? "" : rooms.at(s.footprint.originX, s.footprint.originY);
            if (y.where == null) y.where = "";
            y.state = state(s);
            y.timer = timer(s, y.state);
            y.circuit = grid == null || s.circuit == null ? 0 : grid.circuits.indexOf(s.circuit, true) + 1;
            all.add(y);
        }
        rcsBlocks = grid == null ? 0 : grid.rcsThrusters.size;
        rcsFiring = 0;
        for (int i = 0; i < rcsBlocks; i++) if (grid.rcsThrusters.get(i).works(grid)) rcsFiring++;
        for (SubsystemType t : ORDER) {
            kind.clear();
            for (int i = 0; i < all.size; i++) if (all.get(i).s.type == t) kind.add(all.get(i));
            if (t == SubsystemType.TURRET_MOUNT) {
                // Each gun its own row: two guns are two weapons, with their own arcs
                for (int i = 0; i < kind.size; i++) single(kind.get(i), Row.ONE);
            } else {
                // The others by name, a group where there is more than one
                names.clear();
                for (int i = 0; i < kind.size; i++) if (!names.contains(kind.get(i).name, false)) names.add(kind.get(i).name);
                for (int n = 0; n < names.size; n++) group(names.get(n));
            }
            if (t == SubsystemType.ENGINE && rcsBlocks > 0) rcs();
        }
    }

    private void single(Sys y, int kindOfRow) {
        Row r = row(kindOfRow);
        r.name = y.name;
        r.where = y.where;
        r.systems.add(y);
        sum(r);
    }

    private void group(String name) {
        Row g = null;
        int count = 0;
        for (int i = 0; i < kind.size; i++) if (kind.get(i).name.equals(name)) count++;
        if (count == 1) {
            for (int i = 0; i < kind.size; i++) if (kind.get(i).name.equals(name)) single(kind.get(i), Row.ONE);
            return;
        }
        g = row(Row.GROUP);
        g.name = name + " ×" + count;
        g.open = open.contains(name);
        for (int i = 0; i < kind.size; i++) if (kind.get(i).name.equals(name)) g.systems.add(kind.get(i));
        sum(g);
        // One "where" when they share a room, none when they are spread; then each member outside
        // every room by its side of the keel, so two engines read PORT and STBD
        String where = g.systems.first().where;
        for (int i = 1; i < g.systems.size; i++) if (!g.systems.get(i).where.equals(where)) where = "";
        g.where = where;
        for (int i = 0; i < g.systems.size; i++) {
            Sys y = g.systems.get(i);
            if (y.where.isEmpty()) y.where = side(y.s, grid);
        }
        if (!g.open) return;
        for (int i = 0; i < g.systems.size; i++) single(g.systems.get(i), Row.MEMBER);
    }

    /** Which side of the middle a system lies: the grid's +y is port. Blank on the middle line. */
    static String side(SubsystemComponent s, ShipGridComponent grid) {
        if (grid == null || s.footprint == null) return "";
        float off = s.footprint.originY + s.footprint.height() / 2f - grid.height / 2f;
        return off > 0.5f ? "PORT" : off < -0.5f ? "STBD" : "";
    }

    /** The system on tile ({@code x}, {@code y}), or null. */
    Sys at(int x, int y) {
        for (int i = 0; i < all.size; i++) {
            SubsystemComponent s = all.get(i).s;
            if (s.footprint != null && s.covers(x, y)) return all.get(i);
        }
        return null;
    }

    private void rcs() {
        Row r = row(Row.RCS);
        r.name = "RCS ×" + rcsBlocks;
        r.where = "HULL";
        r.health = Float.NaN;
        r.power = "—";
        int stopped = rcsBlocks - rcsFiring;
        r.state = stopped == 0 ? "ALL " + rcsBlocks + " FIRING" : rcsFiring + " OF " + rcsBlocks + " FIRING";
        r.worst = stopped > 0 ? State.DAMAGED : State.ON;
        r.allOn = r.anyOn = true;
    }

    private Row row(int kindOfRow) {
        Row r = rowPool.size > 0 ? rowPool.pop() : new Row();
        r.kind = kindOfRow;
        r.open = false;
        r.guarded = false;
        r.systems.clear();
        rows.add(r);
        return r;
    }

    /** A row's figures from its systems: the worst health and state, the power, the switch. */
    private static void sum(Row r) {
        float health = 1f;
        State worst = State.ON;
        boolean allOn = true, anyOn = false, needs = false, allPowered = true, anyPowered = false, reactor = false;
        for (int i = 0; i < r.systems.size; i++) {
            Sys y = r.systems.get(i);
            health = Math.min(health, y.s.health);
            if (y.state.ordinal() > worst.ordinal()) worst = y.state;
            allOn &= y.s.switchedOn;
            anyOn |= y.s.switchedOn;
            if (y.s.type == SubsystemType.REACTOR) reactor = true;
            else if (y.s.type.needsPower()) {
                needs = true;
                allPowered &= y.s.powered;
                anyPowered |= y.s.powered;
            }
        }
        r.health = health;
        r.worst = worst;
        r.allOn = allOn;
        r.anyOn = anyOn;
        r.guarded = reactor;
        // A reactor makes its power and a tank or a hold needs none: a dash, as the spec has it. The
        // powered ones name their circuit ("C1"), as the power board counts them
        r.power = !needs ? "—" : allPowered ? circuits(r.systems) : anyPowered ? "SOME" : "NONE";
        r.state = r.systems.size == 1 ? stateText(r.systems.first()) : groupState(r.systems);
    }

    /** The circuits feeding {@code systems}: "C1", "C1,2"; "ON" when the game names none. */
    static String circuits(Array<Sys> systems) {
        StringBuilder sb = new StringBuilder();
        for (int c = 1; c <= 9; c++) {
            boolean on = false;
            for (int i = 0; i < systems.size; i++) on |= systems.get(i).circuit == c;
            if (!on) continue;
            sb.append(sb.length() == 0 ? "C" : ",").append(c);
        }
        return sb.length() == 0 ? "ON" : sb.toString();
    }

    /** "ON", "SHORTED 32 S", "STUNNED 8 S". */
    static String stateText(Sys y) {
        if (Float.isNaN(y.timer)) return y.state.label;
        return y.state.label + " " + seconds(y.timer);
    }

    /** A group's state: one word when they agree, else each state's count, worst first ("1 DAMAGED · 1 ON"). */
    static String groupState(Array<Sys> systems) {
        int[] counts = new int[State.values().length];
        for (int i = 0; i < systems.size; i++) counts[systems.get(i).state.ordinal()]++;
        StringBuilder sb = new StringBuilder();
        int kinds = 0;
        for (int k = counts.length - 1; k >= 0; k--) if (counts[k] > 0) kinds++;
        if (kinds == 1) {
            for (int k = 0; k < counts.length; k++) if (counts[k] > 0) return "ALL " + State.values()[k].label;
        }
        for (int k = counts.length - 1; k >= 0; k--) {
            if (counts[k] == 0) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(counts[k]).append(' ').append(State.values()[k].label);
        }
        return sb.toString();
    }

    /** "32 S" under two minutes, "2:05" above. */
    public static String seconds(float s) {
        int t = Math.max(0, Math.round(s));
        if (t < 120) return t + " S";
        return String.format(Locale.ROOT, "%d:%02d", t / 60, t % 60);
    }

    /** Why {@code s} is or isn't working, as the power board reads it, worst first. */
    public static State state(SubsystemComponent s) {
        if (s.isDestroyed()) return State.WRECKED;
        if (s.isDisabled()) return State.DAMAGED;
        if (s.shorted) return State.SHORTED;
        if (s.isStunned()) return State.STUNNED;
        if (!s.switchedOn) return State.OFF;
        if (s.type.needsPower() && !s.isOperational) return State.CUT_OFF;
        return State.ON;
    }

    /** Seconds until {@code s} comes back by itself in {@code state}: a short's reclose, a stun; NaN for none. */
    public static float timer(SubsystemComponent s, State state) {
        if (state == State.SHORTED) return Math.max(0f, DamageStates.AUTO_RESET - s.shortedFor);
        if (state == State.STUNNED) return s.stunned;
        return Float.NaN;
    }

    /** The gun mounted on turret {@code s}: the mount whose system's tile it covers. */
    public static Mount mount(SubsystemComponent s, Array<Mount> mounts) {
        if (mounts == null) return null;
        for (int i = 0; i < mounts.size; i++) {
            Mount m = mounts.get(i);
            if (m.tileX >= 0 && s.covers(m.tileX, m.tileY)) return m;
        }
        return null;
    }

    /** The system's name on the tube: its kind, a tank by what it holds, a gun by its weapon. */
    public static String name(SubsystemComponent s, Mount gun) {
        switch (s.type) {
            case REACTOR:
                return "REACTOR";
            case SHIELD_EMITTER:
                return "SHIELD EMITTER";
            case TURRET_MOUNT:
                return gun == null || gun.weapon.isEmpty() ? "TURRET" : gun.weapon.toUpperCase(Locale.ROOT);
            case LIFE_SUPPORT:
                return "LIFE SUPPORT";
            case HELM:
                return "HELM";
            case WEAPONS:
                return "WEAPONS CONSOLE";
            case CARGO:
                return "CARGO BAY";
            case AIRLOCK:
                return "AIRLOCK";
            case TANK:
                return s.fluid == null ? "TANK" : s.fluid.name().replace('_', ' ') + " TANK";
            case ENGINE:
                return "ENGINE";
            case DOCKING_PORT:
                return "DOCKING PORT";
            case ESCAPE_POD:
                return "ESCAPE POD";
            default:
                return s.type.name().replace('_', ' ');
        }
    }

    /** The row whose systems include {@code s}: its own, or its group's while the group is shut. */
    int rowOf(SubsystemComponent s) {
        int group = -1;
        for (int i = 0; i < rows.size; i++) {
            Row r = rows.get(i);
            for (int k = 0; k < r.systems.size; k++) {
                if (r.systems.get(k).s != s) continue;
                if (r.kind != Row.GROUP) return i;
                if (group < 0) group = i;
            }
        }
        return group;
    }

    /** Turns row {@code i}'s switch: off when all its systems are on, else all on. Returns what it did. */
    String toggle(int i) {
        Row r = rows.get(i);
        if (r.kind == Row.RCS || r.systems.size == 0) return null;
        boolean on = !r.allOn;
        for (int k = 0; k < r.systems.size; k++) r.systems.get(k).s.switchedOn = on;
        String what = r.kind == Row.GROUP ? "Every " + r.systems.first().name.toLowerCase(Locale.ROOT)
                : Fmt.capital(r.name.toLowerCase(Locale.ROOT)) + (r.where.isEmpty() ? "" : " (" + r.where + ")");
        return what + " switched " + (on ? "on" : "off");
    }

    /** Opens or shuts group row {@code i}. */
    void openOrShut(int i) {
        Row r = rows.get(i);
        if (r.kind != Row.GROUP || r.systems.size == 0) return;
        String name = r.systems.first().name;
        if (!open.remove(name)) open.add(name);
    }
}
