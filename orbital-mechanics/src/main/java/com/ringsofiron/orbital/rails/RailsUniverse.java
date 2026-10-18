package com.ringsofiron.orbital.rails;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;

import java.util.function.DoubleSupplier;

/**
 * Read access to the rails hierarchy: who orbits whom, and where anything is at a given time in any
 * body's frame. Positions are pure functions of time, so these queries are safe from any thread as
 * long as nobody changes elements concurrently (elements only change on the main thread).
 *
 * <p>A body's state relative to the root (the star) is the sum up its parent chain. Prefer
 * {@link #stateInFrame} with the nearest common body as the frame: numbers stay small.
 */
public class RailsUniverse {
    private static final Family CELESTIAL = Family.all(CelestialBodyComponent.class, UuidComponent.class).get();
    private static final int MAX_DEPTH = 16;

    private final ComponentMapper<OrbitComponent> orbitMapper = ComponentMapper.getFor(OrbitComponent.class);
    private final ComponentMapper<CelestialBodyComponent> bodyMapper = ComponentMapper.getFor(CelestialBodyComponent.class);
    private final ComponentMapper<UuidComponent> uuidMapper = ComponentMapper.getFor(UuidComponent.class);

    private final UuidRegistry registry;
    private final DoubleSupplier clock;
    private final ImmutableArray<Entity> celestials;
    private final StateVector scratch = new StateVector();

    public RailsUniverse(GameEngine game) {
        this(game.getEcsEngine(), game.getUuidRegistry(), game::getSimSeconds);
    }

    /** @param clock current sim time in seconds, e.g. {@code simClock::getSimSeconds} */
    public RailsUniverse(Engine engine, UuidRegistry registry, DoubleSupplier clock) {
        this.registry = registry;
        this.clock = clock;
        this.celestials = engine.getEntitiesFor(CELESTIAL);
    }

    public double now() {
        return clock.getAsDouble();
    }

    public Entity entity(String uuid) {
        return uuid == null ? null : registry.getEntity(uuid);
    }

    public String uuidOf(Entity entity) {
        UuidComponent u = uuidMapper.get(entity);
        return u == null ? null : u.id;
    }

    /** The body this entity orbits, or null for the root or a body not on rails. */
    public Entity parentOf(Entity entity) {
        OrbitComponent orbit = orbitMapper.get(entity);
        return orbit == null ? null : entity(orbit.parentUuid);
    }

    public CelestialBodyComponent celestial(Entity entity) {
        return entity == null ? null : bodyMapper.get(entity);
    }

    public OrbitComponent orbit(Entity entity) {
        return orbitMapper.get(entity);
    }

    /** Celestial bodies orbiting {@code parent} directly (moons of a planet, planets of a star). */
    public Array<Entity> childBodies(Entity parent, Array<Entity> out) {
        out.clear();
        String id = uuidOf(parent);
        for (Entity c : celestials) {
            OrbitComponent o = orbitMapper.get(c);
            if (o != null && id != null && id.equals(o.parentUuid)) out.add(c);
        }
        return out;
    }

    public ImmutableArray<Entity> celestialBodies() {
        return celestials;
    }

    /** State relative to the root of the hierarchy at time t. The root itself is at rest at zero. */
    public StateVector absoluteState(Entity entity, double t, StateVector out) {
        out.set(0, 0, 0, 0);
        Entity current = entity;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            OrbitComponent orbit = orbitMapper.get(current);
            if (orbit == null) break;
            out.add(orbit.stateAt(t, scratch));
            current = entity(orbit.parentUuid);
        }
        return out;
    }

    /** State of {@code entity} relative to {@code frame} at time t ({@code frame} may be any body). */
    public StateVector stateInFrame(Entity entity, Entity frame, double t, StateVector out) {
        OrbitComponent orbit = orbitMapper.get(entity);
        if (orbit != null && frame != null && frame == entity(orbit.parentUuid)) {
            return orbit.stateAt(t, out);   // the common case, and the most precise
        }
        if (entity == frame) return out.set(0, 0, 0, 0);
        absoluteState(entity, t, out);
        StateVector f = absoluteState(frame, t, new StateVector());
        return out.sub(f);
    }

    /**
     * An upper bound on {@code entity}'s speed relative to {@code ancestor}: the sum of the fastest
     * speed (periapsis, or the asymptote for an escape) of each orbit up the chain. Used to size
     * sampling steps so no encounter is skipped.
     */
    public double maxSpeedRelativeTo(Entity entity, Entity ancestor) {
        double sum = 0;
        Entity current = entity;
        for (int depth = 0; current != null && current != ancestor && depth < MAX_DEPTH; depth++) {
            OrbitComponent orbit = orbitMapper.get(current);
            if (orbit == null) break;
            sum += maxSpeed(orbit.getOrbit());
            current = entity(orbit.parentUuid);
        }
        return sum;
    }

    /** Speed at periapsis, the fastest point of any conic. */
    public static double maxSpeed(KeplerOrbit k) {
        return Math.sqrt(k.mu * (2 / k.periapsis() - 1 / k.a));
    }

    /** Nearest body that is {@code a} or {@code b} or an ancestor of both. Null if none in common. */
    public Entity commonAncestor(Entity a, Entity b) {
        Array<Entity> chainA = new Array<>();
        for (Entity c = a; c != null && chainA.size < MAX_DEPTH; c = parentOf(c)) chainA.add(c);
        for (Entity c = b; c != null; c = parentOf(c)) {
            if (chainA.contains(c, true)) return c;
        }
        return null;
    }
}
