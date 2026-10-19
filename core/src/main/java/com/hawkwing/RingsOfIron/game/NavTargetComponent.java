package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;

/** On the player's ship: what it navigates to ({@link NavTarget}), by uuid. Saved with the ship. */
public class NavTargetComponent implements Component {
    /** The station or craft, or null for none. */
    public String uuid;
}
