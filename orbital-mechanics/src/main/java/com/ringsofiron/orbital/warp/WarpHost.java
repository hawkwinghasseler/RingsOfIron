package com.ringsofiron.orbital.warp;

/**
 * The clock and warp primitives {@link TimeWarpController} drives. core-engine's {@code GameEngine}
 * provides these (60 Hz tick, {@code setPhysicsWarp}, {@code advanceRails} with its
 * {@code RailsWarpGuard}s); wire it with a small adapter.
 */
public interface WarpHost {
    int TICKS_PER_SECOND = 60;

    /** Current sim tick. Sim seconds = tick / 60.0. */
    long getTick();

    /** Runs this many fixed physics ticks per real tick (1 to 10). */
    void setPhysicsWarp(int multiplier);

    /** True if no guard (active hazard aboard, hostiles near, thrust) currently forbids rails warp. */
    boolean railsWarpAllowed();

    /** Jumps the clock forward by {@code ticks} without stepping physics. False if a guard vetoes. */
    boolean advanceRails(long ticks);
}
