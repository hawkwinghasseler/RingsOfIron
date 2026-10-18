package com.ringsofiron.audiospatial.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Renders voices offline (no sound device) to check the mixer's filtering and panning. */
class SynthVoiceTest {
    private static final int RATE = SynthAudioBackend.SAMPLE_RATE;

    private static float[] render(SynthVoice v, int blocks) {
        float[] all = new float[blocks * 512 * 2];
        float[] block = new float[512 * 2];
        for (int b = 0; b < blocks; b++) {
            java.util.Arrays.fill(block, 0f);
            v.mixInto(block, 512, RATE);
            System.arraycopy(block, 0, all, b * block.length, block.length);
        }
        return all;
    }

    private static SynthVoice hiss(float cutoff, float pan) {
        SynthVoice v = new SynthVoice(SynthVoice.Patch.HISS, true, 0f);
        v.targetGain = 1f;
        v.targetPan = pan;
        v.targetCutoff = cutoff;
        v.active = true;
        v.restart();
        return v;
    }

    /** RMS of the sample-to-sample change in the left channel: a rough measure of treble. */
    private static double treble(float[] s, int from) {
        double sum = 0;
        int n = 0;
        for (int i = from + 2; i < s.length; i += 2, n++) {
            double d = s[i] - s[i - 2];
            sum += d * d;
        }
        return Math.sqrt(sum / n);
    }

    @Test
    void muffledVoiceLosesItsHighFrequencies() {
        float[] open = render(hiss(AcousticModel.OPEN_CUTOFF_HZ, 0f), 80);
        float[] muffled = render(hiss(AcousticModel.MUFFLED_CUTOFF_HZ, 0f), 80);
        int settled = 40 * 1024; // skip while gain and cutoff glide in
        assertTrue(treble(muffled, settled) < treble(open, settled) * 0.2,
                "open " + treble(open, settled) + " muffled " + treble(muffled, settled));
    }

    @Test
    void panningMovesSoundToOneSide() {
        float[] right = render(hiss(AcousticModel.OPEN_CUTOFF_HZ, 1f), 80);
        double l = 0, r = 0;
        for (int i = 40 * 1024; i < right.length; i += 2) {
            l += right[i] * right[i];
            r += right[i + 1] * right[i + 1];
        }
        assertTrue(r > l * 20, "left " + l + " right " + r);
    }

    @Test
    void oneShotEndsAfterItsDuration() {
        SynthVoice laser = new SynthVoice(SynthVoice.Patch.LASER, false, 0.3f);
        laser.targetGain = 1f;
        laser.active = true;
        laser.restart();
        float[] s = render(laser, 60); // ~0.7 s
        int afterEnd = (int) (0.35f * RATE) * 2;
        float tail = 0f;
        for (int i = afterEnd + 4000; i < s.length; i++) tail = Math.max(tail, Math.abs(s[i]));
        assertEquals(0f, tail, 1e-3f);
    }

    @Test
    void combatSoundsHaveTheirOwnPatches() {
        assertEquals(SynthVoice.Patch.AUTOCANNON, SynthVoice.patchFor(AudioEventCatalog.AUTOCANNON));
        assertEquals(SynthVoice.Patch.LAUNCH, SynthVoice.patchFor(AudioEventCatalog.MISSILE_LAUNCH));
        assertEquals(SynthVoice.Patch.MISSILE, SynthVoice.patchFor(AudioEventCatalog.MISSILE_FLIGHT));
        assertEquals(SynthVoice.Patch.EXPLOSION, SynthVoice.patchFor(AudioEventCatalog.EXPLOSION));
        AudioEventCatalog catalog = AudioEventCatalog.withDefaults();
        for (SynthVoice.Patch p : new SynthVoice.Patch[]{SynthVoice.Patch.AUTOCANNON, SynthVoice.Patch.LAUNCH,
                SynthVoice.Patch.MISSILE, SynthVoice.Patch.EXPLOSION}) {
            SynthVoice v = new SynthVoice(p, p == SynthVoice.Patch.MISSILE, 2.5f);
            v.targetGain = 1f;
            v.active = true;
            v.restart();
            float[] s = render(v, 40);
            float peak = 0f;
            for (float x : s) {
                assertTrue(Float.isFinite(x), p + " produced " + x);
                peak = Math.max(peak, Math.abs(x));
            }
            // Audible but not clipping hard
            assertTrue(peak > 0.05f && peak < 2f, p + " peak " + peak);
        }
        assertTrue(catalog.contains(AudioEventCatalog.EXPLOSION));
    }
}
