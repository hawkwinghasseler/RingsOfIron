package com.ringsofiron.combatsys.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;

/** A round struck a missile in flight: point defense at work (combat-sys/Design.md 4.4). */
public class InterceptEvent {
    public Entity missile;
    /** The ship whose round it was, or null. */
    public Entity shooter;
    public final Vector2 point = new Vector2();
    /** Damage dealt, J. */
    public float energy;
    /** True if the hit broke the missile up. */
    public boolean destroyed;
    public long tick;
}
