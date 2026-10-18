package com.ringsofiron.aicrew.pilot;

/**
 * An orbital burn for an NPC pilot to fly: at sim second {@link #time}, change velocity by
 * {@link #prograde} m/s along the direction of travel and {@link #radial} m/s away from the
 * parent body. These are the cancelled rails burns macro-sim hands over when a ship
 * enters the bubble (its {@code RailsModel.Burn} has the same three fields).
 */
public final class PilotBurn {
    public final double time;
    public final double prograde;
    public final double radial;

    /** For loading saves. */
    private PilotBurn() {
        this(0, 0, 0);
    }

    public PilotBurn(double time, double prograde, double radial) {
        this.time = time;
        this.prograde = prograde;
        this.radial = radial;
    }

    @Override
    public String toString() {
        return String.format("burn@%.0fs(pro %.1f, rad %.1f)", time, prograde, radial);
    }
}
