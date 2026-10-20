package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;

/**
 * On the player's ship: an accepted {@link PlayerTransfer} leg still to be carried on, so the rest
 * (capture, then on to the target) is proposed once the ship reaches the next sphere of influence.
 * Saved with the ship.
 */
public class TransferLegComponent implements Component {
    /** The body whose sphere of influence the leg goes on in, or null for none. */
    public String continueIn;
    /** The target the leg goes on to. */
    public String continueTo;
}
