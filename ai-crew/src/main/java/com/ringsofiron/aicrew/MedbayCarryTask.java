package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.crew.RescueTask;

/**
 * Once a fight is over, carry a casualty who is down on a safe tile to a free bed in the medbay
 * ({@link TaskRules#isMedbay}), where treatment runs faster ({@link CrewDef#MEDBAY_CARE}). A
 * rescue of its own kind: the same job as any rescue of that casualty, so the two never double up.
 */
public class MedbayCarryTask extends RescueTask {

    /** Above treating where they lie, so the casualty is moved before the medic settles in. */
    public static final float DEFAULT_PRIORITY = 55f;

    public MedbayCarryTask(Entity casualty, int x, int y) {
        super(casualty, x, y);
        priority = DEFAULT_PRIORITY;
    }
}
