package com.ringsofiron.audiospatial;

import com.badlogic.gdx.Gdx;
import com.ringsofiron.audiospatial.backend.AudioBackend;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.backend.AudioEventInstance;
import com.ringsofiron.audiospatial.backend.AudioParameters;
import com.ringsofiron.audiospatial.backend.HeadlessAudioBackend;
import com.ringsofiron.audiospatial.backend.OpenAlAudioBackend;
import com.ringsofiron.audiospatial.backend.SimulatedBackend;
import com.ringsofiron.audiospatial.backend.SynthAudioBackend;

/**
 * The game's entry point to sound. Wraps an {@link AudioBackend} so game code never touches
 * OpenAL (or the placeholder backends) directly.
 *
 * Positioned looping sounds belong on entities as an
 * {@link com.ringsofiron.audiospatial.components.AudioEmitterComponent}, which the audio systems
 * keep in place and muffle. Use the one-shot methods here for fire-and-forget exterior sounds;
 * for one-shots inside a ship that should be muffled, use {@link AudioEmitters#spawnInteriorOneShot}.
 */
public class AudioManager {
    private final AudioBackend backend;
    private final AudioEventCatalog catalog = AudioEventCatalog.withDefaults();

    public AudioManager(AudioBackend backend) {
        this.backend = backend;
    }

    /**
     * The best backend available: OpenAL when libGDX's desktop audio is running, otherwise the
     * synth backend when some other libGDX audio is, otherwise headless.
     */
    public static AudioManager createDefault() {
        if (Gdx.audio != null) {
            OpenAlAudioBackend openAl = OpenAlAudioBackend.tryCreate(AudioEventCatalog.withDefaults());
            if (openAl != null) return new AudioManager(openAl);
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

    /** Playback rules for each event: the backend's own catalog, or the stock one. */
    public AudioEventCatalog getCatalog() {
        if (backend instanceof SimulatedBackend) return ((SimulatedBackend) backend).getCatalog();
        return catalog;
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
