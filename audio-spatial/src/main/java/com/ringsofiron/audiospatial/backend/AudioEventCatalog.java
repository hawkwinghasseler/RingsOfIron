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
    public static final String AUTOCANNON = "event:/weapons/autocannon";
    public static final String MISSILE_LAUNCH = "event:/weapons/missile_launch";
    /** A missile's motor, looped on an emitter that follows the missile. */
    public static final String MISSILE_FLIGHT = "event:/weapons/missile_flight";
    /** A warhead going off. */
    public static final String EXPLOSION = "event:/impacts/explosion";
    /** One crew footstep on the deck. */
    public static final String FOOTSTEP = "event:/crew/footstep";
    /** A crew member calling out: taking a job, or crying out when knocked down. */
    public static final String VOICE = "event:/crew/voice";
    // Paths named in assets.toml's [audio_event] table (modding-data's AssetRegistry keys
    // evt_railgun_fire and evt_hull_breach)
    public static final String RAILGUN = "event:/weapons/railgun";
    public static final String HULL_BREACH = "event:/interior/hull_breach";
    // Round 3's weapons (keys evt_flak_fire, evt_railgun_heavy_fire, evt_ion_fire, evt_ion_hit, evt_shield_hit)
    public static final String FLAK = "event:/weapons/flak";
    public static final String RAILGUN_HEAVY = "event:/weapons/railgun_heavy";
    public static final String ION = "event:/weapons/ion";
    /** An ion bolt landing on a hull: a crackle, no clang. */
    public static final String ION_HIT = "event:/impacts/ion";
    /** A shield taking part of a hit. */
    public static final String SHIELD_HIT = "event:/impacts/shield";

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
        c.register(SoundEventDef.oneShot(LASER, 0.45f, 10f, 600f, 0.6f));
        c.register(SoundEventDef.oneShot(HULL_IMPACT, 1.2f, 20f, 1500f, 1f));
        c.register(SoundEventDef.oneShot(AUTOCANNON, 0.18f, 10f, 700f, 0.7f));
        c.register(SoundEventDef.oneShot(MISSILE_LAUNCH, 1.2f, 15f, 900f, 0.8f));
        c.register(SoundEventDef.loop(MISSILE_FLIGHT, 8f, 500f, 0.5f));
        c.register(SoundEventDef.oneShot(EXPLOSION, 2.5f, 40f, 3000f, 1f));
        c.register(SoundEventDef.oneShot(RAILGUN, 0.45f, 10f, 800f, 0.8f));
        c.register(SoundEventDef.loop(HULL_BREACH, 2f, 40f, 0.8f));
        c.register(SoundEventDef.oneShot(FLAK, 0.3f, 10f, 700f, 0.7f));
        c.register(SoundEventDef.oneShot(RAILGUN_HEAVY, 0.8f, 15f, 1200f, 1f));
        c.register(SoundEventDef.oneShot(ION, 0.5f, 10f, 600f, 0.65f));
        c.register(SoundEventDef.oneShot(ION_HIT, 0.6f, 10f, 800f, 0.7f));
        c.register(SoundEventDef.oneShot(SHIELD_HIT, 0.5f, 10f, 800f, 0.6f));
        c.register(SoundEventDef.oneShot(FOOTSTEP, 0.12f, 1f, 15f, 0.35f));
        c.register(SoundEventDef.oneShot(VOICE, 0.6f, 1.5f, 25f, 0.6f));
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
