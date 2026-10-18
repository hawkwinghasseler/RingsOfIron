package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/** A medic tends an unconscious crew member lying on a safe tile, speeding recovery. */
public class TreatTask extends CrewTask {

    public static final float DEFAULT_PRIORITY = 50f;

    public final Entity patient;

    public TreatTask(Entity patient, int x, int y) {
        super(Kind.TREAT, x, y, DEFAULT_PRIORITY);
        this.patient = patient;
    }

    @Override
    public boolean sameJob(CrewTask other) {
        return other instanceof TreatTask && ((TreatTask) other).patient == patient;
    }
}
