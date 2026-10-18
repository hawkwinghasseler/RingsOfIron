package com.ringsofiron.audiospatial;

import com.badlogic.gdx.Gdx;
import com.ringsofiron.audiospatial.backend.AudioBackend;
import com.ringsofiron.audiospatial.backend.AudioEventInstance;
import com.ringsofiron.audiospatial.backend.AudioParameters;
import com.ringsofiron.audiospatial.backend.HeadlessAudioBackend;
import com.ringsofiron.audiospatial.backend.SynthAudioBackend;

/**
 * The game's entry point to sound. Wraps an {@link AudioBackend} so game code never touches
 * FMOD (or the placeholder backends) directly.
 *
 * Positioned looping sounds belong on entities as an
 * {@link com.ringsofiron.audiospatial.components.AudioEmitterComponent}, which the audio systems
 * keep in place and muffle. Use the one-shot methods here for fire-and-forget exterior sounds;
 * for one-shots inside a ship that should be muffled, use {@link AudioEmitters#spawnInteriorOneShot}.
 */
public class AudioManager {
    private final AudioBackend backend;

    public AudioManager(AudioBackend backend) {
        this.backend = backend;
    }

    /**
     * The best backend available: the synth backend when libGDX audio is running, otherwise
     * headless. Swap in the FMOD backend here once it exists.
     */
    public static AudioManager createDefault() {
        if (Gdx.audio != null) {
            try {
                return new AudioManager(new SynthAudioBackend());
            } catch (RuntimeException e) {
                if (Gdx.app != null) Gdx.app.error("audio", "No audio device, running silent", e);
            }
        }
        return new AudioManager(new HeadlessAudioBackend());
    }

    public AudioBackend getBackend() {
        return backend;
    }

    public void loadBank(String path) {
        backend.loadBank(path);
    }

    /** Plays an exterior sound once at world position (x, y), e.g. {@code playOneShot("event:/weapons/laser", x, y)}. */
    public AudioEventInstance playOneShot(String eventPath, float x, float y) {
        return playOneShot(eventPath, x, y, 1f);
    }

    /** As {@link #playOneShot(String, float, float)}, with an {@link AudioParameters#INTENSITY} of 0 to 1. */
    public AudioEventInstance playOneShot(String eventPath, float x, float y, float intensity) {
        AudioEventInstance i = backend.createInstance(eventPath);
        i.setPosition(x, y);
        i.setParameter(AudioParameters.INTENSITY, intensity);
        i.start();
        i.release(); // freed once it finishes
        return i;
    }

    /** Creates an instance the caller drives and must release. */
    public AudioEventInstance createInstance(String eventPath) {
        return backend.createInstance(eventPath);
    }

    public void setGlobalParameter(String name, float value) {
        backend.setGlobalParameter(name, value);
    }

    /** Advances the backend. {@link com.ringsofiron.audiospatial.systems.AudioUpdateSystem} calls this each frame. */
    public void update(float deltaTime) {
        backend.update(deltaTime);
    }

    public void dispose() {
        backend.dispose();
    }
}
