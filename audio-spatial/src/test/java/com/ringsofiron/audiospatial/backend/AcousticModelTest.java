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
}
