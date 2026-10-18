package com.ringsofiron.aicrew.pilot;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;

/**
 * Tells {@link NpcCombatSystem} about missiles coming at a ship. The game supplies it from
 * combat-sys, which ai-crew does not depend on.
 */
public interface ThreatSensor {
    /**
     * Writes the bubble position and velocity (m, m/s) of the nearest missile homing on
     * {@code ship} into the out vectors.
     *
     * @return false if none is inbound
     */
    boolean nearestInbound(Entity ship, Vector2 outPosition, Vector2 outVelocity);

    ThreatSensor NONE = (ship, p, v) -> false;
}
