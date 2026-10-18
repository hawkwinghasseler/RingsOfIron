package com.ringsofiron.audiospatial.backend;

import com.badlogic.gdx.utils.ObjectMap;

/**
 * Event paths known to the built-in backends and how each one plays. Unknown paths play as a
 * short one-shot with {@link #fallback} distances, so a typo is audible rather than silent.
 */
public class AudioEventCatalog {

    public static final String REACTOR_HUM = "event:/ship/reactor_hum";
    public static final String THRUSTER = "event:/ship/thruster";
    public static final String ALARM = "event:/ship/alarm";
    public static final String DOOR = "event:/ship/door";
    public static final String FIRE = "event:/hazards/fire";
    public static final String BREACH_HISS = "event:/hazards/breach_hiss";
    public static final String LASER = "event:/weapons/laser";
    public static final String HULL_IMPACT = "event:/impacts/hull";

    private final ObjectMap<String, SoundEventDef> defs = new ObjectMap<>();
    private SoundEventDef fallback = SoundEventDef.oneShot("", 0.5f, 5f, 200f, 1f);

    /** A catalog holding the game's stock events. */
    public static AudioEventCatalog withDefaults() {
        AudioEventCatalog c = new AudioEventCatalog();
        // Interior sounds carry a ship's length; exterior ones carry through the frame ("Spacer Ears").
        c.register(SoundEventDef.loop(REACTOR_HUM, 3f, 60f, 0.6f));
        c.register(SoundEventDef.loop(THRUSTER, 10f, 400f, 0.7f));
        c.register(SoundEventDef.loop(ALARM, 3f, 60f, 0.5f));
        c.register(SoundEventDef.loop(FIRE, 2f, 40f, 0.7f));
        c.register(SoundEventDef.loop(BREACH_HISS, 2f, 40f, 0.8f));
        c.register(SoundEventDef.oneShot(DOOR, 0.35f, 2f, 40f, 0.8f));
        c.register(SoundEventDef.oneShot(LASER, 0.3f, 10f, 600f, 0.6f));
        c.register(SoundEventDef.oneShot(HULL_IMPACT, 0.6f, 20f, 1500f, 1f));
        return c;
    }

    public void register(SoundEventDef def) {
        defs.put(def.path, def);
    }

    public boolean contains(String path) {
        return defs.containsKey(path);
    }

    /** The definition for {@code path}, or {@link #fallback} if the path is not registered. */
    public SoundEventDef get(String path) {
        SoundEventDef d = defs.get(path);
        return d != null ? d : fallback;
    }

    public void setFallback(SoundEventDef fallback) {
        this.fallback = fallback;
    }
}
