package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Entity;

/**
 * A docking port worked as an airlock, for crew going out on EVA and coming back in. Its chamber is
 * the port's opening, between the outer door (the port door) and the inner hatch across the
 * opening's inner edge ({@link DockingPortLayout}). At rest ({@link State#IN}) the hatch stands open
 * and the chamber is part of the cabin, as a port always was.
 *
 * <p>Cycling out, the hatch shuts, the pump draws the chamber's air back into the cabin (an e-fold
 * every {@link #PUMP_TAU} seconds, down to {@link #PUMPED} of standard pressure) and the outer door
 * opens on next to nothing: {@link State#OUT}. A port without power has no pump, and its door opens
 * on the chamber's air, which vents. Cycling in, the outer door shuts, the chamber fills from the
 * cabin through the hatch's valve (no power needed) and the hatch opens. Working a door takes
 * {@link #DOOR_SECONDS}, and a door the cycle holds shut is {@link EdgeWall#locked}: no one opens it
 * under the cycle.
 *
 * <p>One user at a time ({@link #claim}). The user asks for the airlock the way they need it next:
 * a crew member going out asks for it IN to step into the chamber, then OUT to step out of it; one
 * coming back asks for it OUT to step in, then IN to step through to the cabin. A cycle starts only
 * with no one in the chamber but the user ({@link #crowded}, which the crew module sets each tick).
 * With no user the airlock goes back to rest, or stands OUT while someone is out on its tether reel
 * ({@link #tethered}) so they can come straight in.
 *
 * <p>A docked port is the docking module's: its airlock stands at rest, hatch open. Not saved: the
 * doors are, and {@link #of} reads the state back from them.
 */
public final class Airlock {

    /** Where an airlock is in its cycle. */
    public enum State {
        /** Hatch open, outer door shut: the chamber is part of the cabin. At rest. */
        IN,
        /** The hatch shutting, for a cycle out. */
        SEALING_HATCH,
        /** Both shut, the chamber's air pumped back into the cabin. */
        PUMPING,
        /** The outer door opening. */
        OPENING_DOOR,
        /** Outer door open, hatch shut: the chamber is open to space. */
        OUT,
        /** The outer door shutting, for a cycle in. */
        SEALING_DOOR,
        /** Both shut, the chamber filling from the cabin. */
        FILLING,
        /** The hatch opening. */
        OPENING_HATCH
    }

    /** Seconds to shut or open a door or hatch, dogs and all. */
    public static final float DOOR_SECONDS = 2f;
    /** Seconds for the pump to take the chamber's air down by a factor of e. */
    public static final float PUMP_TAU = 4f;
    /** Share of standard pressure left in the chamber when the outer door opens. */
    public static final float PUMPED = 0.03f;
    /** Seconds the pump runs at most before the door opens on what is left, a pump that can't pull it down. */
    public static final float PUMP_LIMIT = 30f;
    /** Seconds for the chamber to fill by a factor of e toward the cabin's pressure. */
    public static final float FILL_TAU = 1.5f;
    /** Share of the cabin's pressure the chamber reaches before the hatch opens. */
    public static final float FILLED = 0.95f;
    /**
     * Seconds the chamber fills at most before the hatch opens anyway (a cabin with no air to give),
     * unless the chamber is holed to space.
     */
    public static final float FILL_LIMIT = 20f;

    public final MountFootprint port;
    private State state;
    private float timer;
    private Object user;
    private boolean userOut;
    /** Someone other than the user stands in the chamber: no cycle starts on them. Set by the crew module each tick. */
    public boolean crowded;
    /** How many are out on this airlock's tether reel. Set by the crew module each tick. */
    public int tethered;

    private final int[] tile = new int[2];

    private Airlock(MountFootprint port, State state) {
        this.port = port;
        this.state = state;
    }

    /**
     * The airlock of docking port {@code port} on {@code grid}: the one it has, or a new one in the
     * state its doors are in (a cycle a load caught part way through finishes as a cycle in). Null
     * if {@code port} is not a docking port.
     */
    public static Airlock of(ShipGridComponent grid, MountFootprint port) {
        if (port == null || port.kind != MountFootprint.MountKind.PORT) return null;
        if (port.airlock == null) port.airlock = new Airlock(port, read(grid, port));
        return port.airlock;
    }

    private static State read(ShipGridComponent grid, MountFootprint port) {
        boolean hatchShut = false, doorOpen = false;
        for (int y = port.originY; y < port.originY + port.height(); y++) {
            for (int x = port.originX; x < port.originX + port.width(); x++) {
                EdgeWall h = DockingPortLayout.hatch(grid, port, x, y);
                if (h != null && h.blocks()) hatchShut = true;
                EdgeWall d = DockingPortLayout.door(grid, port, x, y);
                if (d != null && !d.blocks()) doorOpen = true;
            }
        }
        if (!hatchShut) return State.IN;
        return doorOpen ? State.OUT : State.FILLING;
    }

    public State state() {
        return state;
    }

    /** True at rest, hatch open: whoever is going out can step into the chamber. */
    public boolean isIn() {
        return state == State.IN;
    }

    /** True with the outer door open: whoever is in the chamber can step out, and whoever is outside in. */
    public boolean isOut() {
        return state == State.OUT;
    }

    /** Who the airlock is cycling for, or null. */
    public Object user() {
        return user;
    }

    /**
     * Asks for the airlock {@code out} (outer door open) or in (hatch open) for {@code user}. True if
     * it is theirs to use (no one else holds it); it then goes on that way when it can.
     */
    public boolean claim(Object user, boolean out) {
        if (this.user != null && this.user != user) return false;
        this.user = user;
        userOut = out;
        return true;
    }

    /** Lets the airlock go, if {@code user} holds it. */
    public void release(Object user) {
        if (this.user == user) this.user = null;
    }

    /**
     * True if crew can go out through {@code port} on {@code grid} now or after a cycle: not latched to
     * another craft, its outer doors and inner hatch all there and none blown away.
     */
    public static boolean works(ShipGridComponent grid, MountFootprint port) {
        if (port == null || port.kind != MountFootprint.MountKind.PORT || docked(grid, port)) return false;
        boolean any = false;
        for (int y = port.originY; y < port.originY + port.height(); y++) {
            for (int x = port.originX; x < port.originX + port.width(); x++) {
                if (DockingPortLayout.isDoorTile(port, x, y)) {
                    EdgeWall d = DockingPortLayout.door(grid, port, x, y);
                    if (d == null || d.isDestroyed()) return false;
                    any = true;
                }
                if (DockingPortLayout.isHatchTile(port, x, y)) {
                    EdgeWall h = DockingPortLayout.hatch(grid, port, x, y);
                    if (h == null || h.isDestroyed()) return false;
                }
            }
        }
        return any;
    }

    /** True if {@code port} is latched to another craft: a link runs from one of its tiles. */
    public static boolean docked(ShipGridComponent grid, MountFootprint port) {
        for (int i = 0; i < grid.links.size; i++) {
            GridLink l = grid.links.get(i);
            if (port.covers(l.x, l.y)) return true;
        }
        return false;
    }

    /** The chamber's pressure, a share of standard; 0 with no chamber (the opening holds no air). */
    public float chamberPressure(ShipGridComponent grid) {
        Compartment c = chamber(grid);
        return c == null ? 0f : c.pressureRatio();
    }

    /** The compartment the chamber is: the opening, between the shut doors. */
    public Compartment chamber(ShipGridComponent grid) {
        DockingPortLayout.chamberTile(port, tile);
        return grid.compartmentAt(tile[0], tile[1]);
    }

    /** The compartment beyond the hatch. */
    public Compartment cabin(ShipGridComponent grid) {
        DockingPortLayout.hatchTile(port, tile);
        Side in = DockingPortLayout.facing(port).opposite();
        return grid.compartmentAt(tile[0] + in.dx, tile[1] + in.dy);
    }

    /** True if the port has power to run its pump: a port system powered and working, or none fitted to need any. */
    public boolean powered(ShipGridComponent grid) {
        Entity e = port.installed != null ? port.installed : grid.subsystemAt(port.originX, port.originY);
        SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
        return s == null || s.powered && !s.isDisabled();
    }

    /** Works the doors, pump and valve for {@code dt} seconds. */
    public void update(ShipGridComponent grid, float dt) {
        if (docked(grid, port)) {
            // The docking module has the outer door; the hatch stands open
            if (state != State.IN) setHatch(grid, false, false);
            setDoorLock(grid, false);
            state = State.IN;
            timer = 0f;
            user = null;
            return;
        }
        boolean out = user != null ? userOut : tethered > 0;
        // No cycle on anyone in the chamber but the user, whom it is for
        boolean clear = !crowded;
        timer += dt;
        switch (state) {
            case IN:
                if (out && clear) begin(grid, State.SEALING_HATCH);
                break;
            case SEALING_HATCH:
                if (!out) {
                    begin(grid, State.OPENING_HATCH);
                } else if (timer >= DOOR_SECONDS) {
                    begin(grid, State.PUMPING);
                }
                break;
            case PUMPING:
                if (!out) {
                    begin(grid, State.FILLING);
                    break;
                }
                if (pump(grid, dt)) begin(grid, State.OPENING_DOOR);
                break;
            case OPENING_DOOR:
                if (timer >= DOOR_SECONDS) {
                    setDoor(grid, false);
                    begin(grid, State.OUT);
                }
                break;
            case OUT:
                if (!out && clear) begin(grid, State.SEALING_DOOR);
                break;
            case SEALING_DOOR:
                if (out) {
                    begin(grid, State.OPENING_DOOR);
                } else if (timer >= DOOR_SECONDS) {
                    begin(grid, State.FILLING);
                }
                break;
            case FILLING:
                if (out) {
                    begin(grid, State.PUMPING);
                    break;
                }
                if (fill(grid, dt)) begin(grid, State.OPENING_HATCH);
                break;
            case OPENING_HATCH:
                if (timer >= DOOR_SECONDS) {
                    setHatch(grid, false, false);
                    begin(grid, State.IN);
                }
                break;
        }
        // Whatever the cycle holds shut stays locked shut
        setDoorLock(grid, state != State.IN && state != State.OUT);
        if (state != State.IN) lockHatch(grid);
    }

    private void begin(ShipGridComponent grid, State next) {
        state = next;
        timer = 0f;
        switch (next) {
            case SEALING_HATCH:
                setHatch(grid, true, true);
                break;
            case SEALING_DOOR:
                setDoor(grid, true);
                break;
            default:
                break;
        }
    }

    /** A step of the pump; true once the chamber is down (or can't be brought down). */
    private boolean pump(ShipGridComponent grid, float dt) {
        if (!powered(grid)) return true; // no pump: the door opens on the air, which vents
        Compartment chamber = chamber(grid), cabin = cabin(grid);
        if (chamber == cabin) {
            // The rooms not divided yet after the hatch shut
            CompartmentBuilder.rebuildIfChanged(grid);
            chamber = chamber(grid);
            cabin = cabin(grid);
        }
        if (chamber == null) return true;
        if (cabin != null && cabin != chamber) move(chamber, cabin, 1.0 - Math.exp(-dt / PUMP_TAU));
        return chamber.pressureRatio() <= PUMPED || timer >= PUMP_LIMIT || cabin == null || cabin == chamber;
    }

    /** A step of filling the chamber from the cabin; true once it is full (or can't be filled). */
    private boolean fill(ShipGridComponent grid, float dt) {
        Compartment chamber = chamber(grid), cabin = cabin(grid);
        if (chamber == cabin) {
            CompartmentBuilder.rebuildIfChanged(grid);
            chamber = chamber(grid);
            cabin = cabin(grid);
        }
        if (chamber == null || cabin == null || chamber == cabin) return true;
        // Toward the two sharing their air by volume
        double total = chamber.totalMoles() + cabin.totalMoles();
        double want = total * chamber.volume / Math.max(1e-6, chamber.volume + cabin.volume);
        double give = (want - chamber.totalMoles()) * (1.0 - Math.exp(-dt / FILL_TAU));
        if (give > 0 && cabin.totalMoles() > 0) move(cabin, chamber, Math.min(1.0, give / cabin.totalMoles()));
        // Never opened onto a chamber holed to space: a door shot away leaves the hatch holding the cabin's air
        return chamber.pressure() >= FILLED * cabin.pressure() || timer >= FILL_LIMIT && !ventsToSpace(grid, chamber);
    }

    /** True if {@code room} has a hole to space, not just a leak. */
    static boolean ventsToSpace(ShipGridComponent grid, Compartment room) {
        for (int i = 0; i < grid.airflow.size; i++) {
            AirflowEdge e = grid.airflow.get(i);
            if (e.room == room && e.other == null && e.width > Tile.LEAK_CAP) return true;
        }
        return false;
    }

    /** Moves share {@code f} of {@code from}'s gases and smoke into {@code to}. */
    private static void move(Compartment from, Compartment to, double f) {
        double o2 = from.oxygen * f, n2 = from.nitrogen * f, co2 = from.carbonDioxide * f, smoke = from.smoke * f;
        from.oxygen -= o2;
        from.nitrogen -= n2;
        from.carbonDioxide -= co2;
        from.smoke -= smoke;
        to.oxygen += o2;
        to.nitrogen += n2;
        to.carbonDioxide += co2;
        to.smoke += smoke;
    }

    private void setHatch(ShipGridComponent grid, boolean shut, boolean lock) {
        for (int y = port.originY; y < port.originY + port.height(); y++) {
            for (int x = port.originX; x < port.originX + port.width(); x++) {
                EdgeWall h = DockingPortLayout.hatch(grid, port, x, y);
                if (h == null || h.isDestroyed()) continue;
                h.bulkheadSealed = shut;
                h.locked = lock;
            }
        }
    }

    private void lockHatch(ShipGridComponent grid) {
        for (int y = port.originY; y < port.originY + port.height(); y++) {
            for (int x = port.originX; x < port.originX + port.width(); x++) {
                EdgeWall h = DockingPortLayout.hatch(grid, port, x, y);
                if (h != null && h.bulkheadSealed) h.locked = true;
            }
        }
    }

    private void setDoor(ShipGridComponent grid, boolean shut) {
        for (int y = port.originY; y < port.originY + port.height(); y++) {
            for (int x = port.originX; x < port.originX + port.width(); x++) {
                EdgeWall d = DockingPortLayout.door(grid, port, x, y);
                if (d == null || d.isDestroyed()) continue;
                d.bulkheadSealed = shut;
            }
        }
    }

    private void setDoorLock(ShipGridComponent grid, boolean lock) {
        for (int y = port.originY; y < port.originY + port.height(); y++) {
            for (int x = port.originX; x < port.originX + port.width(); x++) {
                EdgeWall d = DockingPortLayout.door(grid, port, x, y);
                if (d != null) d.locked = lock;
            }
        }
    }
}
