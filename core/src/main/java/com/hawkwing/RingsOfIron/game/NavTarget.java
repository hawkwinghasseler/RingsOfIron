package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.ringsofiron.career.Contract;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.shipops.ShipNames;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The player's navigation target: any station, a craft in the bubble, or a known ship on the rails
 * (a freighter, or a pirate the player knows of through {@link PirateSightings}). The orbit map draws
 * its orbit and the closest approach to it, the transfer planner plans to it, and the helm's
 * kill-velocity, hold and approach orders fly relative to it. Kept on the ship as a
 * {@link NavTargetComponent}, so it is saved.
 */
public class NavTarget {
    private static final Family MARKED = Family.all(HudMarkerComponent.class, PhysicsBodyComponent.class).get();

    private final GameWorld world;
    private final PirateSightings sightings;
    private final double[] trueState = new double[4];
    private final StateVector scratch = new StateVector();

    public NavTarget(GameWorld world, PirateSightings sightings) {
        this.world = world;
        this.sightings = sightings;
    }

    /** The target's uuid, or null for none or one that is gone. */
    public String uuid() {
        Entity ship = world.getPlayer();
        NavTargetComponent t = ship == null ? null : ship.getComponent(NavTargetComponent.class);
        if (t == null || t.uuid == null) return null;
        if (world.engine.getEntity(t.uuid) == null) {
            t.uuid = null;
            return null;
        }
        return t.uuid;
    }

    /** The target's entity, or null. */
    public Entity entity() {
        String uuid = uuid();
        return uuid == null ? null : world.engine.getEntity(uuid);
    }

    /** Sets the target (null clears it). Returns what to tell the player. */
    public String select(String uuid) {
        Entity ship = world.getPlayer();
        if (ship == null) return "No ship";
        if (uuid != null && (uuid.equals(world.universe.uuidOf(ship)) || world.engine.getEntity(uuid) == null)) {
            return "Can't target that";
        }
        NavTargetComponent t = ship.getComponent(NavTargetComponent.class);
        if (t == null) ship.add(t = new NavTargetComponent());
        t.uuid = uuid;
        return uuid == null ? "Navigation target cleared" : "Navigation target: " + name(uuid);
    }

    /** Selects the next of {@link #candidates()}, wrapping round to none. Returns what to tell the player. */
    public String cycle(int direction) {
        List<String> all = candidates();
        if (all.isEmpty()) return "Nothing to target";
        int i = all.indexOf(uuid());
        int n = all.size() + 1; // the last slot is "none"
        int next = ((i < 0 ? all.size() : i) + (direction < 0 ? -1 : 1) + n) % n;
        return select(next == all.size() ? null : all.get(next));
    }

    /**
     * What can be targeted, in the order {@link #cycle} goes through it: contract waypoints first,
     * then hostiles in the bubble, the other craft in the bubble, known pirates, stations and the
     * freighters on the rails. Craft close by come before the stations so the ship you are
     * fighting is a press or two away.
     */
    public List<String> candidates() {
        Set<String> out = new LinkedHashSet<>();
        for (Contract c : world.contracts.active()) {
            String w = c.waypointUuid();
            if (w != null && world.engine.getEntity(w) != null) out.add(w);
        }
        List<String> others = new ArrayList<>();
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(MARKED)) {
            if (e == world.getPlayer() || e.getComponent(StationComponent.class) != null) continue;
            String uuid = world.universe.uuidOf(e);
            if (uuid == null) continue;
            if (world.macroSim.hostiles().get(uuid) != null) out.add(uuid);
            else others.add(uuid);
        }
        out.addAll(others);
        out.addAll(sightings.known());
        for (EconomyNode s : world.macroSim.stations()) if (!world.pirateBase.hidden(s.stationUuid)) out.add(s.stationUuid);
        out.addAll(world.macroSim.freighters().all().keySet());
        Entity ship = world.getPlayer();
        if (ship != null) out.remove(world.universe.uuidOf(ship));
        out.removeIf(u -> world.engine.getEntity(u) == null);
        return new ArrayList<>(out);
    }

    /** What the player calls the target, or "" for none. */
    public String name() {
        String uuid = uuid();
        return uuid == null ? "" : name(uuid);
    }

    /** What the player calls a station or craft by uuid. */
    public String name(String uuid) {
        EconomyNode station = world.macroSim.station(uuid);
        if (station != null && station.displayName != null) return station.displayName;
        FreighterComponent f = world.macroSim.freighters().get(uuid);
        if (f != null && f.displayName != null) return f.displayName;
        HostileComponent h = world.macroSim.hostiles().get(uuid);
        if (h != null && h.displayName != null) return h.displayName;
        Entity e = world.engine.getEntity(uuid);
        return e == null ? "?" : ShipNames.name(e);
    }

    /** True while the target is on the rails rather than flying in the bubble. */
    public boolean onRails() {
        Entity e = entity();
        return e != null && world.universe.orbit(e) != null;
    }

    /**
     * The target's state at {@code t} relative to {@code frame}, into {@code out}; false with no
     * target or when it can't be placed there. A craft in the bubble is carried along the conic it
     * is on now, which is only meant for the near future.
     */
    public boolean stateAt(Entity frame, double t, StateVector out) {
        return stateOf(entity(), frame, t, out);
    }

    /** As {@link #stateAt}, for any craft {@code e}. */
    public boolean stateOf(Entity e, Entity frame, double t, StateVector out) {
        if (e == null || frame == null) return false;
        if (world.universe.orbit(e) != null) {
            world.universe.stateInFrame(e, frame, t, out);
            return true;
        }
        PhysicsBodyComponent pb = e.getComponent(PhysicsBodyComponent.class);
        String anchor = world.physics.getAnchorParentUuid();
        if (pb == null || pb.body == null || anchor == null) return false;
        Entity parent = world.universe.entity(anchor);
        CelestialBodyComponent body = parent == null ? null : world.universe.celestial(parent);
        if (body == null) return false;
        world.physics.getTrueState(pb.body, trueState);
        scratch.set(trueState[0], trueState[1], trueState[2], trueState[3]);
        double now = world.universe.now();
        if (t != now) KeplerOrbit.fromState(scratch, body.mu, now).stateAt(t, scratch);
        if (frame != parent) {
            // Into another body's frame through both bodies' absolute states
            StateVector p = world.universe.absoluteState(parent, t, new StateVector());
            StateVector f = world.universe.absoluteState(frame, t, new StateVector());
            scratch.set(scratch.x + p.x - f.x, scratch.y + p.y - f.y, scratch.vx + p.vx - f.vx, scratch.vy + p.vy - f.vy);
        }
        out.set(scratch.x, scratch.y, scratch.vx, scratch.vy);
        return true;
    }
}
