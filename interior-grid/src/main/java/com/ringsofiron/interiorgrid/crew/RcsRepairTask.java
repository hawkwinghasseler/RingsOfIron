package com.ringsofiron.interiorgrid.crew;

import com.ringsofiron.interiorgrid.RcsThruster;

/**
 * Bring the RCS block on wrecked hull tile (x, y) back into service from outside: a crew member
 * gets into an EVA suit, cycles out through a docking port's airlock, crawls (or jets) along the
 * hull to the block, frames the tile back up ({@link RcsThruster#fieldRepair}) and comes back in.
 * It waits for the fight to be over.
 */
public class RcsRepairTask extends CrewTask {

    public static final float DEFAULT_PRIORITY = 35f;

    public RcsRepairTask(int x, int y) {
        super(Kind.REPAIR_RCS, x, y, DEFAULT_PRIORITY);
    }
}
