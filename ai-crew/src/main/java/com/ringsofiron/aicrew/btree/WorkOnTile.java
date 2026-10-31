package com.ringsofiron.aicrew.btree;

import com.ringsofiron.aicrew.YardTask;

/**
 * Walks onto {@link YardTask#ship} to the task's tile (or beside it, for a tile no one can
 * stand on) and works it until its job says it is done.
 */
public class WorkOnTile extends CrewLeaf {
    @Override
    public Status execute() {
        CrewAgent a = agent();
        YardTask t = (YardTask) a.crew.task;
        if (!t.job.isOpen()) return Status.FAILED;
        a.crew.activity = "going to " + t.job.what();
        CrewAgent.Travel go = a.travel(t.ship, CrewTasks.near(a, t.ship, t.x, t.y, t.w, t.h));
        if (go == CrewAgent.Travel.UNREACHABLE) {
            t.job.unreachable(a.entity);
            a.abandonUnreachable();
            return Status.FAILED;
        }
        if (go == CrewAgent.Travel.GOING) return Status.RUNNING;
        a.crew.activity = t.job.what();
        float dt = a.dt * a.crew.workPace();
        a.crew.record().pendingWork += dt;
        boolean done = t.job.work(a.crew.def.repairRate * dt);
        a.crew.progress = t.job.progress();
        return done ? Status.SUCCEEDED : Status.RUNNING;
    }
}
