package com.ringsofiron.orbital.events;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.coreengine.SaveParticipant;
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
 *
 * <p>Saving: maneuver nodes are components and everything else in the queue is a prediction from
 * the elements, so nothing in the queue needs saving. Register this system as a
 * {@link SaveParticipant}; on load it redoes every prediction once all entities exist.
 */
public class OrbitalEventSystem extends EntitySystem implements SaveParticipant {
    public static final String SAVE_KEY = "orbital-events";
    /** Pseudo-UUID used in events and {@link SoiChange}s for the bubble anchor. */
    public static final String ANCHOR_ID = "bubble-anchor";
    public static final int DEFAULT_PRIORITY = 5;
    private static final Family RAILS = Family.all(OrbitComponent.class, UuidComponent.class).get();

    /** Every applied event, after the orbit has been updated (HUD, logs, AI). */
    public final Signal<OrbitalEvent> applied = new Signal<>();
    /** Surface impacts. The orbit is left as is; whoever owns the body decides what happens. */
    public final Signal<OrbitalEvent> impacts = new Signal<>();
    /** Every SOI transition of a rails body or of the bubble anchor, after it has been applied. */
    public final Signal<SoiChange> soiChanges = new Signal<>();

    private final ComponentMapper<OrbitComponent> orbitMapper = ComponentMapper.getFor(OrbitComponent.class);
    private final ComponentMapper<ManeuverNodeComponent> maneuverMapper = ComponentMapper.getFor(ManeuverNodeComponent.class);
    private final ComponentMapper<CelestialBodyComponent> celestialMapper = ComponentMapper.getFor(CelestialBodyComponent.class);
    private final ComponentMapper<UuidComponent> uuidMapper = ComponentMapper.getFor(UuidComponent.class);

    private final RailsUniverse universe;
    private final OrbitalEventQueue queue;
    private final SoiPredictor predictor;
    private final EntityListener railsListener = new RailsListener();
    private ImmutableArray<Entity> railsEntities;
    private final StateVector s = new StateVector();
    private final StateVector p = new StateVector();
    private final SoiChange soiChange = new SoiChange();

    // The bubble anchor: a free-falling conic owned by physics-exterior, tracked here for SOI changes.
    private String anchorParentUuid;
    private KeplerOrbit anchorOrbit;
    private int anchorVersion;

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
        railsEntities = engine.getEntitiesFor(RAILS);
        engine.addEntityListener(RAILS, railsListener);
        for (Entity e : railsEntities) railsListener.entityAdded(e);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        engine.removeEntityListener(railsListener);
        queue.clear();
        railsEntities = null;
    }

    /** Drops every pending event and predicts again for all rails bodies from the current time. */
    public void rebuildAll() {
        queue.clear();
        double t = universe.now();
        if (railsEntities != null) {
            for (Entity e : railsEntities) schedule(e, t);
        }
        scheduleAnchor(t);
    }

    /**
     * Tracks the bubble anchor's conic so its SOI changes are predicted like any rails body's. Call
     * whenever the anchor's orbit changes (creation, rebase). When it crosses an SOI boundary,
     * {@link #soiChanges} fires with {@code anchor = true} and the new parent and conic; the bubble
     * side adopts them (Box2D bodies do not move). Impacts are not predicted for the anchor.
     */
    public void setAnchor(String parentUuid, KeplerOrbit orbit) {
        anchorParentUuid = parentUuid;
        anchorOrbit = orbit;
        anchorVersion++;
        queue.removeFor(ANCHOR_ID);
        scheduleAnchor(universe.now());
    }

    /** Stops tracking the anchor (e.g. the bubble collapsed for rails warp). */
    public void clearAnchor() {
        anchorParentUuid = null;
        anchorOrbit = null;
        anchorVersion++;
        queue.removeFor(ANCHOR_ID);
    }

    public KeplerOrbit getAnchorOrbit() {
        return anchorOrbit;
    }

    public String getAnchorParentUuid() {
        return anchorParentUuid;
    }

    private void scheduleAnchor(double t) {
        if (anchorOrbit == null) return;
        OrbitalEvent e = predictor.predict(ANCHOR_ID, anchorParentUuid, anchorOrbit, anchorVersion, t);
        if (e.type == OrbitalEvent.Type.IMPACT) {
            // The anchor is a mathematical point; only care about what comes after the surface.
            e = new OrbitalEvent(OrbitalEvent.Type.REPREDICT, e.time + 1e-3, ANCHOR_ID, null, anchorVersion);
        }
        queue.schedule(e);
    }

    private void applyAnchor(OrbitalEvent event) {
        if (anchorOrbit == null || event.orbitVersion != anchorVersion) return;
        double t = event.time;
        if (event.type == OrbitalEvent.Type.SOI_EXIT || event.type == OrbitalEvent.Type.SOI_ENTRY) {
            String oldParent = anchorParentUuid;
            String newParent = crossSoi(event, anchorParentUuid, anchorOrbit, s);
            if (newParent == null) return;
            KeplerOrbit k = KeplerOrbit.fromState(s, celestialMapper.get(universe.entity(newParent)).mu, t);
            anchorParentUuid = newParent;
            anchorOrbit = k;
            anchorVersion++;
            fillSoiChange(ANCHOR_ID, true, oldParent, newParent, t, s, k);
        }
        queue.removeFor(ANCHOR_ID);
        scheduleAnchor(t);
        applied.dispatch(event);
        if (event.type == OrbitalEvent.Type.SOI_EXIT || event.type == OrbitalEvent.Type.SOI_ENTRY) {
            soiChanges.dispatch(soiChange);
        }
    }

    /**
     * State of a conic relative to the body it moves into at an SOI event, written into {@code out}.
     * Returns the new parent's UUID, or null if the bodies involved are gone.
     */
    private String crossSoi(OrbitalEvent event, String parentUuid, KeplerOrbit orbit, StateVector out) {
        double t = event.time;
        if (event.type == OrbitalEvent.Type.SOI_EXIT) {
            OrbitComponent parentOrbit = orbitMapper.get(universe.entity(parentUuid));
            if (parentOrbit == null) return null;
            orbit.stateAt(t, out).add(parentOrbit.stateAt(t, p));
            return parentOrbit.parentUuid;
        }
        Entity child = universe.entity(event.targetUuid);
        if (child == null || !orbitMapper.has(child)) return null;
        orbit.stateAt(t, out).sub(orbitMapper.get(child).stateAt(t, p));
        return event.targetUuid;
    }

    private void fillSoiChange(String id, boolean anchor, String oldParent, String newParent, double t,
                               StateVector state, KeplerOrbit orbit) {
        soiChange.entityUuid = id;
        soiChange.anchor = anchor;
        soiChange.oldParentUuid = oldParent;
        soiChange.newParentUuid = newParent;
        soiChange.time = t;
        soiChange.state.set(state);
        soiChange.newOrbit = orbit;
    }

    @Override
    public String saveKey() {
        return SAVE_KEY;
    }

    @Override
    public void write(Json json) {
        // Nothing to store: the queue is rebuilt from components on load. The count is informational.
        json.writeValue("pending", queue.size());
    }

    @Override
    public void read(Json json, JsonValue data) {
        rebuildAll();
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
        if (ANCHOR_ID.equals(event.entityUuid)) {
            applyAnchor(event);
            return;
        }
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
            case SOI_EXIT:
            case SOI_ENTRY: {
                String oldParent = orbit.parentUuid;
                String newParent = crossSoi(event, oldParent, orbit.getOrbit(), s);
                if (newParent == null) return;
                KeplerOrbit k = KeplerOrbit.fromState(s, celestialMapper.get(universe.entity(newParent)).mu, t);
                orbit.reparent(newParent, k);
                fillSoiChange(event.entityUuid, false, oldParent, newParent, t, s, k);
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
        if (event.type == OrbitalEvent.Type.SOI_EXIT || event.type == OrbitalEvent.Type.SOI_ENTRY) {
            soiChanges.dispatch(soiChange);
        }
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
