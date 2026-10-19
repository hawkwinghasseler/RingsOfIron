package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * Anyone stops a critically wounded crew member's bleeding where they lie
 * ({@link CrewDef#stabiliseTime}), so the wound no longer kills; it still needs treating
 * ({@link TreatTask}) to get better. Ranks with putting out fires.
 */
public class StabiliseTask extends CrewTask {

    public static final float DEFAULT_PRIORITY = 60f;

    public final Entity patient;

    public StabiliseTask(Entity patient, int x, int y) {
        super(Kind.STABILISE, x, y, DEFAULT_PRIORITY);
        this.patient = patient;
    }

    @Override
    public boolean sameJob(CrewTask other) {
        return other instanceof StabiliseTask && ((StabiliseTask) other).patient == patient;
    }
}
