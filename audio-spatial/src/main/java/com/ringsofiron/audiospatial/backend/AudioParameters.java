package com.ringsofiron.audiospatial.backend;

/**
 * Names of the event parameters the audio systems drive. In FMOD Studio these are the
 * parameters the sound designer wires to filters and volume; the built-in backends apply them
 * through {@link AcousticModel}.
 */
public final class AudioParameters {
    private AudioParameters() {}

    /**
     * 0 (clear line) to 1 (fully muffled). Drives the low-pass filter and some volume loss.
     * Set by the occlusion system from the number of walls between emitter and listener.
     */
    public static final String OCCLUSION = "Occlusion";
    /**
     * Air pressure the sound travels through, relative to standard (0 = vacuum, 1 = normal).
     * Sound inside a vented compartment fades out with it.
     */
    public static final String PRESSURE = "Pressure";
    /** 0 to 1 strength of a one-shot, such as how hard an impact was. */
    public static final String INTENSITY = "Intensity";
}
