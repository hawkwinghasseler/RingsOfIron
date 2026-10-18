package com.ringsofiron.macrosim.rails.orbital;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.macrosim.rails.RailsSnapshot;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.Trajectory;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.events.OrbitalEvent;
import com.ringsofiron.orbital.events.OrbitalEventQueue;
import com.ringsofiron.orbital.events.OrbitalEventSystem;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.rails.RailsUniverse;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RailsModel} over orbital-mechanics: elements live in each entity's {@link OrbitComponent},
 * burns in its {@link ManeuverNodeComponent}, and {@link OrbitalEventSystem} fires them along with SOI
 * changes and impacts. Massive bodies are entities with a {@link CelestialBodyComponent}.
 *
 * <p>In the game, pass the game's single {@link OrbitalEventSystem} and its {@link RailsUniverse}
 * (GameWorld owns them). {@link #standalone} builds a private pair for the demo and tests; there the
 * universe reads this model's own clock (the time it last processed up to), so a burn planned in
 * the middle of a rails-warp jump is scheduled from the event time, not the jump's end.
 */
public class OrbitalRailsModel implements RailsModel {
    private static final Family RAILS = Family.all(OrbitComponent.class, UuidComponent.class).get();

    private final ComponentMapper<OrbitComponent> orbits = ComponentMapper.getFor(OrbitComponent.class);
    private final ComponentMapper<ManeuverNodeComponent> maneuvers = ComponentMapper.getFor(ManeuverNodeComponent.class);
    private final ComponentMapper<CelestialBodyComponent> bodies = ComponentMapper.getFor(CelestialBodyComponent.class);
    private final GameEngine engine;
    private final RailsUniverse universe;
    private final OrbitalEventSystem events;
    private final com.ringsofiron.orbital.math.StateVector scratch = new com.ringsofiron.orbital.math.StateVector();
    private final double[] now;

    /** Over the game's existing event system, which must already be in the engine. */
    public OrbitalRailsModel(GameEngine engine, RailsUniverse universe, OrbitalEventSystem events) {
        this(engine, universe, events, new double[]{universe.now()});
    }

    private OrbitalRailsModel(GameEngine engine, RailsUniverse universe, OrbitalEventSystem events, double[] now) {
        this.engine = engine;
        this.universe = universe;
        this.events = events;
        this.now = now;
    }

    /**
     * Creates its own universe and event system, for use without GameWorld. The event system is added
     * to the engine for its entity listener but not processed by it: {@code MacroSimulation} drives it.
     * Do not use alongside another {@link OrbitalEventSystem} in the same engine.
     */
    public static OrbitalRailsModel standalone(GameEngine engine, double startTime) {
        double[] now = {startTime};
        RailsUniverse universe = new RailsUniverse(engine.getEcsEngine(), engine.getUuidRegistry(), () -> now[0]);
        OrbitalEventSystem events = new OrbitalEventSystem(universe, new OrbitalEventQueue());
        engine.getEcsEngine().addSystem(events);
        events.setProcessing(false);
        return new OrbitalRailsModel(engine, universe, events, now);
    }

    public RailsUniverse universe() {
        return universe;
    }

    public OrbitalEventSystem events() {
        return events;
    }

    @Override
    public double muOf(String bodyUuid) {
        CelestialBodyComponent c = bodies.get(require(bodyUuid));
        if (c == null) throw new IllegalArgumentException("Not a massive body: " + bodyUuid);
        return c.mu;
    }

    @Override
    public boolean isOnRails(String uuid) {
        Entity e = universe.entity(uuid);
        return e != null && orbits.has(e);
    }

    @Override
    public String parentOf(String uuid) {
        OrbitComponent o = orbits.get(require(uuid));
        return o == null ? null : o.parentUuid;
    }

    @Override
    public StateVector stateAt(String uuid, double t, StateVector out) {
        orbit(uuid).stateAt(t, scratch);
        return out.set(scratch.x, scratch.y, scratch.vx, scratch.vy);
    }

    @Override
    public Trajectory trajectoryOf(String uuid) {
        return new KeplerTrajectory(orbit(uuid).getOrbit());
    }

    @Override
    public void attach(String uuid, String parentUuid, StateVector s, double t) {
        Entity e = require(uuid);
        KeplerOrbit k = KeplerOrbit.fromState(s.x, s.y, s.vx, s.vy, muOf(parentUuid), t);
        OrbitComponent o = orbits.get(e);
        if (o == null) {
            e.add(new OrbitComponent(parentUuid, k)); // the event system's listener schedules it
        } else {
            o.reparent(parentUuid, k);
            events.orbitChanged(e);
        }
    }

    @Override
    public List<Burn> detach(String uuid) {
        Entity e = require(uuid);
        List<Burn> cancelled = new ArrayList<>();
        ManeuverNodeComponent m = maneuvers.get(e);
        if (m != null) {
            for (ManeuverNodeComponent.Node n : m.nodes) cancelled.add(new Burn(n.time, n.prograde, n.radial));
            m.nodes.clear();
        }
        events.leaveRails(e);
        e.remove(OrbitComponent.class);
        return cancelled;
    }

    @Override
    public void scheduleBurn(String uuid, Burn burn) {
        Entity e = require(uuid);
        orbit(uuid);
        ManeuverNodeComponent m = maneuvers.get(e);
        if (m == null) {
            m = new ManeuverNodeComponent();
            e.add(m);
        }
        m.add(burn.time, burn.prograde, burn.radial);
        events.orbitChanged(e);
    }

    @Override
    public void cancelBurns(String uuid) {
        Entity e = require(uuid);
        ManeuverNodeComponent m = maneuvers.get(e);
        if (m == null || m.nodes.size == 0) return;
        m.nodes.clear();
        if (orbits.has(e)) events.orbitChanged(e);
    }

    @Override
    public void processEventsUntil(double t) {
        // Step the clock event by event, so anything scheduled while applying one starts from its time.
        OrbitalEvent next;
        while ((next = events.getQueue().peek()) != null && next.time <= t) {
            now[0] = Math.max(now[0], next.time);
            events.processUntil(next.time);
        }
        now[0] = Math.max(now[0], t);
    }

    @Override
    public double nextEventTime() {
        OrbitalEvent next = events.getQueue().peek();
        return next == null ? Double.POSITIVE_INFINITY : next.time;
    }

    @Override
    public RailsSnapshot snapshot() {
        List<RailsSnapshot.Entry> entries = new ArrayList<>();
        for (Entity e : engine.getEcsEngine().getEntitiesFor(RAILS)) {
            OrbitComponent o = orbits.get(e);
            entries.add(new RailsSnapshot.Entry(e.getComponent(UuidComponent.class).id, o.parentUuid,
                    new KeplerTrajectory(o.getOrbit())));
        }
        return new RailsSnapshot(now[0], entries);
    }

    private OrbitComponent orbit(String uuid) {
        OrbitComponent o = orbits.get(require(uuid));
        if (o == null) throw new IllegalArgumentException("Not on rails: " + uuid);
        return o;
    }

    private Entity require(String uuid) {
        Entity e = universe.entity(uuid);
        if (e == null) throw new IllegalArgumentException("No entity with UUID " + uuid);
        return e;
    }

    /** Immutable {@link KeplerOrbit} as a macro-sim {@link Trajectory}; safe on any thread. */
    static final class KeplerTrajectory implements Trajectory {
        private final KeplerOrbit orbit;

        KeplerTrajectory(KeplerOrbit orbit) {
            this.orbit = orbit;
        }

        @Override
        public StateVector stateAt(double t, StateVector out) {
            com.ringsofiron.orbital.math.StateVector s = orbit.stateAt(t);
            return out.set(s.x, s.y, s.vx, s.vy);
        }

        @Override
        public double minRadius() {
            return orbit.periapsis();
        }

        @Override
        public double maxRadius() {
            return orbit.isElliptic() ? orbit.apoapsis() : Double.POSITIVE_INFINITY;
        }

        @Override
        public double maxSpeed() {
            return RailsUniverse.maxSpeed(orbit);
        }
    }
}
