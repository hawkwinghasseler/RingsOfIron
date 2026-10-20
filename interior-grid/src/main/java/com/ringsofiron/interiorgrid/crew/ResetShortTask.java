package com.ringsofiron.interiorgrid.crew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.SubsystemComponent;

/**
 * Reset a short circuit (see {@link com.ringsofiron.interiorgrid.DamageStates}): a shorted
 * system, worked from a tile next to its footprint like a repair, or with no system the shorted
 * conduit tile at (x, y), worked from on it or beside it.
 */
public class ResetShortTask extends CrewTask {

    /** Above repairs, so a quick reset gets a system back before anyone starts the long repair; below fires. */
    public static final float DEFAULT_PRIORITY = 50f;
    /** A shorted reactor blacks the ship out: it comes before patching leaks and fighting fires. */
    public static final float REACTOR_PRIORITY = 75f;

    /** The shorted system's entity, or null for a conduit tile. */
    public final Entity subsystem;

    public ResetShortTask(Entity subsystem, int x, int y) {
        super(Kind.RESET_SHORT, x, y, isReactor(subsystem) ? REACTOR_PRIORITY : DEFAULT_PRIORITY);
        this.subsystem = subsystem;
    }

    /** A shorted conduit tile. */
    public ResetShortTask(int x, int y) {
        this(null, x, y);
    }

    private static boolean isReactor(Entity subsystem) {
        SubsystemComponent s = subsystem == null ? null : subsystem.getComponent(SubsystemComponent.class);
        return s != null && s.type == SubsystemComponent.SubsystemType.REACTOR;
    }

    @Override
    public boolean sameJob(CrewTask other) {
        if (!(other instanceof ResetShortTask)) return false;
        ResetShortTask o = (ResetShortTask) other;
        if (subsystem != null || o.subsystem != null) return subsystem == o.subsystem;
        return super.sameJob(other);
    }
}
