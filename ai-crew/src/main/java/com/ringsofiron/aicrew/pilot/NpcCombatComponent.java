package com.ringsofiron.aicrew.pilot;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.components.UuidComponent;

/**
 * Makes {@link NpcCombatSystem} fight a ship: close to {@link #preferredRange} of
 * {@link #target}, hold that range while circling it with the guns on it, and break
 * across the line of an inbound missile. Replaces the ship's {@link NpcPilotComponent}
 * while it is on. Its {@link #stance} is the captain's plan; whoever picks it sets the range
 * and speeds to go with it.
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

    /** How the captain means to fight. */
    public enum Stance {
        /** Hold the range band and circle the target: the default. */
        HOLD,
        /** Keep well out, at missile range, and let the launchers work. */
        STANDOFF,
        /** Close fast to gun range. */
        RUSH,
        /** Swing round behind the target, where its engines are, and hold there. */
        FLANK,
        /**
         * Keep a long range with the nose on the target, and back straight off when it closes:
         * long guns against short ones. No circling, so the guns keep bearing.
         */
        KITE
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

    public Stance stance = Stance.HOLD;
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
