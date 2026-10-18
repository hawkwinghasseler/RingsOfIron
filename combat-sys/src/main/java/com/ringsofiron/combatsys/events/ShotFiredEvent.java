package com.ringsofiron.combatsys.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;

/** A weapon fired a round or launched a missile. */
public class ShotFiredEvent {
    public Entity shooter;
    /** The new projectile or missile entity. */
    public Entity round;
    public String weaponDefId;
    public final Vector2 muzzlePoint = new Vector2();
    public final Vector2 direction = new Vector2();
}
