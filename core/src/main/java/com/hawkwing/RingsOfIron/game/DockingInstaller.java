package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.shipsystems.TransitClearance;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.stationsdocking.BerthingSystem;
import com.ringsofiron.stationsdocking.DockingSystem;

/** stations-docking in {@link GameWorld}: magnets, collars and latches, and craft berthing themselves. */
final class DockingInstaller {
    final DockingSystem docking;
    final BerthingSystem berthing;

    DockingInstaller(GameWorld world) {
        GameEngine engine = world.engine;
        // stations-docking: magnets, collars and latches, after the bodies exist.
        docking = new DockingSystem(world.physics, engine.getUuidRegistry());
        engine.addSystem(docking);
        // A collar still drawing a ship in, or a latch still settling, holds rails warp back.
        engine.addRailsWarpGuard(docking::railsWarpBlockedReason);
        // A loaded game's ships get their bodies back in its first frame and a docked ship its weld in
        // the next; warping before then pulled the pair apart and let the air out of both
        engine.addRailsWarpGuard(world::loadingRefusal);
        // Craft that fly themselves in to dock and away again, on their own thrusters
        berthing = new BerthingSystem(docking, engine.getUuidRegistry());
        engine.addSystem(berthing);
        // ... and ships crossing the bubble on their rails plan keep out of the stations and berths
        engine.addSystem(new TransitClearance(docking));
    }
}
