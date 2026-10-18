package com.ringsofiron.combatsys.damage;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;

/**
 * Where combat finds crew on a tile and hurts them.
 *
 * ai-crew owns the real interfaces ({@code GridOccupantQuery} and {@code CrewDamageSink} in
 * {@code com.ringsofiron.interiorgrid.crew}). Until those are on main, this is the single seam
 * combat calls through; an adapter over them is a few lines. Null means "no crew aboard".
 */
public interface CrewDamageBridge {
    /** Adds every crew entity standing on tile (x, y) of {@code ship} to {@code out}. */
    void occupantsAt(Entity ship, int x, int y, Array<Entity> out);

    /** Applies {@code amount} health points of damage (out of 100 by default). */
    void applyDamage(Entity crew, float amount, HitCause cause);
}
