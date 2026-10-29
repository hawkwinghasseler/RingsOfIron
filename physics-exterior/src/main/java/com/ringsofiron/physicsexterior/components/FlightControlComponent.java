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
    /**
     * Most torque rotation assist may put on the ship, N*m: set while it is welded to another craft,
     * so stopping the pair's spin doesn't tear the latch between them. Runtime only.
     */
    public transient float assistLimit = Float.POSITIVE_INFINITY;
    /**
     * How hard rotation assist is turning against the spin, 0 to 1 of the RCS torque, as of the
     * last physics tick; 0 while the pilot turns or assist is off. Written by the flight control
     * system so the RCS can be heard and seen when it fires on its own. Runtime only.
     */
    public transient float assistTurn;
    /**
     * A body welded to this ship that it tows, or null: the engines' thrust is put through the pair's
     * common centre of mass, as a towing rig is set up to, so the pair flies straight instead of
     * spinning about the weld, and rotation assist works against the pair's spin. Runtime only.
     */
    public transient com.badlogic.gdx.physics.box2d.Body towing;

    public void clear() {
        forward = 0f;
        strafe = 0f;
        turn = 0f;
    }
}
