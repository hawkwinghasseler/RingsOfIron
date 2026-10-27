package com.ringsofiron.combatsys.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.combatsys.defs.DamageType;

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
    /**
     * A fast pass rammed it rather than a round. audio-spatial already plays those from
     * physics-exterior's fastPassImpacts, so listeners that make sound skip them.
     */
    public boolean ram;
    /** A beam burning, not a round striking: no clang, the laser is heard at the gun. */
    public boolean beam;
    /** An ion bolt: it broke nothing, but may have stunned systems near the entry tile. */
    public boolean ion;
    /** Joules the ship's shields took off the hit before the hull felt it, 0 with none up. */
    public float shielded;
    /** How the round did its damage; a ram is kinetic. */
    public DamageType damageType = DamageType.KINETIC;
    /**
     * Where it struck, in the grid's tile space (tile (x, y) spans x..x+1, y..y+1), and which way it
     * was travelling and the hull's outward normal there, unit, in the ship's frame. For the deck
     * view's impact graphics.
     */
    public final Vector2 entry = new Vector2(), direction = new Vector2(), normal = new Vector2();
    /** Where an over-penetrating round left, tile space; the entry point otherwise. */
    public final Vector2 exit = new Vector2();
    /** Tile where a warhead went off inside the hull, or -1; its overpressure reached {@link #blastRadius} tiles. */
    public int detonationX = -1, detonationY = -1, blastRadius;
    public long tick;
}
