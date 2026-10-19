package com.ringsofiron.audiospatial.backend;

/**
 * The player's volume settings: a master level, and one each for effects (one-shot sounds such as
 * guns, impacts and doors) and ambience (looping sounds such as the reactor hum, thrusters, fire and
 * hissing breaches). An event's level is master times its group's. Read on the game thread each
 * frame, so a change is heard at once.
 */
public final class MixLevels {
    public float master = 0.8f;
    public float effects = 1f;
    public float ambience = 1f;

    /** The gain an instance of {@code def} plays at on top of the acoustic mix. */
    public float gain(SoundEventDef def) {
        return master * (def.looping ? ambience : effects);
    }

    public void set(float master, float effects, float ambience) {
        this.master = clamp(master);
        this.effects = clamp(effects);
        this.ambience = clamp(ambience);
    }

    private static float clamp(float v) {
        return Float.isNaN(v) ? 1f : Math.max(0f, Math.min(1f, v));
    }
}
