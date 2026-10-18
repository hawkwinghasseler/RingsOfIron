package com.ringsofiron.moddingdata.defs;

/**
 * Playback rules for one audio event path, so mods can add sounds the built-in audio backends
 * know how to play. {@code [sound_event.<id>]}; the game registers each one with audio-spatial's
 * {@code AudioEventCatalog} after loading. An event path with no entry plays as a short fallback.
 *
 * <pre>
 * [sound_event.railgun_heavy]
 * path = "event:/mods/better_railguns/railgun"
 * looping = false
 * duration = 0.6         # seconds, one-shots only
 * min_distance = 10.0    # full volume within this many metres
 * max_distance = 900.0   # silent beyond this
 * volume = 0.9
 * </pre>
 * Point an asset key at the same path in {@code assets.toml}'s {@code [audio_event]} table so code
 * that plays by key picks it up.
 */
public class SoundEventData {
    public String id;
    /** The event path, e.g. {@code event:/weapons/railgun}. Required. */
    public String path;
    public boolean looping;
    public float duration = 0.5f;
    public float minDistance = 5f;
    public float maxDistance = 200f;
    public float volume = 1f;
}
