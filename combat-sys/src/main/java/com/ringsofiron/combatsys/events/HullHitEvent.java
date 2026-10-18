package com.ringsofiron.combatsys.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;

/**
 * One resolved hit on a ship, for ui-hud (flash the wireframe minimap) and audio-spatial
 * (a located, muffled impact). Section 5.8.
 */
public class HullHitEvent {
    public enum Outcome {
        /** Stopped by the hull. */
        STOPPED,
        /** Glanced off. */
        RICOCHET,
        /** Got inside and spent itself there. */
        PENETRATED,
        /** Went clean through and out the far side. */
        OVER_PENETRATED
    }

    public Entity ship;
    /** Entry tile. */
    public int tileX, tileY;
    public final Vector2 worldPoint = new Vector2();
    public Outcome outcome;
    /** Energy the ship absorbed, J. */
    public float energyAbsorbed;
    /** 0..1: energy absorbed relative to the toughest hull tile's hit points, clamped. */
    public float severity;
    public long tick;
}
