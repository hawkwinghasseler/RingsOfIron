package com.ringsofiron.audiospatial.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MixLevelsTest {

    @Test
    void loopsAreAmbienceAndOneShotsAreEffects() {
        MixLevels levels = new MixLevels();
        levels.set(0.5f, 0.4f, 0.8f);
        SoundEventDef hum = new SoundEventDef("event:/ship/reactor_hum", true, 0f, 1f, 50f, 1f);
        SoundEventDef gun = new SoundEventDef("event:/weapons/autocannon", false, 0.2f, 1f, 50f, 1f);
        assertEquals(0.4f, levels.gain(hum), 1e-6f);
        assertEquals(0.2f, levels.gain(gun), 1e-6f);
    }

    @Test
    void levelsStayBetweenSilentAndFull() {
        MixLevels levels = new MixLevels();
        levels.set(2f, -1f, Float.NaN);
        assertEquals(1f, levels.master);
        assertEquals(0f, levels.effects);
        assertEquals(1f, levels.ambience);
    }

    @Test
    void theBackendsShareTheirLevels() {
        HeadlessAudioBackend backend = new HeadlessAudioBackend();
        backend.levels.master = 0.3f;
        assertEquals(0.3f, backend.levels.master);
    }
}
