package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.macrosim.bubble.GameBubbleGateway;
import com.hawkwing.RingsOfIron.wiring.NpcBubblePilots;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.macrosim.MacroSaveParticipant;
import com.ringsofiron.macrosim.MacroSimSystem;
import com.ringsofiron.macrosim.MacroSimulation;
import com.ringsofiron.macrosim.rails.orbital.OrbitalRailsModel;

/**
 * macro-sim in {@link GameWorld}, on the same rails and event system: the economy, freighters and
 * bubble transitions, with NPCs entering the bubble flown by ai-crew's pilot.
 */
final class MacroSimInstaller {
    private MacroSimInstaller() {
    }

    static MacroSimulation install(GameWorld world) {
        GameEngine engine = world.engine;
        // Prediction runs inline so warp drop-out always sees a current forecast; the bubble pauses
        // while rails warp has it collapsed.
        GameBubbleGateway bubbleGateway = new GameBubbleGateway(world.physics, engine.getUuidRegistry());
        // Pirates and patrols come into the bubble on their own layouts
        bubbleGateway.setShipBodies(world::buildShipBody);
        MacroSimulation macroSim = new MacroSimulation(engine, new OrbitalRailsModel(engine, world.universe, world.orbitalEvents),
                bubbleGateway, world.commodities, null, engine.getSimSeconds());
        macroSim.bubble().addListener(new NpcBubblePilots(engine.getUuidRegistry()));
        // Freighters in the bubble dock at their stations under their own power, and leave the same way
        FreighterTraffic traffic = new FreighterTraffic(engine, macroSim, world.docking, world.berthing, world.timeWarp,
                () -> world.cargo);
        macroSim.bubble().addListener(traffic);
        engine.addSystem(traffic);
        // Economy, fleet and bubble state; restores its own components on load.
        MacroSaveParticipant.register(macroSim, engine.getSerializationService());
        engine.addSystem(new MacroSimSystem(macroSim, engine));
        world.timeWarp.addDropOutSource(macroSim::nextDropOutTime);
        world.timeWarp.changes.add((signal, change) -> macroSim.setBubbleSuspended(change.rails));
        return macroSim;
    }
}
