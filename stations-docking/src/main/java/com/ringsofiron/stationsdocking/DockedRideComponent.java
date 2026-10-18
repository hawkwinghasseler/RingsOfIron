package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Component;

/**
 * A craft riding along with the one it is docked to while rails warp has the bubble collapsed:
 * it shares that craft's orbit, and goes back where it was docked when the bubble is rebuilt.
 * Exists only during rails warp, when the game cannot be saved.
 */
public class DockedRideComponent implements Component {
    /** The craft it rides with: a station, or the heavier ship. */
    public String leaderUuid;
    /** Its origin in the leader's frame, meters, and its angle less the leader's, radians. */
    public float offsetX, offsetY, angleOffset;

    public DockedRideComponent() {
    }

    public DockedRideComponent(String leaderUuid, float offsetX, float offsetY, float angleOffset) {
        this.leaderUuid = leaderUuid;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.angleOffset = angleOffset;
    }
}
