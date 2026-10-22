package com.ringsofiron.audiospatial.backend;

import com.badlogic.gdx.math.MathUtils;

/**
 * How distance, transmission, occlusion and air pressure turn into what the listener hears. This is the
 * mixing math for every backend, so the synth and OpenAL backends sound alike.
 */
public final class AcousticModel {
    private AcousticModel() {}

    /** Low-pass cutoff with nothing in the way: effectively unfiltered. */
    public static final float OPEN_CUTOFF_HZ = 20000f;
    /** Low-pass cutoff at full occlusion: a dull thud through the bulkheads. */
    public static final float MUFFLED_CUTOFF_HZ = 350f;
    /**
     * Low-pass cutoff in near vacuum: only the lowest thump of a sound gets through. Thin air
     * absorbs high frequencies first, so as the pressure falls a sound loses its top before it
     * fades: see {@link #pressureCutoffHz}.
     */
    public static final float VACUUM_CUTOFF_HZ = 150f;
    /** The frequency OpenAL's EFX low-pass filter sets its high-frequency gain at. */
    public static final float EFX_LOWPASS_REFERENCE_HZ = 5000f;
    /** Share of a sound's maximum distance over which it fades to silence. */
    public static final float EDGE_FADE = 0.2f;

    /** The result of {@link #mix}: what a backend should apply to one voice. */
    public static class Mix {
        /** Final volume, 0 to 1. */
        public float gain;
        /** -1 (left) to 1 (right). */
        public float pan;
        /** Low-pass filter cutoff in Hz. */
        public float cutoffHz;
        /** Distance from the listener in meters. */
        public float distance;
    }

    /** Volume from distance alone: inverse rolloff past {@code minDistance}, faded to 0 at {@code maxDistance}. */
    public static float distanceGain(float distance, float minDistance, float maxDistance) {
        if (distance >= maxDistance) return 0f;
        float rolloff = minDistance / Math.max(distance, minDistance);
        float fadeStart = maxDistance * (1f - EDGE_FADE);
        float fade = distance <= fadeStart ? 1f : (maxDistance - distance) / (maxDistance - fadeStart);
        return rolloff * fade;
    }

    /** Low-pass cutoff for an occlusion of 0 to 1, falling exponentially so each wall sounds like a step. */
    public static float cutoffHz(float occlusion) {
        float o = MathUtils.clamp(occlusion, 0f, 1f);
        return OPEN_CUTOFF_HZ * (float) Math.pow(MUFFLED_CUTOFF_HZ / OPEN_CUTOFF_HZ, o);
    }

    /**
     * The high-frequency gain (0 to 1) for an OpenAL EFX low-pass that matches a 12 dB/octave
     * low-pass at {@code cutoffHz}. EFX filters take a gain at {@link #EFX_LOWPASS_REFERENCE_HZ}
     * rather than a cutoff, so this is that low-pass's response at the reference frequency.
     */
    public static float lowPassGainHf(float cutoffHz) {
        float r = EFX_LOWPASS_REFERENCE_HZ / Math.max(cutoffHz, 1f);
        return MathUtils.clamp((float) (1.0 / Math.sqrt(1.0 + r * r * r * r)), 0f, 1f);
    }

    /**
     * Low-pass cutoff for air at {@code pressure} (0 vacuum to 1 standard). Thin air absorbs high
     * frequencies far more than low ones, so the cutoff falls fast as the air thins: about 4.5 kHz
     * at half pressure, 600 Hz at a tenth and {@link #VACUUM_CUTOFF_HZ} in vacuum.
     */
    public static float pressureCutoffHz(float pressure) {
        float thin = 1f - (float) Math.sqrt(MathUtils.clamp(pressure, 0f, 1f));
        return OPEN_CUTOFF_HZ * (float) Math.pow(VACUUM_CUTOFF_HZ / OPEN_CUTOFF_HZ, thin);
    }

    /** Sound needs air: volume falls with the square root of pressure and is gone in vacuum. */
    public static float pressureGain(float pressure) {
        return (float) Math.sqrt(MathUtils.clamp(pressure, 0f, 1f));
    }

    /**
     * Mixes one voice at (dx, dy) meters from the listener. {@code transmission} scales the
     * volume for walls and rooms in the way (see {@code SoundPropagation}); {@code occlusion}
     * and {@code pressure} set the muffling, whichever muffles more.
     */
    public static Mix mix(float dx, float dy, SoundEventDef def, float occlusion, float transmission, float pressure, Mix out) {
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        out.distance = d;
        out.gain = def.volume * distanceGain(d, def.minDistance, def.maxDistance)
                * Math.max(transmission, 0f) * pressureGain(pressure);
        // Sounds right on top of the listener sit in the middle; pan widens with distance.
        out.pan = MathUtils.clamp(dx / (Math.abs(dx) + def.minDistance), -1f, 1f);
        out.cutoffHz = Math.min(cutoffHz(occlusion), pressureCutoffHz(pressure));
        return out;
    }
}
