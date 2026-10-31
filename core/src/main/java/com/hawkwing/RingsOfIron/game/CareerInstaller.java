package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.uihud.HintRegistry;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.macrosim.MacroSimulation;
import com.ringsofiron.stationsdocking.DockingSystem;

/**
 * The career in {@link GameWorld}, once everything it ties together exists: the loop that sets
 * encounters, rescues and what beating ships is worth, the day's events, and the player's crew hired,
 * paid and taken prisoner.
 */
final class CareerInstaller {
    final LoopWiring loop;
    final WorldEvents events;
    final CrewCareer crewCareer;
    final CrewHiring crewHiring;
    final Prisoners prisoners;

    CareerInstaller(GameWorld world) {
        GameEngine engine = world.engine;
        GameContext context = world.context;
        CrewService crew = world.crew;
        DockingSystem docking = world.docking;
        MacroSimulation macroSim = world.macroSim;
        FactionStanding standing = world.standing;
        // The loop: the career sets encounters, rescues and what boarding and beating ships are worth
        loop = LoopWiring.install(world);
        // Things that happen on their own, a day at a time, with jobs on the boards
        events = new WorldEvents(context, () -> world.getCrash() != null, world.universe, macroSim, standing,
                world.tension, world.contracts, world.derelicts, world.hostiles, world.shipyard);
        // A save with no events has the default seed (a new game keeps its own, so reset() keeps it)
        world.addResettable(() -> {
            events.reset();
            events.seed = WorldEvents.DEFAULT_SEED;
        });
        // The player's crew: hired at stations, paid weekly, learning from what they do
        crewCareer = new CrewCareer(context, crew, world.combatEvents, world.boarding, world.budget, world.rescue, loop);
        engine.addSystem(crewCareer);
        // Crew morale drifts from the loaded time on
        world.addResettable(crewCareer::restartClock);
        crewHiring = new CrewHiring(context, crew, docking, macroSim, standing, crewCareer, world::stationDice);
        engine.getSerializationService().addParticipant(crewHiring);
        // ...and nobody is hired from today's pools
        world.addResettable(crewHiring);
        prisoners = new Prisoners(context, crew, docking, macroSim, standing, crewHiring);
        world.hints.register(HintRegistry.Context.LATCHED, "prisoners", 3, Action.CREW)
                .onlyWhen(() -> prisoners.takenAlongside() != null);
    }
}
