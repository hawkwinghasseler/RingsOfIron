package com.ringsofiron.stationsdocking;

/**
 * How a crew member forces a shut port door from across the dock, and how long it takes. Each is a
 * hostile act against the craft whose door it is ({@link DockingEvent.Kind#DOOR_FORCED}).
 */
public enum ForceMethod {
    /** Levers an unpowered door open: no damage. */
    PRY("pry", "prying", 8f, false),
    /** Burns through a locked door, powered or not, and leaves it damaged. */
    CUT("cut", "cutting", 20f, true),
    /** Talks a powered, locked door into opening: no damage, but slower than cutting. */
    HACK("hack", "hacking", 45f, false);

    public final String verb, doing;
    /** Sim seconds of work. */
    public final float seconds;
    /** Leaves the door damaged (below half integrity, so it leaks once shut again). */
    public final boolean damages;

    ForceMethod(String verb, String doing, float seconds, boolean damages) {
        this.verb = verb;
        this.doing = doing;
        this.seconds = seconds;
        this.damages = damages;
    }

    /** True if this method works on a door in {@code state}. */
    public boolean works(DoorState state) {
        switch (state) {
            case UNPOWERED:
                return this != HACK;
            case LOCKED:
                return this != PRY;
            default:
                return false;
        }
    }

    /** A port door as seen from across the dock. */
    public enum DoorState {
        /** No door to force: not a latched port, or none on the grid. */
        NONE,
        OPEN,
        /** Shut, and its port has no power: it can be pried (or cut). */
        UNPOWERED,
        /** Shut and powered: it must be hacked or cut. */
        LOCKED
    }
}
