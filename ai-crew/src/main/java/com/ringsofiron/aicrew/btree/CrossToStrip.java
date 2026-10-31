package com.ringsofiron.aicrew.btree;

import com.ringsofiron.aicrew.HullStripTask;

/**
 * Sets out for work on another craft's hull ({@link HullStripTask}): into an EVA suit, out of the
 * airlock nearest it and across ({@link Departure}). Once out, {@link Spacewalk} has them: onto its
 * hull, along it to the work, and home again with the part.
 */
public class CrossToStrip extends CrewLeaf {
    private final Departure departure = new Departure();

    @Override
    public void end() {
        departure.reset(getObject());
    }

    @Override
    public Status execute() {
        CrewAgent a = agent();
        HullStripTask t = (HullStripTask) a.crew.task;
        if (EvaTasks.stale(a, t)) return Status.FAILED;
        if (a.pos.ship != a.service.homeOf(a.entity)) {
            // Set out from home only
            a.releaseTask();
            return Status.FAILED;
        }
        a.crew.activity = "setting out to " + t.job.doing();
        if (departure.step(a, t.craft, false) == Status.RUNNING) return Status.RUNNING;
        a.ignore(t, EvaTasks.NO_ROUTE);
        a.releaseTask();
        return Status.FAILED;
    }
}
