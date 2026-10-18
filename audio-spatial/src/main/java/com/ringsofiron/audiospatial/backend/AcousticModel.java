package com.ringsofiron.audiospatial.backend;

import com.badlogic.gdx.math.MathUtils;

/**
 * How distance, occlusion and air pressure turn into what the listener hears. This is the
 * mixing math for the built-in backends and the reference curve for the FMOD Studio project's
 * Occlusion and Pressure automation.
 */
public final class AcousticModel {
    private AcousticModel() {}

    /** Low-pass cutoff with nothing in the way: effectively unfiltered. */
    public static final float OPEN_CUTOFF_HZ = 20000f;
    /** Low-pass cutoff at full occlusion: a dull thud through the bulkheads. */
    public static final float MUFFLED_CUTOFF_HZ = 350f;
    /** Share of the volume lost at full occlusion, on top of the filtering. */
    public static final float OCCLUSION_VOLUME_LOSS = 0.65f;
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

    public static float occlusionGain(float occlusion) {
        return 1f - OCCLUSION_VOLUME_LOSS * MathUtils.clamp(occlusion, 0f, 1f);
    }

    /** Sound needs air: volume falls with the square root of pressure and is gone in vacuum. */
    public static float pressureGain(float pressure) {
        return (float) Math.sqrt(MathUtils.clamp(pressure, 0f, 1f));
    }

    /**
     * Mixes one voice at (dx, dy) meters from the listener.
     */
    public static Mix mix(float dx, float dy, SoundEventDef def, float occlusion, float pressure, Mix out) {
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        out.distance = d;
        out.gain = def.volume * distanceGain(d, def.minDistance, def.maxDistance)
                * occlusionGain(occlusion) * pressureGain(pressure);
        // Sounds right on top of the listener sit in the middle; pan widens with distance.
        out.pan = MathUtils.clamp(dx / (Math.abs(dx) + def.minDistance), -1f, 1f);
        out.cutoffHz = cutoffHz(occlusion);
        return out;
    }
}
