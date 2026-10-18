package com.ringsofiron.combatsys.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.combatsys.defs.ProjectileDef;

/**
 * A round in flight on the raycast flight model (combat-sys/Design.md section 2.4). Its state
 * lives here in local meters, not in a Box2D body or a {@code TransformComponent}, so it is
 * free of Box2D's speed cap; {@code BallisticsSystem} integrates it and sweeps it for hits.
 */
public class ProjectileComponent implements Component {
    public ProjectileDef def;
    /** kg; usually {@code def.mass}, smaller for fragments. */
    public float mass;
    /** Local (bubble) meters and m/s. */
    public final Vector2 pos = new Vector2();
    public final Vector2 prevPos = new Vector2();
    public final Vector2 vel = new Vector2();
    /** Seconds since spawn. */
    public float age;
    /** The firing ship; the round ignores it until {@code def.armingTime}. Null for none. */
    public Entity owner;
    /** An entity this round may not hit until {@link #ignoreUntil} (a ship it just ricocheted off). */
    public Entity ignore;
    public float ignoreUntil;
    /**
     * Penetration budget override, J; negative means derive it from kinetic energy at impact.
     * Used by fragments, which carry a fixed share of a warhead.
     */
    public float penetration = -1f;
}
