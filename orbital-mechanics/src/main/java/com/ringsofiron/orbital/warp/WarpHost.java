package com.ringsofiron.orbital.warp;

import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.SimClock;

/**
 * The clock and warp primitives {@link TimeWarpController} drives. In the game this is core-engine's
 * {@link GameEngine} ({@link #of}); tests can supply their own.
 */
public interface WarpHost {
    int TICKS_PER_SECOND = SimClock.TICKS_PER_SECOND;

    /** Adapts core-engine's clock: 60 Hz tick, physics warp, rails jumps and RailsWarpGuards. */
    static WarpHost of(GameEngine engine) {
        return new WarpHost() {
            @Override
            public long getTick() {
                return engine.getTick();
            }

            @Override
            public void setPhysicsWarp(int multiplier) {
                engine.setPhysicsWarp(Math.max(1, Math.min(GameEngine.MAX_PHYSICS_WARP, multiplier)));
            }

            @Override
            public boolean railsWarpAllowed() {
                return engine.getRailsWarpBlockedReason() == null;
            }

            @Override
            public String railsWarpBlockedReason() {
                return engine.getRailsWarpBlockedReason();
            }

            @Override
            public boolean advanceRails(long ticks) {
                return engine.advanceRails(ticks);
            }
        };
    }

    /** Current sim tick. Sim seconds = tick / 60.0. */
    long getTick();

    /** Runs this many fixed physics ticks per real tick (1 to 10). */
    void setPhysicsWarp(int multiplier);

    /** True if no guard (active hazard aboard, hostiles near, thrust) currently forbids rails warp. */
    boolean railsWarpAllowed();

    /** Why rails warp is refused right now ("Fire aboard"), or null if it is allowed. */
    default String railsWarpBlockedReason() {
        return railsWarpAllowed() ? null : "Rails warp not allowed";
    }

    /** Jumps the clock forward by {@code ticks} without stepping physics. False if a guard vetoes. */
    boolean advanceRails(long ticks);
}
