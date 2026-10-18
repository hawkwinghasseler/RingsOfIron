package com.ringsofiron.audiospatial.backend;

import com.badlogic.gdx.utils.ObjectFloatMap;

/**
 * Instance state shared by the built-in backends: it plays for its definition's duration (or
 * forever if looping) and, on every backend update, works out its {@link #mix} relative to the
 * listener through {@link AcousticModel}.
 */
public class SimulatedInstance implements AudioEventInstance {
    public final SoundEventDef def;
    public float x, y, vx, vy;
    /** Seconds since {@link #start()}. */
    public float elapsed;
    public final AcousticModel.Mix mix = new AcousticModel.Mix();
    private final ObjectFloatMap<String> params = new ObjectFloatMap<>();
    private boolean playing;
    private boolean released;

    protected SimulatedInstance(SoundEventDef def) {
        this.def = def;
    }

    @Override public String path() { return def.path; }

    @Override
    public void start() {
        checkLive();
        playing = true;
        elapsed = 0f;
    }

    @Override public void stop() { playing = false; }

    @Override public boolean isPlaying() { return playing; }

    @Override
    public void release() {
        released = true;
    }

    public boolean isReleased() { return released; }

    @Override public void setPosition(float x, float y) { this.x = x; this.y = y; }

    @Override public void setVelocity(float vx, float vy) { this.vx = vx; this.vy = vy; }

    @Override public void setParameter(String name, float value) { params.put(name, value); }

    @Override public float getParameter(String name, float defaultValue) { return params.get(name, defaultValue); }

    /** Advances playback and recomputes the mix against the listener. */
    void advance(float deltaTime, float listenerX, float listenerY) {
        if (playing) {
            elapsed += deltaTime;
            if (!def.looping && elapsed >= def.duration) playing = false;
        }
        float occlusion = getParameter(AudioParameters.OCCLUSION, 0f);
        float transmission = getParameter(AudioParameters.TRANSMISSION, 1f);
        float pressure = getParameter(AudioParameters.PRESSURE, 1f);
        AcousticModel.mix(x - listenerX, y - listenerY, def, occlusion, transmission, pressure, mix);
        mix.gain *= getParameter(AudioParameters.INTENSITY, 1f);
        if (!playing) mix.gain = 0f;
    }

    private void checkLive() {
        if (released) throw new IllegalStateException("Instance of " + def.path + " was released");
    }
}
