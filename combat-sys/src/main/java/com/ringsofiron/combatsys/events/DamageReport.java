package com.ringsofiron.combatsys.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

/**
 * Everything one impact changed inside a ship, so crew AI can post jobs (seal, repair,
 * extinguish, rescue) without re-scanning the grid. Tiles are packed as {@code y * width + x}.
 */
public class DamageReport {
    public Entity ship;
    /** Hull tiles this impact breached, and tiles a collision crushed open to space. */
    public final IntArray breaches = new IntArray();
    /** Tiles a collision crushed flat (see interior-grid's {@code Tile#crushed}); each is in {@link #breaches} too. */
    public final IntArray crushed = new IntArray();
    /** Tiles this impact set on fire. */
    public final IntArray fires = new IntArray();
    /** Systems and conduit tiles this impact shorted out, by tile. */
    public final IntArray shorts = new IntArray();
    /** Tanks this impact burst, by the tile at the middle of each. */
    public final IntArray bursts = new IntArray();
    /** Subsystem entities that lost health, and the tile each was hit on. */
    public final Array<Entity> damagedSubsystems = new Array<>();
    public final IntArray subsystemTiles = new IntArray();
    /** Crew hit, and the tile each was standing on. */
    public final Array<Entity> injuredCrew = new Array<>();
    public final IntArray crewTiles = new IntArray();
    /** Whether walls or conduits changed, so power and compartments need rebuilding. */
    public boolean structureChanged;

    public void clear() {
        ship = null;
        breaches.clear();
        crushed.clear();
        fires.clear();
        shorts.clear();
        bursts.clear();
        damagedSubsystems.clear();
        subsystemTiles.clear();
        injuredCrew.clear();
        crewTiles.clear();
        structureChanged = false;
    }

    public boolean isEmpty() {
        return breaches.size == 0 && fires.size == 0 && shorts.size == 0 && bursts.size == 0 && damagedSubsystems.size == 0
                && injuredCrew.size == 0 && !structureChanged;
    }
}
