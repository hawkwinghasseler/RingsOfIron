package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.Task.Status;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.BoardTask;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.EvaComponent;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.aicrew.HullStripTask;
import com.ringsofiron.aicrew.OutsideTask;
import com.ringsofiron.aicrew.RetrieveTask;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.interiorgrid.Airlock;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.HullPlating;
import com.ringsofiron.interiorgrid.HullSurface;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.RcsThruster;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SuitLockers;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.Suit;

/**
 * Work outside the hull, in a crew member's behavior tree: the jobs only done on EVA ({@link
 * CrewTask.Kind#isEva}) through {@link EvaSystem}. An RCS block whose hull tile is wrecked, framed
 * back up; a wrecked hull tile, plated over; and whatever work outside the game posts ({@link
 * OutsideTask}): a jammed docking collar to free, a part to mount.
 *
 * <pre>
 * [OnEva]        Spacewalk                                      first under the root
 * [CrateToStow]  StowCrate                                      before the task branch
 * Sequence( IsEvaJob, FetchPart, SuitUpForEva, GoOutside )      in the task branch
 * </pre>
 *
 * A crew member takes the job only with an EVA suit to be had ({@link #suitReady}). They hang up
 * any pressure suit they have on (or an EVA suit too spent to go out in: {@link #fitToGoOut}), get
 * into an EVA suit at a locker, and walk to the docking port the
 * job is best reached from ({@link #plan}): one that can cycle (undocked, its door and hatch
 * standing), whose door opens onto the hull, and from which the hull runs round to the ring tile the
 * job is worked from ({@link #workTile}) within the tether's reach. Standing in its chamber they go
 * out ({@link EvaSystem#begin}), and from then on {@link Spacewalk} has them, the task still theirs:
 * along the hull to the job, the work there ({@link #workSeconds} at their suited pace), then on to
 * another job outside within reach, or back in. They head back in as soon as battle stations sound,
 * or once their air is down to what the way back takes and {@link #AIR_MARGIN} more.
 *
 * <p>Work that needs a part ({@link OutsideTask.Job#part}) has it fetched first: they lift its
 * crate out of the hold ({@link FetchPart}), carry it out, and set it down at the work. A crate in
 * hand aboard with no work outside to take it to (the work called off, or the part it took off)
 * goes back in the hold ({@link StowCrate}).
 *
 * <p>And across to another craft with no dock between ({@link Departure}): a boarder ordered aboard
 * a craft standing off nearby, or one going home from it. Into an EVA suit, out of the airlock
 * nearest the other craft, over the hull to the side facing it, off on the jetpack, onto its hull
 * and along it to the port they go in by ({@link #entryPort}); a boarder forces its door from outside
 * first ({@link BoardTask.Job#breach}). {@link Spacewalk} has them from the airlock on.
 */
public final class EvaTasks {
    /** Seconds of air an EVA suit needs in it to go out in: half a full pack. */
    public static final float MIN_AIR = Suit.EVA_AIR * 0.5f;
    /** Delta-v an EVA suit's jetpack needs in it to go out with, m/s: half a full pack, a way back for one thrown off. */
    public static final float MIN_JET = Suit.JET_DV * 0.5f;
    /** Seconds of air kept in hand beyond the way back in. */
    public static final float AIR_MARGIN = 300f;
    /** Seconds a crew member leaves an EVA job alone that they found no way out to. */
    public static final float NO_ROUTE = 60f;
    /** Seconds an airlock cycle takes, near enough, for reckoning the way back in. */
    static final float CYCLE_SECONDS = 30f;
    /** Meters of line kept spare at the job. */
    static final float LINE_SPARE = 2f;
    /** Seconds between fresh looks at which port to go out of. */
    static final float REPLAN = 10f;
    /** Seconds a crew member keeps trying to reach an airlock's chamber they can't path to before giving the job up. */
    static final float STUCK = 10f;
    /** Seconds between fresh looks at whether an EVA suit is to be had aboard. */
    static final float SUIT_LOOK = 2f;
    /** Crawling a tile of hull costs as much as walking this many inside, for choosing the port. */
    static final float CRAWL_COST = 3.5f;

    private EvaTasks() {
    }

    // -----------------------------------------------------------------------
    // Shared by the leaves
    // -----------------------------------------------------------------------

    /**
     * True, and the job dropped from the board, if nobody needs it done any more: mended from inside
     * or by another hand, or a castaway who has caught hold again, before they were out to it.
     */
    static boolean stale(CrewAgent a, CrewTask t) {
        ShipGridComponent grid = a.service.grid(a.service.homeOf(a.entity));
        if (grid == null || TaskRules.isStillNeeded(t, grid, a.service)) return false;
        ShipTaskBoardComponent board = a.board();
        if (board != null) board.cancel(t);
        a.crew.task = null;
        return true;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * True if the player lets {@code a} go out on EVA: sent out ({@link
     * CrewMemberComponent.EvaOrder#OUT}), or left to go out as work comes ({@link
     * CrewMemberComponent.EvaOrder#NONE}) while their ship doesn't hold everyone in ({@link
     * ShipTaskBoardComponent#evaHeld}). Not kept in.
     */
    public static boolean mayGoOut(CrewAgent a) {
        switch (a.crew.evaOrder) {
            case OUT:
                return true;
            case IN:
                return false;
            default:
                ShipTaskBoardComponent board = a.board();
                return board == null || !board.evaHeld;
        }
    }

    /**
     * Sent out on EVA with no work outside to take, and not out: the order lapses (true), so they go
     * back to whatever else there is.
     */
    static boolean sentOutForNothing(CrewAgent a) {
        if (a.crew.evaOrder != CrewMemberComponent.EvaOrder.OUT || EvaSystem.of(a.entity) != null) return false;
        a.crew.evaOrder = CrewMemberComponent.EvaOrder.NONE;
        return true;
    }

    /**
     * True if {@code a} could go out on EVA for a job: they wear suits, and have an EVA suit on fit to
     * go out in ({@link #fitToGoOut}) or there is one hanging in a locker aboard the craft they work
     * for, and a docking port there can cycle them out. Already out, true. Looked at every {@link
     * #SUIT_LOOK}.
     */
    public static boolean suitReady(CrewAgent a) {
        CrewMemberComponent c = a.crew;
        if (!c.wearsSuits()) return false;
        if (EvaSystem.of(a.entity) != null) return true;
        if (a.time < a.evaSuitLookAt) return a.evaSuitReady;
        a.evaSuitLookAt = a.time + SUIT_LOOK;
        ShipGridComponent grid = a.service.grid(a.service.homeOf(a.entity));
        boolean suit = fitToGoOut(c);
        if (!suit && grid != null) {
            for (Furnishing f : SuitLockers.all(grid)) {
                if (SuitErrand.hasEvaSuit(f, EvaTasks::fitToGoOut)) {
                    suit = true;
                    break;
                }
            }
        }
        boolean port = false;
        if (suit && grid != null) {
            for (int i = 0; i < grid.mounts.size && !port; i++) {
                MountFootprint m = grid.mounts.get(i);
                port = m.kind == MountFootprint.MountKind.PORT && Airlock.works(grid, m);
            }
        }
        a.evaSuitReady = suit && port;
        return a.evaSuitReady;
    }

    /** True if {@code s} is an EVA suit fit to go out in: {@link #MIN_AIR} of air and {@link #MIN_JET} in its jetpack. */
    public static boolean fitToGoOut(Suit s) {
        return s.kind == Suit.Kind.EVA && s.air >= MIN_AIR && s.jet >= MIN_JET;
    }

    /** True if {@code c} has an EVA suit on fit to go out in ({@link #fitToGoOut(Suit)}). */
    public static boolean fitToGoOut(CrewMemberComponent c) {
        return c.inEvaSuit() && c.suitAir >= MIN_AIR && c.suitJet >= MIN_JET;
    }

    /**
     * The best way out to {@code t} for {@code a}: through the docking port whose airlock can cycle,
     * whose door opens onto the hull, and from which the hull runs round to the job within the
     * tether's reach, the walk to it and the crawl from it (a tile of crawling counts {@link
     * #CRAWL_COST} tiles of walking) the shortest; one with no power to pump down counts more. Null if
     * there is none.
     */
    static EvaRoute plan(CrewAgent a, ShipGridComponent grid, HullSurface hull, CrewTask t) {
        int[] work = new int[2], chamber = new int[2];
        if (!workTile(grid, hull, t, work)) return null;
        Array<int[]> path = new Array<>();
        EvaRoute best = null;
        for (int i = 0; i < grid.mounts.size; i++) {
            MountFootprint port = grid.mounts.get(i);
            if (port.kind != MountFootprint.MountKind.PORT || !Airlock.works(grid, port)) continue;
            int crawl = crawlTiles(grid, hull, port, work[0], work[1], path);
            if (crawl < 0) continue;
            DockingPortLayout.chamberTile(port, chamber);
            int walk;
            int x = a.pos.tileX(), y = a.pos.tileY();
            if (x == chamber[0] && y == chamber[1]) {
                walk = 0;
            } else if (a.findPath(x, y, chamber[0], chamber[1], a.pathScratch)) {
                walk = a.pathScratch.size;
            } else if (port.airlock != null && !port.airlock.isIn()) {
                // Cycling, its hatch shut: reachable once it is in again
                walk = Math.abs(chamber[0] - x) + Math.abs(chamber[1] - y);
            } else {
                continue;
            }
            float cost = walk + crawl * CRAWL_COST + (Airlock.of(grid, port).powered(grid) ? 0f : 50f);
            if (best == null || cost < best.cost) best = new EvaRoute(t, port, work[0], work[1], cost);
        }
        return best;
    }

    /**
     * Tiles of hull crawled from {@code port}'s door to ring tile (wx, wy), or -1 if the door doesn't
     * open onto the hull, there is no way round, or it is beyond the tether's reach.
     */
    static int crawlTiles(ShipGridComponent grid, HullSurface hull, MountFootprint port, int wx, int wy, Array<int[]> path) {
        int[] door = EvaSystem.doorRing(port, new int[2]);
        if (!hull.isRing(door[0], door[1]) || !hull.path(door[0], door[1], wx, wy, path)) return -1;
        return path.size * grid.tileSize > EvaSystem.LINE_MAX - LINE_SPARE ? -1 : path.size;
    }

    /**
     * The ring tile outside the hull job {@code t} is worked from: {@code out = {x, y}}; false if
     * there is none. An RCS block's is beyond the edge it sits on, else beside its tile; a hull
     * tile's is beside it; work the game posts says where its own is.
     */
    public static boolean workTile(ShipGridComponent grid, HullSurface hull, CrewTask t, int[] out) {
        switch (t.kind) {
            case REPAIR_RCS:
                for (int i = 0; i < grid.rcsThrusters.size; i++) {
                    RcsThruster r = grid.rcsThrusters.get(i);
                    if (r.x != t.x || r.y != t.y) continue;
                    int[] o = hull.outside(t.x, t.y, r.side);
                    if (o != null) {
                        out[0] = o[0];
                        out[1] = o[1];
                        return true;
                    }
                }
                return beside(hull, t.x, t.y, out);
            case PATCH_HULL:
                return beside(hull, t.x, t.y, out);
            case RETRIEVE: {
                EvaComponent o = EvaSystem.of(((RetrieveTask) t).castaway);
                int[] n = o == null ? null : hull.nearest(o.rx, o.ry);
                if (n == null) return false;
                out[0] = n[0];
                out[1] = n[1];
                return true;
            }
            default:
                return t instanceof OutsideTask && ((OutsideTask) t).job.workTile(hull, out);
        }
    }

    /**
     * The ring tile beside tile (x, y): beyond one of its edges, else off one of its corners.
     * {@code out = {x, y}}; false if the tile has no outside.
     */
    public static boolean beside(HullSurface hull, int x, int y, int[] out) {
        for (Side s : Side.values()) {
            int[] o = hull.outside(x, y, s);
            if (o != null) {
                out[0] = o[0];
                out[1] = o[1];
                return true;
            }
        }
        for (int dy = -1; dy <= 1; dy += 2) {
            for (int dx = -1; dx <= 1; dx += 2) {
                if (!hull.isRing(x + dx, y + dy)) continue;
                out[0] = x + dx;
                out[1] = y + dy;
                return true;
            }
        }
        return false;
    }

    /** Seconds of work job {@code t} takes at a steady pace. */
    public static float workSeconds(CrewDef def, CrewTask t) {
        switch (t.kind) {
            case REPAIR_RCS: return def.rcsRepairTime;
            case PATCH_HULL: return def.hullPatchTime;
            default: return t instanceof OutsideTask ? ((OutsideTask) t).job.seconds(def) : 0f;
        }
    }

    /** Seconds of work already put into job {@code t}, kept from an earlier spacewalk. */
    static float worked(CrewTask t) {
        return t instanceof OutsideTask ? ((OutsideTask) t).job.worked() : 0f;
    }

    /** What job {@code t}'s work does, once it is done. */
    static void finishWork(CrewAgent a, ShipGridComponent grid, CrewTask t) {
        switch (t.kind) {
            case REPAIR_RCS:
            case PATCH_HULL:
                // Framed back up, so a block on it fires again, and plated airtight while there are
                // kits: a block's tile in the corvette's bow corner leaks where nobody inside can reach
                HullPlating.plate(grid, t.x, t.y);
                break;
            default:
                if (t instanceof OutsideTask) ((OutsideTask) t).job.done(a.entity);
                break;
        }
    }

    /** What a crew member at job {@code t} is doing, for their activity line. */
    public static String doing(CrewTask t) {
        switch (t.kind) {
            case REPAIR_RCS: return "repairing an RCS thruster on EVA";
            case PATCH_HULL: return "plating a holed hull tile on EVA";
            case RETRIEVE: return "fetching a shipmate adrift on EVA";
            default:
                if (t instanceof HullStripTask) return ((HullStripTask) t).job.doing() + " on EVA";
                return t instanceof OutsideTask ? ((OutsideTask) t).job.doing() + " on EVA" : "working on EVA";
        }
    }

    /** What a crew member crawls to for job {@code t}. */
    static String where(CrewTask t) {
        switch (t.kind) {
            case REPAIR_RCS: return "an RCS thruster";
            case PATCH_HULL: return "a holed hull tile";
            case RETRIEVE: return "a shipmate adrift";
            default:
                if (t instanceof HullStripTask) return ((HullStripTask) t).job.where();
                return t instanceof OutsideTask ? ((OutsideTask) t).job.where() : "the job";
        }
    }

    /** The job outside {@code a} holds, or null (forgetting a task that is no longer theirs). */
    static CrewTask held(CrewAgent a) {
        CrewTask t = a.crew.task;
        if (t == null) return null;
        if (t.kind.isEva() && t.isActive() && t.claimedBy == a.entity) return t;
        a.service.releaseTask(a.entity);
        return null;
    }

    /**
     * Claims another job outside the line reaches from the port they are out of, the best for them;
     * null if none. Work whose part is still inside ({@link OutsideTask.Job#part}) isn't one.
     */
    static CrewTask next(CrewAgent a, EvaComponent eva, ShipGridComponent grid, HullSurface hull, ShipTaskBoardComponent board) {
        if (board == null || board.faction != a.crew.faction) return null;
        int[] work = new int[2];
        Array<int[]> path = new Array<>();
        CrewTask t = board.best(a.crew.role, eva.ringX, eva.ringY, task -> task.kind.isEva()
                && !(task instanceof OutsideTask && ((OutsideTask) task).job.part() != null) && a.mayTake(task)
                && workTile(grid, hull, task, work)
                && crawlTiles(grid, hull, eva.portMount, work[0], work[1], path) >= 0);
        if (t == null || !board.claim(t, a.entity)) return null;
        a.crew.task = t;
        return t;
    }

    /** Seconds the way back in from where {@code eva} is takes: the crawl to the door and a cycle. */
    static float wayBack(EvaComponent eva, HullSurface hull, Array<int[]> path, int[] tile) {
        EvaSystem.doorRing(eva.portMount, tile);
        int fromX = eva.ringX, fromY = eva.ringY;
        float extra = 0f;
        if (eva.phase == EvaComponent.Phase.FREE) {
            int[] near = hull.nearest(eva.rx, eva.ry);
            if (near == null) return Float.MAX_VALUE;
            fromX = near[0];
            fromY = near[1];
            extra = (Math.abs(eva.rx - fromX) + Math.abs(eva.ry - fromY)) * hull.tileSize() / EvaSystem.CLIMB_SPEED;
        }
        if (!hull.path(fromX, fromY, tile[0], tile[1], path)) return Float.MAX_VALUE;
        return path.size * hull.tileSize() / EvaSystem.CRAWL_SPEED + extra + CYCLE_SECONDS;
    }

    /**
     * One step of {@code a} to the chamber of {@code port}'s airlock, theirs to cycle out: they wait by
     * the hatch while it is in use or standing open for someone outside. ARRIVED standing in the
     * chamber; UNREACHABLE if they can't get there.
     */
    static CrewAgent.Travel toChamber(CrewAgent a, ShipGridComponent grid, MountFootprint port, int[] tile) {
        Airlock lock = Airlock.of(grid, port);
        if (lock.user() != null && lock.user() != a.entity || !lock.isIn()) {
            // In use, or standing open for someone outside: asked to cycle in for them, they wait by the hatch
            lock.claim(a.entity, false);
            a.crew.activity = "waiting for the airlock";
            DockingPortLayout.hatchTile(port, tile);
            Side in = DockingPortLayout.facing(port).opposite();
            int wx = tile[0] + in.dx, wy = tile[1] + in.dy;
            if (a.travel(a.pos.ship, (x, y) -> x == wx && y == wy) == CrewAgent.Travel.UNREACHABLE && !a.pos.isMoving()) a.pos.stop();
            return CrewAgent.Travel.GOING;
        }
        lock.claim(a.entity, false);
        DockingPortLayout.chamberTile(port, tile);
        int cx = tile[0], cy = tile[1];
        CrewAgent.Travel go = a.travel(a.pos.ship, (x, y) -> x == cx && y == cy);
        if (go == CrewAgent.Travel.GOING) a.crew.activity = "heading for the airlock";
        if (go == CrewAgent.Travel.ARRIVED) a.pos.stop();
        return go;
    }

    /**
     * The port of {@code ship} (grid {@code grid}) to go in or out by for a crossing to or from
     * {@code other}: one whose outer door opens onto the hull and isn't latched to anything, the one
     * whose door is nearest {@code other}'s middle. Its airlock need not cycle (a hostile craft's door
     * is forced). Null if there is none.
     */
    public static MountFootprint entryPort(CrewAgent a, EvaSystem system, ShipGridComponent grid, Entity ship, Entity other) {
        float[] mid = new float[2];
        if (!system.middleIn(other, ship, mid)) return null;
        HullSurface hull = system.hull(grid);
        int[] door = new int[2];
        MountFootprint best = null;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < grid.mounts.size; i++) {
            MountFootprint port = grid.mounts.get(i);
            if (port.kind != MountFootprint.MountKind.PORT || Airlock.docked(grid, port)) continue;
            EvaSystem.doorRing(port, door);
            if (!hull.isRing(door[0], door[1])) continue;
            float d = (door[0] + 0.5f - mid[0]) * (door[0] + 0.5f - mid[0]) + (door[1] + 0.5f - mid[1]) * (door[1] + 0.5f - mid[1]);
            if (d < bestD) {
                bestD = d;
                best = port;
            }
        }
        return best;
    }

    /** What {@code a} calls {@code craft}, on their way to it: "their ship", or "the other craft". */
    static String craft(CrewAgent a, Entity craft) {
        return craft == a.service.homeOf(a.entity) ? "their ship" : "the other craft";
    }

    /** The work on another craft's hull {@code a} holds, or null. */
    static HullStripTask stripOrder(CrewAgent a) {
        CrewTask t = a.crew.task;
        return t instanceof HullStripTask && t.isActive() && t.claimedBy == a.entity ? (HullStripTask) t : null;
    }

    /** The boarding order {@code a} holds, or null. */
    static BoardTask boardOrder(CrewAgent a) {
        CrewTask t = a.crew.task;
        return t instanceof BoardTask && t.isActive() && t.claimedBy == a.entity ? (BoardTask) t : null;
    }

    /** True if {@code a} has an EVA suit on with at least {@code minAir} seconds of air in it. */
    static boolean inEvaSuit(CrewAgent a, float minAir) {
        CrewMemberComponent c = a.crew;
        return c.suited && c.suitKind == Suit.Kind.EVA && c.suitAir >= minAir;
    }

    /** True if (x, y) is in the opening of a docking port whose airlock someone is using: nobody else stands there. */
    public static boolean inAirlock(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        MountFootprint m = t == null ? null : t.mount;
        return m != null && m.kind == MountFootprint.MountKind.PORT && m.airlock != null && m.airlock.user() != null
                && DockingPortLayout.isOpening(m, x, y);
    }
}
