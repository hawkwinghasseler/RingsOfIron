package com.ringsofiron.aicrew.btree;

import com.badlogic.gdx.ai.btree.Task.Status;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.Suit;

/** Gets the agent into an EVA suit with air enough to go out in, out of any other suit first. */
public class SuitUpForEva extends CrewLeaf {
    @Override
    public Status execute() {
        CrewAgent a = agent();
        CrewMemberComponent c = a.crew;
        CrewTask t = c.task;
        if (t == null || EvaTasks.stale(a, t)) return Status.FAILED;
        if (EvaTasks.fitToGoOut(c)) {
            c.suiting = 0f;
            return Status.SUCCEEDED;
        }
        // Out of the pressure suit (or a spent EVA suit) first, into its locker
        Status s = c.suited ? SuitErrand.stow(a) : SuitErrand.suitUpEva(a, EvaTasks::fitToGoOut);
        if (s == Status.FAILED) {
            a.ignore(t, EvaTasks.NO_ROUTE);
            a.releaseTask();
            return Status.FAILED;
        }
        return c.suited && c.suitKind == Suit.Kind.EVA && s == Status.SUCCEEDED ? Status.SUCCEEDED : Status.RUNNING;
    }
}
