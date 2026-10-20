package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;

/**
 * A person picked up from an escape pod for a rescue ({@link ContractBoard}): carried aboard the
 * player's ship in a berth, held out of the crew's work and fights, until handed over at a station.
 * Saved with the crew entity.
 */
public class SurvivorComponent implements Component {
    /** The rescue contract they were picked up for. */
    public long contract;
    /** The pod they came from, for notices. */
    public String from = "";
}
