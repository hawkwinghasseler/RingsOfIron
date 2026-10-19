package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;

/**
 * How an NPC ship's crew take a losing fight (see {@link ShipDisablement}). A ship without one
 * surrenders on the defaults. Saved with the ship.
 */
public class MoraleComponent implements Component {
    /** False for a crew who fight on until the ship is disabled under them. */
    public boolean surrenders = true;
    /** Hull integrity (0 to 1) below which they give up. */
    public float surrenderHull = ShipDisablement.SURRENDER_HULL;
    /** Share of their own crew still conscious below which they give up. */
    public float surrenderCrew = ShipDisablement.SURRENDER_CREW;

    public MoraleComponent() {}

    public MoraleComponent(boolean surrenders) {
        this.surrenders = surrenders;
    }
}
