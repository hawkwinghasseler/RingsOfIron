package com.ringsofiron.aicrew.btree;

import com.ringsofiron.aicrew.BoardTask;

/**
 * Goes aboard {@link BoardTask#ship} to the task's tile, suited (no one boards a hostile craft
 * without a suit if there is one to be had: a pressure suit from a locker, or an EVA suit), and
 * reports arriving to the job. With no dock to walk across, over on EVA ({@link EvaTasks.Departure}).
 */
public class Board extends CrewLeaf {
    private final EvaTasks.Departure departure = new EvaTasks.Departure();

    @Override
    public void end() {
        departure.reset(getObject());
    }

    @Override
    public Status execute() {
        CrewAgent a = agent();
        BoardTask t = (BoardTask) a.crew.task;
        if (!t.job.isOpen()) return Status.FAILED;
        if (a.pos.ship != t.ship && a.pos.crossing == null && !a.service.joined(a.pos.ship, t.ship)) {
            // No dock between: out of the airlock and across
            a.crew.activity = t.job.what();
            if (departure.step(a, t.ship, true) == Status.RUNNING) return Status.RUNNING;
            a.abandonUnreachable();
            return Status.FAILED;
        }
        if (!a.crew.suited && a.crew.wearsSuits() && a.pos.ship == a.service.homeOf(a.entity)
                && SuitErrand.suitUp(a, true) == Status.RUNNING) {
            if (a.crew.suiting > 0f) a.crew.activity = "suiting up to board";
            return Status.RUNNING;
        }
        a.crew.activity = t.job.what();
        CrewAgent.Travel go = a.travel(t.ship, CrewTasks.near(a, t.ship, t.x, t.y, 1, 1));
        if (go == CrewAgent.Travel.UNREACHABLE) {
            a.abandonUnreachable();
            return Status.FAILED;
        }
        if (go == CrewAgent.Travel.GOING) return Status.RUNNING;
        t.job.arrived(a.entity);
        return Status.SUCCEEDED;
    }
}
