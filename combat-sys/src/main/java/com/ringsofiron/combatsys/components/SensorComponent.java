package com.ringsofiron.combatsys.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.combatsys.fire.Track;

/**
 * A ship's sensors and the tracks they hold, one per sensed contact (combat-sys/Design.md
 * 3.1). Updated by {@code SensorTrackingSystem}; read by weapon control, missile datalinks and
 * the HUD.
 */
public class SensorComponent implements Component {
    /** meters */
    public float range = 30_000f;
    /** Position noise, 1 sigma, at 1 km; it grows linearly with range. */
    public float sigmaPerKm = 0.5f;
    /** Seconds between fixes. */
    public float fixInterval = 0.2f;
    /**
     * Crew skill 0 (green) .. 1 (veteran). Sets the tracker gains: a green crew runs gamma 0
     * and only leads on velocity. ai-crew writes this from the sensor operator's stats.
     */
    public float trackingSkill = 0.5f;
    /** Seconds without a fix before a track is dropped. */
    public float trackTimeout = 3f;
    /** The contact the crew (or player) has selected, or null. */
    public Entity lockedTarget;

    public final Array<Track> tracks = new Array<>();
    /** Seconds until the next fix. */
    public float fixTimer;

    public Track trackFor(Entity target) {
        if (target == null) return null;
        for (int i = 0; i < tracks.size; i++) {
            if (tracks.get(i).target == target) return tracks.get(i);
        }
        return null;
    }
}
