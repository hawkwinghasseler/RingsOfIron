package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Component;
import com.ringsofiron.coreengine.EntityRef;

/**
 * A craft flying itself in to dock at a station, berthed there, or flying off again, under its own
 * thrusters ({@link BerthingSystem}). Saved with the craft.
 */
public class BerthingComponent implements Component {
    public enum Phase {
        /** Asking for clearance and flying to a point in front of the port it was given. */
        APPROACH,
        /** Closing on the port along its centerline until the latch takes it. */
        FINAL,
        /** Docked. */
        BERTHED,
        /** Undocked: backing off the port, then flying away. */
        DEPART
    }

    public EntityRef station;
    public Phase phase = Phase.APPROACH;
    /** The craft's own port it docks with. */
    public int ownPort;
    /** The station's port cleared for it; -1 until it has clearance. */
    public int port = -1;
    /** Seconds in this phase. */
    public float time;
    /** Seconds until it asks for clearance again. */
    public float retry;
    /** What it is doing, for the screen. */
    public String status = "";
    /**
     * Docking with a craft that gives no clearance (another ship): it picks the nearest free port of
     * its own port's size there and flies in without leave, rather than asking a station.
     */
    public boolean uninvited;

    public BerthingComponent() {
    }

    public BerthingComponent(EntityRef station, int ownPort, Phase phase) {
        this.station = station;
        this.ownPort = ownPort;
        this.phase = phase;
    }
}
