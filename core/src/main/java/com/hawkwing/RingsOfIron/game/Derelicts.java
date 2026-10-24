package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.components.TransientComponent;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Escape pods and wrecks drifting on the rails: what a freighter leaves behind when pirates rob or
 * wreck it, for rescue and recovery contracts to point at. Each rides a Kepler orbit around a body
 * from the moment it was cast off; it is not an entity, so it never comes into the bubble as a
 * body. The player picks one up with a slow close pass ({@link #closeTo}): within
 * {@link #PICKUP_RANGE} at under {@link #PICKUP_SPEED} relative.
 *
 * <p>While one is within {@link #MARKER_RANGE} of the player and around the same body, it gets a
 * HUD marker, so it can be found by eye. A pod's air lasts {@link #POD_AIR}; a wreck is lost track
 * of after {@link #WRECK_LIFE}. Saved with the game.
 */
public class Derelicts implements SaveParticipant, Resettable {
    public static final double DAY = 86_400;
    /** How close, and how slowly, the player must pass to pick one up: m and m/s. */
    public static final double PICKUP_RANGE = 2_000, PICKUP_SPEED = 30;
    /** How long the crew take to carry a wreck's cargo across while the ship holds alongside, sim seconds. */
    public static final double SALVAGE_SECONDS = 60;
    /** How long a pod's air lasts, sim seconds. */
    public static final double POD_AIR = 4 * DAY;
    /** How long a wreck is tracked, sim seconds. */
    public static final double WRECK_LIFE = 10 * DAY;
    /** A derelict this close to the player gets a marker, m. */
    public static final double MARKER_RANGE = 200_000;
    /** How fast a pod or wreck drifts off the path of the ship it came from, m/s. */
    public static final double DRIFT_SPEED = 3;

    public enum Kind { POD, WRECK }

    /** One pod or wreck. Plain fields, saved as they are. */
    public static class Derelict {
        public String id;
        public Kind kind;
        public String name;
        /** The body it orbits, and its state relative to that body at {@link #epoch}. */
        public String parentUuid;
        public double x, y, vx, vy, epoch;
        /** A pod's people. */
        public int survivors;
        /** A wreck's cargo. */
        public String commodity;
        public double quantity;
        /** Sim second it is lost: air gone, or drifted out of reach. */
        public double expires;
        transient KeplerOrbit orbit;
        transient Entity marker;

        public boolean isPod() {
            return kind == Kind.POD;
        }
    }

    private final GameWorld world;
    private final List<Derelict> all = new ArrayList<>();
    private long nextId = 1;
    private final StateVector a = new StateVector(), b = new StateVector();
    private final double[] trueState = new double[4];
    private final float[] local = new float[4];

    Derelicts(GameWorld world) {
        this.world = world;
        world.engine.getSerializationService().addParticipant(this);
    }

    /** Every pod and wreck still drifting, oldest first. */
    public List<Derelict> all() {
        return Collections.unmodifiableList(all);
    }

    /** The derelict {@code id}, or null if there is none (picked up, lost, or never was). */
    public Derelict get(String id) {
        if (id == null) return null;
        for (Derelict d : all) if (d.id.equals(id)) return d;
        return null;
    }

    /**
     * A pod with {@code survivors} aboard, or a wreck with {@code quantity} of {@code commodity},
     * cast off from the craft {@code from} where it is now and drifting gently away from it. Null if
     * {@code from} cannot be placed.
     */
    public Derelict castOff(Entity from, Kind kind, String name, int survivors, String commodity, double quantity,
                            Random rnd) {
        double t = world.engine.getSimSeconds();
        String parent;
        OrbitComponent orbit = from == null ? null : world.universe.orbit(from);
        PhysicsBodyComponent pb = from == null ? null : from.getComponent(PhysicsBodyComponent.class);
        if (orbit != null) {
            parent = orbit.parentUuid;
            orbit.stateAt(t, a);
        } else if (pb != null && pb.body != null && world.physics.getAnchorParentUuid() != null) {
            parent = world.physics.getAnchorParentUuid();
            world.physics.getTrueState(pb.body, trueState);
            a.set(trueState[0], trueState[1], trueState[2], trueState[3]);
        } else {
            return null;
        }
        Derelict d = new Derelict();
        d.id = (kind == Kind.POD ? "pod-" : "wreck-") + nextId++;
        d.kind = kind;
        d.name = name;
        d.parentUuid = parent;
        double angle = rnd.nextDouble() * Math.PI * 2;
        d.x = a.x;
        d.y = a.y;
        d.vx = a.vx + Math.cos(angle) * DRIFT_SPEED;
        d.vy = a.vy + Math.sin(angle) * DRIFT_SPEED;
        d.epoch = t;
        d.survivors = survivors;
        d.commodity = commodity;
        d.quantity = quantity;
        d.expires = t + (kind == Kind.POD ? POD_AIR : WRECK_LIFE);
        if (orbitOf(d) == null) return null;
        all.add(d);
        return d;
    }

    /** Takes {@code id} off the rails: picked up, or lost. */
    public void remove(String id) {
        Derelict d = get(id);
        if (d == null) return;
        all.remove(d);
        dropMarker(d);
    }

    /** Clears everything, for a new game. */
    @Override
    public void reset() {
        for (Derelict d : all) dropMarker(d);
        all.clear();
        nextId = 1;
    }

    /** Where {@code d} is at sim second {@code t}, relative to the root body; false if its body is gone. */
    public boolean position(Derelict d, double t, StateVector out) {
        KeplerOrbit k = orbitOf(d);
        Entity parent = world.universe.entity(d.parentUuid);
        if (k == null || parent == null) return false;
        k.stateAt(t, out);
        world.universe.absoluteState(parent, t, b);
        out.x += b.x;
        out.y += b.y;
        out.vx += b.vx;
        out.vy += b.vy;
        return true;
    }

    /** True if the player's ship is within {@link #PICKUP_RANGE} of {@code d}, at under {@link #PICKUP_SPEED} relative. */
    public boolean closeTo(Entity player, Derelict d) {
        StateVector p = new StateVector();
        if (d == null || !playerState(player, p) || !position(d, world.engine.getSimSeconds(), a)) return false;
        return Math.hypot(p.x - a.x, p.y - a.y) <= PICKUP_RANGE && Math.hypot(p.vx - a.vx, p.vy - a.vy) <= PICKUP_SPEED;
    }

    /** "escape pod, 4 aboard" or "wreck, 40 Water": what it is, in a few words. */
    public String describe(Derelict d) {
        if (d.isPod()) return String.format(Locale.ROOT, "escape pod, %d aboard", d.survivors);
        return d.commodity == null || d.quantity <= 0 ? "wreck"
                : String.format(Locale.ROOT, "wreck, %.0f %s", d.quantity, commodityName(d.commodity));
    }

    String commodityName(String id) {
        var c = world.macroSim.economy().catalog().commodity(id);
        return c == null || c.displayName == null ? id : c.displayName;
    }

    /**
     * Loses what has run out of air or drifted away, and moves the markers. Called once a frame, after
     * the engine's update, so markers come and go outside it.
     */
    void update() {
        double t = world.engine.getSimSeconds();
        for (Derelict d : new ArrayList<>(all)) {
            if (t > d.expires) {
                remove(d.id);
                continue;
            }
            mark(d, t);
        }
    }

    // ------------------------------------------------------------------ markers

    private void mark(Derelict d, double t) {
        Entity player = world.getPlayer();
        PhysicsBodyComponent pb = player == null ? null : player.getComponent(PhysicsBodyComponent.class);
        String anchor = world.physics.getAnchorParentUuid();
        KeplerOrbit k = orbitOf(d);
        if (pb == null || pb.body == null || anchor == null || !anchor.equals(d.parentUuid) || k == null) {
            dropMarker(d);
            return;
        }
        k.stateAt(t, a);
        trueState[0] = a.x;
        trueState[1] = a.y;
        trueState[2] = a.vx;
        trueState[3] = a.vy;
        world.physics.trueToLocal(trueState, local);
        world.physics.getTrueState(pb.body, trueState);
        if (Math.hypot(trueState[0] - a.x, trueState[1] - a.y) > MARKER_RANGE) {
            dropMarker(d);
            return;
        }
        if (d.marker == null) {
            d.marker = world.engine.createEntity();
            d.marker.add(new TransformComponent());
            d.marker.add(new TransientComponent());
            d.marker.add(new HudMarkerComponent(d.name, HudMarkerComponent.Kind.NEUTRAL).radius(5f));
            world.engine.addEntity(d.marker);
        }
        TransformComponent tr = d.marker.getComponent(TransformComponent.class);
        tr.x = local[0] * PhysicsManager.UNITS_PER_METER;
        tr.y = local[1] * PhysicsManager.UNITS_PER_METER;
        HudMarkerComponent m = d.marker.getComponent(HudMarkerComponent.class);
        boolean waypoint = world.contracts.isWaypoint(d.id);
        String label = waypoint ? d.name + ContractBoard.MARKER_SUFFIX : d.name;
        if (!label.equals(m.label)) m.label = label;
        m.color.set(waypoint ? HudMarkerComponent.Kind.WAYPOINT.defaultColor : m.kind.defaultColor);
    }

    private void dropMarker(Derelict d) {
        if (d.marker == null) return;
        world.engine.removeEntity(d.marker);
        d.marker = null;
    }

    /** The marker entity of {@code d} while it has one, for tests. */
    Entity marker(Derelict d) {
        return d.marker;
    }

    // ------------------------------------------------------------------ helpers

    private KeplerOrbit orbitOf(Derelict d) {
        if (d.orbit != null) return d.orbit;
        Entity parent = world.universe.entity(d.parentUuid);
        CelestialBodyComponent body = parent == null ? null : world.universe.celestial(parent);
        if (body == null) return null;
        d.orbit = KeplerOrbit.fromState(d.x, d.y, d.vx, d.vy, body.mu, d.epoch);
        return d.orbit;
    }

    /** The player's ship's absolute state, on the rails or in the bubble. */
    private boolean playerState(Entity player, StateVector out) {
        if (player == null) return false;
        double t = world.engine.getSimSeconds();
        if (world.universe.orbit(player) != null) {
            world.universe.absoluteState(player, t, out);
            return true;
        }
        PhysicsBodyComponent pb = player.getComponent(PhysicsBodyComponent.class);
        Entity parent = world.universe.entity(world.physics.getAnchorParentUuid());
        if (pb == null || pb.body == null || parent == null) return false;
        world.physics.getTrueState(pb.body, trueState);
        world.universe.absoluteState(parent, t, out);
        out.x += trueState[0];
        out.y += trueState[1];
        out.vx += trueState[2];
        out.vy += trueState[3];
        return true;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public String saveKey() {
        return "derelicts";
    }

    @Override
    public void write(Json json) {
        json.writeValue("nextId", nextId);
        json.writeValue("all", all.toArray(new Derelict[0]), Derelict[].class);
    }

    @Override
    public void read(Json json, JsonValue data) {
        reset();
        nextId = data.getLong("nextId", 1);
        Derelict[] saved = data.has("all") ? json.readValue(Derelict[].class, data.get("all")) : null;
        if (saved != null) for (Derelict d : saved) if (d != null && d.id != null && d.kind != null) all.add(d);
    }
}
