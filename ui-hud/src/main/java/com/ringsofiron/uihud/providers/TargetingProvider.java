package com.ringsofiron.uihud.providers;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;

/**
 * Fire-control data the HUD shows: which entity the player has locked and where to aim at it.
 * Implemented by combat-sys from its track and firing-solution code; ui-hud ships a
 * {@link SimpleTargetingProvider} so the HUD works before combat-sys is wired in.
 *
 * All positions are bubble meters, the same frame as {@code TransformComponent}.
 */
public interface TargetingProvider {
    /** The entity currently locked by the player's fire control, or null when nothing is locked. */
    Entity getLockedTarget();

    /**
     * Writes the lead point (where to aim so a shot fired now hits the locked target) into
     * {@code out}. Returns false when there is no target or no firing solution, and the HUD
     * then hides the lead reticle.
     */
    boolean getLeadPoint(Vector2 out);

    /** Seconds until a shot fired now reaches the lead point, or {@code Float.NaN} if unknown. */
    float getTimeToImpact();
}
