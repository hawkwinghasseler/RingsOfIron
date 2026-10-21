package com.ringsofiron.combatsys.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.combatsys.defs.ProjectileDef;

/**
 * A round in flight on the raycast flight model (combat-sys/Design.md section 2.4). Its state
 * lives here in local meters, not in a Box2D body or a {@code TransformComponent}, so it is
 * free of Box2D's speed cap; {@code BallisticsSystem} integrates it and sweeps it for hits.
 * Rounds are transient entities and never saved, so the entity links are transient too.
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
    /**
     * {@link com.ringsofiron.combatsys.systems.BallisticsSystem#getClock} when the shot this came
     * from was fired: a warhead's fragments keep their missile's, so a hit can tell when it was fired.
     */
    public float firedAt;
    /**
     * What the weapon that fired this was engaging, kept by its fragments and ricochets, or null for a
     * shot down the barrel; a hit on anything else is stray fire.
     */
    public transient Entity aimedAt;
    /** Fired by a point-defense mount, at a missile; kept by fragments and ricochets. */
    public boolean pointDefense;
    /** The firing ship; the round ignores it until {@code def.armingTime}. Null for none. */
    public transient Entity owner;
    /** An entity this round may not hit until {@link #ignoreUntil} (a ship it just ricocheted off). */
    public transient Entity ignore;
    public float ignoreUntil;
    /**
     * Penetration budget override, J; negative means derive it from kinetic energy at impact.
     * Used by fragments, which carry a fixed share of a warhead.
     */
    public float penetration = -1f;
    /** Hit, expired or shot down this tick; waiting to leave the engine. */
    public transient boolean spent;
}
