package com.ringsofiron.aicrew.btree;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.crew.RescueTask;

/** Lifts the rescue task's casualty, who must be on the agent's tile. */
public class PickUpCasualty extends CrewLeaf {
    @Override
    public Status execute() {
        CrewAgent a = agent();
        Entity casualty = ((RescueTask) a.crew.task).casualty;
        CrewMemberComponent c = a.service.crew(casualty);
        GridPositionComponent cp = a.service.position(casualty);
        if (c == null || c.state != CrewState.UNCONSCIOUS) return Status.FAILED;
        if (c.carriedBy != null && c.carriedBy != a.entity) return Status.FAILED;
        if (cp.tileX() != a.pos.tileX() || cp.tileY() != a.pos.tileY()) return Status.FAILED;
        a.service.pickUp(a.entity, casualty);
        return Status.SUCCEEDED;
    }
}
