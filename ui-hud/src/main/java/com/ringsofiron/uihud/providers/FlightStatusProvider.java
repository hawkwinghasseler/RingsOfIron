package com.ringsofiron.uihud.providers;

/**
 * How the player's ship is flying, as numbers for a flight instrument: its speed against the frame
 * that matters now, what its engines are doing, and how much it can still change its velocity.
 * Filled once a frame by the game wiring layer from the ship's body, thrusters, stick and propellant.
 */
public interface FlightStatusProvider {
    /** Fills {@code out}, already cleared; leaves {@link FlightStatus#present} false with no ship. */
    void read(FlightStatus out);

    /**
     * What the speed is measured against, first that applies: the locked target; the nav target
     * when it is within {@link FlightStatus#NAV_FRAME_RANGE}; the nearest station within
     * {@link FlightStatus#STATION_FRAME_RANGE}; else the body the ship orbits.
     */
    enum Frame {
        NONE, TARGET, NAV, STATION, ORBIT
    }

    final class FlightStatus {
        /** How close the nav target must be to become the frame, m. */
        public static final float NAV_FRAME_RANGE = 5_000f;
        /** How close a station must be to become the frame, m. */
        public static final float STATION_FRAME_RANGE = 20_000f;

        public boolean present;

        public Frame frame = Frame.NONE;
        /** The frame's name: the target's or station's label, the body's name ("Ceres"), or "". */
        public String frameName = "";
        /** Distance to the frame's object, m: range to the target or station, altitude over the body. NaN unknown. */
        public float frameDistance = Float.NaN;
        /** Speed against the frame, m/s. */
        public float speed = Float.NaN;
        /** Its part along the line to the frame's object, m/s, + closing (NaN for the orbit frame). */
        public float closing = Float.NaN;
        /** Its part across that line, m/s, + to port of the line (NaN for the orbit frame). */
        public float lateral = Float.NaN;
        /** Velocity against the frame in the bubble's axes, m/s (for a velocity marker). */
        public float vx, vy;

        /** Nose heading in the bubble's axes, degrees counter-clockwise from +x, 0 to 360. */
        public float heading = Float.NaN;
        /** Turn rate, degrees per second, + counter-clockwise. */
        public float spin = Float.NaN;

        /** Delta-v the propellant aboard can still make, m/s; infinite for free thrust, NaN unknown. */
        public double deltaVLeft = Double.NaN;
        /** Delta-v to the nearest station that would take the ship, m/s; 0 docked at one, NaN when none can be reached. */
        public double deltaVHome = Double.NaN;
        /** Delta-v the planned burns (or the transfer proposal) need, m/s; 0 with none. */
        public double deltaVPlanned;
        /** Mass, kg, and what the main engine gives at full thrust, m/s². NaN unknown. */
        public float mass = Float.NaN, acceleration = Float.NaN;

        /** The stick this frame, -1 to 1: main engine (+ forward, − retro), strafe (+ port), turn (+ counter-clockwise). */
        public float forward, strafe, turn;
        /** The main engine is thrusting forward. */
        public boolean mainEngine;
        /** The RCS is firing: retro, strafe, a turn, or rotation assist stopping a spin. */
        public boolean rcs;
        /** Rotation assist is on; held while something else turns the ship (a docking collar drawing it in). */
        public boolean rotationAssist, assistHeld;
        /** The propellant is gone. */
        public boolean outOfPropellant;
        /** The autopilot flies the ship. */
        public boolean autopilot;

        public void clear() {
            present = false;
            frame = Frame.NONE;
            frameName = "";
            frameDistance = Float.NaN;
            speed = closing = lateral = Float.NaN;
            vx = vy = 0f;
            heading = spin = Float.NaN;
            deltaVLeft = deltaVHome = Double.NaN;
            deltaVPlanned = 0;
            mass = acceleration = Float.NaN;
            forward = strafe = turn = 0f;
            mainEngine = rcs = rotationAssist = assistHeld = outOfPropellant = autopilot = false;
        }
    }
}
