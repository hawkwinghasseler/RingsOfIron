package com.ringsofiron.aicrew.pilot;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayDeque;

/**
 * An AI pilot flying a ship in the bubble through its {@code FlightControlComponent}.
 *
 * It flies its {@link #burns} in time order. With none left it either seeks
 * {@link #seekTarget} (arriving at rest) or, by default, departs: it accelerates
 * straight away from the bubble origin to {@link #departSpeed} and coasts until
 * macro-sim moves the ship back onto rails at the bubble's edge.
 */
public class NpcPilotComponent implements Component {

    public enum Mode {
        /** Coasting until the next burn is near. */
        WAITING,
        /** Turning to the next burn's direction ahead of its time. */
        ALIGNING,
        /** Firing a burn. */
        BURNING,
        /** Flying to {@link #seekTarget}. */
        SEEKING,
        /** Leaving the bubble. */
        DEPARTING,
        /** Nothing to do: holding attitude. */
        COASTING
    }

    public final ArrayDeque<PilotBurn> burns = new ArrayDeque<>();
    /** In-bubble point to fly to once the burns are done, m; null to depart instead. */
    public Vector2 seekTarget;
    /** Closing speed limit when seeking, m/s. */
    public float seekMaxSpeed = 200f;
    /** Outward speed to reach when departing, m/s. */
    public float departSpeed = 150f;
    /** Seconds before a burn to start turning toward it. */
    public float alignLead = 60f;

    public Mode mode = Mode.COASTING;

    // Burn in progress
    /** Velocity change the current burn must deliver, bubble axes, m/s. */
    public final Vector2 burnTarget = new Vector2();
    /** Velocity when the current burn started, m/s. */
    public final Vector2 burnStartVelocity = new Vector2();
    public boolean burnActive;

    public NpcPilotComponent() {}

    public NpcPilotComponent(Iterable<PilotBurn> burns) {
        for (PilotBurn b : burns) this.burns.add(b);
    }
}
