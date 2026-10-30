package com.ringsofiron.aicrew.btree;

import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/**
 * Succeeds while the agent's suit is running low ({@link CrewDef#suitRefillShare}) out of
 * breathable air and the ship has oxygen to fill it from, and on until the suit is full.
 */
public class SuitAirLow extends CrewLeaf {
    @Override
    public Status execute() {
        CrewAgent a = agent();
        CrewMemberComponent c = a.crew;
        if (!c.suited || !c.wearsSuits() || c.held || a.time < a.refillBlockedUntil) return fail(a);
        if (!a.refilling) {
            ShipGridComponent grid = a.grid();
            if (grid == null || TaskRules.isBreathable(grid, a.pos.tileX(), a.pos.tileY())) return fail(a);
            if (c.suitAir >= c.suitCapacity() * c.def.suitRefillShare) return fail(a);
        } else if (c.suitAir >= c.suitCapacity()) {
            return fail(a);
        }
        return CrewTasks.oxygenSupplies(a, null) ? Status.SUCCEEDED : fail(a);
    }

    private static Status fail(CrewAgent a) {
        a.refilling = false;
        return Status.FAILED;
    }
}
