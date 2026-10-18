package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Entity;

/** Something happened at a pair of docking ports. Dispatched on {@link DockingSystem#events}; reused. */
public class DockingEvent {
    public enum Kind {
        /** An active collar caught the other port and is drawing the craft together. */
        CAPTURED,
        /** The ports latched: the craft are joined. */
        DOCKED,
        /** The craft let go of each other on command. */
        UNDOCKED,
        /** The connection was strained past its limit: both collars tore off and the hull around them is damaged. */
        TORN
    }

    public Kind kind;
    /** The craft whose port did it (the one that captured, or asked to undock) and its port. */
    public Entity vessel;
    public int port;
    /** The other craft and its port. */
    public Entity other;
    public int otherPort;
    /** Docking clearance had been given (the other side's port was armed for this). */
    public boolean permitted;
}
