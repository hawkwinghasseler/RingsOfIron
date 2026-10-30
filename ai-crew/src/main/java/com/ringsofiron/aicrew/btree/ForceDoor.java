package com.ringsofiron.aicrew.btree;

import com.ringsofiron.aicrew.ForceDoorTask;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.nav.GridNavGraph;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/** Walks to the spot a door is forced from and works at it until it opens. */
public class ForceDoor extends CrewLeaf {
    @Override
    public Status execute() {
        CrewAgent a = agent();
        ForceDoorTask t = (ForceDoorTask) a.crew.task;
        if (!t.job.isOpen()) {
            ShipTaskBoardComponent board = a.board();
            if (board != null) board.cancel(t);
            a.crew.task = null;
            return Status.FAILED;
        }
        a.crew.activity = "going to " + t.job.what();
        ShipGridComponent grid = a.service.grid(t.ship);
        if (grid == null) return Status.FAILED;
        GridNavGraph nav = a.service.pathfinder(grid).graph;
        CrewAgent.Travel go = a.travel(t.ship, (x, y) -> x == t.x && y == t.y && nav.isWalkable(x, y));
        if (go == CrewAgent.Travel.UNREACHABLE) {
            a.abandonUnreachable();
            return Status.FAILED;
        }
        if (go == CrewAgent.Travel.GOING) return Status.RUNNING;
        a.crew.activity = t.job.what();
        float dt = a.dt * a.crew.workPace();
        a.crew.record().pendingWork += dt;
        boolean open = t.job.work(a.entity, dt);
        a.crew.progress = t.job.progress(a.entity);
        return open ? Status.SUCCEEDED : Status.RUNNING;
    }
}
