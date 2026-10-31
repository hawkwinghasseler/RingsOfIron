package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.macrosim.MacroSimulation;
import com.ringsofiron.physicsexterior.PhysicsSaveParticipant;

/**
 * Pirates and faction patrols in {@link GameWorld}, on the rails and in the bubble: their traffic,
 * the pirates boarding the player and their hidden base, and the tension between the factions.
 */
final class PirateInstaller {
    final HostileTraffic hostiles;
    final PirateBoarding pirateBoarding;
    final PirateBase pirateBase;
    final FactionTension tension;

    PirateInstaller(GameWorld world) {
        GameEngine engine = world.engine;
        GameContext context = world.context;
        MacroSimulation macroSim = world.macroSim;
        FactionStanding standing = world.standing;
        // Bubble bodies are saved as true states, never as live Box2D bodies.
        PhysicsSaveParticipant.install(world.physics, engine);
        // Pirates and faction patrols, on the rails and in the bubble
        hostiles = HostileTraffic.install(world);
        // Hostile traffic's own state starts again from the load
        world.addResettable(hostiles);
        pirateBoarding = new PirateBoarding(context, world.docking, world.combatEvents, world.rescue);
        engine.getSerializationService().addParticipant(pirateBoarding);
        world.addResettable(pirateBoarding);
        pirateBase = new PirateBase(context, world.crew, macroSim, standing, world.contracts, hostiles, world.disablement,
                world.missiles, () -> world.tension);
        engine.addSystem(pirateBase);
        engine.getSerializationService().addParticipant(pirateBase);
        hostiles.extraRaiders = pirateBase::extraRaiders;
        tension = new FactionTension(context, macroSim, standing, hostiles);
        engine.addSystem(tension);
        engine.getSerializationService().addParticipant(tension);
        // ...with no strain between the factions
        world.addResettable(tension);
        world.yard.priceFactor = tension::priceFactor;
    }
}
