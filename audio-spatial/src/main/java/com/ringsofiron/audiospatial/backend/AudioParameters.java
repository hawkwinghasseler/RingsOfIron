package com.ringsofiron.audiospatial.backend;

/**
 * Names of the event parameters the audio systems drive. Backends apply them through
 * {@link AcousticModel}.
 */
public final class AudioParameters {
    private AudioParameters() {}

    /**
     * 0 (bright) to 1 (fully muffled). Drives the low-pass filter. Set by the occlusion system
     * from the walls and rooms between emitter and listener.
     */
    public static final String OCCLUSION = "Occlusion";
    /**
     * Volume through walls and rooms, relative to the same sound unobstructed at the same
     * distance (1 = clear). Set by the occlusion system.
     */
    public static final String TRANSMISSION = "Transmission";
    /**
     * Air pressure the sound travels through, relative to standard (0 = vacuum, 1 = normal).
     * Sound inside a vented compartment fades out with it.
     */
    public static final String PRESSURE = "Pressure";
    /** 0 to 1 strength of a one-shot, such as how hard an impact was. */
    public static final String INTENSITY = "Intensity";
}
