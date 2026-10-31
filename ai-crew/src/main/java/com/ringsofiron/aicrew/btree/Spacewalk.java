package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.Task.Status;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.BoardTask;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.EvaComponent;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.aicrew.HullStripTask;
import com.ringsofiron.aicrew.OutsideTask;
import com.ringsofiron.aicrew.RetrieveTask;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.interiorgrid.HullSurface;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * Out on EVA: along the hull to the job, the work, and back in. While still in the airlock's
 * chamber, on the way out or back, they wait for it. A crew member out cold is the line's to bring
 * in ({@link EvaSystem}); this runs only while they are conscious.
 */
public class Spacewalk extends CrewLeaf {
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
        CrewTask t = EvaTasks.held(a);
        if ((backLook -= a.dt) <= 0f) {
            backLook = 2f;
            backSeconds = EvaTasks.wayBack(eva, hull, scratch, tile);
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
        if (!eva.returning) t = jobOrBack(a, c, eva, grid, hull, board, t);
        if (eva.returning) return headBack(a, c, eva);
        if (t.kind == CrewTask.Kind.RETRIEVE) return fetch(a, c, eva, grid, hull, (RetrieveTask) t);
        return workAtJob(a, c, eva, grid, hull, board, t);
    }

    /**
     * The job to work on next: the one in hand while it still wants doing, or the next within reach.
     * None, and {@link EvaComponent#returning} set, when they are called in by battle stations
     * (unless sent out all the same) or by the player, their line has parted, nothing is left out
     * here for them, or their air is down to the way back, {@link EvaTasks#AIR_MARGIN} and the work.
     */
    private CrewTask jobOrBack(CrewAgent a, CrewMemberComponent c, EvaComponent eva, ShipGridComponent grid, HullSurface hull,
                               ShipTaskBoardComponent board, CrewTask t) {
        // Called in by battle stations, unless sent out all the same, or by the player; or their
        // line has parted, and nobody works on untethered
        boolean sent = c.evaOrder == CrewMemberComponent.EvaOrder.OUT;
        boolean back = board == null || board.battleStations && !sent || !EvaTasks.mayGoOut(a) || !eva.tethered;
        if (!back && t != null && (!TaskRules.isStillNeeded(t, grid, a.service) || !EvaTasks.workTile(grid, hull, t, tile))) {
            a.service.releaseTask(a.entity);
            t = null;
        }
        if (!back && t != null && eva.outOfReach) {
            // The line runs out, or the hull has no way round, short of it
            a.ignore(t, EvaTasks.NO_ROUTE);
            a.service.releaseTask(a.entity);
            t = null;
        }
        eva.outOfReach = false;
        if (!back && t == null) t = EvaTasks.next(a, eva, grid, hull, board);
        if (t == null) back = true;
        if (!back) {
            float workLeft = (EvaTasks.workSeconds(c.def, t) - (workingOn == t ? work : EvaTasks.worked(t))) / Math.max(0.05f, c.workPace());
            back = c.suitAir < backSeconds + EvaTasks.AIR_MARGIN + workLeft;
        }
        if (back) {
            if (c.task != null) a.service.releaseTask(a.entity);
            eva.returning = true;
        }
        return t;
    }

    /** Back along the hull to the airlock's door, the work left where it is. */
    private Status headBack(CrewAgent a, CrewMemberComponent c, EvaComponent eva) {
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

    /** Along the hull to the job, with its part if it takes one, and the work there until it is done. */
    private Status workAtJob(CrewAgent a, CrewMemberComponent c, EvaComponent eva, ShipGridComponent grid, HullSurface hull,
                             ShipTaskBoardComponent board, CrewTask t) {
        OutsideTask.Job job = t instanceof OutsideTask ? ((OutsideTask) t).job : null;
        boolean bringing = job != null && c.crate != null && c.crate.job == job;
        if (job != null && job.part() != null && !bringing) {
            // Its part is still inside, for whoever fetches it
            a.service.releaseTask(a.entity);
            return Status.RUNNING;
        }
        EvaTasks.workTile(grid, hull, t, tile);
        eva.goalX = tile[0];
        eva.goalY = tile[1];
        if (workingOn != t) {
            workingOn = t;
            work = EvaTasks.worked(t);
        }
        if (!eva.atGoal()) {
            c.activity = moving(eva, (bringing ? "carrying " + c.crate.name + " along the hull to " : "crawling along the hull to ") + EvaTasks.where(t));
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
        c.activity = EvaTasks.doing(t);
        c.showProgress(work, EvaTasks.workSeconds(c.def, t));
        if (work >= EvaTasks.workSeconds(c.def, t)) {
            EvaTasks.finishWork(a, grid, t);
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
        EvaTasks.workTile(grid, hull, r, tile);
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

    /**
     * A step of crossing: over their hull to the side facing the craft they cross to and off it;
     * then across, onto its hull and along it to the port they go in by, its door forced first if
     * they are boarding. A boarder whose order is gone goes back across home.
     */
    private Status crossing(CrewAgent a, CrewMemberComponent c, EvaComponent eva, EvaSystem system) {
        Entity home = a.service.homeOf(a.entity);
        BoardTask order = EvaTasks.boardOrder(a);
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
        if (eva.crossTo == null && a.pos.ship != home && !eva.breaking) {
            // Over on another craft's hull for a part off it: the work there, then home with it
            HullStripTask strip = EvaTasks.stripOrder(a);
            if (strip != null && strip.craft == a.pos.ship && c.crate == null) {
                eva.salvaging = true;
                Status s = stripping(a, c, eva, system, strip);
                if (s != null) return s;
            }
            if (eva.salvaging) {
                if (EvaTasks.stripOrder(a) != null) a.service.releaseTask(a.entity);
                if (!homeward(a, eva, system, home)) {
                    c.activity = "adrift";
                    return Status.RUNNING;
                }
                eva.salvaging = false;
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
                c.activity = "jetting across to " + EvaTasks.craft(a, to);
                return Status.RUNNING;
            }
            c.activity = moving(eva, (c.crate != null ? "carrying " + c.crate.name + " over the hull" : "crawling over the hull")
                    + " to cross to " + EvaTasks.craft(a, to));
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
        if (eva.phase == EvaComponent.Phase.FREE && !eva.fell) c.activity = "jetting across to " + EvaTasks.craft(a, a.pos.ship);
        else c.activity = moving(eva, c.crate != null ? "bringing " + c.crate.name + " back in" : "crawling along the hull to the airlock");
        return Status.RUNNING;
    }

    /**
     * A step of work on the hull of the craft they crossed to ({@link HullStripTask}): along it to
     * the work, the work there, and once it is done, the part's crate in hand. Null when they are to
     * head back across home: the work done, called off (by battle stations, the player, or its poster),
     * out of reach, or their air down to {@link EvaTasks#AIR_MARGIN} and the work left.
     */
    private Status stripping(CrewAgent a, CrewMemberComponent c, EvaComponent eva, EvaSystem system, HullStripTask t) {
        ShipGridComponent grid = a.grid();
        HullSurface hull = system.hull(grid);
        ShipTaskBoardComponent board = a.board();
        boolean sent = c.evaOrder == CrewMemberComponent.EvaOrder.OUT;
        boolean called = board == null || board.battleStations && !sent || !EvaTasks.mayGoOut(a);
        if (!called && (eva.outOfReach || !t.job.workTile(hull, tile))) {
            // No way along this hull to it
            a.ignore(t, EvaTasks.NO_ROUTE);
            called = true;
        }
        eva.outOfReach = false;
        if (workingOn != t) {
            workingOn = t;
            work = t.job.worked();
        }
        float seconds = t.job.seconds(c.def);
        if (!called) called = c.suitAir < EvaTasks.AIR_MARGIN + (seconds - work) / Math.max(0.05f, c.workPace());
        if (called || !t.job.isOpen()) {
            workingOn = null;
            work = 0f;
            return null;
        }
        eva.returning = false;
        eva.goalX = tile[0];
        eva.goalY = tile[1];
        if (!eva.atGoal()) {
            c.activity = moving(eva, "crawling along the hull of the other craft to " + t.job.where());
            return Status.RUNNING;
        }
        float dt = a.dt * c.workPace();
        work += dt;
        t.job.work(a.entity, dt);
        c.record().pendingWork += dt; // engineering experience
        c.activity = EvaTasks.doing(t);
        c.showProgress(work, seconds);
        if (work < seconds) return Status.RUNNING;
        CrewMemberComponent.Crate crate = t.job.takeOff(a.entity);
        if (board != null) board.complete(t);
        c.task = null;
        c.crate = crate;
        workingOn = null;
        work = 0f;
        return null;
    }

    /** Sets {@code eva} to go back across home from where they are; false if there is no way in there. */
    private static boolean homeward(CrewAgent a, EvaComponent eva, EvaSystem system, Entity home) {
        ShipGridComponent grid = a.service.grid(home);
        MountFootprint port = grid == null ? null : EvaTasks.entryPort(a, system, grid, home, a.pos.ship);
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
        if (eva.heldBy != null) return "being brought in";
        if (eva.phase == EvaComponent.Phase.ON_HULL) return eva.burnWait ? "waiting for the main engines' burn to end" : crawling;
        if (eva.jetting) return eva.tethered ? "jetting to the hull" : "jetting back to the ship";
        if (eva.climbing) return "pulling in along the tether";
        if (eva.tethered) return "swinging on the tether";
        if (eva.shortOfFuel) return "adrift, waiting to be picked up";
        return eva.waiting ? "adrift, waiting for the ship's burn to end" : "adrift";
    }
}
