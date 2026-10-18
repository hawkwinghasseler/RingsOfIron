package com.ringsofiron.aicrew.pilot;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.components.UuidComponent;

/**
 * Makes {@link NpcCombatSystem} fight a ship: close to {@link #preferredRange} of
 * {@link #target}, hold that range while circling it with the guns on it, and break
 * across the line of an inbound missile. Replaces the ship's {@link NpcPilotComponent}
 * while it is on.
 */
public class NpcCombatComponent implements Component {

    public enum Mode {
        /** No target, or the target is gone: coast with rotation assist. */
        IDLE,
        /** Closing on or opening to the preferred range. */
        CLOSING,
        /** In the range band, circling the target. */
        HOLDING,
        /** Breaking across an inbound missile's line. */
        EVADING
    }

    /** The ship being fought. Not saved; {@link #targetRef} is. */
    public transient Entity target;
    public EntityRef targetRef;

    /** Distance to fight at, m. */
    public float preferredRange = 1500f;
    /** Speed toward or away from the target while correcting range, m/s. */
    public float maxClosingSpeed = 80f;
    /** Range error that gets the full closing speed, m. */
    public float rangeGain = 0.1f;
    /** Sideways speed around the target while holding range, so it is not a sitting duck, m/s. */
    public float circleSpeed = 25f;
    /** An inbound missile inside this distance is evaded, m. */
    public float evadeRange = 3000f;

    public Mode mode = Mode.IDLE;
    /** +1 or -1: which way the ship circles and breaks. */
    public float side = 1f;

    public NpcCombatComponent() {}

    public NpcCombatComponent(Entity target) {
        setTarget(target);
    }

    /** Sets the target, keeping {@link #targetRef} in step. */
    public void setTarget(Entity target) {
        this.target = target;
        targetRef = target != null && target.getComponent(UuidComponent.class) != null
                ? EntityRef.of(target) : null;
    }
}
