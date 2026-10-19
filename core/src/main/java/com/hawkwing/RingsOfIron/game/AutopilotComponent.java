package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;

/**
 * On the player's ship: the autopilot is flying a planned transfer to {@link #targetUuid}
 * ({@link Autopilot}). Saved with the ship.
 */
public class AutopilotComponent implements Component {
    /** Where the transfer goes. */
    public String targetUuid;
    /** Holding at 1x for the player's word (something came close, a guard dropped warp), and why. */
    public boolean held;
    public String holdReason;
}
