package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.rails.RailsSnapshot;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.providers.TrafficMapProvider;

/**
 * ui-hud's traffic map from macro-sim's rails snapshot: stations and haulers around the body the
 * player orbits (or the body the bubble falls around), plus that body's moons and the player.
 * Haulers inside the bubble have no rails entry and show as world markers instead.
 */
public class RailsTrafficMap implements TrafficMapProvider {
    private final GameWorld world;
    private final StateVector parentState = new StateVector();
    private final StateVector bodyState = new StateVector();
    private final com.ringsofiron.macrosim.rails.StateVector entryState = new com.ringsofiron.macrosim.rails.StateVector();
    private final double[] trueState = new double[4];
    private final Array<Entity> moons = new Array<>();

    public RailsTrafficMap(GameWorld world) {
        this.world = world;
    }

    @Override
    public void collect(TrafficFrame out) {
        Entity player = world.getPlayer();
        Entity frame = frameOf(player);
        CelestialBodyComponent frameBody = frame == null ? null : world.universe.celestial(frame);
        if (frameBody == null) return;
        out.frameName = frameBody.name == null ? "" : frameBody.name;
        out.frameRadius = frameBody.radius;

        RailsSnapshot snapshot = world.macroSim.rails().snapshot();
        double t = snapshot.time;
        out.time = t;

        world.universe.childBodies(frame, moons);
        for (Entity moon : moons) {
            CelestialBodyComponent c = world.universe.celestial(moon);
            if (c == null) continue;
            world.universe.stateInFrame(moon, frame, t, bodyState);
            MapBody b = out.addBody();
            b.name = c.name == null ? "" : c.name;
            b.x = bodyState.x;
            b.y = bodyState.y;
            b.radius = c.radius;
        }

        String playerUuid = player == null ? null : world.universe.uuidOf(player);
        String frameUuid = world.universe.uuidOf(frame);
        for (RailsSnapshot.Entry e : snapshot.entries) {
            if (e.uuid.equals(playerUuid)) continue;
            EconomyNode station = world.macroSim.station(e.uuid);
            FreighterComponent hauler = station == null ? world.macroSim.freighters().get(e.uuid) : null;
            if (station == null && hauler == null) continue;
            // The pirates' base stays off the map until it is found
            if (station != null && world.pirateBase.hidden(e.uuid)) continue;
            if (!positionInFrame(e, frame, frameUuid, t)) continue;
            Contact c = out.addContact();
            c.x = entryState.x;
            c.y = entryState.y;
            c.objective = world.contracts.isWaypoint(e.uuid);
            if (station != null) {
                c.kind = ContactKind.STATION;
                c.name = station.displayName == null ? "Station" : station.displayName;
            } else {
                c.kind = ContactKind.HAULER;
                c.name = hauler.displayName == null ? "Hauler" : hauler.displayName;
                c.detail = haulerDetail(hauler, t);
            }
        }

        if (player != null && playerPosition(player, frame)) {
            Contact c = out.addContact();
            c.kind = ContactKind.PLAYER;
            c.name = "You";
            c.x = bodyState.x;
            c.y = bodyState.y;
        }
    }

    /** The body the player's orbit or the bubble is around. */
    private Entity frameOf(Entity player) {
        if (player != null && world.universe.orbit(player) != null) return world.universe.parentOf(player);
        String anchor = world.physics.getAnchorParentUuid();
        return anchor == null ? null : world.universe.entity(anchor);
    }

    /** Writes the entry's frame-relative state into {@link #entryState}; false if its parent is unknown. */
    private boolean positionInFrame(RailsSnapshot.Entry e, Entity frame, String frameUuid, double t) {
        e.trajectory.stateAt(t, entryState);
        if (e.parentUuid == null || e.parentUuid.equals(frameUuid)) return e.parentUuid != null;
        Entity parent = world.universe.entity(e.parentUuid);
        if (parent == null) return false;
        world.universe.stateInFrame(parent, frame, t, parentState);
        entryState.x += parentState.x;
        entryState.y += parentState.y;
        return true;
    }

    /** Writes the player's frame-relative state into {@link #bodyState}. */
    private boolean playerPosition(Entity player, Entity frame) {
        if (world.universe.orbit(player) != null) {
            world.universe.stateInFrame(player, frame, world.universe.now(), bodyState);
            return true;
        }
        PhysicsBodyComponent pb = player.getComponent(PhysicsBodyComponent.class);
        if (pb == null || pb.body == null) return false;
        world.physics.getTrueState(pb.body, trueState);
        bodyState.set(trueState[0], trueState[1], trueState[2], trueState[3]);
        return true;
    }

    private String haulerDetail(FreighterComponent f, double now) {
        switch (f.state) {
            case IDLE:
                return "docked at " + stationName(f.dockedAtUuid);
            case IN_BUBBLE:
                return "nearby";
            default:
                String to = "to " + stationName(f.destinationUuid);
                return Double.isNaN(f.arrivalTime) ? to : to + ", " + formatEta(f.arrivalTime - now);
        }
    }

    private String stationName(String uuid) {
        EconomyNode n = uuid == null ? null : world.macroSim.station(uuid);
        return n == null || n.displayName == null ? "?" : n.displayName;
    }

    static String formatEta(double seconds) {
        if (seconds <= 0) return "arriving";
        if (seconds < 3600) return Math.max(1, Math.round(seconds / 60)) + " min";
        if (seconds < 2 * 86400) return String.format("%.1f h", seconds / 3600);
        return String.format("%.1f d", seconds / 86400);
    }
}
