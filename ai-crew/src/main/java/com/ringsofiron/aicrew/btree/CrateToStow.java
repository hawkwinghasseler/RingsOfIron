package com.ringsofiron.aicrew.btree;

import com.badlogic.gdx.ai.btree.Task.Status;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.aicrew.OutsideTask;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * Succeeds if the agent, aboard and not out on EVA, has a crate in hand that no work of theirs
 * takes outside: a part whose work they put down, or the part the work took off.
 */
public class CrateToStow extends CrewLeaf {
    @Override
    public Status execute() {
        CrewAgent a = agent();
        CrewMemberComponent.Crate crate = a.crew.crate;
        if (crate == null || EvaSystem.of(a.entity) != null) return Status.FAILED;
        CrewTask t = a.crew.task;
        boolean carried = crate.job != null && t instanceof OutsideTask && ((OutsideTask) t).job == crate.job
                && t.isActive() && t.claimedBy == a.entity;
        return carried ? Status.FAILED : Status.SUCCEEDED;
    }
}
