package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.coreengine.SimClock;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.warp.BubbleHandoff;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Rescue and requisition (Technical Design Decisions section 6). When the player's escape pod
 * launches (see {@link AbandonShip}), at the end of that frame: the old ship is left a derelict,
 * time skips {@link #TIME_SKIP} on the rails, and the player wakes at the nearest station of their
 * sponsor, aboard a ship requisitioned there ({@link Requisition}), docked, with the crew who got
 * away in the pods. Crew who didn't reach a pod are lost with the old ship.
 *
 * <p>The player's avatar is kept, not made anew: it is the player, and what belongs to the player
 * goes with it. The credits in the old ship's account go to the new one; its cargo is lost.
 */
public class Rescue {
    /** How long the pod drifts before the player wakes at the station, seconds. */
    public static final double TIME_SKIP = 3 * 86_400;
    /** How far behind the station, along its orbit, the new ship is brought in, m. */
    public static final double ARRIVAL_OFFSET = 150;
    /** Frames to wait for the station to come into the bubble before giving up on docking. */
    static final int DOCK_FRAMES = 600;

    private static final Family STATIONS = Family.all(StationComponent.class, OrbitComponent.class).get();

    /** Which ship the player is given at the station, and what it costs. */
    public interface Requisition {
        /** The ship id (in {@code ships.toml}) to requisition at {@code station}, with {@code credits} to spend. */
        String ship(Entity station, double credits);

        /** Credits the ship costs; 0 for one that is free. */
        double price(String shipId);
    }

    /**
     * Until ranks exist (category 1 of the round-2 plan): always the starter corvette, free, so
     * the player is never stuck.
     */
    public static final Requisition STARTER = new Requisition() {
        @Override
        public String ship(Entity station, double credits) {
            return GameScenario.PLAYER_SHIP;
        }

        @Override
        public double price(String shipId) {
            return 0;
        }
    };

    /** What came of a rescue. */
    public static final class Result {
        public final Entity oldShip;
        public final Entity newShip;
        public final Entity station;
        public final String stationName;
        /** Crew who came through with the player. */
        public final List<AbandonShip.Survivor> survivors;
        /** Crew left aboard the old ship. */
        public final int lost;
        /** Seconds that passed: {@link #TIME_SKIP}, or 0 if rails warp was refused. */
        public final double skipped;
        /** What the new ship cost. */
        public final double price;

        Result(Entity oldShip, Entity newShip, Entity station, String stationName, List<AbandonShip.Survivor> survivors,
               int lost, double skipped, double price) {
            this.oldShip = oldShip;
            this.newShip = newShip;
            this.station = station;
            this.stationName = stationName;
            this.survivors = survivors;
            this.lost = lost;
            this.skipped = skipped;
            this.price = price;
        }
    }

    /** Every rescue, as soon as the player is aboard their new ship. */
    public final Signal<Result> rescues = new Signal<>();
    /** The ship the player is given. */
    public Requisition requisition = STARTER;
    /** Which stations will take the player in: their sponsor's. Until factions exist, any. */
    public Predicate<Entity> sponsorStation = station -> true;

    private final GameWorld world;
    private final BubbleHandoff bubble;
    /** Crew who got away from each ship, gathered until the player's pod goes. */
    private final Map<Entity, List<AbandonShip.Survivor>> escaped = new HashMap<>();
    private Entity playerEscapedFrom;
    private Entity docking, dockingAt;
    private int dockFrames;
    private final StateVector state = new StateVector(), parentState = new StateVector();
    private final double[] trueState = new double[4];

    Rescue(GameWorld world, BubbleHandoff bubble) {
        this.world = world;
        this.bubble = bubble;
        world.abandon.launches.add((signal, escape) -> {
            escaped.computeIfAbsent(escape.ship, k -> new ArrayList<>()).addAll(escape.crew);
            if (escape.player) playerEscapedFrom = escape.ship;
        });
    }

    /** True while the player's pod has launched and the rescue has yet to run. */
    public boolean pending() {
        return playerEscapedFrom != null;
    }

    /** Runs a rescue that is due, and docks the new ship once its station is in the bubble. Call after the engine's update. */
    void update() {
        if (playerEscapedFrom != null) {
            Entity from = playerEscapedFrom;
            playerEscapedFrom = null;
            run(from);
        }
        if (docking != null) dock();
    }

    private void run(Entity oldShip) {
        // The rest of the pods go with the player's
        if (AbandonShip.abandoning(oldShip)) {
            world.abandon.launchNow(oldShip);
            world.abandon.update(0f);
            world.abandon.callOff(oldShip);
        }
        List<AbandonShip.Survivor> survivors = escaped.remove(oldShip);
        if (survivors == null) survivors = new ArrayList<>();
        escaped.clear();
        if (world.timeWarp.isRailsWarp()) world.timeWarp.setWarp(1);

        Entity avatar = avatar();
        double t = world.engine.getSimSeconds();
        Entity station = nearestStation(oldShip, t);
        TraderComponent oldAccount = oldShip.getComponent(TraderComponent.class);
        double credits = oldAccount == null ? 0 : oldAccount.credits;

        String shipId = requisition.ship(station, credits);
        double price = requisition.price(shipId);
        if (price > credits) {
            shipId = STARTER.ship(station, credits);
            price = 0;
        }

        // The old ship is left a derelict where it is
        oldShip.remove(PlayerControllerComponent.class);
        world.disablement.set(oldShip, DisabledComponent.Reason.ABANDONED);
        if (oldShip.getComponent(HudMarkerComponent.class) == null) {
            if (oldShip.getComponent(TransformComponent.class) == null) oldShip.add(new TransformComponent());
            HudMarkerComponent m = new HudMarkerComponent("Your old ship (abandoned)", HudMarkerComponent.Kind.NEUTRAL).radius(10f);
            m.color.set(ShipDisablement.DISABLED_COLOR);
            oldShip.add(m);
        }
        int lost = crewAboard(oldShip);

        // The new ship, requisitioned at the station
        PhysicsBodyComponent oldBody = oldShip.getComponent(PhysicsBodyComponent.class);
        float x = 0f, y = 0f;
        if (oldBody != null && oldBody.body != null) {
            x = oldBody.body.getPosition().x + 200f;
            y = oldBody.body.getPosition().y;
        }
        Entity ship = world.getContent().factory.spawnShip(shipId, x, y, 0f);
        ship.add(new PlayerControllerComponent());
        ShipConsoles.install(ship);
        ship.add(new TraderComponent(credits - price, 0));
        if (oldAccount != null) oldAccount.credits = 0;
        ShipCargo.attach(ship);
        FlightControlComponent controls = ship.getComponent(FlightControlComponent.class);
        if (controls != null) controls.rotationAssist = true;
        crewShip(ship, avatar, survivors);

        // The pod drifts; the player wakes days later at the station
        double skipped = 0;
        if (station != null) skipped = skipTo(ship, station);

        dockingAt = station;
        docking = ship;
        dockFrames = DOCK_FRAMES;
        StationComponent sc = station == null ? null : station.getComponent(StationComponent.class);
        rescues.dispatch(new Result(oldShip, ship, station, sc == null ? "the nearest station" : sc.name,
                survivors, lost, skipped, price));
    }

    /** Puts the avatar and the survivors aboard {@code ship}, on its open deck tiles. */
    private void crewShip(Entity ship, Entity avatar, List<AbandonShip.Survivor> survivors) {
        CrewService crew = world.crew;
        List<int[]> floor = GameScenario.floorTiles(ship.getComponent(ShipGridComponent.class));
        CrewFactory.ensureBoard(ship);
        if (avatar != null && !floor.isEmpty()) {
            GridPositionComponent p = avatar.getComponent(GridPositionComponent.class);
            crew.releaseTask(avatar);
            p.stop();
            p.crossing = null;
            p.setShip(ship);
            p.setTile(floor.get(0)[0], floor.get(0)[1]);
            PlayerAvatarComponent walk = avatar.getComponent(PlayerAvatarComponent.class);
            walk.moveX = walk.moveY = 0f;
        }
        for (int i = 0; i < survivors.size() && i + 1 < floor.size(); i++) {
            int[] tile = floor.get((i + 1) * floor.size() / (survivors.size() + 1));
            AbandonShip.Survivor s = survivors.get(i);
            CrewFactory.spawn(crew, ship, s.name, s.role, 0, tile[0], tile[1]);
        }
    }

    /**
     * Collapses the bubble, moves the new ship onto the station's orbit a little behind it, lets
     * {@link #TIME_SKIP} pass on the rails and rebuilds the bubble there. Returns the seconds that
     * passed: 0 if rails warp was refused, when the new ship still ends up at the station.
     */
    private double skipTo(Entity ship, Entity station) {
        world.macroSim.setBubbleSuspended(true);
        bubble.collapseToRails();
        double t = world.engine.getSimSeconds();
        OrbitComponent so = station.getComponent(OrbitComponent.class);
        so.stateAt(t, state);
        double v = Math.hypot(state.vx, state.vy);
        double back = v > 0 ? ARRIVAL_OFFSET / v : 0;
        OrbitComponent orbit = new OrbitComponent(so.parentUuid, KeplerOrbit.fromState(
                state.x - state.vx * back, state.y - state.vy * back, state.vx, state.vy, so.mu, t));
        orbit.attitude = Math.atan2(state.vy, state.vx);
        ship.add(orbit);
        double skipped = 0;
        if (world.engine.advanceRails(SimClock.secondsToTicks(TIME_SKIP))) {
            skipped = TIME_SKIP;
            world.orbitalEvents.processUntil(world.engine.getSimSeconds());
        }
        bubble.rebuildFromRails();
        world.macroSim.setBubbleSuspended(false);
        return skipped;
    }

    /** Docks the new ship at the station once both are in the bubble; gives up after {@link #DOCK_FRAMES}. */
    private void dock() {
        if (--dockFrames < 0 || dockingAt == null) {
            docking = dockingAt = null;
            return;
        }
        Body station = world.docking.bodyOf(dockingAt);
        PhysicsBodyComponent pb = docking.getComponent(PhysicsBodyComponent.class);
        if (station == null || pb == null || pb.body == null) return;
        DockingPortsComponent ports = dockingAt.getComponent(DockingPortsComponent.class);
        DockingPortsComponent own = docking.getComponent(DockingPortsComponent.class);
        if (ports != null && own != null && own.ports.length > 0) {
            MountFootprint.MountSize size = own.ports[0].size;
            for (int i = 0; i < ports.ports.length; i++) {
                DockingPort p = ports.ports[i];
                if (p.size == size && p.state != DockingPort.State.DOCKED) {
                    world.docking.dockInPlace(docking, 0, dockingAt, i);
                    break;
                }
            }
        }
        docking = dockingAt = null;
    }

    /** The sponsor's station nearest {@code ship} (by where both are now), or null if there is none. */
    Entity nearestStation(Entity ship, double t) {
        double[] at = absolutePosition(ship, t);
        Entity best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (Entity s : world.engine.getEcsEngine().getEntitiesFor(STATIONS)) {
            if (!sponsorStation.test(s)) continue;
            world.universe.absoluteState(s, t, state);
            double d = at == null ? 0 : Math.hypot(state.x - at[0], state.y - at[1]);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /** {@code ship}'s position relative to the root body, or null if it can't be found. */
    private double[] absolutePosition(Entity ship, double t) {
        OrbitComponent orbit = ship.getComponent(OrbitComponent.class);
        if (orbit != null) {
            world.universe.absoluteState(ship, t, state);
            return new double[]{state.x, state.y};
        }
        PhysicsBodyComponent pb = ship.getComponent(PhysicsBodyComponent.class);
        String parentUuid = world.physics.getAnchorParentUuid();
        if (pb == null || pb.body == null || parentUuid == null) return null;
        world.physics.getTrueState(pb.body, trueState);
        Entity parent = world.engine.getEntity(parentUuid);
        if (parent == null) return new double[]{trueState[0], trueState[1]};
        world.universe.absoluteState(parent, t, parentState);
        return new double[]{parentState.x + trueState[0], parentState.y + trueState[1]};
    }

    private Entity avatar() {
        for (Entity e : world.crew.crewEntities()) if (e.getComponent(PlayerAvatarComponent.class) != null) return e;
        return null;
    }

    private int crewAboard(Entity ship) {
        int n = 0;
        for (Entity e : world.crew.crewEntities()) {
            GridPositionComponent p = e.getComponent(GridPositionComponent.class);
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (p.ship == ship && c.isAlive() && e.getComponent(PlayerAvatarComponent.class) == null
                    && (c.home == null || c.home == ship)) n++;
        }
        return n;
    }
}
