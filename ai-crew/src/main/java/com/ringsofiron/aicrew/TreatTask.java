package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * A medic tends a wounded crew member: one down on a safe tile, speeding recovery, or (round 7)
 * one on their feet with a light wound, which anyone with nothing else to do may treat, the
 * wounded among them ({@link #LIGHT_PRIORITY}).
 */
public class TreatTask extends CrewTask {

    public static final float DEFAULT_PRIORITY = 50f;
    /** A light wound on someone still on their feet: below every other task but manning a console. */
    public static final float LIGHT_PRIORITY = 5f;

    public final Entity patient;

    public TreatTask(Entity patient, int x, int y) {
        this(patient, x, y, DEFAULT_PRIORITY);
    }

    public TreatTask(Entity patient, int x, int y, float priority) {
        super(Kind.TREAT, x, y, priority);
        this.patient = patient;
    }

    /** True for treatment of a light wound on someone on their feet: the job that waits for nothing else. */
    public static boolean isLight(CrewTask task, CrewService crew) {
        if (!(task instanceof TreatTask)) return false;
        CrewMemberComponent p = crew.crew(((TreatTask) task).patient);
        return p != null && p.isConscious() && p.wound == Wound.LIGHT;
    }

    @Override
    public boolean sameJob(CrewTask other) {
        return other instanceof TreatTask && ((TreatTask) other).patient == patient;
    }
}
