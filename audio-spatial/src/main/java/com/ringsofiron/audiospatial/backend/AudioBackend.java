package com.ringsofiron.audiospatial.backend;

/**
 * The sound engine under {@code AudioManager}. The game talks to this interface only, so
 * backends can be swapped without touching game code.
 *
 * <ul>
 *   <li>{@link OpenAlAudioBackend}: OpenAL Soft through libGDX's desktop audio, with EFX
 *       muffling and HRTF. The default on desktop.</li>
 *   <li>{@link HeadlessAudioBackend}: no output, records everything. For tests, servers and
 *       machines without a sound device.</li>
 *   <li>{@link SynthAudioBackend}: procedural placeholder sounds played through libGDX's
 *       {@code AudioDevice}, with real low-pass muffling. For prototyping before banks exist.</li>
 * </ul>
 */
public interface AudioBackend {

    /** Loads a sound bank. No current backend uses banks; they ignore it. */
    void loadBank(String path);

    AudioEventInstance createInstance(String eventPath);

    /** Places the listener, in world (bubble) meters and meters per second. */
    void setListener(float x, float y, float vx, float vy);

    /** Sets a parameter on every event, such as a global music intensity. */
    void setGlobalParameter(String name, float value);

    float getGlobalParameter(String name, float defaultValue);

    /** Advances the sound engine. Call once per frame after the audio systems have run. */
    void update(float deltaTime);

    /** Stops every sound and frees the engine. */
    void dispose();
}
