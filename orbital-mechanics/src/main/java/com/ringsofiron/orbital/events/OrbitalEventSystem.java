package com.ringsofiron.orbital.events;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.signals.Signal;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.rails.RailsUniverse;
import com.ringsofiron.orbital.rails.SoiPredictor;

/**
 * Drives the rails: pops due events from the {@link OrbitalEventQueue} and applies them on the main
 * thread. Each event is applied at its exact time, not at the tick that noticed it, so the result is
 * the same at any warp speed.
 *
 * <p>Rails entities are those with an {@link OrbitComponent} and a {@link UuidComponent}. Celestial
 * bodies are on rails too but get no SOI predictions (their orbits are fixed). Whenever code outside
 * this system changes an orbit (a bubble exit handoff, a scripted teleport), call
 * {@link #orbitChanged} so its predictions are redone.
 */
public class OrbitalEventSystem extends EntitySystem {
    public static final int DEFAULT_PRIORITY = 5;
    private static final Family RAILS = Family.all(OrbitComponent.class, UuidComponent.class).get();

    /** Every applied event, after the orbit has been updated (HUD, logs, AI). */
    public final Signal<OrbitalEvent> applied = new Signal<>();
    /** Surface impacts. The orbit is left as is; whoever owns the body decides what happens. */
    public final Signal<OrbitalEvent> impacts = new Signal<>();

    private final ComponentMapper<OrbitComponent> orbitMapper = ComponentMapper.getFor(OrbitComponent.class);
    private final ComponentMapper<ManeuverNodeComponent> maneuverMapper = ComponentMapper.getFor(ManeuverNodeComponent.class);
    private final ComponentMapper<CelestialBodyComponent> celestialMapper = ComponentMapper.getFor(CelestialBodyComponent.class);
    private final ComponentMapper<UuidComponent> uuidMapper = ComponentMapper.getFor(UuidComponent.class);

    private final RailsUniverse universe;
    private final OrbitalEventQueue queue;
    private final SoiPredictor predictor;
    private final EntityListener railsListener = new RailsListener();
    private final StateVector s = new StateVector();
    private final StateVector p = new StateVector();

    public OrbitalEventSystem(RailsUniverse universe, OrbitalEventQueue queue) {
        this(universe, queue, new SoiPredictor(universe));
    }

    public OrbitalEventSystem(RailsUniverse universe, OrbitalEventQueue queue, SoiPredictor predictor) {
        super(DEFAULT_PRIORITY);
        this.universe = universe;
        this.queue = queue;
        this.predictor = predictor;
    }

    @Override
    public void addedToEngine(Engine engine) {
        engine.addEntityListener(RAILS, railsListener);
        for (Entity e : engine.getEntitiesFor(RAILS)) railsListener.entityAdded(e);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        engine.removeEntityListener(railsListener);
        queue.clear();
    }

    @Override
    public void update(float deltaTime) {
        processUntil(universe.now());
    }

    /** Applies every event due at or before t, in time order. Events may schedule further events. */
    public void processUntil(double t) {
        OrbitalEvent event;
        while ((event = queue.pollDue(t)) != null) {
            apply(event);
        }
    }

    /** Redo every prediction for this entity after its orbit changed outside the event system. */
    public void orbitChanged(Entity entity) {
        String id = uuidMapper.get(entity).id;
        queue.removeFor(id);
        schedule(entity, universe.now());
    }

    /** Cancels everything pending for an entity that is leaving the rails (e.g. entering the bubble). */
    public void leaveRails(Entity entity) {
        queue.removeFor(uuidMapper.get(entity).id);
    }

    public OrbitalEventQueue getQueue() {
        return queue;
    }

    private void schedule(Entity entity, double t) {
        String id = uuidMapper.get(entity).id;
        ManeuverNodeComponent maneuvers = maneuverMapper.get(entity);
        if (maneuvers != null && maneuvers.next() != null) {
            queue.schedule(new OrbitalEvent(OrbitalEvent.Type.MANEUVER, Math.max(t, maneuvers.next().time), id, null, -1));
        }
        if (!celestialMapper.has(entity)) {
            queue.schedule(predictor.predict(entity, t));
        }
    }

    private void apply(OrbitalEvent event) {
        Entity entity = universe.entity(event.entityUuid);
        if (entity == null) return;
        OrbitComponent orbit = orbitMapper.get(entity);
        if (orbit == null) return;
        if (event.isPrediction() && event.orbitVersion != orbit.version) return;  // superseded

        double t = event.time;
        switch (event.type) {
            case MANEUVER: {
                ManeuverNodeComponent maneuvers = maneuverMapper.get(entity);
                ManeuverNodeComponent.Node node = maneuvers == null ? null : maneuvers.next();
                if (node == null) return;
                maneuvers.nodes.removeIndex(0);
                orbit.setOrbit(orbit.getOrbit().withBurn(t, node.prograde, node.radial));
                break;
            }
            case SOI_EXIT: {
                Entity parent = universe.entity(orbit.parentUuid);
                OrbitComponent parentOrbit = orbitMapper.get(parent);
                Entity grandparent = universe.entity(parentOrbit.parentUuid);
                orbit.stateAt(t, s).add(parentOrbit.stateAt(t, p));
                orbit.reparent(parentOrbit.parentUuid,
                        KeplerOrbit.fromState(s, celestialMapper.get(grandparent).mu, t));
                break;
            }
            case SOI_ENTRY: {
                Entity child = universe.entity(event.targetUuid);
                if (child == null) return;
                orbit.stateAt(t, s).sub(orbitMapper.get(child).stateAt(t, p));
                orbit.reparent(event.targetUuid, KeplerOrbit.fromState(s, celestialMapper.get(child).mu, t));
                break;
            }
            case IMPACT:
                impacts.dispatch(event);
                break;
            case REPREDICT:
                break;
        }
        // An impact leaves the orbit unchanged, so its old predictions are still valid except itself.
        queue.removeFor(event.entityUuid);
        if (event.type == OrbitalEvent.Type.IMPACT) {
            // Look again only after the surface crossing, or the same impact would be found again.
            scheduleAfterImpact(entity, t);
        } else {
            schedule(entity, t);
        }
        applied.dispatch(event);
    }

    private void scheduleAfterImpact(Entity entity, double t) {
        ManeuverNodeComponent maneuvers = maneuverMapper.get(entity);
        String id = uuidMapper.get(entity).id;
        if (maneuvers != null && maneuvers.next() != null) {
            queue.schedule(new OrbitalEvent(OrbitalEvent.Type.MANEUVER, Math.max(t, maneuvers.next().time), id, null, -1));
        }
        queue.schedule(predictor.predict(entity, t + 1e-3));
    }

    private class RailsListener implements EntityListener {
        @Override
        public void entityAdded(Entity entity) {
            schedule(entity, universe.now());
        }

        @Override
        public void entityRemoved(Entity entity) {
            UuidComponent id = uuidMapper.get(entity);
            if (id != null) queue.removeFor(id.id);
        }
    }
}
