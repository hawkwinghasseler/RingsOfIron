package com.hawkwing.RingsOfIron.game;

import com.hawkwing.RingsOfIron.wiring.PlayerOrbitInfo;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.orbital.events.OrbitalEventQueue;
import com.ringsofiron.orbital.events.OrbitalEventSystem;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.orbital.warp.BubbleHandoff;
import com.ringsofiron.orbital.warp.EncounterPredictor;
import com.ringsofiron.orbital.warp.TimeWarpController;
import com.ringsofiron.physicsexterior.OrbitalAnchorBridge;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.RailsBubbleHandoff;
import com.ringsofiron.stationsdocking.DockingBubbleHandoff;

/** orbital-mechanics in {@link GameWorld}: the rails, their events, time warp and planned burns. */
final class OrbitInstaller {
    final RailsUniverse universe;
    final OrbitalEventSystem orbitalEvents;
    final OrbitalAnchorBridge anchorBridge;
    /** How ships leave and enter the bubble; Rescue collapses and rebuilds it too. */
    final BubbleHandoff bubble;
    final TimeWarpController timeWarp;
    final BurnPlanner burns;

    OrbitInstaller(GameWorld world, GameWorld.Modules modules) {
        GameEngine engine = world.engine;
        PhysicsManager physics = world.physics;
        universe = new RailsUniverse(engine);
        orbitalEvents = new OrbitalEventSystem(universe, new OrbitalEventQueue());
        engine.addSystem(orbitalEvents);
        engine.getSerializationService().addParticipant(orbitalEvents);
        // Keeps orbital's copy of the bubble anchor in step with physics, both ways.
        anchorBridge = new OrbitalAnchorBridge(physics, orbitalEvents);
        EncounterPredictor encounters = new EncounterPredictor(engine.getEcsEngine(), universe);
        // A docked pair goes onto the rails as one body and comes back latched.
        bubble = new DockingBubbleHandoff(modules.bubble != null
                ? modules.bubble : new RailsBubbleHandoff(physics, engine.getEcsEngine()),
                world.docking, physics, engine.getEcsEngine(), engine.getUuidRegistry());
        timeWarp = new TimeWarpController(engine, bubble, orbitalEvents,
                universe, encounters, world::getPlayer);
        orbitalEvents.impacts.add((signal, event) -> world.onImpact(event));
        // Planned burns: the event system fires them on rails; in the bubble the helm flies them (HelmOrders)
        burns = new BurnPlanner(universe, orbitalEvents, world::getPlayer,
                new PlayerOrbitInfo(universe, physics, world::getPlayer));
    }
}
