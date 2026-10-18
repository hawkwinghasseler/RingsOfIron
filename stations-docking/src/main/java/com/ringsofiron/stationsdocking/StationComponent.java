package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Component;

/**
 * Marks a station: a walkable grid that stays on rails. In the bubble it is a kinematic body
 * following its orbit ({@link StationBodies}), never a dynamic one, so it cannot be pushed.
 */
public class StationComponent implements Component {
    public String name = "Station";
    /** The {@code station_type} def it was built from. */
    public String typeId;
    /** The chassis blueprint its grid came from. */
    public String blueprint;
    /** Heading in radians; stations hold it. */
    public float angle;
    /**
     * Gives docking clearance to anyone who asks: its active ports arm for them. False refuses
     * (no standing system yet decides this per craft).
     */
    public boolean grantsClearance = true;

    public StationComponent() {
    }

    public StationComponent(String name, String typeId, String blueprint) {
        this.name = name;
        this.typeId = typeId;
        this.blueprint = blueprint;
    }
}
