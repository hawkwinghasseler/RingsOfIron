package com.ringsofiron.audiospatial.backend;

/**
 * Playback rules for one event path: whether it loops, how long a one-shot lasts and how far
 * it carries. Backends read it from an {@link AudioEventCatalog}, which is also where mod data can register new sounds.
 */
public class SoundEventDef {
    public final String path;
    public final boolean looping;
    /** Length of a one-shot in seconds. Ignored for looping sounds. */
    public final float duration;
    /** Distance in meters within which the sound plays at full volume. */
    public final float minDistance;
    /** Distance in meters beyond which the sound is silent. */
    public final float maxDistance;
    /** Base volume, 0 to 1. */
    public final float volume;

    public SoundEventDef(String path, boolean looping, float duration, float minDistance, float maxDistance, float volume) {
        this.path = path;
        this.looping = looping;
        this.duration = duration;
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.volume = volume;
    }

    public static SoundEventDef loop(String path, float minDistance, float maxDistance, float volume) {
        return new SoundEventDef(path, true, 0f, minDistance, maxDistance, volume);
    }

    public static SoundEventDef oneShot(String path, float duration, float minDistance, float maxDistance, float volume) {
        return new SoundEventDef(path, false, duration, minDistance, maxDistance, volume);
    }
}
