package com.ringsofiron.audiospatial.backend;

import com.badlogic.gdx.math.MathUtils;

/**
 * A procedural placeholder sound for {@link SynthAudioBackend}, picked by event path, so the
 * spatial mix can be heard before any recorded sounds or FMOD banks exist.
 *
 * The game thread writes the {@code target*} fields; the mixer thread reads them and smooths
 * toward them to avoid clicks. Every patch has plenty of high-frequency content so the
 * occlusion low-pass is easy to hear.
 */
public class SynthVoice {

    public enum Patch { HUM, FIRE, ALARM, HISS, THRUSTER, LASER, IMPACT, DOOR, BEEP }

    final Patch patch;
    final boolean looping;
    final float duration;

    volatile float targetGain;
    volatile float targetPan;
    volatile float targetCutoff = AcousticModel.OPEN_CUTOFF_HZ;
    volatile boolean active;
    /** Bumped by {@link #restart()} so the mixer rewinds a one-shot. */
    volatile int startCount;

    // Mixer thread state
    private int seenStart = -1;
    private long sample;
    private float gain, pan, cutoff = AcousticModel.OPEN_CUTOFF_HZ;
    private float lp1, lp2;
    private float brown;
    private float pop;
    private int seed = 0x9E3779B9;

    SynthVoice(Patch patch, boolean looping, float duration) {
        this.patch = patch;
        this.looping = looping;
        this.duration = duration;
    }

    public static Patch patchFor(String path) {
        if (path.contains("reactor") || path.contains("hum") || path.contains("engine")) return Patch.HUM;
        if (path.contains("fire")) return Patch.FIRE;
        if (path.contains("alarm")) return Patch.ALARM;
        if (path.contains("hiss") || path.contains("breach") || path.contains("vent")) return Patch.HISS;
        if (path.contains("thruster")) return Patch.THRUSTER;
        if (path.contains("laser") || path.contains("weapon")) return Patch.LASER;
        if (path.contains("impact")) return Patch.IMPACT;
        if (path.contains("door")) return Patch.DOOR;
        return Patch.BEEP;
    }

    void restart() {
        startCount++;
    }

    /** Adds {@code frames} stereo frames of this voice into {@code out}. Mixer thread only. */
    void mixInto(float[] out, int frames, int sampleRate) {
        int sc = startCount;
        if (sc != seenStart) {
            seenStart = sc;
            sample = 0;
            pop = 0f;
        }
        boolean on = active;
        float tg = on ? targetGain : 0f;
        if (!on && gain < 1e-4f) return;
        float tp = targetPan, tc = targetCutoff;
        // One smoothing step per block keeps changes click-free and cheap
        float k = 0.15f;
        cutoff += (tc - cutoff) * k;
        pan += (tp - pan) * k;
        float a = 1f - (float) Math.exp(-MathUtils.PI2 * Math.min(cutoff, sampleRate * 0.45f) / sampleRate);
        float gainStep = (tg - gain) / frames;
        for (int f = 0; f < frames; f++) {
            gain += gainStep;
            float t = (float) sample / sampleRate;
            float s = looping || t < duration ? generate(t) : 0f;
            sample++;
            // Two cascaded one-pole low-passes: a 12 dB/octave muffle
            lp1 += a * (s - lp1);
            lp2 += a * (lp1 - lp2);
            float v = lp2 * gain;
            float angle = (pan + 1f) * MathUtils.PI / 4f;
            out[2 * f] += v * MathUtils.cos(angle);
            out[2 * f + 1] += v * MathUtils.sin(angle);
        }
    }

    private float noise() {
        seed ^= seed << 13;
        seed ^= seed >>> 17;
        seed ^= seed << 5;
        return (seed & 0xFFFFFF) / (float) 0x800000 - 1f;
    }

    private static float sin(float hz, float t) {
        return (float) Math.sin(MathUtils.PI2 * hz * t);
    }

    private static float saw(float hz, float t) {
        float p = hz * t;
        return 2f * (p - (float) Math.floor(p)) - 1f;
    }

    private static float square(float hz, float t) {
        return (hz * t) % 1f < 0.5f ? 1f : -1f;
    }

    private float generate(float t) {
        switch (patch) {
            case HUM:
                return 0.45f * sin(55f, t) + 0.25f * sin(110f, t) + 0.12f * saw(220f, t) + 0.05f * noise();
            case FIRE: {
                brown = brown * 0.98f + noise() * 0.06f;
                if (noise() > 0.9993f) pop = 1f;
                pop *= 0.995f;
                return 0.8f * brown + 0.35f * pop * noise();
            }
            case ALARM: {
                float phase = t % 1f;
                float hz = phase < 0.5f ? 880f : 660f;
                float gate = phase % 0.5f < 0.4f ? 1f : 0f;
                return 0.35f * gate * square(hz, t);
            }
            case HISS:
                return 0.5f * noise();
            case THRUSTER: {
                brown = brown * 0.995f + noise() * 0.03f;
                return 1.2f * brown + 0.15f * noise();
            }
            case LASER: {
                float hz = 2400f * (float) Math.exp(-t * 7f) + 200f;
                return 0.5f * square(hz, t) * (float) Math.exp(-t * 6f);
            }
            case IMPACT:
                return (0.8f * sin(50f, t) + 0.6f * noise()) * (float) Math.exp(-t * 7f);
            case DOOR:
                return (0.6f * sin(120f, t) + 0.4f * noise()) * (float) Math.exp(-t * 14f);
            default:
                return 0.3f * sin(440f, t) * (t < duration ? 1f : 0f);
        }
    }
}
