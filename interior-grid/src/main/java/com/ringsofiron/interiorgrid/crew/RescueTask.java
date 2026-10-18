package com.ringsofiron.interiorgrid.crew;

import com.badlogic.ashley.core.Entity;

/**
 * Carry an unconscious crew member to a pressurized, breathable tile. (x, y) is where
 * the casualty lay when the task was posted; the rescuer follows the casualty itself.
 */
public class RescueTask extends CrewTask {

    public static final float DEFAULT_PRIORITY = 90f;

    public final Entity casualty;

    public RescueTask(Entity casualty, int x, int y) {
        super(Kind.RESCUE, x, y, DEFAULT_PRIORITY);
        this.casualty = casualty;
    }

    @Override
    public boolean sameJob(CrewTask other) {
        return other instanceof RescueTask && ((RescueTask) other).casualty == casualty;
    }
}
