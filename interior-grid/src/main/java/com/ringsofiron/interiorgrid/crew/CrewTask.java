package com.ringsofiron.interiorgrid.crew;

import com.badlogic.ashley.core.Entity;

/**
 * A job for the crew, posted to a ship's task board. Plain data: the crew AI decides who
 * takes it and how it is done.
 *
 * Tasks target a tile (x, y) on the ship's interior grid. Higher {@link #priority} is
 * served first.
 */
public abstract class CrewTask {

    public enum Kind {
        SEAL_BREACH,
        /** Patch a leaking wall or deck plate from the ship's stores, so it stops venting. */
        PATCH_LEAK,
        REPAIR,
        EXTINGUISH,
        RESCUE,
        TREAT,
        MAN_CONSOLE,
        /** Carry goods from one craft's cargo bays to another's (or within one). */
        CARRY,
        /** Yard work on a docked craft: repairing a damaged tile or system, or installing a refit part. */
        YARD,
        /** Force a shut door from where it can be reached: an order, e.g. a port door across a dock. */
        FORCE_DOOR,
        /** Get into an escape pod and wait there for launch: the order to abandon ship. */
        EVACUATE,
        /** Go aboard another craft as one of a boarding party and sweep it: an order. */
        BOARD
    }

    public enum Status {
        OPEN,
        CLAIMED,
        DONE,
        CANCELLED
    }

    public final Kind kind;
    /** Target tile on the interior grid. */
    public final int x, y;
    public float priority;

    public Status status = Status.OPEN;
    /** The crew entity working this task while {@link Status#CLAIMED}, else null. */
    public Entity claimedBy;

    protected CrewTask(Kind kind, int x, int y, float priority) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.priority = priority;
    }

    /** True while the task still needs doing (open or being worked). */
    public boolean isActive() {
        return status == Status.OPEN || status == Status.CLAIMED;
    }

    /** True if {@code other} describes the same job, so posting both would be a duplicate. */
    public boolean sameJob(CrewTask other) {
        return other != null && other.kind == kind && other.x == x && other.y == y;
    }

    @Override
    public String toString() {
        return kind + "(" + x + "," + y + ")" + (status == Status.CLAIMED ? "*" : "");
    }
}
