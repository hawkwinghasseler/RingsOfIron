package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.EvaComponent;
import com.ringsofiron.aicrew.EvaSystem;
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
 * any pressure suit they have on, get into an EVA suit at a locker, and walk to the docking port the
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

    /** Succeeds if the agent's task is work outside ({@link CrewTask.Kind#isEva}); negated, if it is any other. */
    public static class IsEvaJob extends CrewLeaf {
        private final boolean negate;

        public IsEvaJob() {
            this(false);
        }

        public IsEvaJob(boolean negate) {
            this.negate = negate;
        }

        @Override
        public Status execute() {
            CrewTask t = agent().crew.task;
            return t != null && t.kind.isEva() != negate ? Status.SUCCEEDED : Status.FAILED;
        }
    }

    /** Gets the agent into an EVA suit with air enough to go out in, out of any other suit first. */
    public static class SuitUpForEva extends CrewLeaf {
        @Override
        public Status execute() {
            CrewAgent a = agent();
            CrewMemberComponent c = a.crew;
            CrewTask t = c.task;
            if (t == null || stale(a, t)) return Status.FAILED;
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

    /**
     * For work outside that needs a part ({@link OutsideTask.Job#part}): to a bay of the hold that
     * has it, and its crate lifted out ({@link CrewDef#loadTime}) to carry out. Succeeds at once for
     * work that needs none, or with its crate in hand already.
     */
    public static class FetchPart extends CrewLeaf {
        private float working;
        private boolean anyBay;

        @Override
        public void start() {
            working = 0f;
            anyBay = false;
        }

        @Override
        public Status execute() {
            CrewAgent a = agent();
            CrewMemberComponent c = a.crew;
            OutsideTask.Job job = c.task instanceof OutsideTask ? ((OutsideTask) c.task).job : null;
            if (job == null || c.crate != null && c.crate.job == job) return Status.SUCCEEDED;
            String part = job.part();
            if (part == null) return Status.SUCCEEDED;
            Entity ship = a.service.homeOf(a.entity);
            if (c.crate != null || stale(a, c.task) || a.pos.ship != ship) {
                a.releaseTask();
                return Status.FAILED;
            }
            c.activity = "fetching a crate from the hold";
            CrewAgent.Travel go = a.travel(ship, CrewTasks.beside(a, ship, anyBay ? null : part, true));
            if (go == CrewAgent.Travel.UNREACHABLE && !anyBay) {
                // No way to the bays it is in: work from whichever bay of the hold can be reached
                anyBay = true;
                go = a.travel(ship, CrewTasks.beside(a, ship, null, true));
            }
            if (go == CrewAgent.Travel.UNREACHABLE) {
                a.ignore(c.task, NO_ROUTE);
                a.releaseTask();
                return Status.FAILED;
            }
            if (go == CrewAgent.Travel.GOING) return Status.RUNNING;
            c.activity = "lifting a crate out of the hold";
            working += a.dt;
            if (working < c.def.loadTime) return Status.RUNNING;
            CrewMemberComponent.Crate crate = job.take(a.entity);
            if (crate == null) {
                // Gone from the hold
                a.releaseTask();
                return Status.FAILED;
            }
            c.crate = crate;
            return Status.SUCCEEDED;
        }
    }

    /**
     * Succeeds if the agent, aboard and not out on EVA, has a crate in hand that no work of theirs
     * takes outside: a part whose work they put down, or the part the work took off.
     */
    public static class CrateToStow extends CrewLeaf {
        @Override
        public Status execute() {
            CrewAgent a = agent();
            CrewMemberComponent.Crate crate = a.crew.crate;
            if (crate == null || EvaSystem.of(a.entity) != null) return Status.FAILED;
            CrewTask t = a.crew.task;
            boolean carried = crate.job != null && t instanceof OutsideTask && ((OutsideTask) t).job == crate.job
                    && t.isActive() && t.claimedBy == a.entity;
            return carried ? Status.FAILED : Status.SUCCEEDED;
        }
    }

    /**
     * Carries the crate in hand to a bay of the craft's hold with room for it and sets it down there
     * ({@link CrewDef#loadTime}). With no way to the hold, it goes in all the same; with no room in
     * it, it is cast off. Its work hears which ({@link OutsideTask.Job#stowed}).
     */
    public static class StowCrate extends CrewLeaf {
        private float working;
        private boolean anyBay;

        @Override
        public void start() {
            working = 0f;
            anyBay = false;
        }

        @Override
        public Status execute() {
            CrewAgent a = agent();
            CrewMemberComponent c = a.crew;
            CrewMemberComponent.Crate crate = c.crate;
            if (crate == null) return Status.SUCCEEDED;
            Entity ship = a.service.homeOf(a.entity);
            CargoHoldComponent hold = ship == null ? null : ship.getComponent(CargoHoldComponent.class);
            CrewAgent.Travel go = CrewAgent.Travel.UNREACHABLE;
            if (hold != null && a.pos.ship == ship) {
                go = a.travel(ship, CrewTasks.beside(a, ship, anyBay ? null : crate.commodity, false));
                if (go == CrewAgent.Travel.UNREACHABLE && !anyBay) {
                    anyBay = true;
                    go = a.travel(ship, CrewTasks.beside(a, ship, null, false));
                }
            }
            if (go == CrewAgent.Travel.GOING) {
                c.activity = "carrying " + crate.name + " to the hold";
                return Status.RUNNING;
            }
            if (go == CrewAgent.Travel.ARRIVED) {
                c.activity = "stowing " + crate.name + " in the hold";
                working += a.dt;
                if (working < c.def.loadTime) return Status.RUNNING;
            }
            double in = hold == null ? 0 : hold.load(crate.commodity, 1, crate.volume, crate.mass);
            c.crate = null;
            if (crate.job != null) crate.job.stowed(a.entity, crate, in >= 1 - 1e-6);
            return Status.SUCCEEDED;
        }
    }

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
            if (t == null || eva == null || grid == null || a.pos.ship != a.service.homeOf(a.entity) || stale(a, t)) return Status.FAILED;
            if (route == null || route.task != t || a.time - planned > REPLAN || !Airlock.works(grid, route.port)) {
                if (route != null && route.port.airlock != null) route.port.airlock.release(a.entity);
                route = plan(a, grid, eva.hull(grid), t);
                planned = a.time;
                if (route == null) return giveUp(a, t);
            }
            MountFootprint port = route.port;
            Airlock lock = Airlock.of(grid, port);
            if (lock.user() != null && lock.user() != a.entity || !lock.isIn()) {
                // In use, or standing open for someone outside: asked to cycle in for them, they wait by the hatch
                lock.claim(a.entity, false);
                a.crew.activity = "waiting for the airlock";
                DockingPortLayout.hatchTile(port, tile);
                Side in = DockingPortLayout.facing(port).opposite();
                int wx = tile[0] + in.dx, wy = tile[1] + in.dy;
                if (a.travel(a.pos.ship, (x, y) -> x == wx && y == wy) == CrewAgent.Travel.UNREACHABLE && !a.pos.isMoving()) a.pos.stop();
                return Status.RUNNING;
            }
            lock.claim(a.entity, false);
            DockingPortLayout.chamberTile(port, tile);
            int cx = tile[0], cy = tile[1];
            CrewAgent.Travel go = a.travel(a.pos.ship, (x, y) -> x == cx && y == cy);
            if (go == CrewAgent.Travel.UNREACHABLE) {
                if ((stuck += a.dt) > STUCK) return giveUp(a, t);
                return Status.RUNNING;
            }
            stuck = 0f;
            if (go == CrewAgent.Travel.GOING) {
                a.crew.activity = a.crew.crate != null ? "carrying " + a.crew.crate.name + " to the airlock" : "heading for the airlock";
                return Status.RUNNING;
            }
            a.pos.stop();
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
            HullSurface hull = system.hull(grid);
            ShipTaskBoardComponent board = a.board();
            CrewTask t = held(a);
            if ((backLook -= a.dt) <= 0f) {
                backLook = 2f;
                backSeconds = wayBack(eva, hull, scratch, tile);
            }
            if (eva.towing != null) {
                // Hold of the shipmate they came out for: back in with them
                if (t != null && t.kind == CrewTask.Kind.RETRIEVE && board != null) {
                    board.complete(t);
                    c.task = null;
                    t = null;
                }
                eva.returning = true;
            }
            if (!eva.returning) {
                // Called in by battle stations, unless sent out all the same, or by the player; or their
                // line has parted, and nobody works on untethered
                boolean sent = c.evaOrder == CrewMemberComponent.EvaOrder.OUT;
                boolean back = board == null || board.battleStations && !sent || !mayGoOut(a) || !eva.tethered;
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
                    float workLeft = (workSeconds(c.def, t) - (workingOn == t ? work : worked(t))) / Math.max(0.05f, c.workPace());
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
                eva.chase = null;
                EvaSystem.doorRing(eva.portMount, tile);
                eva.goalX = tile[0];
                eva.goalY = tile[1];
                CrewMemberComponent towed = eva.towing == null ? null : a.service.crew(eva.towing);
                c.activity = towed != null ? "bringing " + towed.name + " back in"
                        : moving(eva, c.crate != null ? "bringing " + c.crate.name + " back in" : "heading back to the airlock");
                return Status.RUNNING;
            }
            if (t.kind == CrewTask.Kind.RETRIEVE) return fetch(a, c, eva, grid, hull, (RetrieveTask) t);
            OutsideTask.Job job = t instanceof OutsideTask ? ((OutsideTask) t).job : null;
            boolean bringing = job != null && c.crate != null && c.crate.job == job;
            if (job != null && job.part() != null && !bringing) {
                // Its part is still inside, for whoever fetches it
                a.service.releaseTask(a.entity);
                return Status.RUNNING;
            }
            workTile(grid, hull, t, tile);
            eva.goalX = tile[0];
            eva.goalY = tile[1];
            if (workingOn != t) {
                workingOn = t;
                work = worked(t);
            }
            if (!eva.atGoal()) {
                c.activity = moving(eva, (bringing ? "carrying " + c.crate.name + " along the hull to " : "crawling along the hull to ") + where(t));
                return Status.RUNNING;
            }
            if (bringing) {
                job.setDown(a.entity, c.crate);
                c.crate = null;
            }
            float dt = a.dt * c.workPace();
            work += dt;
            if (job != null) job.work(a.entity, dt);
            c.record().pendingWork += dt; // engineering experience
            c.activity = doing(t);
            if (work >= workSeconds(c.def, t)) {
                finishWork(a, grid, t);
                board.complete(t);
                c.task = null;
                workingOn = null;
                work = 0f;
            }
            return Status.RUNNING;
        }

        /**
         * Out for a shipmate adrift: along the hull to the ring tile nearest them, then off it to them
         * on the jetpack, until they take hold of them ({@link EvaSystem}).
         */
        private Status fetch(CrewAgent a, CrewMemberComponent c, EvaComponent eva, ShipGridComponent grid, HullSurface hull,
                             RetrieveTask r) {
            String name = a.service.crew(r.castaway).name;
            if (eva.phase == EvaComponent.Phase.FREE) {
                c.activity = eva.chase != null ? "jetting out to " + name : moving(eva, "");
                // The line run out short of them
                if (eva.chase != null && eva.lineOut >= EvaSystem.LINE_MAX - 0.05f && eva.stretch > 0.05f) {
                    eva.chase = null;
                    eva.outOfReach = true;
                }
                return Status.RUNNING;
            }
            workTile(grid, hull, r, tile);
            eva.goalX = tile[0];
            eva.goalY = tile[1];
            if (!eva.atGoal()) {
                c.activity = moving(eva, "crawling along the hull toward " + name);
                return Status.RUNNING;
            }
            eva.chase = r.castaway;
            c.activity = "pushing off the hull to " + name;
            return Status.RUNNING;
        }

        /** What they are doing on their way: crawling, flying, swinging on the line or adrift. */
        private static String moving(EvaComponent eva, String crawling) {
            if (eva.heldBy != null) return "being brought in";
            if (eva.phase == EvaComponent.Phase.ON_HULL) return eva.burnWait ? "waiting for the main engines' burn to end" : crawling;
            if (eva.jetting) return eva.tethered ? "jetting to the hull" : "jetting back to the ship";
            if (eva.climbing) return "pulling in along the tether";
            if (eva.tethered) return "swinging on the tether";
            if (eva.shortOfFuel) return "adrift, waiting to be picked up";
            return eva.waiting ? "adrift, waiting for the ship's burn to end" : "adrift";
        }
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
                RcsThruster.fieldRepair(grid, t.x, t.y);
                break;
            case PATCH_HULL:
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
            default: return t instanceof OutsideTask ? ((OutsideTask) t).job.doing() + " on EVA" : "working on EVA";
        }
    }

    /** What a crew member crawls to for job {@code t}. */
    static String where(CrewTask t) {
        switch (t.kind) {
            case REPAIR_RCS: return "an RCS thruster";
            case PATCH_HULL: return "a holed hull tile";
            case RETRIEVE: return "a shipmate adrift";
            default: return t instanceof OutsideTask ? ((OutsideTask) t).job.where() : "the job";
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

    /** True if (x, y) is in the opening of a docking port whose airlock someone is using: nobody else stands there. */
    public static boolean inAirlock(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        MountFootprint m = t == null ? null : t.mount;
        return m != null && m.kind == MountFootprint.MountKind.PORT && m.airlock != null && m.airlock.user() != null
                && DockingPortLayout.isOpening(m, x, y);
    }
}
