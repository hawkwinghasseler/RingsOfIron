package com.ringsofiron.interiorgrid.crew;

import com.badlogic.ashley.core.Entity;

/**
 * Isolate a leaking tank: shut its valve so it stops leaking (see
 * {@link com.ringsofiron.interiorgrid.DamageStates#isolate}). Worked from a tile next to its
 * footprint, like a repair.
 */
public class IsolateTankTask extends CrewTask {

    /** Above resets and repairs, below fires: a leaking oxygen tank feeds them. */
    public static final float DEFAULT_PRIORITY = 55f;

    /** The tank's entity (with a SubsystemComponent). */
    public final Entity tank;

    public IsolateTankTask(Entity tank, int x, int y) {
        super(Kind.ISOLATE_TANK, x, y, DEFAULT_PRIORITY);
        this.tank = tank;
    }

    @Override
    public boolean sameJob(CrewTask other) {
        return other instanceof IsolateTankTask && ((IsolateTankTask) other).tank == tank;
    }
}
