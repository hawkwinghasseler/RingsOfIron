package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.Task.Status;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.OutsideTask;

/**
 * For work outside that needs a part ({@link OutsideTask.Job#part}): to a bay of the hold that
 * has it, and its crate lifted out ({@link CrewDef#loadTime}) to carry out. Succeeds at once for
 * work that needs none, or with its crate in hand already.
 */
public class FetchPart extends CrewLeaf {
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
        if (c.crate != null || EvaTasks.stale(a, c.task) || a.pos.ship != ship) {
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
            a.ignore(c.task, EvaTasks.NO_ROUTE);
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
