package com.ringsofiron.physicsexterior.components;

import com.badlogic.ashley.core.Component;

/**
 * The pilot's current stick input, written by player input or AI and read by the
 * {@code FlightControlSystem}. All values are in [-1, 1] and scale the matching
 * {@link ThrusterComponent} limit.
 */
public class FlightControlComponent implements Component {
    /** +1 full main engine, -1 full retro thrust. */
    public float forward;
    /** +1 full thrust to port (left), -1 to starboard (right). */
    public float strafe;
    /** +1 full counter-clockwise torque, -1 clockwise. */
    public float turn;
    /**
     * When true and {@link #turn} is 0, RCS fires against any spin to bring the ship to a stop
     * rotationally. Linear motion is never damped.
     */
    public boolean rotationAssist;
    /**
     * Set while something outside the pilot is turning the ship into line (a docking collar
     * drawing it in): rotation assist stands down so it does not fight it. Runtime only.
     */
    public transient boolean assistHeld;

    public void clear() {
        forward = 0f;
        strafe = 0f;
        turn = 0f;
    }
}
