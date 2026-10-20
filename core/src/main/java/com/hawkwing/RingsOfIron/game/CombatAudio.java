package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.ringsofiron.audiospatial.AudioManager;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.audiospatial.systems.AcousticOcclusionSystem;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.systems.ImpactResolutionSystem;
import com.ringsofiron.moddingdata.ModContext;

import java.util.function.Supplier;

/**
 * Plays combat on the audio bus: each weapon's {@code sound} when it fires, a motor loop that
 * follows each missile in flight, a hull impact at every hull hit by a round (louder for harder
 * hits) and an explosion at every warhead going off; an ion bolt crackles instead of a hull
 * impact, and a shield shimmers whenever it takes part of a hit. Sounds are asset keys from the
 * {@code [audio_event]} table (such as {@code evt_railgun_fire}) or event paths. A weapon whose
 * sound is missing or not in the table plays {@link #DEFAULT_SHOT}, which the audio catalog plays
 * with its fallback one-shot; without {@link #EXPLOSION_KEY} warheads play a hull impact, and
 * without {@link #MISSILE_FLIGHT_KEY} missiles fly silent.
 */
public final class CombatAudio {
    /** Quietest a hull hit plays, as an intensity 0..1. */
    public static final float MIN_HIT_INTENSITY = 0.2f;
    /** Played for a weapon whose data names no sound. */
    public static final String DEFAULT_SHOT = "event:/weapons/default";
    /** Asset keys for the missile motor loop and for a warhead going off. */
    public static final String MISSILE_FLIGHT_KEY = "evt_missile_flight", EXPLOSION_KEY = "evt_explosion";
    /** Asset keys for an ion bolt landing (in place of a hull impact) and a shield taking part of a hit. */
    public static final String ION_HIT_KEY = "evt_ion_hit", SHIELD_HIT_KEY = "evt_shield_hit";

    private CombatAudio() {
    }

    public static void install(Engine engine, CombatEvents events, CombatDefs defs, AudioManager audio,
                               Supplier<ModContext> content) {
        events.shots.add((signal, shot) -> {
            WeaponDef weapon = defs.weapons.get(shot.weaponDefId);
            if (weapon != null) audio.playOneShot(eventPath(weapon.sound, content.get()),
                    shot.muzzlePoint.x, shot.muzzlePoint.y);
            if (shot.round != null && shot.round.getComponent(MissileComponent.class) != null) {
                String motor = keyPath(MISSILE_FLIGHT_KEY, content.get());
                if (motor != null) {
                    AudioEmitterComponent emitter = new AudioEmitterComponent(motor);
                    emitter.worldX = shot.muzzlePoint.x;
                    emitter.worldY = shot.muzzlePoint.y;
                    shot.round.add(emitter);
                }
            }
        });
        events.hullHits.add((signal, hit) -> {
            // A shield shimmers whenever it takes part of a hit, a beam's included
            if (hit.shielded > 0f) {
                String shield = keyPath(SHIELD_HIT_KEY, content.get());
                if (shield != null) audio.playOneShot(shield, hit.worldPoint.x, hit.worldPoint.y,
                        Math.max(MIN_HIT_INTENSITY, Math.min(1f, hit.shielded / ImpactResolutionSystem.SEVERITY_SCALE)));
            }
            // Rams are heard through audio-spatial's own fast-pass listener, and a beam at its gun.
            if (hit.ram || hit.beam) return;
            if (hit.ion) {
                String ion = keyPath(ION_HIT_KEY, content.get());
                if (ion != null) audio.playOneShot(ion, hit.worldPoint.x, hit.worldPoint.y, 1f);
                return;
            }
            audio.playOneShot(AudioEventCatalog.HULL_IMPACT,
                    hit.worldPoint.x, hit.worldPoint.y, Math.max(MIN_HIT_INTENSITY, hit.severity));
        });
        events.detonations.add((signal, boom) -> {
            String path = keyPath(EXPLOSION_KEY, content.get());
            audio.playOneShot(path != null ? path : AudioEventCatalog.HULL_IMPACT,
                    boom.worldPoint.x, boom.worldPoint.y, 1f);
        });
        engine.addSystem(new MissileMotors());
    }

    /** A weapon's sound as an event path: an asset key, an event path, or {@link #DEFAULT_SHOT}. */
    static String eventPath(String sound, ModContext content) {
        if (sound == null || sound.isEmpty()) return DEFAULT_SHOT;
        if (sound.startsWith("event:")) return sound;
        String path = keyPath(sound, content);
        return path != null ? path : DEFAULT_SHOT;
    }

    /** The event path an asset key names in the loaded content, or null. */
    static String keyPath(String key, ModContext content) {
        return content != null && content.assets.has(key) ? content.assets.getString(key) : null;
    }

    /**
     * Keeps each missile's motor loop where the missile is. Missiles have no transform, so the
     * emitter is moved by hand just before the audio systems run; removing the missile stops it.
     */
    static class MissileMotors extends IteratingSystem {
        private final ComponentMapper<ProjectileComponent> rounds = ComponentMapper.getFor(ProjectileComponent.class);
        private final ComponentMapper<AudioEmitterComponent> emitters = ComponentMapper.getFor(AudioEmitterComponent.class);

        MissileMotors() {
            super(Family.all(MissileComponent.class, ProjectileComponent.class, AudioEmitterComponent.class).get(),
                    AcousticOcclusionSystem.DEFAULT_PRIORITY - 1);
        }

        @Override
        protected void processEntity(Entity entity, float deltaTime) {
            ProjectileComponent p = rounds.get(entity);
            AudioEmitterComponent e = emitters.get(entity);
            e.worldX = p.pos.x;
            e.worldY = p.pos.y;
        }
    }
}
