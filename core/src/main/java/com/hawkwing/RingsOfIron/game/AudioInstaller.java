package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.audiospatial.AudioManager;
import com.ringsofiron.audiospatial.ImpactAudio;
import com.ringsofiron.audiospatial.systems.AcousticOcclusionSystem;
import com.ringsofiron.audiospatial.systems.AudioUpdateSystem;
import com.ringsofiron.audiospatial.systems.InteriorHazardAudioSystem;
import com.ringsofiron.coreengine.GameEngine;

/** audio-spatial in {@link GameWorld}: sound, occlusion and the listener on the player. */
final class AudioInstaller {
    final AudioManager audio;
    final AudioListenerSync audioListener;
    final EngineAudio engineAudio;
    final CrewAudio crewAudio;

    AudioInstaller(GameWorld world, GameWorld.Modules modules) {
        GameEngine engine = world.engine;
        audio = modules.audio != null ? modules.audio : AudioManager.createDefault();
        audioListener = new AudioListenerSync(world::getPlayer, world.avatar);
        engine.addSystem(audioListener);
        engineAudio = new EngineAudio(world::getPlayer);
        engine.addSystem(engineAudio);
        crewAudio = new CrewAudio();
        engine.addSystem(crewAudio);
        // Fires, breaches, alarms and doors aboard every ship
        engine.addSystem(new InteriorHazardAudioSystem());
        engine.addSystem(new AcousticOcclusionSystem(audio.getCatalog()));
        AudioUpdateSystem audioUpdate = new AudioUpdateSystem(audio);
        engine.addSystem(audioUpdate);
        new ImpactAudio(audio).attach(world.physics);
        CombatAudio.install(engine.getEcsEngine(), world.combatEvents, world.combatDefs, audio, world::getContent);
        NoticeAudio.install(world.notices, audio);
        world.physics.worldShifts.add(audioUpdate.worldShiftListener());
    }
}
