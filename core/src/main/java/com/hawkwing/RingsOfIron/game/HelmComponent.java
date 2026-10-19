package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;

/**
 * On a ship the player commands: the attitude its helm holds while the player is away from it, and
 * the player's own rotation assist setting, which a helmsman's (or nobody's) replaces meanwhile. See
 * {@link Bridge}. Saved with the ship.
 */
public class HelmComponent implements Component {
    /** Radians: the ship's heading when the player last had the helm. */
    public float heldAngle;
    /** False until the player has had the helm once; until then a helmsman holds the current heading. */
    public boolean holding;
    /** The player's rotation assist (X), given back when they take the helm again. */
    public boolean playerAssist = true;
    /** Whether the player had the helm last frame. */
    public boolean playerHad = true;
}
