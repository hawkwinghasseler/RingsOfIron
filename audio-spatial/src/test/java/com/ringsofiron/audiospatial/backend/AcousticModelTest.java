package com.ringsofiron.audiospatial.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcousticModelTest {

    @Test
    void efxHighFrequencyGainFollowsTheCutoff() {
        assertEquals(1f, AcousticModel.lowPassGainHf(AcousticModel.OPEN_CUTOFF_HZ), 0.01f);
        // A 12 dB/octave low-pass is 3 dB down at its own cutoff
        assertEquals(0.707f, AcousticModel.lowPassGainHf(AcousticModel.EFX_LOWPASS_REFERENCE_HZ), 0.01f);
        assertTrue(AcousticModel.lowPassGainHf(AcousticModel.MUFFLED_CUTOFF_HZ) < 0.01f);
        float last = 2f;
        for (float o = 0f; o <= 1f; o += 0.1f) {
            float g = AcousticModel.lowPassGainHf(AcousticModel.cutoffHz(o));
            assertTrue(g < last, "falls with occlusion");
            last = g;
        }
    }

    @Test
    void thinAirMufflesBeforeItSilences() {
        assertEquals(AcousticModel.OPEN_CUTOFF_HZ, AcousticModel.pressureCutoffHz(1f), 1f);
        assertEquals(AcousticModel.VACUUM_CUTOFF_HZ, AcousticModel.pressureCutoffHz(0f), 1f);
        // Half an atmosphere already takes the top off; a tenth leaves a dull thud
        assertTrue(AcousticModel.pressureCutoffHz(0.5f) < 6000f);
        assertTrue(AcousticModel.pressureCutoffHz(0.1f) < 1000f);
        float last = Float.MAX_VALUE;
        for (float p = 1f; p >= 0f; p -= 0.1f) {
            float c = AcousticModel.pressureCutoffHz(p);
            assertTrue(c < last, "falls as the air thins");
            last = c;
        }
    }

    @Test
    void theMixTakesWhicheverMufflesMore() {
        SoundEventDef def = SoundEventDef.loop("event:/test", 1f, 50f, 1f);
        AcousticModel.Mix m = AcousticModel.mix(1f, 0f, def, 0f, 1f, 0.1f, new AcousticModel.Mix());
        assertEquals(AcousticModel.pressureCutoffHz(0.1f), m.cutoffHz, 1f);
        assertTrue(m.gain > 0f, "thin air still carries a muffled sound");
        AcousticModel.mix(1f, 0f, def, 1f, 1f, 1f, m);
        assertEquals(AcousticModel.MUFFLED_CUTOFF_HZ, m.cutoffHz, 1f);
        AcousticModel.mix(1f, 0f, def, 0f, 1f, 0f, m);
        assertEquals(0f, m.gain, 1e-6f);
    }
}
