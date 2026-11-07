package com.ringsofiron.uihud.variants.vfd.screens;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.DamageStates;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.vfd.Fmt;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The ship's systems as a FILAMENT terminal lists them (functional-context 10.2, version 18): every
 * system in the ship's registry by kind, each with its health, its power, its state and its switch. A
 * kind the ship has more than one of is a group: its row switches them together, and opening it lists
 * each member with its own switch. Each gun is its own row, by its weapon and its mount
 * ("AUTOCANNON MK1 (FWD STBD)", version 19). The RCS blocks
 * are hull parts, not systems: one row with no switch, counting those that fire. Read from the
 * components, as the classic console window reads and switches the one it shows.
 */
public final class ShipSystems {
    /** Why a system is or isn't working, mildest first. */
    public enum State {
        ON("ON"), OFF("OFF"), STUNNED("STUNNED"), SHORTED("SHORTED"), CUT_OFF("CUT OFF"), DAMAGED("DAMAGED"), WRECKED("WRECKED");

        public final String label;

        State(String label) {
            this.label = label;
        }

        /** Out of action: stopped until the crew or a yard put it right, or power reaches it. */
        public boolean down() {
            return this == DAMAGED || this == WRECKED || this == CUT_OFF;
        }
    }

    /** One system as read this time. */
    public static final class Sys {
        public SubsystemComponent s;
        public String name = "", where = "";
        public State state = State.ON;
        /** Seconds until it comes back by itself (a short's reclose, a stun), NaN for none. */
        public float timer = Float.NaN;
        /** Its circuit, counted from 1; 0 when none reaches it. */
        public int circuit;
    }

    /** A row of the list: a group, a system on its own, a member of an open group, or the RCS. */
    public static final class Row {
        public static final int GROUP = 0, ONE = 1, MEMBER = 2, RCS = 3;
        public int kind;
        public String name = "", where = "";
        /** The worst health of its systems, 0 to 1; NaN for the RCS. */
        public float health = Float.NaN;
        public String power = "-", state = "";
        /** Its systems are all switched on; some are. */
        public boolean allOn, anyOn;
        /** Its switch asks twice before it turns them off: a reactor, which darkens the ship. */
        public boolean guarded;
        public boolean open;
        public State worst = State.ON;
        public final Array<Sys> systems = new Array<>();
    }

    /** The order the kinds are listed in; the RCS row follows the engines. */
    private static final SubsystemType[] ORDER = {
            SubsystemType.REACTOR, SubsystemType.ENGINE, SubsystemType.SHIELD_EMITTER, SubsystemType.TURRET_MOUNT,
            SubsystemType.WEAPONS, SubsystemType.HELM, SubsystemType.LIFE_SUPPORT, SubsystemType.TANK,
            SubsystemType.AIRLOCK, SubsystemType.DOCKING_PORT, SubsystemType.ESCAPE_POD, SubsystemType.CARGO};

    public final Array<Sys> all = new Array<>();
    public final Array<Row> rows = new Array<>();
    /** The groups shown open, by name: kept from one read to the next. */
    public final Set<String> open = new HashSet<>();
    public int rcsBlocks, rcsFiring;
    public ShipGridComponent grid;
    /** The ship's rooms' names, kept with its layout. */
    public final RoomNames rooms = new RoomNames();
    private final Array<Sys> sysPool = new Array<>();
    private final Array<Row> rowPool = new Array<>();
    private final Array<Sys> kind = new Array<>();
    private final Array<String> names = new Array<>();

    /** Reads the systems of {@code ship} again; {@code mounts} names the guns. */
    public void read(Entity ship, Array<Mount> mounts) {
        ShipSubsystemRegistryComponent reg = ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class);
        grid = ship == null ? null : ship.getComponent(ShipGridComponent.class);
        sysPool.addAll(all);
        all.clear();
        for (int i = 0; i < rows.size; i++) rows.get(i).systems.clear();
        rowPool.addAll(rows);
        rows.clear();
        rcsBlocks = rcsFiring = 0;
        if (reg == null) return;
        Array<Entity> parts = reg.subsystems;
        for (int i = 0; i < parts.size; i++) {
            SubsystemComponent s = parts.get(i).getComponent(SubsystemComponent.class);
            if (s == null) continue;
            Sys y = sysPool.size > 0 ? sysPool.pop() : new Sys();
            y.s = s;
            Mount m = s.type == SubsystemType.TURRET_MOUNT ? mount(s, mounts) : null;
            y.name = name(s, m);
            String room = grid == null || s.footprint == null ? "" : rooms.at(grid, s.footprint.originX, s.footprint.originY);
            y.where = m != null && !m.name.isEmpty() ? where(m.name) : room == null ? "" : room.toUpperCase(Locale.ROOT);
            y.state = state(s);
            y.timer = y.state == State.SHORTED ? Math.max(0f, DamageStates.AUTO_RESET - s.shortedFor) : y.state == State.STUNNED ? s.stunned : Float.NaN;
            y.circuit = grid == null || s.circuit == null ? 0 : grid.circuits.indexOf(s.circuit, true) + 1;
            all.add(y);
        }
        if (grid != null) {
            rcsBlocks = grid.rcsThrusters.size;
            for (int i = 0; i < rcsBlocks; i++) if (grid.rcsThrusters.get(i).works(grid)) rcsFiring++;
        }
        for (SubsystemType t : ORDER) {
            kind.clear();
            for (int i = 0; i < all.size; i++) if (all.get(i).s.type == t) kind.add(all.get(i));
            if (t == SubsystemType.TURRET_MOUNT) {
                // Each gun its own row: two guns are two weapons, with their own arcs
                for (int i = 0; i < kind.size; i++) single(kind.get(i), Row.ONE);
            } else {
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
        int count = 0;
        for (int i = 0; i < kind.size; i++) if (kind.get(i).name.equals(name)) count++;
        if (count == 1) {
            for (int i = 0; i < kind.size; i++) if (kind.get(i).name.equals(name)) single(kind.get(i), Row.ONE);
            return;
        }
        Row g = row(Row.GROUP);
        g.name = name + " ×" + count;
        g.open = open.contains(name);
        for (int i = 0; i < kind.size; i++) if (kind.get(i).name.equals(name)) g.systems.add(kind.get(i));
        sum(g);
        // One "where" when they share a room, none when they are spread; each member outside every
        // room then by its side of the keel, so two engines read PORT and STBD
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

    /** Which side of the keel a system lies: the grid's +y is port. Blank on the middle line. */
    static String side(SubsystemComponent s, ShipGridComponent grid) {
        if (grid == null || s.footprint == null) return "";
        float off = s.footprint.originY + s.footprint.height() / 2f - grid.height / 2f;
        return off > 0.5f ? "PORT" : off < -0.5f ? "STBD" : "";
    }

    private void rcs() {
        Row r = row(Row.RCS);
        r.name = "RCS ×" + rcsBlocks;
        r.where = "HULL";
        r.health = Float.NaN;
        r.power = "-";
        int stopped = rcsBlocks - rcsFiring;
        r.state = stopped == 0 ? rcsBlocks + " ON" : rcsFiring + " ON · " + stopped + " STOPPED";
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
        // A reactor makes its power, and a tank or a hold needs none: a dash
        r.power = !needs ? "-" : allPowered ? "OK" : anyPowered ? "SOME" : "NONE";
        r.state = r.systems.size == 1 ? stateText(r.systems.first()) : groupState(r.systems);
    }

    /** "ON", "SHORTED 32 S", "STUNNED 8 S". */
    public static String stateText(Sys y) {
        if (Float.isNaN(y.timer)) return y.state.label;
        return y.state.label + " " + seconds(y.timer);
    }

    /** A group's state: one word when they agree, else each state's count, worst first ("1 DAMAGED · 1 ON"). */
    static String groupState(Array<Sys> systems) {
        int[] counts = new int[State.values().length];
        for (int i = 0; i < systems.size; i++) counts[systems.get(i).state.ordinal()]++;
        int kinds = 0;
        for (int count : counts) if (count > 0) kinds++;
        if (kinds == 1) {
            for (int k = 0; k < counts.length; k++) if (counts[k] > 0) return State.values()[k].label;
        }
        StringBuilder sb = new StringBuilder();
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

    /**
     * A gun's mount where it sits, less its kind, which the weapon's name already says (functional-context
     * 10.2, version 19): "FWD PORT PD" is FWD PORT; a mount the game could not place keeps its name.
     */
    static String where(String mount) {
        String at = Fmt.place(mount);
        return at.isEmpty() ? mount.toUpperCase(Locale.ROOT) : at;
    }

    /** The gun mounted on turret {@code s}: the mount whose tile it covers. */
    static Mount mount(SubsystemComponent s, Array<Mount> mounts) {
        if (mounts == null) return null;
        for (int i = 0; i < mounts.size; i++) {
            Mount m = mounts.get(i);
            if (m.tileX >= 0 && s.covers(m.tileX, m.tileY)) return m;
        }
        return null;
    }

    /** A system's name: its kind, a tank by what it holds, a gun by its weapon. */
    public static String name(SubsystemComponent s, Mount gun) {
        switch (s.type) {
            case SHIELD_EMITTER:
                return "SHIELD EMITTER";
            case TURRET_MOUNT:
                return gun == null || gun.weapon.isEmpty() ? "TURRET" : gun.weapon.toUpperCase(Locale.ROOT);
            case LIFE_SUPPORT:
                return "LIFE SUPPORT";
            case WEAPONS:
                return "WEAPONS CONSOLE";
            case CARGO:
                return "CARGO BAY";
            case TANK:
                return s.fluid == null ? "TANK" : s.fluid.name().replace('_', ' ') + " TANK";
            case DOCKING_PORT:
                return "DOCKING PORT";
            case ESCAPE_POD:
                return "ESCAPE POD";
            default:
                return s.type.name().replace('_', ' ');
        }
    }

    /** The row whose systems include {@code s}: its own, or its group's while the group is shut; -1 for none. */
    public int rowOf(SubsystemComponent s) {
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

    /** The system on tile ({@code x}, {@code y}), or null. */
    public Sys at(int x, int y) {
        for (int i = 0; i < all.size; i++) {
            SubsystemComponent s = all.get(i).s;
            if (s.footprint != null && s.covers(x, y)) return all.get(i);
        }
        return null;
    }

    /**
     * Turns row {@code i}'s switch (version 18): when its members disagree it turns them all on, and
     * once all are on it turns them all off. Returns what it did, or null for a row with no switch.
     */
    public String toggle(int i) {
        if (i < 0 || i >= rows.size) return null;
        Row r = rows.get(i);
        if (r.kind == Row.RCS || r.systems.size == 0) return null;
        boolean on = !r.allOn;
        for (int k = 0; k < r.systems.size; k++) r.systems.get(k).s.switchedOn = on;
        String what = r.kind == Row.GROUP ? "Every " + r.systems.first().name.toLowerCase(Locale.ROOT)
                : capital(r.name.toLowerCase(Locale.ROOT)) + (r.where.isEmpty() ? "" : " (" + r.where.toLowerCase(Locale.ROOT) + ")");
        return what + " switched " + (on ? "on" : "off");
    }

    /** Opens or shuts group row {@code i}; false for a row that isn't a group. */
    public boolean openOrShut(int i, boolean open) {
        if (i < 0 || i >= rows.size) return false;
        Row r = rows.get(i);
        if (r.kind != Row.GROUP || r.systems.size == 0) return false;
        String name = r.systems.first().name;
        return open ? this.open.add(name) : this.open.remove(name);
    }

    static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
