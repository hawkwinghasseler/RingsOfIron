package com.ringsofiron.audiospatial.backend;

import com.badlogic.gdx.math.MathUtils;

/**
 * A procedural placeholder sound for {@link SynthAudioBackend}, picked by event path, so the
 * spatial mix can be heard before any recorded sounds exist.
 *
 * The game thread writes the {@code target*} fields; the mixer thread reads them and smooths
 * toward them to avoid clicks. Every patch has plenty of high-frequency content so the
 * occlusion low-pass is easy to hear.
 */
public class SynthVoice {

    public enum Patch { HUM, FIRE, ALARM, HISS, THRUSTER, LASER, IMPACT, DOOR, AUTOCANNON, LAUNCH, MISSILE, EXPLOSION, FOOTSTEP, VOICE, FLAK, ION, SHIELD, BEEP }

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
    /** Phase accumulators (cycles) for patches whose pitch moves, so sweeps stay clean. */
    private double phaseA;
    private int seed = 0x9E3779B9;

    SynthVoice(Patch patch, boolean looping, float duration) {
        this.patch = patch;
        this.looping = looping;
        this.duration = duration;
    }

    public static Patch patchFor(String path) {
        // Match on the name only: the "event:" scheme itself contains "vent"
        if (path.startsWith("event:")) path = path.substring("event:".length());
        // Weapon sounds first: their paths also contain "weapon"
        if (path.contains("autocannon")) return Patch.AUTOCANNON;
        if (path.contains("flak")) return Patch.FLAK;
        // By folder: "ion" is in "explosion" too
        if (path.endsWith("weapons/ion") || path.endsWith("impacts/ion")) return Patch.ION;
        if (path.contains("shield")) return Patch.SHIELD;
        if (path.contains("missile_launch")) return Patch.LAUNCH;
        if (path.contains("missile")) return Patch.MISSILE;
        if (path.contains("explosion")) return Patch.EXPLOSION;
        if (path.contains("reactor") || path.contains("hum") || path.contains("engine")) return Patch.HUM;
        if (path.contains("fire")) return Patch.FIRE;
        if (path.contains("alarm")) return Patch.ALARM;
        if (path.contains("hiss") || path.contains("breach") || path.contains("vent")) return Patch.HISS;
        if (path.contains("thruster")) return Patch.THRUSTER;
        if (path.contains("laser") || path.contains("weapon")) return Patch.LASER;
        if (path.contains("impact")) return Patch.IMPACT;
        if (path.contains("door")) return Patch.DOOR;
        if (path.contains("footstep")) return Patch.FOOTSTEP;
        if (path.contains("voice")) return Patch.VOICE;
        return Patch.BEEP;
    }

    void restart() {
        startCount++;
    }

    /** Adds {@code frames} stereo frames of this voice into {@code out}. Mixer thread only. */
    void mixInto(float[] out, int frames, int sampleRate) {
        rewindIfRestarted();
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
            float s = nextSample(sampleRate);
            // Two cascaded one-pole low-passes: a 12 dB/octave muffle
            lp1 += a * (s - lp1);
            lp2 += a * (lp1 - lp2);
            float v = lp2 * gain;
            float angle = (pan + 1f) * MathUtils.PI / 4f;
            out[2 * f] += v * MathUtils.cos(angle);
            out[2 * f + 1] += v * MathUtils.sin(angle);
        }
    }

    /**
     * Writes {@code frames} mono samples of the bare patch into {@code out}: no gain, filter or
     * pan, for a backend that applies those itself. Use either this or {@link #mixInto}, not both.
     */
    void renderDry(float[] out, int frames, int sampleRate) {
        rewindIfRestarted();
        for (int f = 0; f < frames; f++) out[f] = nextSample(sampleRate);
    }

    private void rewindIfRestarted() {
        int sc = startCount;
        if (sc != seenStart) {
            seenStart = sc;
            sample = 0;
            pop = 0f;
            phaseA = 0;
        }
    }

    private float nextSample(int sampleRate) {
        float t = (float) sample / sampleRate;
        sample++;
        // One-shots fade over their last 30 ms instead of stopping with a click
        return looping ? generate(t) : t < duration ? generate(t) * Math.min(1f, (duration - t) / 0.03f) : 0f;
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

    private static float square(float hz, float t) {
        return (hz * t) % 1f < 0.5f ? 1f : -1f;
    }

    /** Advances a phase accumulator by {@code hz} for one sample and returns the new phase. */
    private static double step(double phase, float hz) {
        double p = phase + hz / (double) SynthAudioBackend.SAMPLE_RATE;
        return p - Math.floor(p);
    }

    /** A band-limited buzz: the first {@code harmonics} partials of a sawtooth. */
    private static float buzz(double phase, int harmonics) {
        float s = 0f;
        for (int k = 1; k <= harmonics; k++) s += (float) Math.sin(MathUtils.PI2 * k * phase) / k;
        return s * 0.6f;
    }

    private float generate(float t) {
        switch (patch) {
            case HUM: {
                // A deep generator hum: 50 Hz and its first few harmonics, slowly beating
                float wobble = 1f + 0.08f * sin(0.7f, t);
                return wobble * (0.55f * sin(50f, t) + 0.3f * sin(100f, t) + 0.12f * sin(150f, t)
                        + 0.05f * sin(200f, t) + 0.03f * sin(301f, t));
            }
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
                // "Bzap": a buzzy tone diving from 1.4 kHz to 120 Hz, warbled at 40 Hz
                float hz = 120f + 1300f * (float) Math.exp(-t * 14f);
                hz *= 1f + 0.12f * sin(40f, t);
                phaseA = step(phaseA, hz);
                float env = Math.min(1f, t * 400f) * (float) Math.exp(-t * 9f);
                return 0.7f * env * buzz(phaseA, 8);
            }
            case IMPACT: {
                // A whump with a metallic ring: a thud dropping 110 -> 45 Hz, plus inharmonic hull partials
                float thudHz = 45f + 65f * (float) Math.exp(-t * 25f);
                phaseA = step(phaseA, thudHz);
                float thud = (float) Math.sin(MathUtils.PI2 * phaseA) * (float) Math.exp(-t * 6f);
                float ring = 0.35f * sin(187f, t) * (float) Math.exp(-t * 3.0f)
                        + 0.22f * sin(431f, t) * (float) Math.exp(-t * 4.5f)
                        + 0.14f * sin(743f, t) * (float) Math.exp(-t * 6f)
                        + 0.08f * sin(1187f, t) * (float) Math.exp(-t * 9f);
                float click = t < 0.006f ? 0.5f * noise() : 0f;
                return 0.9f * thud + 0.5f * ring + click;
            }
            case DOOR:
                return (0.6f * sin(120f, t) + 0.4f * noise()) * (float) Math.exp(-t * 14f);
            case AUTOCANNON: {
                // A short "chug": a punchy thump dropping 95 -> 50 Hz under a crack of noise
                float hz = 50f + 45f * (float) Math.exp(-t * 40f);
                phaseA = step(phaseA, hz);
                float thump = (float) Math.sin(MathUtils.PI2 * phaseA) * (float) Math.exp(-t * 22f);
                float crack = noise() * (float) Math.exp(-t * 55f);
                return 0.9f * thump + 0.6f * crack;
            }
            case LAUNCH: {
                // Ignition crack, then a rushing whoosh that swells and fades as the missile leaves
                brown = brown * 0.97f + noise() * 0.08f;
                float swell = Math.min(1f, t * 12f) * (float) Math.exp(-t * 2.5f);
                float crack = noise() * (float) Math.exp(-t * 30f);
                return 0.9f * swell * (1.5f * brown + 0.3f * noise()) + 0.5f * crack;
            }
            case MISSILE: {
                // A rocket motor: rough roar fluttering at 23 Hz, brighter than a ship's thruster
                brown = brown * 0.985f + noise() * 0.05f;
                return (1f + 0.25f * sin(23f, t)) * (0.9f * brown + 0.25f * noise());
            }
            case EXPLOSION: {
                // A boom: a crack, a sub-bass drop from 70 to 28 Hz and a long rumbling tail
                float hz = 28f + 42f * (float) Math.exp(-t * 6f);
                phaseA = step(phaseA, hz);
                float boom = (float) Math.sin(MathUtils.PI2 * phaseA) * (float) Math.exp(-t * 2.5f);
                brown = brown * 0.992f + noise() * 0.04f;
                float rumble = 2.5f * brown * (float) Math.exp(-t * 1.4f);
                float crack = noise() * (float) Math.exp(-t * 25f);
                return 0.8f * boom + 0.7f * rumble + 0.5f * crack;
            }
            case FOOTSTEP: {
                // A boot on deck plating: a soft thud dropping 90 -> 55 Hz and a short metal tick
                float hz = 55f + 35f * (float) Math.exp(-t * 50f);
                phaseA = step(phaseA, hz);
                float thud = (float) Math.sin(MathUtils.PI2 * phaseA) * (float) Math.exp(-t * 30f);
                float tick = 0.3f * sin(1630f, t) * (float) Math.exp(-t * 90f) + (t < 0.004f ? 0.3f * noise() : 0f);
                return 0.7f * thud + tick;
            }
            case VOICE: {
                // A wordless murmur: a buzzy 130-190 Hz voice, syllables at 5 Hz, falling at the end
                float hz = 165f + 25f * sin(2.3f, t) - 40f * Math.max(0f, t - duration * 0.6f) / Math.max(duration, 0.01f);
                phaseA = step(phaseA, hz);
                float syllables = 0.55f - 0.45f * (float) Math.cos(MathUtils.PI2 * 5f * t);
                float env = Math.min(1f, t * 40f);
                return 0.45f * env * syllables * buzz(phaseA, 12);
            }
            case FLAK: {
                // A dull "pok": a low pop under a burst of filtered noise, softer than the autocannon
                float hz = 70f + 60f * (float) Math.exp(-t * 30f);
                phaseA = step(phaseA, hz);
                float pop = (float) Math.sin(MathUtils.PI2 * phaseA) * (float) Math.exp(-t * 14f);
                brown = brown * 0.9f + noise() * 0.2f;
                return 0.8f * pop + 0.9f * brown * (float) Math.exp(-t * 20f);
            }
            case ION: {
                // A crackling zap: a buzz rising 200 -> 900 Hz, chopped by sparks of noise
                float hz = 900f - 700f * (float) Math.exp(-t * 18f);
                phaseA = step(phaseA, hz);
                float env = Math.min(1f, t * 300f) * (float) Math.exp(-t * 7f);
                float spark = noise() > 0.6f ? noise() : 0f;
                return env * (0.5f * buzz(phaseA, 6) + 0.45f * spark);
            }
            case SHIELD: {
                // A glassy shimmer: two close high tones beating, fading fast
                float env = Math.min(1f, t * 200f) * (float) Math.exp(-t * 8f);
                return 0.4f * env * (sin(1320f, t) + sin(1347f, t) + 0.5f * sin(2650f, t));
            }
            default:
                return 0.3f * sin(440f, t) * (t < duration ? 1f : 0f);
        }
    }
}
