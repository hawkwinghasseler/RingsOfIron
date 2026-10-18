package com.ringsofiron.interiorgrid.crew;

import com.badlogic.ashley.core.Entity;

/**
 * Repair a damaged ship system back to full health. (x, y) is any tile of its mount
 * footprint; crew work it from a tile next to the footprint.
 */
public class RepairTask extends CrewTask {

    public static final float DEFAULT_PRIORITY = 40f;

    /** The subsystem entity (with a SubsystemComponent) to repair. */
    public final Entity subsystem;

    public RepairTask(Entity subsystem, int x, int y) {
        super(Kind.REPAIR, x, y, DEFAULT_PRIORITY);
        this.subsystem = subsystem;
    }

    @Override
    public boolean sameJob(CrewTask other) {
        if (!(other instanceof RepairTask)) return false;
        RepairTask o = (RepairTask) other;
        return subsystem != null ? subsystem == o.subsystem : super.sameJob(other);
    }
}
