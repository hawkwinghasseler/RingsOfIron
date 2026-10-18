package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.orbital.warp.WarpHost;

/** Lets orbital-mechanics' {@code TimeWarpController} drive core-engine's clock. */
public class GameEngineWarpHost implements WarpHost {
    private final GameEngine engine;

    public GameEngineWarpHost(GameEngine engine) {
        this.engine = engine;
    }

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
    public boolean advanceRails(long ticks) {
        return engine.advanceRails(ticks);
    }
}
