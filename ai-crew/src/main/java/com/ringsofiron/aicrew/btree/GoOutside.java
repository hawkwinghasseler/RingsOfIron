package com.ringsofiron.aicrew.btree;

import com.badlogic.gdx.ai.btree.Task.Status;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.interiorgrid.Airlock;
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
        if (route == null || route.task != t || a.time - planned > EvaTasks.REPLAN || !Airlock.works(grid, route.port)) {
            if (route != null && route.port.airlock != null) route.port.airlock.release(a.entity);
            route = EvaTasks.plan(a, grid, eva.hull(grid), t);
            planned = a.time;
            if (route == null) return giveUp(a, t);
        }
        MountFootprint port = route.port;
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

    private Status giveUp(CrewAgent a, CrewTask t) {
        a.ignore(t, EvaTasks.NO_ROUTE);
        a.releaseTask();
        return Status.FAILED;
    }
}
