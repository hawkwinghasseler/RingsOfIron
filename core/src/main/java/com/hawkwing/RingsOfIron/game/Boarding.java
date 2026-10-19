package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.BoardTask;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.stationsdocking.ForceMethod;
import com.ringsofiron.stationsdocking.StationComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.IntPredicate;

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
 * <p>Boarding a ship of a faction costs standing with it ({@link #STANDING_COST}, once per
 * craft), on top of any door forced; boarding an outlaw's ship ({@link #outlaw}) or a craft of
 * no one's costs nothing. Undocking before it is taken calls the boarding off.
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
        crew.doorRights = this::mayPass;
    }

    @Override
    public void addedToEngine(Engine engine) {
        boarded = engine.getEntitiesFor(BOARDED);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        boarded = null;
    }

    // ------------------------------------------------------------------
    // What there is to board
    // ------------------------------------------------------------------

    /** The ship (not a station) {@code ship} is latched to, or null. */
    public Entity target(Entity ship) {
        Entity other = ship == null ? null : docking.dockedTo(ship);
        return other == null || other.getComponent(StationComponent.class) != null ? null : other;
    }

    /** Why {@code ship}'s crew can't board {@code target}, or null if they can. */
    public String refusal(Entity ship, Entity target) {
        if (target == null) return "Latch onto another ship first";
        if (target.getComponent(StationComponent.class) != null) return "Stations aren't boarded";
        BoardedComponent b = BOARDED_M.get(target);
        if (b != null && b.taken) return name(target) + " is taken";
        if (!ShipDisablement.isDisabled(target)) return name(target) + " can still fight: disable it first";
        if (party(ship).size == 0) return "No one aboard to send";
        return null;
    }

    /**
     * Orders {@code ship}'s boarding party onto the ship it is latched to, forcing the door between
     * first if it is shut. Returns what was ordered, or why nothing was.
     */
    public String order(Entity ship) {
        Entity target = target(ship);
        String no = refusal(ship, target);
        if (no != null) return no;
        boolean begun = begin(ship, target);
        String door = "";
        if (doorShut(ship, target)) {
            String forced = forcing.order(ship, false);
            door = forced.startsWith("Crew ordered") ? "; the door is being forced first" : "";
        }
        return (begun ? "Boarding party ordered aboard " : "The boarding party goes on aboard ") + name(target) + door;
    }

    /** Calls a boarding off: the party comes home. Returns what was done. */
    public String recall(Entity ship) {
        Entity target = target(ship);
        BoardedComponent b = target == null ? null : BOARDED_M.get(target);
        if (b == null || b.by.get(uuids) != ship || b.taken) return "No boarding under way";
        end(target, b);
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

    /** The boarding of {@code target}, or null. */
    public static BoardedComponent of(Entity target) {
        return target == null ? null : BOARDED_M.get(target);
    }

    /** Lets a boarding party through the boarded craft's doors. */
    private boolean mayPass(Entity member, Entity ship) {
        BoardedComponent b = BOARDED_M.get(ship);
        return b != null && b.by != null && b.by.get(uuids) == crew.homeOf(member);
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
            if (ship == null || !docking.docked(ship, target)) {
                end(target, b);
                notices.add("Undocked: the boarding of " + name(target) + " is called off");
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

    /** Keeps the party's orders posted: one room to sweep for each boarder without one. */
    private void sweep(Entity ship, Entity target, BoardedComponent b) {
        for (int i = b.orders.size - 1; i >= 0; i--) {
            if (!b.orders.get(i).isActive()) b.orders.removeIndex(i);
        }
        if (b.sweep == null) b.sweep = sweepSpots(ship, target);
        if (b.sweep.length == 0) return;
        ShipTaskBoardComponent board = crew.board(ship);
        if (board == null) return;
        Array<Entity> party = party(ship);
        // Boarders call their own ship home, so they come back to it
        for (Entity e : party) {
            CrewMemberComponent c = crew.crew(e);
            if (c.home == null) c.setHome(ship);
        }
        int want = party.size;
        int spots = b.sweep.length / 2;
        for (int tries = 0; b.orders.size < want && tries < spots; tries++) {
            int k = b.next++ % spots;
            BoardTask t = new BoardTask(target, b.sweep[2 * k], b.sweep[2 * k + 1], new Sweep(target));
            if (board.post(t)) b.orders.add(t);
        }
    }

    /** The craft is taken: its crew are held, the party stands down, and it stays surrendered. */
    private void take(Entity target, BoardedComponent b) {
        b.taken = true;
        cancelOrders(b);
        b.prisoners = keepHeld(target);
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

    /** A boarder's order to sweep one room. */
    private final class Sweep implements BoardTask.Job {
        final Entity target;

        Sweep(Entity target) {
            this.target = target;
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
