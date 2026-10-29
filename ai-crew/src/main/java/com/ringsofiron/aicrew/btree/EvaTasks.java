package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.Task.Status;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.BoardTask;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.EvaComponent;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.interiorgrid.Airlock;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.Furnishing;
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
 * Work outside the hull, in a crew member's behavior tree: an RCS block whose hull tile is wrecked,
 * mended on EVA ({@link EvaSystem}).
 *
 * <pre>
 * [OnEva]  Spacewalk                                          first under the root
 * Sequence( IsTaskKind(REPAIR_RCS), SuitUpForEva, GoOutside ) in the task branch
 * </pre>
 *
 * A crew member takes the job only with an EVA suit to be had ({@link #suitReady}). They hang up
 * any pressure suit they have on, get into an EVA suit at a locker, and walk to the docking port the
 * block is best reached from ({@link #plan}): one that can cycle (undocked, its door and hatch
 * standing), whose door opens onto the hull, and from which the hull runs round to the block within
 * the tether's reach. Standing in its chamber they go out ({@link EvaSystem#begin}), and from then
 * on {@link Spacewalk} has them, the task still theirs: along the hull to the block, the work there
 * ({@link com.ringsofiron.aicrew.CrewDef#rcsRepairTime} at their suited pace), then on to another
 * wrecked block within reach, or back in. They head back in as soon as battle stations sound, or
 * once their air is down to what the way back takes and {@link #AIR_MARGIN} more.
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
    // Leaves
    // -----------------------------------------------------------------------

    /** Succeeds while the agent is on an EVA: from stepping into the airlock to the hatch opening behind them. */
    public static class OnEva extends CrewLeaf {
        @Override
        public Status execute() {
            return EvaSystem.of(agent().entity) != null ? Status.SUCCEEDED : Status.FAILED;
        }
    }

    /** Gets the agent into an EVA suit with air enough to go out in, out of any other suit first. */
    public static class SuitUpForEva extends CrewLeaf {
        @Override
        public Status execute() {
            CrewAgent a = agent();
            CrewMemberComponent c = a.crew;
            CrewTask t = c.task;
            if (t == null) return Status.FAILED;
            if (c.suited && c.suitKind == Suit.Kind.EVA && c.suitAir >= MIN_AIR) {
                c.suiting = 0f;
                return Status.SUCCEEDED;
            }
            // Out of the pressure suit (or a spent EVA suit) first, into its locker
            Status s = c.suited ? SuitErrand.stow(a) : SuitErrand.suitUpEva(a, MIN_AIR);
            if (s == Status.FAILED) {
                a.ignore(t, NO_ROUTE);
                a.releaseTask();
                return Status.FAILED;
            }
            return c.suited && c.suitKind == Suit.Kind.EVA && s == Status.SUCCEEDED ? Status.SUCCEEDED : Status.RUNNING;
        }
    }

    /** Walks to the airlock the job is best reached from, waits for it, and goes out through it. */
    public static class GoOutside extends CrewLeaf {
        private Route route;
        private float planned, stuck;
        private final int[] tile = new int[2];

        @Override
        public void start() {
            route = null;
            stuck = 0f;
        }

        @Override
        public void end() {
            CrewAgent a = getObject();
            // Gave up on the way: an airlock asked to cycle in for them is free again
            if (a != null && route != null && route.port.airlock != null && EvaSystem.of(a.entity) == null) {
                route.port.airlock.release(a.entity);
            }
        }

        @Override
        public Status execute() {
            CrewAgent a = agent();
            CrewTask t = a.crew.task;
            EvaSystem eva = a.service.eva;
            ShipGridComponent grid = a.grid();
            if (t == null || eva == null || grid == null || a.pos.ship != a.service.homeOf(a.entity)) return Status.FAILED;
            if (route == null || route.task != t || a.time - planned > REPLAN || !Airlock.works(grid, route.port)) {
                if (route != null && route.port.airlock != null) route.port.airlock.release(a.entity);
                route = plan(a, grid, eva.hull(grid), t);
                planned = a.time;
                if (route == null) return giveUp(a, t);
            }
            MountFootprint port = route.port;
            CrewAgent.Travel go = toChamber(a, grid, port, tile);
            if (go == CrewAgent.Travel.UNREACHABLE) {
                if ((stuck += a.dt) > STUCK) return giveUp(a, t);
                return Status.RUNNING;
            }
            stuck = 0f;
            if (go == CrewAgent.Travel.GOING) return Status.RUNNING;
            a.crew.activity = "cycling out through the airlock";
            // Out: the Spacewalk branch has them from the next step, the task still theirs
            if (!eva.begin(a.entity, port)) a.crew.activity = "waiting for the airlock";
            return Status.RUNNING;
        }

        private Status giveUp(CrewAgent a, CrewTask t) {
            a.ignore(t, NO_ROUTE);
            a.releaseTask();
            return Status.FAILED;
        }
    }

    /**
     * Out on EVA: along the hull to the job, the work, and back in. While still in the airlock's
     * chamber, on the way out or back, they wait for it. A crew member out cold is the line's to bring
     * in ({@link EvaSystem}); this runs only while they are conscious.
     */
    public static class Spacewalk extends CrewLeaf {
        private CrewTask workingOn;
        private float work, backLook, backSeconds;
        private final int[] tile = new int[2];
        private final Array<int[]> scratch = new Array<>();

        @Override
        public void start() {
            backLook = 0f;
        }

        @Override
        public Status execute() {
            CrewAgent a = agent();
            CrewMemberComponent c = a.crew;
            EvaComponent eva = EvaSystem.of(a.entity);
            if (eva == null) return Status.SUCCEEDED;
            if (eva.phase == EvaComponent.Phase.LEAVING || eva.phase == EvaComponent.Phase.RETURNING) {
                c.activity = eva.phase == EvaComponent.Phase.LEAVING ? "cycling out through the airlock" : "cycling back in";
                a.pos.stop();
                return Status.RUNNING;
            }
            ShipGridComponent grid = a.grid();
            EvaSystem system = a.service.eva;
            if (grid == null || system == null || eva.portMount == null) return Status.RUNNING;
            // Across to another craft, or on its hull: a boarding, or the way home from one
            if (eva.crossTo != null || eva.crossing) return crossing(a, c, eva, system);
            HullSurface hull = system.hull(grid);
            ShipTaskBoardComponent board = a.board();
            CrewTask t = held(a);
            if ((backLook -= a.dt) <= 0f) {
                backLook = 2f;
                backSeconds = wayBack(eva, hull, scratch, tile);
            }
            if (!eva.returning) {
                // Called in by battle stations; or their line has parted, and nobody works on untethered
                boolean back = board == null || board.battleStations || !eva.tethered;
                if (!back && t != null && (!TaskRules.isStillNeeded(t, grid, a.service) || !workTile(grid, hull, t, tile))) {
                    a.service.releaseTask(a.entity);
                    t = null;
                }
                if (!back && t != null && eva.outOfReach) {
                    // The line runs out, or the hull has no way round, short of it
                    a.ignore(t, NO_ROUTE);
                    a.service.releaseTask(a.entity);
                    t = null;
                }
                eva.outOfReach = false;
                if (!back && t == null) t = next(a, eva, grid, hull, board);
                if (t == null) back = true;
                if (!back) {
                    float workLeft = (c.def.rcsRepairTime - (workingOn == t ? work : 0f)) / Math.max(0.05f, c.workPace());
                    back = c.suitAir < backSeconds + AIR_MARGIN + workLeft;
                }
                if (back) {
                    if (c.task != null) a.service.releaseTask(a.entity);
                    eva.returning = true;
                }
            }
            if (eva.returning) {
                workingOn = null;
                work = 0f;
                EvaSystem.doorRing(eva.portMount, tile);
                eva.goalX = tile[0];
                eva.goalY = tile[1];
                c.activity = moving(eva, "heading back to the airlock");
                return Status.RUNNING;
            }
            workTile(grid, hull, t, tile);
            eva.goalX = tile[0];
            eva.goalY = tile[1];
            if (workingOn != t) {
                workingOn = t;
                work = 0f;
            }
            if (!eva.atGoal()) {
                c.activity = moving(eva, "crawling along the hull to an RCS thruster");
                return Status.RUNNING;
            }
            float dt = a.dt * c.workPace();
            work += dt;
            c.record().pendingWork += dt; // engineering experience
            c.activity = "repairing an RCS thruster on EVA";
            c.showProgress(work, c.def.rcsRepairTime);
            if (work >= c.def.rcsRepairTime) {
                RcsThruster.fieldRepair(grid, t.x, t.y);
                board.complete(t);
                c.task = null;
                workingOn = null;
                work = 0f;
            }
            return Status.RUNNING;
        }

        /**
         * A step of crossing: over their hull to the side facing the craft they cross to and off it;
         * then across, onto its hull and along it to the port they go in by, its door forced first if
         * they are boarding. A boarder whose order is gone goes back across home.
         */
        private Status crossing(CrewAgent a, CrewMemberComponent c, EvaComponent eva, EvaSystem system) {
            Entity home = a.service.homeOf(a.entity);
            BoardTask order = boardOrder(a);
            if (eva.crossBreaking || eva.breaking) {
                Entity to = eva.crossTo != null ? eva.crossTo : a.pos.ship;
                if (order == null || order.ship != to || !order.job.isOpen()) {
                    // The boarding called off (or a load put the order down): home
                    if (!homeward(a, eva, system, home)) {
                        c.activity = "adrift";
                        return Status.RUNNING;
                    }
                }
            }
            if (eva.crossTo != null) {
                Entity to = eva.crossTo;
                if (!system.launchTile(a.entity, to, tile)) return Status.RUNNING;
                // Not in again by the door they came out of: over the hull to push off
                eva.returning = false;
                eva.goalX = tile[0];
                eva.goalY = tile[1];
                if (eva.phase == EvaComponent.Phase.FREE || eva.atGoal() || eva.outOfReach) {
                    eva.outOfReach = false;
                    system.cross(a.entity, to, eva.crossPort, eva.crossBreaking);
                    c.activity = "jetting across to " + craft(a, to);
                    return Status.RUNNING;
                }
                c.activity = moving(eva, "crawling over the hull to cross to " + craft(a, to));
                return Status.RUNNING;
            }
            EvaSystem.doorRing(eva.portMount, tile);
            eva.goalX = tile[0];
            eva.goalY = tile[1];
            eva.returning = true;
            if (eva.breaking && eva.atGoal()) {
                float dt = a.dt * c.workPace();
                c.record().pendingWork += dt;
                c.activity = order.job.breaching(eva.portMount);
                BoardTask.Breach how = order.job.breach(a.entity, eva.portMount, dt);
                if (how == BoardTask.Breach.OPEN) {
                    eva.breaking = false;
                    eva.forcedIn = true;
                } else if (how == BoardTask.Breach.CYCLED) {
                    eva.breaking = false;
                }
                return Status.RUNNING;
            }
            if (eva.phase == EvaComponent.Phase.FREE && !eva.fell) c.activity = "jetting across to " + craft(a, a.pos.ship);
            else c.activity = moving(eva, "crawling along the hull to the airlock");
            return Status.RUNNING;
        }

        /** Sets {@code eva} to go back across home from where they are; false if there is no way in there. */
        private static boolean homeward(CrewAgent a, EvaComponent eva, EvaSystem system, Entity home) {
            ShipGridComponent grid = a.service.grid(home);
            MountFootprint port = grid == null ? null : entryPort(a, system, grid, home, a.pos.ship);
            if (port == null) return false;
            if (a.pos.ship == home) {
                // Not yet off their own hull: back in by the door they came out of
                eva.crossTo = null;
                eva.crossPort = null;
                eva.crossBreaking = false;
                eva.crossing = false;
                eva.breaking = false;
                eva.returning = true;
                return true;
            }
            eva.crossTo = home;
            eva.crossPort = port;
            eva.crossBreaking = false;
            eva.breaking = false;
            eva.forcedIn = false;
            return true;
        }

        /** What they are doing on their way: crawling, flying, swinging on the line or adrift. */
        private static String moving(EvaComponent eva, String crawling) {
            if (eva.phase == EvaComponent.Phase.ON_HULL) return crawling;
            if (eva.jetting) return "jetting to the hull";
            if (eva.climbing) return "pulling in along the tether";
            return eva.tethered ? "swinging on the tether" : "adrift";
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * True if {@code a} could go out on EVA for a job: they wear suits, and have an EVA suit on with
     * {@link #MIN_AIR} in it or there is one hanging in a locker aboard the craft they work for, and
     * a docking port there can cycle them out. Already out, true. Looked at every {@link #SUIT_LOOK}.
     */
    public static boolean suitReady(CrewAgent a) {
        CrewMemberComponent c = a.crew;
        if (!c.wearsSuits()) return false;
        if (EvaSystem.of(a.entity) != null) return true;
        if (a.time < a.evaSuitLookAt) return a.evaSuitReady;
        a.evaSuitLookAt = a.time + SUIT_LOOK;
        ShipGridComponent grid = a.service.grid(a.service.homeOf(a.entity));
        boolean suit = c.suited && c.suitKind == Suit.Kind.EVA && c.suitAir >= MIN_AIR;
        if (!suit && grid != null) {
            for (Furnishing f : SuitLockers.all(grid)) {
                if (SuitErrand.hasEvaSuit(f, MIN_AIR)) {
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

    /** The way out to a job: the port, and the ring tile outside the hull the job is worked from. */
    static final class Route {
        final CrewTask task;
        final MountFootprint port;
        final int workX, workY;
        final float cost;

        Route(CrewTask task, MountFootprint port, int workX, int workY, float cost) {
            this.task = task;
            this.port = port;
            this.workX = workX;
            this.workY = workY;
            this.cost = cost;
        }
    }

    /**
     * The best way out to {@code t} for {@code a}: through the docking port whose airlock can cycle,
     * whose door opens onto the hull, and from which the hull runs round to the job within the
     * tether's reach, the walk to it and the crawl from it (a tile of crawling counts {@link
     * #CRAWL_COST} tiles of walking) the shortest; one with no power to pump down counts more. Null if
     * there is none.
     */
    static Route plan(CrewAgent a, ShipGridComponent grid, HullSurface hull, CrewTask t) {
        int[] work = new int[2], chamber = new int[2];
        if (!workTile(grid, hull, t, work)) return null;
        Array<int[]> path = new Array<>();
        Route best = null;
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
            if (best == null || cost < best.cost) best = new Route(t, port, work[0], work[1], cost);
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
     * The ring tile outside the hull that RCS job {@code t} is worked from: beyond the edge its block
     * sits on, else beyond another edge of its tile, else off a corner. {@code out = {x, y}}; false if
     * the tile has no outside.
     */
    static boolean workTile(ShipGridComponent grid, HullSurface hull, CrewTask t, int[] out) {
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
        for (Side s : Side.values()) {
            int[] o = hull.outside(t.x, t.y, s);
            if (o != null) {
                out[0] = o[0];
                out[1] = o[1];
                return true;
            }
        }
        for (int dy = -1; dy <= 1; dy += 2) {
            for (int dx = -1; dx <= 1; dx += 2) {
                if (!hull.isRing(t.x + dx, t.y + dy)) continue;
                out[0] = t.x + dx;
                out[1] = t.y + dy;
                return true;
            }
        }
        return false;
    }

    /** The RCS job {@code a} holds, or null (forgetting a task that is no longer theirs). */
    static CrewTask held(CrewAgent a) {
        CrewTask t = a.crew.task;
        if (t == null) return null;
        if (t.kind == CrewTask.Kind.REPAIR_RCS && t.isActive() && t.claimedBy == a.entity) return t;
        a.service.releaseTask(a.entity);
        return null;
    }

    /** Claims another RCS job the line reaches from the port they are out of, the best for them; null if none. */
    static CrewTask next(CrewAgent a, EvaComponent eva, ShipGridComponent grid, HullSurface hull, ShipTaskBoardComponent board) {
        if (board == null || board.faction != a.crew.faction) return null;
        int[] work = new int[2];
        Array<int[]> path = new Array<>();
        CrewTask t = board.best(a.crew.role, eva.ringX, eva.ringY, task -> task.kind == CrewTask.Kind.REPAIR_RCS
                && !a.isIgnoring(task) && workTile(grid, hull, task, work)
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

    /**
     * Setting out to cross to another craft with no dock between, a step at a time: into an EVA suit
     * (aboard their own craft; the way home they go in the one they have on), to the airlock of the
     * craft they are aboard nearest the other, and out ({@link EvaSystem#beginCrossing}), to go in
     * there by its {@link #entryPort}. A boarder ({@code breaking}) forces its door from outside.
     */
    public static final class Departure {
        private MountFootprint port;
        private float planned, stuck;
        private final int[] tile = new int[2];

        /** Forgets the airlock picked, and lets it go if it was asked to cycle for them. */
        public void reset(CrewAgent a) {
            if (a != null && port != null && port.airlock != null && EvaSystem.of(a.entity) == null) port.airlock.release(a.entity);
            port = null;
            stuck = 0f;
        }

        /**
         * One step toward going out to cross to {@code to}: RUNNING while on the way (and once out,
         * when {@link Spacewalk} has them), FAILED if it can't be done: no EVA suit to be had, no
         * airlock out, no way in there, or {@code to} beyond {@link EvaSystem#CROSS_RANGE}.
         */
        public Status step(CrewAgent a, Entity to, boolean breaking) {
            EvaSystem system = a.service.eva;
            ShipGridComponent grid = a.grid(), there = a.service.grid(to);
            if (system == null || grid == null || there == null || EvaSystem.of(a.entity) != null) return Status.FAILED;
            float[] sep = new float[2];
            if (!system.separation(a.pos.ship, to, sep) || sep[0] > EvaSystem.CROSS_RANGE + 0.5f * (grid.width + there.width) * grid.tileSize) {
                return Status.FAILED;
            }
            boolean home = a.pos.ship == a.service.homeOf(a.entity);
            if (!inEvaSuit(a, home ? MIN_AIR : 1f)) {
                // Into an EVA suit at home, out of any other first; away, they go in the one they have on
                if (!home) return Status.FAILED;
                Status s = a.crew.suited && a.crew.suitKind != Suit.Kind.EVA ? SuitErrand.stow(a) : SuitErrand.suitUpEva(a, MIN_AIR);
                if (s == Status.FAILED) return Status.FAILED;
                if (s == Status.RUNNING && a.crew.suiting > 0f) a.crew.activity = "getting into an EVA suit to cross to " + craft(a, to);
                return Status.RUNNING;
            }
            MountFootprint in = entryPort(a, system, there, to, a.pos.ship);
            if (in == null) return Status.FAILED;
            if (port == null || a.time - planned > REPLAN || !Airlock.works(grid, port)) {
                if (port != null && port.airlock != null) port.airlock.release(a.entity);
                port = outPort(a, system, grid, to);
                planned = a.time;
                if (port == null) return Status.FAILED;
            }
            CrewAgent.Travel go = toChamber(a, grid, port, tile);
            if (go == CrewAgent.Travel.UNREACHABLE) return (stuck += a.dt) > STUCK ? Status.FAILED : Status.RUNNING;
            stuck = 0f;
            if (go == CrewAgent.Travel.GOING) return Status.RUNNING;
            a.crew.activity = "cycling out through the airlock";
            if (!system.beginCrossing(a.entity, port, to, in, breaking)) a.crew.activity = "waiting for the airlock";
            return Status.RUNNING;
        }

        /**
         * The airlock of the craft {@code a} is aboard to go out by for {@code to}: one that can cycle
         * and opens onto the hull, by the walk to it plus the crawl from its door to the side facing
         * {@code to}. Null if none.
         */
        private static MountFootprint outPort(CrewAgent a, EvaSystem system, ShipGridComponent grid, Entity to) {
            HullSurface hull = system.hull(grid);
            int[] launch = new int[2], chamber = new int[2], door = new int[2];
            if (!system.launchTile(a.entity, to, launch)) return null;
            Array<int[]> path = new Array<>();
            MountFootprint best = null;
            float bestCost = Float.MAX_VALUE;
            for (int i = 0; i < grid.mounts.size; i++) {
                MountFootprint port = grid.mounts.get(i);
                if (port.kind != MountFootprint.MountKind.PORT || !Airlock.works(grid, port)) continue;
                EvaSystem.doorRing(port, door);
                if (!hull.path(door[0], door[1], launch[0], launch[1], path)) continue;
                DockingPortLayout.chamberTile(port, chamber);
                int x = a.pos.tileX(), y = a.pos.tileY(), walk;
                if (x == chamber[0] && y == chamber[1]) walk = 0;
                else if (a.findPath(x, y, chamber[0], chamber[1], a.pathScratch)) walk = a.pathScratch.size;
                else if (port.airlock != null && !port.airlock.isIn()) walk = Math.abs(chamber[0] - x) + Math.abs(chamber[1] - y);
                else continue;
                float cost = walk + path.size * CRAWL_COST;
                if (cost < bestCost) {
                    bestCost = cost;
                    best = port;
                }
            }
            return best;
        }
    }

    /** True if (x, y) is in the opening of a docking port whose airlock someone is using: nobody else stands there. */
    public static boolean inAirlock(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        MountFootprint m = t == null ? null : t.mount;
        return m != null && m.kind == MountFootprint.MountKind.PORT && m.airlock != null && m.airlock.user() != null
                && DockingPortLayout.isOpening(m, x, y);
    }
}
