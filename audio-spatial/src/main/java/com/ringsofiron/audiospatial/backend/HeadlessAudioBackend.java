package com.ringsofiron.audiospatial.backend;

import com.badlogic.gdx.utils.Array;

/**
 * Plays nothing but tracks every instance and what it would sound like. Use it in tests, on
 * servers and when no sound device is available.
 */
public class HeadlessAudioBackend extends SimulatedBackend {

    public HeadlessAudioBackend() {
        this(AudioEventCatalog.withDefaults());
    }

    public HeadlessAudioBackend(AudioEventCatalog catalog) {
        super(catalog);
    }

    /** Every live instance of {@code path}. */
    public Array<SimulatedInstance> find(String path) {
        Array<SimulatedInstance> out = new Array<>();
        for (SimulatedInstance i : instances) if (i.path().equals(path)) out.add(i);
        return out;
    }
}
