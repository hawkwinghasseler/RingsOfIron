package com.hawkwing.RingsOfIron.game;

import com.hawkwing.RingsOfIron.wiring.CombatThreatSensor;
import com.ringsofiron.aicrew.pilot.TrueStatePilotReference;
import com.ringsofiron.aicrew.pilot.NpcCombatSystem;
import com.ringsofiron.aicrew.pilot.NpcPilotSystem;
import com.ringsofiron.aicrew.pilot.PilotReference;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.systems.FlightControlSystem;
import com.ringsofiron.physicsexterior.systems.FloatingOriginSystem;
import com.ringsofiron.physicsexterior.systems.KinematicTransientSystem;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import com.ringsofiron.physicsexterior.systems.TidalGravitySystem;
import com.ringsofiron.stationsdocking.StationBodies;

/** {@link GameWorld}'s first systems: the NPC pilots, then physics-exterior's flight and bodies. */
final class FlightInstaller {
    private FlightInstaller() {
    }

    static void install(GameWorld world, GameWorld.Modules modules) {
        GameEngine engine = world.engine;
        PhysicsManager physics = world.physics;
        // ai-crew's NPC pilot sets flight controls before physics reads them.
        PilotReference pilotFrame = modules.pilotReference != null
                ? modules.pilotReference : new TrueStatePilotReference(physics, engine::getSimSeconds);
        engine.addSystem(new NpcPilotSystem(pilotFrame));
        // ... and its combat pilot flies NPC ships in a fight, dodging combat-sys missiles.
        NpcCombatSystem npcCombat = new NpcCombatSystem();
        npcCombat.setThreatSensor(new CombatThreatSensor(engine.getEcsEngine()));
        engine.addSystem(npcCombat);

        // physics-exterior
        engine.addSystem(new FlightControlSystem(physics));
        // The anchor carries the planet's main pull; bubble bodies feel only the tidal difference.
        engine.addSystem(new TidalGravitySystem(physics));
        engine.addSystem(new PhysicsUpdateSystem(physics));
        // Fast passes and stations: drawn and swept for collisions while they stay on rails.
        // Stations with an interior get a kinematic body shaped like it, which ships dock with.
        KinematicTransientSystem transients = new KinematicTransientSystem(physics);
        transients.setBodyFactory(new StationBodies(physics));
        engine.addSystem(transients);
        engine.addSystem(new FloatingOriginSystem(physics));
    }
}
