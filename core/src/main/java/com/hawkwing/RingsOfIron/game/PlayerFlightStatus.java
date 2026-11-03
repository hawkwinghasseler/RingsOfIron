package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.hawkwing.RingsOfIron.wiring.PlayerOrbitInfo;
import com.ringsofiron.combatsys.ContactState;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.fire.Track;
import com.ringsofiron.combatsys.systems.SensorTrackingSystem;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.shipops.ShipNames;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.providers.FlightStatusProvider;

/**
 * ui-hud's {@link FlightStatusProvider}: the player's ship against the frame that matters (the
 * locked target, the nav target close by, the nearest station, else the body it orbits), its stick
 * and engines, and its delta-v from navigation's fuel sums.
 */
public class PlayerFlightStatus implements FlightStatusProvider {
    /** Reads between fresh estimates of the delta-v home: each plans a route to every station, as {@link FlightWarnings} does. */
    static final int HOME_REFRESH = 60;
    /** Stick input counted as firing. */
    static final float FIRING = 0.01f;

    private static final Family STATIONS = Family.all(StationComponent.class).get();

    private final GameContext ctx;
    private final DockingSystem docking;
    private final SensorTrackingSystem sensors;
    private final Navigation nav;
    private final PlayerOrbitInfo orbits;
    private final ImmutableArray<Entity> stations;
    private final Vector2 pos = new Vector2(), vel = new Vector2(), shipPos = new Vector2(), shipVel = new Vector2();
    private int sinceHome = HOME_REFRESH;
    private double home = Double.NaN;

    public PlayerFlightStatus(GameContext ctx, DockingSystem docking, SensorTrackingSystem sensors, Navigation nav, PlayerOrbitInfo orbits) {
        this.ctx = ctx;
        this.docking = docking;
        this.sensors = sensors;
        this.nav = nav;
        this.orbits = orbits;
        this.stations = ctx.engine.getEcsEngine().getEntitiesFor(STATIONS);
    }

    @Override
    public void read(FlightStatus out) {
        Entity ship = ctx.player();
        if (ship == null) return;
        out.present = true;
        Body body = docking.bodyOf(ship);
        ThrusterComponent thruster = ship.getComponent(ThrusterComponent.class);
        if (body != null) {
            out.heading = HudContacts.heading(body.getAngle() * MathUtils.radiansToDegrees);
            out.spin = body.getAngularVelocity() * MathUtils.radiansToDegrees;
            out.mass = PhysicsManager.getMass(body);
            if (thruster != null && out.mass > 0f) out.acceleration = thruster.forwardThrust / out.mass;
        }
        out.outOfPropellant = thruster != null && thruster.isOutOfFuel();
        FlightControlComponent stick = ship.getComponent(FlightControlComponent.class);
        if (stick != null) {
            out.forward = stick.forward;
            out.strafe = stick.strafe;
            out.turn = stick.turn;
            out.rotationAssist = stick.rotationAssist;
            out.assistHeld = stick.assistHeld;
            boolean fuel = thruster != null && !out.outOfPropellant;
            out.mainEngine = fuel && stick.forward > FIRING;
            out.rcs = fuel && (stick.forward < -FIRING || Math.abs(stick.strafe) > FIRING || Math.abs(stick.turn) > FIRING
                    || stick.assistTurn > FIRING);
        }
        out.autopilot = nav.autopilot.engaged();
        out.deltaVLeft = nav.deltaVLeft();
        out.deltaVPlanned = nav.deltaVNeeded();
        if (++sinceHome >= HOME_REFRESH) {
            sinceHome = 0;
            home = nav.returnDeltaV();
        }
        out.deltaVHome = home;
        frame(out, ship, body);
    }

    /** The frame, first that applies: the locked target, the nav target near, the nearest station near, the orbit. */
    private void frame(FlightStatus out, Entity ship, Body body) {
        if (body != null) {
            PhysicsManager.getPosition(body, shipPos);
            PhysicsManager.getVelocity(body, shipVel);
            SensorComponent sensor = ship.getComponent(SensorComponent.class);
            Entity locked = sensor == null ? null : sensor.lockedTarget;
            if (locked != null && locked != ship && target(sensor, locked)) {
                against(out, Frame.TARGET, label(locked));
                return;
            }
            Entity navTarget = nav.target.entity();
            if (navTarget != null && navTarget != ship && HudContacts.read(navTarget, pos, vel)
                    && pos.dst(shipPos) <= FlightStatus.NAV_FRAME_RANGE) {
                against(out, Frame.NAV, nav.target.name());
                return;
            }
            Entity nearest = null;
            float best = FlightStatus.STATION_FRAME_RANGE;
            for (int i = 0; i < stations.size(); i++) {
                Entity s = stations.get(i);
                if (s == ship || !ContactState.read(s, pos, vel)) continue;
                float d = pos.dst(shipPos);
                if (d <= best) {
                    best = d;
                    nearest = s;
                }
            }
            if (nearest != null && ContactState.read(nearest, pos, vel)) {
                against(out, Frame.STATION, ShipNames.name(nearest));
                return;
            }
        }
        orbit(out);
    }

    /** Where the locked target is: from the sensor track as the target card reads it, else as it is. */
    private boolean target(SensorComponent sensor, Entity locked) {
        Track track = sensor.trackFor(locked);
        if (track != null) {
            float age = sensors.ageOf(track);
            if (track.positionAt(age, pos) != null) {
                track.velocityAt(age, vel);
                return true;
            }
        }
        return HudContacts.read(locked, pos, vel);
    }

    /** The ship against the object at {@link #pos} moving at {@link #vel}. */
    private void against(FlightStatus out, Frame frame, String name) {
        float rx = pos.x - shipPos.x, ry = pos.y - shipPos.y;
        float d = (float) Math.sqrt(rx * rx + ry * ry);
        float vx = shipVel.x - vel.x, vy = shipVel.y - vel.y;
        out.frame = frame;
        out.frameName = name;
        out.frameDistance = d;
        out.vx = vx;
        out.vy = vy;
        out.speed = (float) Math.sqrt(vx * vx + vy * vy);
        if (d > 1e-3f) {
            float ux = rx / d, uy = ry / d;
            out.closing = vx * ux + vy * uy;
            // Port of the line to it is its left: the line turned a quarter counter-clockwise
            out.lateral = -vx * uy + vy * ux;
        } else {
            out.closing = 0f;
            out.lateral = 0f;
        }
    }

    private void orbit(FlightStatus out) {
        KeplerOrbit k = orbits.currentConic();
        CelestialBodyComponent b = orbits.body();
        if (k == null || b == null) return;
        StateVector s = orbits.state();
        out.frame = Frame.ORBIT;
        out.frameName = b.name == null ? "" : b.name;
        out.frameDistance = (float) (Math.hypot(s.x, s.y) - b.radius);
        out.vx = (float) s.vx;
        out.vy = (float) s.vy;
        out.speed = (float) Math.hypot(s.vx, s.vy);
    }

    private static String label(Entity e) {
        HudMarkerComponent m = e.getComponent(HudMarkerComponent.class);
        return m != null && m.label != null && !m.label.isEmpty() ? m.label : ShipNames.name(e);
    }
}
