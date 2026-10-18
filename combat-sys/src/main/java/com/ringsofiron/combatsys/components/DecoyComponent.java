package com.ringsofiron.combatsys.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;

/**
 * A countermeasure in flight (combat-sys/Design.md 4.4): a short-lived bright signature that a
 * missile seeker may lock instead of the ship that dropped it. It carries a
 * {@link SignatureComponent} and drifts ballistically; {@code MissileGuidanceSystem} flies it.
 * Transient, never saved.
 */
public class DecoyComponent implements Component {
    public final Vector2 pos = new Vector2();
    public final Vector2 vel = new Vector2();
    public float age;
    public float lifetime = 8f;
    /** The ship that dropped it. */
    public transient Entity owner;
}
