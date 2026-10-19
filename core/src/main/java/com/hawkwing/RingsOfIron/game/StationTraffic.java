package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.bubble.BubbleTransitionListener;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.BerthingSystem;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.stationsdocking.StationComponent;

import java.util.List;

/**
 * Freighters macro-sim brings into the bubble while they sit at a station. On rails a docked
 * freighter rides its station's orbit, so it would come into the bubble inside the station's hull.
 * Instead it is latched to a free Medium port there and stays put, its pilot stood down, until it
 * flies itself off ({@link FreighterTraffic}) or leaves the bubble. With no port free it is moved
 * clear of the station.
 *
 * <p>Freighters fly a default hull with no grid, so each one gets a passive Medium port on its
 * nose. Register after {@link NpcBubblePilots}: this takes the pilot back off a docked freighter.
 */
public class StationTraffic implements BubbleTransitionListener {
    /** The default freighter hull's nose, where its port sits. */
    static final float NOSE_X = 20f;
    /** Space kept between a freighter moved clear and the station's hull, meters. */
    static final float CLEARANCE = 25f;
    private static final Family STATIONS = Family.all(StationComponent.class, ShipGridComponent.class, UuidComponent.class).get();

    private final ComponentMapper<DockingPortsComponent> portsMapper = ComponentMapper.getFor(DockingPortsComponent.class);
    private final ComponentMapper<ShipGridComponent> gridMapper = ComponentMapper.getFor(ShipGridComponent.class);
    private final GameWorld world;
    private final StateVector station = new StateVector();
    private final Vector2 ship = new Vector2();

    public StationTraffic(GameWorld world) {
        this.world = world;
    }

    @Override
    public void onEnterBubble(String uuid, List<RailsModel.Burn> cancelledBurns, double t) {
        Entity freighter = world.engine.getEntity(uuid);
        if (freighter == null || freighter.getComponent(FreighterComponent.class) == null) return;
        PhysicsBodyComponent pb = freighter.getComponent(PhysicsBodyComponent.class);
        if (pb == null || pb.body == null) return;
        if (!portsMapper.has(freighter)) {
            freighter.add(new DockingPortsComponent(DockingPortsComponent.hullPort(MountFootprint.MountSize.M,
                    false, NOSE_X, 0f, 1f, 0f)));
        }
        Entity at = stationInside(pb.body, t);
        if (at == null) return;
        int port = freePort(at, MountFootprint.MountSize.M);
        if (port < 0) {
            moveClear(pb.body, at);
            return;
        }
        world.docking.dockInPlace(freighter, 0, at, port);
        freighter.remove(NpcPilotComponent.class);
        // Berthed, so it flies itself off when it has somewhere to go (FreighterTraffic)
        freighter.add(BerthingSystem.berthed(at, 0, port));
        FlightControlComponent controls = freighter.getComponent(FlightControlComponent.class);
        if (controls != null) {
            controls.forward = 0f;
            controls.turn = 0f;
            controls.strafe = 0f;
        }
    }

    @Override
    public void onExitBubble(String uuid, double t) {
        // The latch lets go on its own once the freighter's body is gone.
    }

    /** The station whose hull {@code body} came into the bubble inside of, or null. */
    private Entity stationInside(Body body, double t) {
        PhysicsManager.getPosition(body, ship);
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(STATIONS)) {
            String id = e.getComponent(UuidComponent.class).id;
            if (world.macroSim.station(id) == null) continue;
            world.macroSim.relativeToAnchor(id, t, station);
            if (Math.hypot(station.x - ship.x, station.y - ship.y) < reach(e)) return e;
        }
        return null;
    }

    /** Distance from a station's middle within which a freighter hull would touch it, meters. */
    private float reach(Entity station) {
        ShipGridComponent grid = gridMapper.get(station);
        return (float) Math.hypot(grid.width, grid.height) * grid.tileSize * 0.5f + NOSE_X;
    }

    private int freePort(Entity station, MountFootprint.MountSize size) {
        DockingPortsComponent ports = portsMapper.get(station);
        if (ports == null) return -1;
        for (int i = 0; i < ports.ports.length; i++) {
            DockingPort p = ports.ports[i];
            if (p.size == size && p.isFree() && p.intact()) return i;
        }
        return -1;
    }

    /** Moves the freighter straight out from the station's middle until its hull is clear. */
    private void moveClear(Body body, Entity at) {
        float dx = ship.x - (float) station.x, dy = ship.y - (float) station.y;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) {
            dx = 0f;
            dy = -1f;
        } else {
            dx /= len;
            dy /= len;
        }
        float out = reach(at) + CLEARANCE;
        PhysicsManager.setPosition(body, (float) station.x + dx * out, (float) station.y + dy * out, body.getAngle());
    }
}
