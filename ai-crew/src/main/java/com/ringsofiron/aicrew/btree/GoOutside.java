package com.ringsofiron.aicrew.btree;

import com.badlogic.gdx.ai.btree.Task.Status;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.interiorgrid.Airlock;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/** Walks to the airlock the job is best reached from, waits for it, and goes out through it. */
public class GoOutside extends CrewLeaf {
    private EvaRoute route;
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
        if (t == null || eva == null || grid == null || a.pos.ship != a.service.homeOf(a.entity) || EvaTasks.stale(a, t)) return Status.FAILED;
        if (route == null || route.task != t || a.time - planned > EvaTasks.REPLAN
                || !Airlock.works(grid, route.port) && !EvaTasks.mayCutOut(grid, route.port)) {
            if (route != null && route.port.airlock != null) route.port.airlock.release(a.entity);
            route = EvaTasks.plan(a, grid, eva.hull(grid), t);
            planned = a.time;
            if (route == null) return giveUp(a, t);
        }
        MountFootprint port = route.port;
        if (!Airlock.works(grid, port)) return cutOut(a, t, grid, port);
        CrewAgent.Travel go = EvaTasks.toChamber(a, grid, port, tile);
        if (go == CrewAgent.Travel.UNREACHABLE) {
            if ((stuck += a.dt) > EvaTasks.STUCK) return giveUp(a, t);
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

    /**
     * No way out but to cut {@code port} open ({@link EvaTasks#mayCutOut}), suited: from the deck
     * just inside its hatch while one stands, then from the chamber, each door or hatch in turn
     * ({@link EvaTasks#CUT_SECONDS} apiece), till it is a hole they go out of.
     */
    private Status cutOut(CrewAgent a, CrewTask t, ShipGridComponent grid, MountFootprint port) {
        boolean hatch = false;
        Side in = DockingPortLayout.facing(port).opposite();
        DockingPortLayout.hatchTile(port, tile);
        EdgeWall next = Airlock.nextToCut(grid, port);
        EdgeWall h = grid.getEdge(tile[0], tile[1], in);
        if (next != null && next == h) hatch = true;
        int wx, wy;
        if (hatch) {
            wx = tile[0] + in.dx;
            wy = tile[1] + in.dy;
        } else {
            DockingPortLayout.chamberTile(port, tile);
            wx = tile[0];
            wy = tile[1];
        }
        CrewAgent.Travel go = a.travel(a.pos.ship, (x, y) -> x == wx && y == wy);
        if (go == CrewAgent.Travel.UNREACHABLE) {
            if ((stuck += a.dt) > EvaTasks.STUCK) return giveUp(a, t);
            return Status.RUNNING;
        }
        stuck = 0f;
        if (go == CrewAgent.Travel.GOING) {
            a.crew.activity = "heading for the airlock to cut it open";
            return Status.RUNNING;
        }
        a.pos.stop();
        a.crew.activity = "cutting the airlock open";
        // The work done stands on the airlock, for whoever carries on with it
        Airlock lock = Airlock.of(grid, port);
        lock.cutting += a.dt * a.crew.workPace();
        a.crew.showProgress(lock.cutting, EvaTasks.CUT_SECONDS);
        if (lock.cutting >= EvaTasks.CUT_SECONDS) {
            lock.cutting = 0f;
            Airlock.cutThrough(grid, port);
        }
        return Status.RUNNING;
    }

    private Status giveUp(CrewAgent a, CrewTask t) {
        a.ignore(t, EvaTasks.NO_ROUTE);
        a.releaseTask();
        return Status.FAILED;
    }
}
