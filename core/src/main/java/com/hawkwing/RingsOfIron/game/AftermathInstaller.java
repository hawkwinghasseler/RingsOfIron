package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.shipops.Boarding;
import com.ringsofiron.shipops.Salvage;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.orbital.warp.BubbleHandoff;
import com.ringsofiron.stationsdocking.DockingSystem;

/**
 * What follows a fight in {@link GameWorld}: the player's rescue and the distress beacon, beaten ships
 * boarded and stripped, prizes and ships at the yards, and furnishings carried aboard.
 */
final class AftermathInstaller {
    final Rescue rescue;
    final DistressBeacon beacon;
    final Boarding boarding;
    final Salvage salvage;
    final Shipyard shipyard;
    final FurnishingStore furnishings;

    AftermathInstaller(GameWorld world, BubbleHandoff bubble) {
        GameEngine engine = world.engine;
        DockingSystem docking = world.docking;
        CrewService crew = world.crew;
        FactionStanding standing = world.standing;
        GameContext context = world.context;
        rescue = new Rescue(world, bubble);
        beacon = new DistressBeacon(context, () -> world.getCrash() != null, world::playerStation, rescue, standing,
                world.warnings);
        // Beaten ships are boarded, taken and stripped
        boarding = new Boarding(docking, crew, world.forcing, standing, world.disablement, engine.getUuidRegistry(),
                engine::getSimSeconds);
        engine.addSystem(boarding);
        // A craft being stormed fights its boarders, whatever the player's standing with its owners
        crew.hostility = (a, b) -> standing.hostile(a, b) || boarding.storming(a, b);
        salvage = new Salvage(docking, crew, boarding, world.cargo, engine.getUuidRegistry(), world.combatDefs);
        engine.addSystem(salvage);
        // Ships bought at faction yards, and taken ships towed home as prizes
        shipyard = new Shipyard(context, crew, world.physics, world.macroSim, docking, standing, world.yard,
                world.contracts, boarding, () -> world.prisoners);
        engine.addSystem(shipyard);
        // ... and furnishings for the crew's downtime, carried aboard by the yards' loaders
        furnishings = new FurnishingStore(context, crew, docking, world.yard, world::getContent);
        engine.addSystem(furnishings);
    }
}
