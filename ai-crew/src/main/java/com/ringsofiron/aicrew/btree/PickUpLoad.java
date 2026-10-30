package com.ringsofiron.aicrew.btree;

import com.ringsofiron.aicrew.CarryTask;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * Walks to a bay of the carry task's source craft (across a docked link if need be), or to the
 * tile the load waits by ({@link CarryTask#fromTile}), lifts a load off it and takes it in hand. Fails, ending the trip, when nothing more is to go.
 */
public class PickUpLoad extends CrewLeaf {
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
        CarryTask t = (CarryTask) a.crew.task;
        if (a.crew.load != null) return Status.SUCCEEDED;
        if (!t.job.isOpen()) return finish(a, t);
        a.crew.activity = "going for " + t.job.commodity();
        CrewAgent.Travel go = t.fromTile ? a.travel(t.from, CrewTasks.near(a, t.from, t.x, t.y, 1, 1))
                : a.travel(t.from, CrewTasks.beside(a, t.from, anyBay ? null : t.job.commodity(), true));
        if (go == CrewAgent.Travel.UNREACHABLE && !anyBay && !t.fromTile) {
            // No way to the bays it is in: work from whichever bay of the hold can be reached
            anyBay = true;
            go = a.travel(t.from, CrewTasks.beside(a, t.from, null, true));
        }
        if (go == CrewAgent.Travel.UNREACHABLE) {
            a.abandonUnreachable();
            return Status.FAILED;
        }
        if (go == CrewAgent.Travel.GOING) return Status.RUNNING;
        a.crew.activity = "loading " + t.job.commodity();
        working += a.dt;
        if (working < a.crew.def.loadTime) return Status.RUNNING;
        double units = t.job.take(a.crew.def.carryVolume);
        if (units <= 0) return finish(a, t);
        a.crew.load = new CrewMemberComponent.Load(t, units);
        return Status.SUCCEEDED;
    }

    /** Nothing (more) to carry: the trip is over. */
    private Status finish(CrewAgent a, CarryTask t) {
        ShipTaskBoardComponent board = a.board();
        if (board != null) board.cancel(t);
        else t.status = CrewTask.Status.CANCELLED;
        a.crew.task = null;
        a.crew.activity = "idle";
        return Status.FAILED;
    }
}
