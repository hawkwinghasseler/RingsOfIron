package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.audiospatial.AudioManager;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.moddingdata.ModContext;

import java.util.function.Supplier;

/**
 * Plays combat on the audio bus: each weapon's {@code sound} when it fires, a hull impact at
 * every hull hit (louder for harder hits) and at every warhead going off. A weapon's sound is an
 * asset key from the {@code [audio_event]} table (such as {@code evt_railgun_fire}) or an event
 * path; weapons with none fire silently.
 */
public final class CombatAudio {
    /** Quietest a hull hit plays, as an intensity 0..1. */
    public static final float MIN_HIT_INTENSITY = 0.2f;

    private CombatAudio() {
    }

    public static void install(CombatEvents events, CombatDefs defs, AudioManager audio, Supplier<ModContext> content) {
        events.shots.add((signal, shot) -> {
            WeaponDef weapon = defs.weapons.get(shot.weaponDefId);
            String path = weapon == null ? null : eventPath(weapon.sound, content.get());
            if (path != null) audio.playOneShot(path, shot.muzzlePoint.x, shot.muzzlePoint.y);
        });
        events.hullHits.add((signal, hit) -> audio.playOneShot(AudioEventCatalog.HULL_IMPACT,
                hit.worldPoint.x, hit.worldPoint.y, Math.max(MIN_HIT_INTENSITY, hit.severity)));
        events.detonations.add((signal, boom) -> audio.playOneShot(AudioEventCatalog.HULL_IMPACT,
                boom.worldPoint.x, boom.worldPoint.y, 1f));
    }

    /** An asset key resolved through the loaded content, or the value itself if it is a path. */
    static String eventPath(String sound, ModContext content) {
        if (sound == null || sound.isEmpty()) return null;
        if (content != null && content.assets.has(sound)) return content.assets.getString(sound);
        return sound.startsWith("event:") ? sound : null;
    }
}
