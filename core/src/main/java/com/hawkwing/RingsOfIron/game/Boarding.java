package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.ashley.signals.Signal;
import com.ringsofiron.aicrew.BoardTask;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewDoors;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.interiorgrid.Airlock;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SuitLockers;
import com.ringsofiron.interiorgrid.crew.Suit;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.stationsdocking.ForceMethod;
import com.ringsofiron.stationsdocking.StationComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Boarding a beaten ship (GDD section 3): latched onto a disabled or surrendered craft, the
 * player orders a boarding party across ({@link #order}). If the port door between is shut the
 * crew force it first ({@link DoorForcing}); then the party (the ship's marines, or its crewmen if
 * it has none) goes aboard suited and sweeps it room by room ({@link BoardTask}), fighting whoever
 * stands. Boarders may open the boarded craft's doors.
 *
 * <p>The craft is taken once boarders stand aboard and none of its own crew still fight: all of
 * them down, or a surrendered crew yielding as soon as the first boarder steps aboard. Its crew
 * are then held ({@link CrewMemberComponent#held}), the party goes home, and the craft stays
 * surrendered (a wreck stays a wreck), the boarders' to salvage. A disabled craft with no one
 * aboard to fight for it is taken as soon as the salvage begins ({@link #takeUndefended}).
 *
 * <p>With no dock between, a ship standing off within {@link EvaSystem#CROSS_RANGE} and drifting
 * less than {@link EvaSystem#CROSS_DRIFT} ({@link #evaTarget}) is boarded on EVA: as many of the
 * party as there are EVA suits aboard go over on their jetpacks and in by one of its airlocks, whose
 * door they force from outside ({@link Sweep#breach}): hacked or pried as its power allows, or cut
 * when storming. Prying or cutting it with the inner hatch open vents the cabin behind it.
 *
 * <p>Latched on or standing off, the player can also storm a craft that can still fight ({@link
 * #storm}): the boarders force their way through every shut door aboard, the dock's door first,
 * prying where the craft has no power and cutting where it has ({@link #forcing}), and its crew fight
 * them whatever the player's standing with them, until it is taken.
 *
 * <p>Boarding a ship of a faction costs standing with it ({@link #STANDING_COST}, once per
 * craft), on top of any door forced; boarding an outlaw's ship ({@link #outlaw}) or a craft of
 * no one's costs nothing. Undocking (or drifting out of reach of a boarding on EVA) before it is
 * taken calls the boarding off.
 */
public class Boarding extends EntitySystem {
    /** Standing a boarding costs with the boarded craft's faction. */
    public static final float STANDING_COST = 20f;
    /** Most rooms swept, nearest the dock first. */
    public static final int MAX_SWEEP = 8;

    private static final Family BOARDED = Family.all(BoardedComponent.class).get();
    private static final ComponentMapper<BoardedComponent> BOARDED_M = ComponentMapper.getFor(BoardedComponent.class);

    private final DockingSystem docking;
    private final CrewService crew;
    private final DoorForcing forcing;
    private final FactionStanding standing;
    private final ShipDisablement disablement;
    private final UuidRegistry uuids;
    private final DoubleSupplier simSeconds;
    private ImmutableArray<Entity> boarded;
    /** Factions whose ships are anyone's to board (outlaws); by default none. */
    public IntPredicate outlaw = faction -> false;
    /** The craft the player has picked out (the navigation target), boarded on EVA before any other in reach; by default none. */
    public Supplier<Entity> preferred = () -> null;
    /** Seconds of cutting or prying a door aboard, while storming. */
    static final CrewDoors.Forcing CUT_DOOR = new CrewDoors.Forcing(ForceMethod.CUT.seconds, "cutting through a door"),
            PRY_DOOR = new CrewDoors.Forcing(ForceMethod.PRY.seconds, "prying a door open");
    private static final Family CRAFT = Family.all(ShipGridComponent.class, PhysicsBodyComponent.class).get();
    private ImmutableArray<Entity> craft;
    private final float[] sep = new float[2];
    /** Each craft as it is taken. */
    public final Signal<Entity> taken = new Signal<>();
    /** What the boardings came to, for the screen; drained by the caller. */
    public final List<String> notices = new ArrayList<>();

    public Boarding(DockingSystem docking, CrewService crew, DoorForcing forcing, FactionStanding standing,
                    ShipDisablement disablement, UuidRegistry uuids, DoubleSupplier simSeconds) {
        this.docking = docking;
        this.crew = crew;
        this.forcing = forcing;
        this.standing = standing;
        this.disablement = disablement;
        this.uuids = uuids;
        this.simSeconds = simSeconds;
        crew.doorRights = new CrewService.DoorRights() {
            @Override
            public boolean mayPass(Entity member, Entity ship) {
                return Boarding.this.mayPass(member, ship);
            }

            @Override
            public CrewDoors.Forcing forcing(Entity member, Entity ship, EdgeWall door) {
                return Boarding.this.forcing(member, ship, door);
            }

            @Override
            public void forced(Entity member, Entity ship, EdgeWall door, CrewDoors.Forcing how) {
                if (how == CUT_DOOR && !door.isDestroyed()) door.integrity = Math.min(door.integrity, DockingSystem.CUT_DOOR_INTEGRITY);
            }
        };
    }

    @Override
    public void addedToEngine(Engine engine) {
        boarded = engine.getEntitiesFor(BOARDED);
        craft = engine.getEntitiesFor(CRAFT);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        boarded = null;
        craft = null;
    }

    // ------------------------------------------------------------------
    // What there is to board
    // ------------------------------------------------------------------

    /** The ship (not a station) {@code ship} is latched to, or null. */
    public Entity target(Entity ship) {
        Entity other = ship == null ? null : docking.dockedTo(ship);
        return other == null || other.getComponent(StationComponent.class) != null ? null : other;
    }

    /**
     * The ship (not a station) {@code ship} could board on EVA, with no dock between: crewed or not,
     * standing off within {@link EvaSystem#CROSS_RANGE} of its hull and drifting less than {@link
     * EvaSystem#CROSS_DRIFT} from it; the {@link #preferred} one if it is such a ship, else the
     * nearest. Null while latched to anything, or with none in reach.
     */
    public Entity evaTarget(Entity ship) {
        if (ship == null || craft == null || docking.dockedTo(ship) != null || crew.eva == null) return null;
        Entity pick = preferred.get();
        if (pick != null && inReach(ship, pick)) return pick;
        Entity best = null;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < craft.size(); i++) {
            Entity e = craft.get(i);
            if (!inReach(ship, e) || sep[0] >= bestD) continue;
            bestD = sep[0];
            best = e;
        }
        return best;
    }

    /** True if {@code other} is a ship {@code ship}'s crew could cross to on EVA; leaves the separation in {@link #sep}. */
    private boolean inReach(Entity ship, Entity other) {
        if (other == ship || other.getComponent(StationComponent.class) != null || other.getComponent(ShipGridComponent.class) == null
                || !crew.eva.separation(ship, other, sep)) return false;
        return sep[0] <= reach(ship, other) && Math.abs(sep[1]) <= EvaSystem.CROSS_DRIFT;
    }

    /** How far apart the middles of {@code a} and {@code b} may be for a crossing, meters. */
    private static float reach(Entity a, Entity b) {
        ShipGridComponent ga = a.getComponent(ShipGridComponent.class), gb = b.getComponent(ShipGridComponent.class);
        return EvaSystem.CROSS_RANGE + 0.5f * (Math.max(ga.width, ga.height) * ga.tileSize + Math.max(gb.width, gb.height) * gb.tileSize);
    }

    /** The ship {@code ship}'s crew would board: the one it is latched to, else the one on EVA ({@link #evaTarget}). */
    public Entity boardable(Entity ship) {
        Entity docked = target(ship);
        if (docked != null) return docked;
        Entity under = boardedBy(ship);
        return under != null ? under : evaTarget(ship);
    }

    /** Why {@code ship}'s crew can't board {@code target}, or null if they can. */
    public String refusal(Entity ship, Entity target) {
        return refusal(ship, target, false);
    }

    /** Why {@code ship}'s crew can't board {@code target}, storming it if {@code force}; null if they can. */
    public String refusal(Entity ship, Entity target, boolean force) {
        if (target == null) return "Latch onto another ship first, or stand off within " + (int) EvaSystem.CROSS_RANGE + " m of one";
        if (target.getComponent(StationComponent.class) != null) return "Stations aren't boarded";
        BoardedComponent b = BOARDED_M.get(target);
        if (b != null && b.taken) return name(target) + " is taken";
        if (!force && !ShipDisablement.isDisabled(target)) return name(target) + " can still fight: disable it first, or storm it";
        if (party(ship).size == 0) return "No one aboard to send";
        if (!docking.docked(ship, target) && evaSuits(ship) == 0) return "No EVA suits aboard to go across in";
        return null;
    }

    /**
     * Orders {@code ship}'s boarding party onto the ship it is latched to, forcing the door between
     * first if it is shut; or with no dock, across on EVA to the one in reach. Returns what was
     * ordered, or why nothing was.
     */
    public String order(Entity ship) {
        return order(ship, false);
    }

    /**
     * Orders {@code ship}'s boarding party to storm the ship it is latched to, or the one in reach on
     * EVA, beaten or not: they force their way through every shut door on the way. Returns what was
     * ordered, or why nothing was.
     */
    public String storm(Entity ship) {
        return order(ship, true);
    }

    private String order(Entity ship, boolean force) {
        Entity target = boardable(ship);
        String no = refusal(ship, target, force);
        if (no != null) return no;
        boolean begun = begin(ship, target);
        BoardedComponent b = BOARDED_M.get(target);
        b.force |= force;
        b.byEva = !docking.docked(ship, target);
        String door = "";
        if (b.byEva) {
            door = "; " + Math.min(party(ship).size, evaSuits(ship)) + " going across on EVA";
        } else if (doorShut(ship, target)) {
            String forced = forcing.order(ship, force && doorLocked(ship, target));
            door = forced.startsWith("Crew ordered") ? "; the door is being forced first" : "";
        }
        String what = force ? "Boarders ordered to storm " : begun ? "Boarding party ordered aboard " : "The boarding party goes on aboard ";
        return what + name(target) + door;
    }

    /** Calls a boarding off: the party comes home. Returns what was done. */
    public String recall(Entity ship) {
        Entity target = boardedBy(ship);
        BoardedComponent b = target == null ? null : BOARDED_M.get(target);
        if (b == null || b.by.get(uuids) != ship || b.taken) return "No boarding under way";
        end(target, b);
        bringDownedHome(target, ship);
        return "Boarding party recalled";
    }

    /**
     * Takes {@code target} for {@code ship} at once if it is disabled, latched to it and no one
     * aboard fights for it (a wreck with its crew dead, say). Returns true if it is now taken by
     * {@code ship}.
     */
    public boolean takeUndefended(Entity ship, Entity target) {
        if (!canTakeUndefended(ship, target)) return false;
        if (takenBy(target, ship)) return true;
        begin(ship, target);
        take(target, BOARDED_M.get(target));
        return true;
    }

    /**
     * True if {@code ship} has taken {@code target}, or {@link #takeUndefended} would take it; a
     * check only, which changes nothing.
     */
    public boolean canTakeUndefended(Entity ship, Entity target) {
        if (target == null || target(ship) != target) return false;
        BoardedComponent b = BOARDED_M.get(target);
        if (b != null && b.taken) return b.by.get(uuids) == ship;
        return ShipDisablement.isDisabled(target) && defenders(target) == 0;
    }

    /** True if {@code target} has been taken by {@code ship}. */
    public boolean takenBy(Entity target, Entity ship) {
        BoardedComponent b = target == null ? null : BOARDED_M.get(target);
        return b != null && b.taken && b.by.get(uuids) == ship;
    }

    /** The craft {@code ship}'s crew are boarding and have not yet taken, or null. */
    public Entity boardedBy(Entity ship) {
        if (boarded == null || ship == null) return null;
        for (int i = 0; i < boarded.size(); i++) {
            BoardedComponent b = BOARDED_M.get(boarded.get(i));
            if (!b.taken && b.by != null && b.by.get(uuids) == ship) return boarded.get(i);
        }
        return null;
    }

    /** The boarding of {@code target}, or null. */
    public static BoardedComponent of(Entity target) {
        return target == null ? null : BOARDED_M.get(target);
    }

    /** Lets a boarding party through the boarded craft's doors, unless it is storming it (then they force them). */
    private boolean mayPass(Entity member, Entity ship) {
        BoardedComponent b = BOARDED_M.get(ship);
        return b != null && !b.force && b.by != null && b.by.get(uuids) == crew.homeOf(member);
    }

    /**
     * How a boarding party forces a door of the craft it boards that it doesn't open: every shut door
     * while storming it, and an airlock's held shut for its cycle whichever way they board. Pried
     * while the craft has no reactor running, cut while it has. Null for anyone else.
     */
    CrewDoors.Forcing forcing(Entity member, Entity ship, EdgeWall door) {
        BoardedComponent b = BOARDED_M.get(ship);
        if (b == null || b.taken || b.by == null || b.by.get(uuids) != crew.homeOf(member)) return null;
        if (!b.force && (door == null || !door.locked)) return null;
        return ShipDisablement.reactorRunning(ship.getComponent(ShipSubsystemRegistryComponent.class)) ? CUT_DOOR : PRY_DOOR;
    }

    /**
     * True while {@code ship} is boarding a craft of faction {@code b}'s by storm, and is crewed by
     * faction {@code a} (or the other way round): those two crews fight while it lasts, whatever
     * they think of each other otherwise.
     */
    public boolean storming(int a, int b) {
        if (boarded == null || a == b) return false;
        for (int i = 0; i < boarded.size(); i++) {
            Entity target = boarded.get(i);
            BoardedComponent bc = BOARDED_M.get(target);
            if (!bc.force || bc.taken) continue;
            Entity by = bc.by == null ? null : bc.by.get(uuids);
            ShipTaskBoardComponent ours = by == null ? null : crew.board(by), theirs = crew.board(target);
            if (ours == null || theirs == null) continue;
            if (ours.faction == a && theirs.faction == b || ours.faction == b && theirs.faction == a) return true;
        }
        return false;
    }

    /**
     * EVA suits aboard {@code ship} fit to go out in, worn or hanging in its lockers: with half their
     * air and half their jetpack or more ({@link com.ringsofiron.aicrew.btree.EvaTasks#fitToGoOut}).
     */
    public int evaSuits(Entity ship) {
        ShipGridComponent grid = crew.grid(ship);
        if (grid == null) return 0;
        int n = 0;
        for (Furnishing f : SuitLockers.all(grid)) {
            for (int i = 0; i < f.suits.size; i++) {
                Suit s = f.suits.get(i);
                if (com.ringsofiron.aicrew.btree.EvaTasks.fitToGoOut(s)) n++;
            }
        }
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            CrewMemberComponent c = crew.crew(all.get(i));
            if (crew.homeOf(all.get(i)) == ship && com.ringsofiron.aicrew.btree.EvaTasks.fitToGoOut(c)) n++;
        }
        return n;
    }

    /** Starts boarding {@code target}, charging standing the first time; false if it was already under way. */
    private boolean begin(Entity ship, Entity target) {
        BoardedComponent b = BOARDED_M.get(target);
        if (b != null && b.by.get(uuids) == ship) return false;
        if (b != null) target.remove(BoardedComponent.class);
        target.add(new BoardedComponent(EntityRef.of(ship), simSeconds.getAsDouble()));
        int faction = standing.factionOf(target);
        if (ship.getComponent(com.ringsofiron.coreengine.components.PlayerControllerComponent.class) != null
                && faction >= 0 && faction != FactionStanding.PLAYER && !outlaw.test(faction)) {
            standing.change(faction, -STANDING_COST);
            notices.add(String.format(Locale.ROOT, "Boarding %s costs standing with its owners (%.0f)",
                    name(target), standing.standing(faction)));
        }
        return true;
    }

    private void end(Entity target, BoardedComponent b) {
        cancelOrders(b);
        target.remove(BoardedComponent.class);
    }

    // ------------------------------------------------------------------
    // Each tick
    // ------------------------------------------------------------------

    @Override
    public void update(float deltaTime) {
        if (boarded == null) return;
        for (int i = boarded.size() - 1; i >= 0; i--) {
            Entity target = boarded.get(i);
            BoardedComponent b = BOARDED_M.get(target);
            if (b.taken) {
                keepHeld(target);
                continue;
            }
            Entity ship = b.by == null ? null : b.by.get(uuids);
            boolean docked = ship != null && docking.docked(ship, target);
            if (docked) b.byEva = false;
            // Over on EVA, the boarding holds while the two craft stay near enough to cross back
            boolean near = ship != null && b.byEva && crew.eva != null && crew.eva.separation(ship, target, sep)
                    && sep[0] <= EvaSystem.CROSS_REACH;
            if (!docked && !near) {
                end(target, b);
                notices.add((b.byEva ? "Out of reach" : "Undocked") + ": the boarding of " + name(target) + " is called off");
                continue;
            }
            int aboard = boardersAboard(ship, target);
            if (aboard > 0 && (defenders(target) == 0 || ShipDisablement.reason(target) == DisabledComponent.Reason.SURRENDERED)) {
                take(target, b);
                continue;
            }
            sweep(ship, target, b);
        }
    }

    /**
     * Keeps the party's orders posted: one room to sweep for each boarder without one. Each order is
     * the boarder's own, so only the party goes (not the crewman at the helm), and all of it at once
     * however few rooms the craft has.
     */
    private void sweep(Entity ship, Entity target, BoardedComponent b) {
        for (int i = b.orders.size - 1; i >= 0; i--) {
            if (!b.orders.get(i).isActive()) b.orders.removeIndex(i);
        }
        if (b.sweep == null) b.sweep = sweepSpots(ship, target);
        if (b.sweep.length == 0) return;
        ShipTaskBoardComponent board = crew.board(ship);
        if (board == null) return;
        Array<Entity> party = party(ship);
        // Across on EVA only as many go as there are EVA suits; those already over stay in it
        if (b.byEva) {
            int suits = evaSuits(ship), going = 0;
            for (int i = 0; i < party.size; i++) {
                Entity e = party.get(i);
                boolean over = crew.position(e).ship == target || EvaSystem.of(e) != null;
                if (over || going < suits) going++;
                else party.removeIndex(i--);
            }
        }
        int spots = b.sweep.length / 2;
        for (Entity e : party) {
            // Boarders call their own ship home, so they come back to it
            CrewMemberComponent c = crew.crew(e);
            if (c.home == null) c.setHome(ship);
            if (hasOrder(b, e)) continue;
            int k = b.next++ % spots;
            BoardTask t = new BoardTask(target, b.sweep[2 * k], b.sweep[2 * k + 1], new Sweep(ship, target), e);
            if (board.post(t)) b.orders.add(t);
        }
    }

    private static boolean hasOrder(BoardedComponent b, Entity member) {
        for (BoardTask t : b.orders) if (t.member == member) return true;
        return false;
    }

    /** The craft is taken: its crew are held, the party stands down, and it stays surrendered. */
    private void take(Entity target, BoardedComponent b) {
        b.taken = true;
        // Before the party stands down, so listeners can tell who was in it
        taken.dispatch(target);
        cancelOrders(b);
        b.prisoners = keepHeld(target);
        Entity by = b.by == null ? null : b.by.get(uuids);
        if (by != null) bringDownedHome(target, by);
        DisabledComponent.Reason r = ShipDisablement.reason(target);
        if (r != DisabledComponent.Reason.WRECKED && r != DisabledComponent.Reason.ABANDONED) {
            disablement.set(target, DisabledComponent.Reason.SURRENDERED);
        }
        notices.add(name(target) + " is taken" + (b.prisoners > 0 ? ": " + b.prisoners + " held" : ""));
    }

    /** Holds every crew member of the taken craft's own aboard it; returns how many are held. */
    private int keepHeld(Entity target) {
        int faction = ownFaction(target), n = 0;
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            CrewMemberComponent c = crew.crew(e);
            if (crew.position(e).ship != target || c.faction != faction || !c.isAlive()) continue;
            if (!c.held) {
                c.held = true;
                crew.releaseTask(e);
            }
            n++;
        }
        return n;
    }

    /**
     * As the party stands down, its members lying wounded or knocked out aboard {@code target} are
     * carried back aboard {@code home} by their shipmates (moved, as prisoners are), so nobody is
     * left behind on the craft: their own ship's crew treat them there.
     */
    private void bringDownedHome(Entity target, Entity home) {
        ShipGridComponent grid = home.getComponent(ShipGridComponent.class);
        List<int[]> floor = grid == null ? null : GameScenario.floorTiles(grid);
        if (floor == null || floor.isEmpty()) return;
        ImmutableArray<Entity> all = crew.crewEntities();
        int n = 0;
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            CrewMemberComponent c = crew.crew(e);
            GridPositionComponent p = crew.position(e);
            if (p.ship != target || crew.homeOf(e) != home || !c.isAlive() || c.isConscious() || c.held) continue;
            crew.releaseTask(e);
            if (c.carriedBy != null) crew.drop(c.carriedBy);
            p.stop();
            p.crossing = null;
            p.setShip(home);
            int[] t = floor.get(n++ % floor.size());
            p.setTile(t[0], t[1]);
            notices.add(c.name + " is carried back aboard");
        }
    }

    private void cancelOrders(BoardedComponent b) {
        for (BoardTask t : b.orders) {
            if (t.status == CrewTask.Status.OPEN) t.status = CrewTask.Status.CANCELLED;
        }
        b.orders.clear();
    }

    // ------------------------------------------------------------------
    // Who is where
    // ------------------------------------------------------------------

    /**
     * {@code ship}'s crew who would go: its marines, or its crewmen if it has no marines; conscious,
     * of the ship's own, not the player's avatar.
     */
    public Array<Entity> party(Entity ship) {
        Array<Entity> marines = new Array<>(), others = new Array<>();
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            CrewMemberComponent c = crew.crew(e);
            if (crew.homeOf(e) != ship || !c.isConscious() || c.held || e.getComponent(PlayerAvatarComponent.class) != null) continue;
            if (c.role == CrewRole.MARINE) marines.add(e);
            else if (c.role.affinity(CrewTask.Kind.BOARD) > 0f) others.add(e);
        }
        return marines.size > 0 ? marines : others;
    }

    /** {@code ship}'s own crew standing aboard {@code target}, conscious. */
    public int boardersAboard(Entity ship, Entity target) {
        int n = 0;
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            GridPositionComponent p = crew.position(e);
            if (p.ship == target && crew.homeOf(e) == ship && crew.crew(e).isConscious()) n++;
        }
        return n;
    }

    /** {@code target}'s own crew aboard it still able to fight: conscious and not held. */
    public int defenders(Entity target) {
        int faction = ownFaction(target), n = 0;
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            CrewMemberComponent c = crew.crew(e);
            if (crew.position(e).ship == target && c.faction == faction && c.isConscious() && !c.held) n++;
        }
        return n;
    }

    private int ownFaction(Entity target) {
        ShipTaskBoardComponent board = target.getComponent(ShipTaskBoardComponent.class);
        return board == null ? 0 : board.faction;
    }

    /** True if a shut, powered port door stands between {@code ship} and {@code target}. */
    private boolean doorLocked(Entity ship, Entity target) {
        ShipGridComponent grid = crew.grid(ship);
        if (grid == null) return false;
        for (GridLink l : grid.links) {
            if (l.otherShip != target) continue;
            int port = DoorForcing.portAt(target, l.ox, l.oy);
            if (port >= 0 && docking.doorState(target, port) == ForceMethod.DoorState.LOCKED) return true;
        }
        return false;
    }

    /** True if a shut port door stands between {@code ship} and {@code target}. */
    private boolean doorShut(Entity ship, Entity target) {
        ShipGridComponent grid = crew.grid(ship);
        if (grid == null) return false;
        for (GridLink l : grid.links) {
            if (l.otherShip != target) continue;
            int port = DoorForcing.portAt(target, l.ox, l.oy);
            if (port < 0) continue;
            ForceMethod.DoorState s = docking.doorState(target, port);
            if (s == ForceMethod.DoorState.LOCKED || s == ForceMethod.DoorState.UNPOWERED) return true;
        }
        return false;
    }

    /**
     * One spot in each room of {@code target}, the walkable tile nearest the room's middle, nearest
     * the dock first; at most {@link #MAX_SWEEP}. The tile across the dock if it has no rooms.
     */
    int[] sweepSpots(Entity ship, Entity target) {
        ShipGridComponent grid = crew.grid(target);
        if (grid == null) return new int[0];
        int dx = -1, dy = -1;
        ShipGridComponent own = crew.grid(ship);
        if (own != null) {
            for (GridLink l : own.links) {
                if (l.otherShip == target) {
                    dx = l.ox;
                    dy = l.oy;
                    break;
                }
            }
        }
        com.ringsofiron.aicrew.nav.GridNavGraph nav = crew.pathfinder(grid).graph;
        List<int[]> spots = new ArrayList<>();
        for (Compartment c : grid.compartments) {
            if (c.tiles.size == 0) continue;
            float cx = 0, cy = 0;
            for (int i = 0; i < c.tiles.size; i++) {
                cx += c.tiles.get(i) % grid.width;
                cy += c.tiles.get(i) / grid.width;
            }
            cx /= c.tiles.size;
            cy /= c.tiles.size;
            int[] best = null;
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < c.tiles.size; i++) {
                int x = c.tiles.get(i) % grid.width, y = c.tiles.get(i) / grid.width;
                if (!nav.isWalkable(x, y)) continue;
                float d = Math.abs(x - cx) + Math.abs(y - cy);
                if (d < bestD) {
                    bestD = d;
                    best = new int[]{x, y};
                }
            }
            if (best != null) spots.add(best);
        }
        if (spots.isEmpty() && dx >= 0) spots.add(new int[]{dx, dy});
        final int fx = dx, fy = dy;
        spots.sort((a, b) -> Integer.compare(Math.abs(a[0] - fx) + Math.abs(a[1] - fy), Math.abs(b[0] - fx) + Math.abs(b[1] - fy)));
        int n = Math.min(spots.size(), MAX_SWEEP);
        int[] out = new int[2 * n];
        for (int i = 0; i < n; i++) {
            out[2 * i] = spots.get(i)[0];
            out[2 * i + 1] = spots.get(i)[1];
        }
        return out;
    }

    /** What the player calls the craft, without its disabled tag. */
    public static String name(Entity craft) {
        DisabledComponent d = craft == null ? null : craft.getComponent(DisabledComponent.class);
        if (d != null && d.markerLabel != null && !d.markerLabel.isEmpty()) return d.markerLabel;
        return craft == null ? "the other ship" : PlayerDocking.name(craft);
    }

    /** A boarder's order to sweep one room, getting in by an airlock from outside if they go over on EVA. */
    private final class Sweep implements BoardTask.Job {
        final Entity ship, target;

        Sweep(Entity ship, Entity target) {
            this.ship = ship;
            this.target = target;
        }

        /**
         * How the airlock door of {@code port} is forced from outside: storming, pried or cut as
         * the port's power allows; else pried or hacked, so a powered airlock is taken over and
         * cycles the boarders in without venting the cabin.
         */
        ForceMethod method(MountFootprint port) {
            ShipGridComponent grid = crew.grid(target);
            boolean powered = grid != null && Airlock.of(grid, port).powered(grid);
            BoardedComponent b = BOARDED_M.get(target);
            if (!powered) return ForceMethod.PRY;
            return b != null && b.force ? ForceMethod.CUT : ForceMethod.HACK;
        }

        @Override
        public BoardTask.Breach breach(Entity member, MountFootprint port, float dt) {
            BoardedComponent b = BOARDED_M.get(target);
            ShipGridComponent grid = crew.grid(target);
            if (b == null || grid == null) return BoardTask.Breach.WORKING;
            if (EvaSystem.doorOpen(grid, port)) return BoardTask.Breach.OPEN;
            if (b.taken) return BoardTask.Breach.CYCLED;
            ForceMethod how = method(port);
            float done = b.breaching.get(port, 0f) + dt;
            b.breaching.put(port, done);
            if (done < how.seconds) return BoardTask.Breach.WORKING;
            b.breaching.remove(port, 0f);
            if (ship.getComponent(com.ringsofiron.coreengine.components.PlayerControllerComponent.class) != null) {
                standing.onForcedEntry(target, how);
            }
            notices.add("The airlock door of " + name(target) + (how == ForceMethod.HACK ? " is hacked" : " is forced open"));
            if (how == ForceMethod.HACK) return BoardTask.Breach.CYCLED;
            for (int y = port.originY; y < port.originY + port.height(); y++) {
                for (int x = port.originX; x < port.originX + port.width(); x++) {
                    EdgeWall d = DockingPortLayout.door(grid, port, x, y);
                    if (d == null || d.isDestroyed()) continue;
                    d.bulkheadSealed = false;
                    d.locked = false;
                    if (how.damages) d.integrity = Math.min(d.integrity, DockingSystem.CUT_DOOR_INTEGRITY);
                }
            }
            return BoardTask.Breach.OPEN;
        }

        @Override
        public String breaching(MountFootprint port) {
            return method(port).doing + " the airlock door of " + name(target);
        }

        @Override
        public boolean isOpen() {
            BoardedComponent b = BOARDED_M.get(target);
            return b != null && !b.taken;
        }

        @Override
        public void arrived(Entity member) {
            // The room is swept; the next order goes up on the next tick
        }

        @Override
        public String what() {
            return "boarding " + name(target);
        }
    }
}
