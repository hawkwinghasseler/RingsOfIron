package com.ringsofiron.audiospatial.backend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Plays through a real OpenAL Soft mixer on a loopback device and checks what comes out. */
class OpenAlAudioBackendTest {
    private static final float FRAME = 1f / 60f;
    private static final int FRAME_SAMPLES = LoopbackOpenAl.RATE / 60;

    private LoopbackOpenAl al;
    private OpenAlAudioBackend backend;

    private OpenAlAudioBackend start(String clipDir) {
        al = LoopbackOpenAl.open();
        backend = new OpenAlAudioBackend(AudioEventCatalog.withDefaults(), clipDir);
        backend.setMasterVolume(1f);
        return backend;
    }

    @AfterEach
    void tearDown() {
        if (backend != null) backend.dispose();
        if (al != null) al.close();
    }

    /** Runs the game loop for {@code seconds} and returns the stereo output. */
    private float[] run(float seconds) {
        int frames = Math.round(seconds * 60);
        float[] out = new float[frames * FRAME_SAMPLES * 2];
        for (int f = 0; f < frames; f++) {
            backend.update(FRAME);
            float[] block = al.render(FRAME_SAMPLES);
            System.arraycopy(block, 0, out, f * block.length, block.length);
        }
        return out;
    }

    private static double energy(float[] s, int channel) {
        double sum = 0;
        for (int i = channel; i < s.length; i += 2) sum += s[i] * s[i];
        return sum;
    }

    /** RMS of the sample-to-sample change in the left channel: a rough measure of treble. */
    private static double treble(float[] s) {
        double sum = 0;
        int n = 0;
        for (int i = 2; i < s.length; i += 2, n++) {
            double d = s[i] - s[i - 2];
            sum += d * d;
        }
        return Math.sqrt(sum / n);
    }

    private static double rms(float[] s) {
        return Math.sqrt((energy(s, 0) + energy(s, 1)) / s.length);
    }

    private AudioEventInstance play(String path, float x, float y) {
        AudioEventInstance i = backend.createInstance(path);
        i.setPosition(x, y);
        i.start();
        return i;
    }

    @Test
    void needsACurrentContext() {
        // No loopback opened: nothing is current
        assertNull(OpenAlAudioBackend.tryCreate(AudioEventCatalog.withDefaults()));
    }

    @Test
    void streamsTheSynthPlaceholderWhenThereIsNoClip() {
        start("no-such-dir");
        AudioEventInstance laser = play(AudioEventCatalog.LASER, 0f, 5f);
        laser.release();
        float[] heard = run(0.3f);
        assertTrue(rms(heard) > 0.01, "rms " + rms(heard));
        assertEquals(1, backend.getVoicesInUse());

        run(0.5f); // the 0.45 s shot is over
        assertEquals(0, backend.getVoicesInUse());
        assertEquals(0, backend.getInstances().size);
        assertTrue(rms(run(0.2f)) < 1e-4);
    }

    @Test
    void loopsKeepStreamingPastTheirBuffers() {
        start("no-such-dir");
        play(AudioEventCatalog.BREACH_HISS, 0f, 1f);
        run(1f);
        float[] later = run(0.5f);
        assertTrue(rms(later) > 0.05, "rms " + rms(later));
    }

    @Test
    void occlusionMufflesThroughTheEfxLowPass() {
        start("no-such-dir");
        assertTrue(backend.hasEfx(), "OpenAL Soft supports EFX");
        AudioEventInstance hiss = play(AudioEventCatalog.BREACH_HISS, 0f, 1f);
        run(0.2f);
        double open = treble(run(0.5f));
        hiss.setParameter(AudioParameters.OCCLUSION, 1f);
        run(0.2f);
        double muffled = treble(run(0.5f));
        assertTrue(muffled < open * 0.3, "open " + open + " muffled " + muffled);
    }

    @Test
    void positionPansAroundTheListener() {
        start("no-such-dir");
        backend.setListener(100f, 50f, 0f, 0f);
        play(AudioEventCatalog.BREACH_HISS, 120f, 50f); // 20 m to the listener's right
        run(0.2f);
        float[] s = run(0.5f);
        assertTrue(energy(s, 1) > energy(s, 0) * 3, "left " + energy(s, 0) + " right " + energy(s, 1));
    }

    @Test
    void distanceAndVacuumFollowTheAcousticModel() {
        start("no-such-dir");
        AudioEventInstance near = play(AudioEventCatalog.BREACH_HISS, 0f, 2f);
        run(0.2f);
        double nearRms = rms(run(0.5f));
        near.setPosition(0f, 20f); // 10x the 2 m minimum distance: about a tenth as loud, then the edge fade
        run(0.2f);
        double farRms = rms(run(0.5f));
        assertTrue(farRms < nearRms * 0.15 && farRms > 0, "near " + nearRms + " far " + farRms);

        near.setPosition(0f, 2f);
        near.setParameter(AudioParameters.PRESSURE, 0f);
        run(0.2f);
        assertTrue(rms(run(0.3f)) < 1e-4, "silent in vacuum");
    }

    @Test
    void aRecordedClipReplacesThePlaceholder(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("ship"));
        Files.write(dir.resolve("ship/door.wav"), WavClipTest.wav16(tone(440f, 0.8f, 44100), 1, 44100));
        start(dir.toString());
        AudioEventInstance door = play(AudioEventCatalog.DOOR, 0f, 1f);
        // The recording's length, not the catalog's 0.35 s
        assertEquals(0.8f, ((SimulatedInstance) door).def.duration, 1e-3f);
        door.release();
        float[] heard = run(0.6f);
        assertTrue(rms(heard) > 0.05, "rms " + rms(heard));
        // A 440 Hz tone crosses zero about 880 times a second
        int crossings = 0;
        for (int i = 2 * 2 * FRAME_SAMPLES * 6; i < heard.length; i += 2) if ((heard[i] >= 0) != (heard[i - 2] >= 0)) crossings++;
        float seconds = (heard.length / 2f - 2 * FRAME_SAMPLES * 6) / LoopbackOpenAl.RATE;
        assertEquals(880, crossings / seconds, 40);
        run(0.4f);
        assertEquals(0, backend.getInstances().size);
    }

    @Test
    void reportsHrtfState() {
        start("no-such-dir");
        assertEquals("disabled", backend.hrtfStatus());
    }

    private static short[] tone(float hz, float seconds, int rate) {
        short[] s = new short[(int) (seconds * rate)];
        for (int i = 0; i < s.length; i++) s[i] = (short) (Math.sin(2 * Math.PI * hz * i / rate) * 16000);
        return s;
    }
}
