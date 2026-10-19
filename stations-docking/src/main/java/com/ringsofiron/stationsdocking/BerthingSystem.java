package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.signals.Listener;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;

/**
 * Flies craft with a {@link BerthingComponent} in to dock at a station and away again, with their
 * own thrusters (through {@link FlightControlComponent}), the way a pilot would: ask the station for
 * clearance once in range, fly round the station to a point in front of the port it gives, turn to
 * face it, close along its centerline slower the nearer it gets, and let the station's collar or
 * the magnets take it. Once docked it stays put until {@link #depart}: it undocks, backs off the
 * port and flies straight out. {@link #listener} hears when a craft has berthed and when it leaves.
 *
 * <p>On its way in it steers round other craft ({@link #avoid}): any ship in the bubble it would
 * pass closer to than {@link #CLEARANCE} beyond both hulls before it gets where it is going. Craft
 * docked at the station are left to the station's own approach (they sit at its ports), and the
 * final run down a port's centerline does not swerve.
 */
public class BerthingSystem extends EntitySystem {
    /** How far in front of the port the final approach starts, meters. */
    public static final float STANDOFF = 40f;
    /** Where a craft waits while it has no clearance, meters off the station's hull. */
    public static final float HOLD_OFF = 300f;
    /** Room kept between the station's hull and a craft flying round it, meters. */
    public static final float ROUND_OFF = 120f;
    /** Fastest a craft flies in, m/s. */
    public static final float MAX_SPEED = 60f;
    /** Slowest it closes on the port at the end, m/s. */
    public static final float CREEP = 0.15f;
    /** Speed it backs off the port at when it leaves, and then flies off at, m/s. */
    public static final float BACK_OFF_SPEED = 2f, DEPART_SPEED = 40f;
    /** Distance off the port it backs to before turning away, meters. */
    public static final float BACK_OFF = 60f;
    /** Seconds before asking for clearance again after a refusal. */
    public static final float RETRY_SECONDS = 20f;
    /** Bearing off the port's centerline within which a craft heads straight for the standoff point. */
    static final float FRONT_ARC = 50f * MathUtils.degreesToRadians;
    /** Room kept between its hull and another craft's as it flies past, meters. */
    public static final float CLEARANCE = 25f;
    /** Seconds ahead it looks for craft in its way. */
    static final float LOOKAHEAD = 30f;

    private static final float M = PhysicsManager.METERS_PER_UNIT;

    /** Hears craft berthing and leaving. */
    public interface BerthingListener {
        void berthed(Entity craft, Entity station);

        void departed(Entity craft, Entity station);
    }

    public BerthingListener listener = new BerthingListener() {
        @Override
        public void berthed(Entity craft, Entity station) {
        }

        @Override
        public void departed(Entity craft, Entity station) {
        }
    };

    private static final Family FAMILY = Family.all(BerthingComponent.class, FlightControlComponent.class,
            ThrusterComponent.class).get();
    private static final Family OTHERS = Family.all(PhysicsBodyComponent.class, ShipGridComponent.class).get();
    private final ComponentMapper<BerthingComponent> berthing = ComponentMapper.getFor(BerthingComponent.class);
    private final ComponentMapper<FlightControlComponent> controls = ComponentMapper.getFor(FlightControlComponent.class);
    private final ComponentMapper<ThrusterComponent> thrusters = ComponentMapper.getFor(ThrusterComponent.class);
    private final ComponentMapper<DockingPortsComponent> ports = ComponentMapper.getFor(DockingPortsComponent.class);
    private final DockingSystem docking;
    private final UuidRegistry uuids;
    private final Listener<DockingEvent> events = (signal, e) -> onEvent(e);
    private ImmutableArray<Entity> craft, others;

    private final Vector2 face = new Vector2(), qFace = new Vector2(), n = new Vector2(), centre = new Vector2();
    private final Vector2 v = new Vector2(), vs = new Vector2(), want = new Vector2(), tmp = new Vector2(), look = new Vector2();
    private final Vector2 at = new Vector2(), rel = new Vector2(), miss = new Vector2(), ov = new Vector2();

    public BerthingSystem(DockingSystem docking, UuidRegistry uuids) {
        this.docking = docking;
        this.uuids = uuids;
    }

    @Override
    public void addedToEngine(Engine engine) {
        craft = engine.getEntitiesFor(FAMILY);
        others = engine.getEntitiesFor(OTHERS);
        docking.events.add(events);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        docking.events.remove(events);
        craft = others = null;
    }

    /** Sends a berthed craft off: it undocks, backs off the port and flies away. */
    public void depart(Entity e) {
        BerthingComponent b = berthing.get(e);
        if (b == null || b.phase != BerthingComponent.Phase.BERTHED) return;
        docking.undock(e);
        enter(b, BerthingComponent.Phase.DEPART, "Undocking");
    }

    /** Puts a craft already docked at {@code station}'s port {@code port} under berthing, berthed. */
    public static BerthingComponent berthed(Entity station, int ownPort, int port) {
        BerthingComponent b = new BerthingComponent(EntityRef.of(station), ownPort, BerthingComponent.Phase.BERTHED);
        b.port = port;
        b.status = "Docked";
        return b;
    }

    @Override
    public void update(float dt) {
        for (int i = craft.size() - 1; i >= 0; i--) {
            Entity e = craft.get(i);
            BerthingComponent b = berthing.get(e);
            FlightControlComponent c = controls.get(e);
            Entity station = b.station == null ? null : b.station.get(uuids);
            Body body = docking.bodyOf(e);
            Body sb = station == null ? null : docking.bodyOf(station);
            DockingPort own = ports.has(e) ? ports.get(e).get(b.ownPort) : null;
            b.time += dt;
            if (body == null || sb == null || own == null) {
                c.clear();
                continue;
            }
            switch (b.phase) {
                case APPROACH:
                case FINAL:
                    approach(e, b, c, own, body, station, sb, dt);
                    break;
                case BERTHED:
                    c.clear();
                    break;
                case DEPART:
                    leave(e, b, c, own, body, station, sb);
                    break;
                default:
                    break;
            }
        }
    }

    private void approach(Entity e, BerthingComponent b, FlightControlComponent c, DockingPort own, Body body,
                          Entity station, Body sb, float dt) {
        if (own.state != DockingPort.State.IDLE) {
            // The collar or the magnets have it
            c.clear();
            b.status = "Drawing in";
            return;
        }
        DockingSystem.worldPoint(body, own.faceX(), own.faceY(), face);
        stationCentre(station, sb, centre);
        float radius = radius(station);
        if (b.port >= 0 && !cleared(e, station, b.port)) b.port = -1;
        b.retry -= dt;
        if (b.port < 0) {
            if (b.phase == BerthingComponent.Phase.FINAL) enter(b, BerthingComponent.Phase.APPROACH, "");
            if (b.retry <= 0f && face.dst(centre) < DockingSystem.CLEARANCE_RANGE * 0.9f) ask(e, b, station);
        }
        DockingSystem.velocityAt(sb, centre, vs);
        if (b.port < 0) {
            // Wait off the hull on this side until it may come in
            want.set(face).sub(centre).nor().scl(radius + HOLD_OFF).add(centre);
            fly(e, body, c, own, want, vs, centre, false);
            return;
        }
        DockingPort q = ports.get(station).ports[b.port];
        DockingSystem.worldPoint(sb, q.faceX(), q.faceY(), qFace);
        DockingSystem.worldVector(sb, q.normalX, q.normalY, n);
        DockingSystem.velocityAt(sb, qFace, vs);
        tmp.set(face).sub(qFace);
        float along = tmp.dot(n), lateral = tmp.crs(n);
        if (b.phase == BerthingComponent.Phase.APPROACH) {
            float bearing = angle(tmp.set(face).sub(centre), n);
            if (Math.abs(bearing) > FRONT_ARC) {
                // Round the station's side, not through it
                float step = Math.signum(bearing) * Math.min(Math.abs(bearing) - FRONT_ARC * 0.5f, 60f * MathUtils.degreesToRadians);
                want.set(n).rotateRad(bearing - step).scl(radius + ROUND_OFF).add(centre);
                b.status = "Flying round " + nameOf(station);
                fly(e, body, c, own, want, vs, qFace, false);
                return;
            }
            want.set(n).scl(STANDOFF).add(qFace);
            b.status = "Approaching port " + (b.port + 1);
            if (face.dst(want) < 6f && v.set(velocity(body)).sub(vs).len() < 1.5f) {
                enter(b, BerthingComponent.Phase.FINAL, "Final approach");
            } else {
                fly(e, body, c, own, want, vs, qFace, true);
                return;
            }
        }
        if (along > STANDOFF * 2.5f || Math.abs(lateral) > 15f) {
            enter(b, BerthingComponent.Phase.APPROACH, "Going round again");
            return;
        }
        // Down the centerline: close slower the nearer it gets, and steer back onto the line
        float closing = MathUtils.clamp(0.08f * along, CREEP, 2.5f);
        want.set(vs).mulAdd(n, -closing).add(-n.y * lateral * 0.3f, n.x * lateral * 0.3f);
        b.status = String.format(java.util.Locale.ROOT, "Final approach: %.0f m", Math.max(0f, along));
        steer(body, c, thrusters.get(e), want, facing(own, n), 1.0f);
    }

    private void leave(Entity e, BerthingComponent b, FlightControlComponent c, DockingPort own, Body body,
                       Entity station, Body sb) {
        if (own.state != DockingPort.State.IDLE) {
            c.clear();
            return;
        }
        DockingSystem.worldPoint(body, own.faceX(), own.faceY(), face);
        int port = b.port;
        DockingPort[] list = ports.get(station).ports;
        if (port >= 0 && port < list.length) {
            DockingSystem.worldPoint(sb, list[port].faceX(), list[port].faceY(), qFace);
            DockingSystem.worldVector(sb, list[port].normalX, list[port].normalY, n);
        } else {
            stationCentre(station, sb, qFace);
            n.set(face).sub(qFace).nor();
        }
        DockingSystem.velocityAt(sb, qFace, vs);
        float off = tmp.set(face).sub(qFace).dot(n);
        if (off < BACK_OFF) {
            want.set(vs).mulAdd(n, BACK_OFF_SPEED);
            steer(body, c, thrusters.get(e), want, facing(own, n), 0.8f);
            b.status = "Backing off";
            return;
        }
        if (!"Leaving".equals(b.status)) listener.departed(e, station);
        b.status = "Leaving";
        // Nose out along the port's normal, and away
        want.set(vs).mulAdd(n, DEPART_SPEED);
        steer(body, c, thrusters.get(e), want, n.angleRad(), 0.4f);
    }

    /**
     * Flies the craft's port face to {@code to}, arriving at the station's velocity {@code vs}.
     * Far out the nose turns to where the thrust is needed; close in it faces {@code look}, the
     * port it is going to (or the station), when {@code face} is set.
     */
    private void fly(Entity e, Body body, FlightControlComponent c, DockingPort own, Vector2 to, Vector2 vs,
                     Vector2 look, boolean face) {
        ThrusterComponent t = thrusters.get(e);
        float mass = Math.max(1f, PhysicsManager.getMass(body));
        tmp.set(to).sub(this.face);
        float d = tmp.len();
        boolean near = d < 200f;
        float brake = 0.5f * Math.min(Math.min(t.forwardThrust, t.reverseThrust), near ? t.lateralThrust : Float.MAX_VALUE) / mass;
        float speed = Math.min(MAX_SPEED, (float) Math.sqrt(2f * Math.max(0.02f, brake) * d));
        speed = Math.min(speed, 0.4f * d + 0.05f);
        want.set(vs).mulAdd(tmp.nor(), speed);
        avoid(e, body, want, d / Math.max(1f, speed));
        float heading;
        if (near) {
            this.look.set(look).sub(this.face).nor().scl(-1f);
            heading = face ? facing(own, this.look) : body.getAngle();
        } else {
            tmp.set(want).sub(velocity(body));
            heading = tmp.len() > 1f ? tmp.angleRad() : tmp.set(to).sub(this.face).angleRad();
        }
        steer(body, c, t, want, heading, near ? 0.8f : 0.5f);
    }

    /**
     * Bends {@code want} (world, m/s) round the craft it would otherwise pass too close to in the
     * next {@code arrive} seconds (capped at {@link #LOOKAHEAD}): for each, where the two would be
     * nearest, flying at {@code want} against the other's own velocity, and if that is inside both
     * hulls plus {@link #CLEARANCE}, the sideways speed that opens it out to there in the time left.
     * A craft already too close is backed away from.
     */
    void avoid(Entity e, Body body, Vector2 want, float arrive) {
        if (others == null) return;
        float horizon = Math.min(LOOKAHEAD, arrive);
        Vector2 w = body.getWorldCenter();
        at.set(w.x * M, w.y * M);
        float mine = radius(e, 20f);
        Entity dockedTo = docking.dockedTo(e);
        float wx = want.x, wy = want.y;
        for (int i = 0; i < others.size(); i++) {
            Entity o = others.get(i);
            if (o == e || o == dockedTo || docking.dockedTo(o) != null) continue;
            Body ob = docking.bodyOf(o);
            if (ob == null) continue;
            Vector2 c = ob.getWorldCenter();
            rel.set(c.x * M, c.y * M).sub(at);
            float clear = mine + radius(o, 20f) + CLEARANCE;
            float dist = rel.len();
            if (dist > clear + MAX_SPEED * horizon) continue;
            if (dist < clear) {
                // Too close already: back away from it
                want.mulAdd(rel, -Math.min(MAX_SPEED, (clear - dist) * 0.5f) / Math.max(0.01f, dist));
                continue;
            }
            // Our velocity relative to it, and the nearest the two get within the horizon
            v.set(wx, wy).sub(PhysicsManager.getVelocity(ob, ov));
            float closing = v.len2();
            if (closing < 1e-4f) continue;
            float t = rel.dot(v) / closing;
            if (t <= 0f || t > horizon) continue;
            miss.set(v).scl(t).sub(rel); // where we'd be from it then
            float gap = miss.len();
            if (gap >= clear) continue;
            if (gap < 1e-3f) miss.set(-v.y, v.x); // dead ahead: either side will do
            want.mulAdd(miss.nor(), (clear - gap) / Math.max(2f, t));
        }
        if (want.len() > MAX_SPEED + vs.len()) want.sub(vs).limit(MAX_SPEED).add(vs);
    }

    /**
     * Sets the controls to bring the craft's velocity to {@code want} (world, m/s) and its nose to
     * {@code heading}, within its thrusters: {@code gain} is how hard it chases the velocity, per second.
     */
    private void steer(Body body, FlightControlComponent c, ThrusterComponent t, Vector2 want, float heading, float gain) {
        float mass = Math.max(1f, PhysicsManager.getMass(body));
        tmp.set(want).sub(velocity(body)).scl(gain * mass).rotateRad(-body.getAngle());
        c.forward = MathUtils.clamp(tmp.x / Math.max(1f, tmp.x >= 0f ? t.forwardThrust : t.reverseThrust), -1f, 1f);
        c.strafe = MathUtils.clamp(tmp.y / Math.max(1f, t.lateralThrust), -1f, 1f);
        float err = wrap(heading - body.getAngle());
        float spin = MathUtils.clamp(err * 0.6f, -0.25f, 0.25f);
        c.turn = MathUtils.clamp((spin - body.getAngularVelocity()) * PhysicsManager.getInertia(body) * 2f
                / Math.max(1f, t.maxTorque), -1f, 1f);
        c.rotationAssist = false;
    }

    private Vector2 velocity(Body body) {
        return PhysicsManager.getVelocity(body, v);
    }

    /** The heading that puts {@code own}'s face looking along {@code -dir}, square onto a port facing {@code dir}. */
    private static float facing(DockingPort own, Vector2 dir) {
        return MathUtils.atan2(-dir.y, -dir.x) - MathUtils.atan2(own.normalY, own.normalX);
    }

    /** The port is still armed for this craft. */
    private boolean cleared(Entity e, Entity station, int port) {
        DockingPort q = ports.get(station).get(port);
        UuidComponent id = e.getComponent(UuidComponent.class);
        return q != null && q.armed && q.isFree() && id != null && id.id.equals(q.armedFor);
    }

    private void ask(Entity e, BerthingComponent b, Entity station) {
        b.retry = RETRY_SECONDS;
        String refused = docking.arm(e, b.ownPort);
        if (refused != null) {
            // Its own trouble, such as power still coming up: try again soon
            b.retry = 2f;
            b.status = "Can't arm its port: " + refused;
            return;
        }
        DockingSystem.Clearance got = docking.requestClearance(e, b.ownPort);
        if (got.outcome == DockingSystem.Clearance.Outcome.GRANTED && got.station == station) {
            b.port = got.port;
            b.status = "Cleared for port " + (got.port + 1);
            return;
        }
        if (got.outcome == DockingSystem.Clearance.Outcome.GRANTED) {
            // Another station answered: withdraw from it, and keep the port armed
            docking.disarm(e, b.ownPort);
            docking.arm(e, b.ownPort);
        }
        b.status = got.outcome == DockingSystem.Clearance.Outcome.DENIED ? "Refused clearance; holding off"
                : got.outcome == DockingSystem.Clearance.Outcome.NO_FREE_PORT ? "No free port; holding off" : "Holding off";
    }

    private void onEvent(DockingEvent ev) {
        if (craft == null) return;
        for (Entity e : new Entity[]{ev.vessel, ev.other}) {
            BerthingComponent b = e == null ? null : berthing.get(e);
            if (b == null) continue;
            Entity other = e == ev.vessel ? ev.other : ev.vessel;
            Entity station = b.station == null ? null : b.station.get(uuids);
            if (other != station) continue;
            if (ev.kind == DockingEvent.Kind.DOCKED && b.phase != BerthingComponent.Phase.BERTHED) {
                b.port = e == ev.vessel ? ev.otherPort : ev.port;
                enter(b, BerthingComponent.Phase.BERTHED, "Docked");
                if (controls.has(e)) controls.get(e).clear();
                listener.berthed(e, station);
            } else if (ev.kind == DockingEvent.Kind.TORN) {
                enter(b, BerthingComponent.Phase.DEPART, "Torn loose");
            }
        }
    }

    private static void enter(BerthingComponent b, BerthingComponent.Phase phase, String status) {
        b.phase = phase;
        b.time = 0f;
        if (!status.isEmpty()) b.status = status;
    }

    /** Middle of the station: its grid's, or its body's. */
    private static Vector2 stationCentre(Entity station, Body sb, Vector2 out) {
        Vector2 w = sb.getWorldCenter();
        return out.set(w.x * M, w.y * M);
    }

    /** Half the station's diagonal, meters: no part of it is farther from its middle. */
    private static float radius(Entity station) {
        return radius(station, 50f);
    }

    /** Half a craft's grid's diagonal, meters, or {@code otherwise} if it has no grid. */
    private static float radius(Entity e, float otherwise) {
        ShipGridComponent g = e.getComponent(ShipGridComponent.class);
        return g == null ? otherwise : (float) Math.hypot(g.width, g.height) * g.tileSize * 0.5f;
    }

    private static String nameOf(Entity station) {
        StationComponent s = station.getComponent(StationComponent.class);
        return s != null && s.name != null ? s.name : "the station";
    }

    /** Angle from {@code ref} to {@code dir}, -pi..pi. */
    private static float angle(Vector2 dir, Vector2 ref) {
        return MathUtils.atan2(ref.crs(dir), ref.dot(dir));
    }

    private static float wrap(float a) {
        return MathUtils.atan2(MathUtils.sin(a), MathUtils.cos(a));
    }
}
