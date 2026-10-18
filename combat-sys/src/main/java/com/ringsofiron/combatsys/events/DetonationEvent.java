package com.ringsofiron.combatsys.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.combatsys.defs.WarheadDef;

/** A warhead going off at a point in space (proximity fuse). Section 4.5. */
public class DetonationEvent {
    public final Vector2 worldPoint = new Vector2();
    /** Warhead velocity in the bubble frame; fragments inherit it. */
    public final Vector2 velocity = new Vector2();
    public WarheadDef warhead;
    public Entity owner;
    /** The entity the fuse detected, if any; fragments are thrown toward it. */
    public Entity target;
    public long tick;
}
