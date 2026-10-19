package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;
import com.ringsofiron.moddingdata.defs.ChassisDef;

/**
 * The interior layout a station yard rebuilt a ship to ({@link ShipRefit}), where it differs from
 * its chassis in the data. Saved with the ship, so the ship editor opens what the ship is built to.
 */
public class ShipLayoutComponent implements Component {
    public ChassisDef layout;
}
