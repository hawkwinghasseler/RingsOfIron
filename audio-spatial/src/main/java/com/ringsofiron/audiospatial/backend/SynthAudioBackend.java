package com.ringsofiron.audiospatial.backend;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Plays procedural placeholder sounds ({@link SynthVoice}) through libGDX's raw
 * {@link AudioDevice}, with distance attenuation, stereo panning and a real low-pass filter for
 * occlusion. It lets the spatial and muffling behaviour be heard and tuned before FMOD banks
 * exist. Mixing runs on its own daemon thread.
 *
 * Requires a running libGDX application with audio ({@code Gdx.audio}).
 */
public class SynthAudioBackend extends SimulatedBackend {

    public static final int SAMPLE_RATE = 44100;
    private static final int BLOCK_FRAMES = 512;

    private final AudioDevice device;
    private final CopyOnWriteArrayList<SynthVoice> voices = new CopyOnWriteArrayList<>();
    private final Thread mixer;
    private volatile boolean running = true;
    private volatile float masterVolume = 0.8f;

    public SynthAudioBackend() {
        this(AudioEventCatalog.withDefaults());
    }

    public SynthAudioBackend(AudioEventCatalog catalog) {
        super(catalog);
        if (Gdx.audio == null) throw new IllegalStateException("libGDX audio is not available");
        device = Gdx.audio.newAudioDevice(SAMPLE_RATE, false);
        mixer = new Thread(this::mixLoop, "audio-synth-mixer");
        mixer.setDaemon(true);
        mixer.start();
    }

    public void setMasterVolume(float volume) {
        masterVolume = volume;
    }

    private static class SynthInstance extends SimulatedInstance {
        final SynthVoice voice;

        SynthInstance(SoundEventDef def) {
            super(def);
            voice = new SynthVoice(SynthVoice.patchFor(def.path), def.looping, def.duration);
        }

        @Override
        public void start() {
            super.start();
            voice.restart();
        }
    }

    @Override
    protected SimulatedInstance createSimulated(SoundEventDef def) {
        SynthInstance i = new SynthInstance(def);
        voices.add(i.voice);
        return i;
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        float master = masterVolume;
        for (SimulatedInstance inst : instances) {
            SynthVoice v = ((SynthInstance) inst).voice;
            v.targetGain = inst.mix.gain * master;
            v.targetPan = inst.mix.pan;
            v.targetCutoff = inst.mix.cutoffHz;
            v.active = inst.isPlaying();
        }
    }

    @Override
    protected void onRemoved(SimulatedInstance instance) {
        voices.remove(((SynthInstance) instance).voice);
    }

    private void mixLoop() {
        float[] block = new float[BLOCK_FRAMES * 2];
        while (running) {
            java.util.Arrays.fill(block, 0f);
            for (SynthVoice v : voices) v.mixInto(block, BLOCK_FRAMES, SAMPLE_RATE);
            for (int i = 0; i < block.length; i++) {
                // Soft clip so many loud voices saturate instead of wrapping
                float s = block[i];
                block[i] = s / (1f + Math.abs(s));
            }
            device.writeSamples(block, 0, block.length); // blocks, pacing this thread
        }
    }

    @Override
    public void dispose() {
        running = false;
        try {
            mixer.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        voices.clear();
        device.dispose();
        super.dispose();
    }
}
