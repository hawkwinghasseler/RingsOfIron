package com.ringsofiron.audiospatial;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.audiospatial.backend.AudioParameters;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.coreengine.components.TransientComponent;

/**
 * Helpers for sounds that need an entity of their own, so the audio systems can place and
 * muffle them, but no other game object to live on.
 */
public final class AudioEmitters {
    private AudioEmitters() {}

    /**
     * Plays a one-shot inside {@code ship} at ship-local (localX, localY), muffled by the walls
     * between it and the listener. The entity removes itself when the sound ends and is never saved.
     */
    public static Entity spawnInteriorOneShot(Engine engine, String eventPath, Entity ship, float localX, float localY, float intensity) {
        Entity e = engine.createEntity();
        AudioEmitterComponent emitter = AudioEmitterComponent.interior(eventPath, ship, localX, localY);
        emitter.removeEntityWhenDone = true;
        emitter.setParameter(AudioParameters.INTENSITY, intensity);
        e.add(emitter);
        e.add(new TransientComponent());
        engine.addEntity(e);
        return e;
    }
}
