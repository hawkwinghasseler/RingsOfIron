package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Joint;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IdentityMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

/**
 * Docking in the bubble ({@code documentation/stations-docking/Design.md}): port magnets, active
 * latch collars, the weld between docked craft, undocking and tearing loose.
 *
 * <p>Every physics tick, before the step:
 * <ul>
 *   <li>Each intact port gets its collar as a fixture on its craft's body, half a tile deep past
 *   the hull, so collars meet before hulls do.</li>
 *   <li>An armed passive port pulls on same-sized free ports of other craft within its
 *   {@link DockingTuning#magnetRange}, at two points across its face so the pull also lines the
 *   ports up, with eddy damping. Collar faces touching within tolerance latch.</li>
 *   <li>An armed active port puts its collar head out on a slider (a prismatic joint) and a spring
 *   (a distance joint). The head's stronger magnet reaches for the other port; on contact the head
 *   is welded to it (captured), and the slider's motor draws the craft together until the collar
 *   is home, which latches.</li>
 *   <li>A latch seats the craft into line with a motor joint, then welds them rigidly. The weld's
 *   strain, low-pass filtered, tears both collars off past the port's break force or torque (one
 *   step's spike counts only up to {@link DockingTuning#STRAIN_SPIKE_CAP} times it, so a single hit
 *   doesn't; a shock past {@link DockingTuning#SHOCK_LIMIT} times it tears at once), scaled by the
 *   weaker collar's integrity.</li>
 * </ul>
 * A ship is the entity's {@link PhysicsBodyComponent} body; a station is the kinematic body on its
 * {@link KinematicTransientComponent}. Latch records live on the ports and are saved; the joints
 * are made again whenever both craft have bodies (after a load or rails warp).
 */
public class DockingSystem extends EntitySystem implements PhysicsManager.StepListener {
    private static final Family VESSELS = Family.all(DockingPortsComponent.class, UuidComponent.class).get();
    private static final Family STATIONS = Family.all(StationComponent.class, DockingPortsComponent.class).get();
    private static final float U = PhysicsManager.UNITS_PER_METER;
    private static final float M = PhysicsManager.METERS_PER_UNIT;
    /** How long docking clearance keeps a station's port armed for the craft that asked, sim seconds. */
    public static final double CLEARANCE_SECONDS = 600;
    /** How far a station hears a request for docking clearance, meters. */
    public static final float CLEARANCE_RANGE = 5_000f;

    /** Captures, latches, undocks and tears. */
    public final Signal<DockingEvent> events = new Signal<>();
    /** Ports only arm while their docking port system has power. Tests without a reactor turn it off. */
    public boolean requirePower = true;
    /** Integrity a door is left at once it has been cut through: below half, so it leaks once shut. */
    public static final float CUT_DOOR_INTEGRITY = 0.4f;

    /** Whether a station gives a craft docking clearance. */
    public interface ClearancePolicy {
        /** Null to grant clearance; otherwise why it is refused ("" for no reason given). */
        String refuse(Entity station, Entity vessel);
    }

    /** By default a station grants clearance if {@link StationComponent#grantsClearance} says so. */
    /**
     * Whether someone is walking through a pair of linked port openings, so a port door held shut
     * for pressure opens for them ({@link PortSeals}). The game points this at its crew; by default no one is.
     */
    public java.util.function.Predicate<GridLink> crossing = link -> false;

    /**
     * Pairs whose weld never tears, however hard it is pulled: a prize rigged for towing, so the
     * pair flies as one body. The game decides which; by default none.
     */
    public java.util.function.BiPredicate<Entity, Entity> towLocked = (a, b) -> false;

    /**
     * Craft that let anyone dock with them (a ship that has struck its colours): each of their
     * ports counts as armed for whoever latches on, so the dock has clearance and their door opens,
     * and one that strikes while latched opens its door then. The game decides which; by default none.
     */
    public java.util.function.Predicate<Entity> yields = craft -> false;

    public ClearancePolicy clearance = (station, vessel) ->
            station.getComponent(StationComponent.class).grantsClearance ? null : "";

    final PhysicsManager physics;
    final UuidRegistry uuids;
    final ComponentMapper<DockingPortsComponent> portsMapper = ComponentMapper.getFor(DockingPortsComponent.class);
    private final ComponentMapper<UuidComponent> uuidMapper = ComponentMapper.getFor(UuidComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<KinematicTransientComponent> transientMapper =
            ComponentMapper.getFor(KinematicTransientComponent.class);
    final ComponentMapper<ShipGridComponent> gridMapper = ComponentMapper.getFor(ShipGridComponent.class);
    private final ComponentMapper<SubsystemComponent> subsystemMapper = ComponentMapper.getFor(SubsystemComponent.class);
    private final ComponentMapper<FlightControlComponent> controlMapper = ComponentMapper.getFor(FlightControlComponent.class);

    /** Latched pairs, under both of their ports. */
    final IdentityMap<DockingPort, LatchLink> links = new IdentityMap<>();
    final Array<LatchLink> linkList = new Array<>();
    private final ObjectSet<Joint> liveJoints = new ObjectSet<>();
    private final Array<Joint> jointBuffer = new Array<>();
    private final DockingEvent event = new DockingEvent();
    private final Vector2 v1 = new Vector2();
    private final EntityListener vesselLifecycle = new VesselLifecycle(this);
    final DockingClearance clearanceDesk = new DockingClearance(this);
    final CollarHeads heads = new CollarHeads(this);
    final HullCollars hullCollars = new HullCollars();
    final MagneticCapture capture = new MagneticCapture(this);
    final LatchLinks latches = new LatchLinks(this);
    ImmutableArray<Entity> vessels;
    ImmutableArray<Entity> stations;
    boolean suspended;
    float dt = PhysicsManager.TIME_STEP;

    /** What a request for docking clearance got. */
    public static final class Clearance {
        public enum Outcome { GRANTED, DENIED, NO_FREE_PORT, NO_STATION }

        public Outcome outcome = Outcome.NO_STATION;
        /** The station asked, or null if none was in range. */
        public Entity station;
        /** The station port armed for the asking craft, when granted. */
        public int port = -1;
        /** Why the station refused, when it did and said; empty otherwise. */
        public String reason = "";
    }

    /** How a port lines up with the nearest free port it could dock with, for a pilot's readout. */
    public static final class Approach {
        /** The craft whose port it is, or null when no matching port is within range. */
        public Entity target;
        public int port = -1;
        /** Face to face, meters. */
        public float distance;
        /** How far off the target port's centerline, meters. */
        public float lateral;
        /** Turn still needed to face the target port square on, radians (-pi, pi]. */
        public float angle;
        /** Closing speed along the target port's normal, m/s; negative when drawing apart. */
        public float closing;
        /** The target port's face in the world, meters, and its outward normal there. */
        public float faceX, faceY, normalX, normalY;
    }

    public DockingSystem(PhysicsManager physics, UuidRegistry uuids) {
        this.physics = physics;
        this.uuids = uuids;
    }

    @Override
    public void addedToEngine(Engine engine) {
        vessels = engine.getEntitiesFor(VESSELS);
        stations = engine.getEntitiesFor(STATIONS);
        engine.addEntityListener(VESSELS, vesselLifecycle);
        physics.addStepListener(this);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        physics.removeStepListener(this);
        engine.removeEntityListener(vesselLifecycle);
        vessels = null;
        stations = null;
        links.clear();
        linkList.clear();
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    /**
     * Arms a port: magnets on, and an active port puts its collar out. Returns null when armed, or
     * why it cannot arm (no such port, collar torn off or jammed, no power).
     */
    public String arm(Entity vessel, int index) {
        DockingPortsComponent ports = portsMapper.get(vessel);
        DockingPort port = ports == null ? null : ports.get(index);
        if (port == null) return "no docking port";
        if (!port.intact()) return "the collar is torn off";
        if (port.jammed) return "the collar is jammed";
        if (!hasPower(vessel, port)) return "the port has no power";
        port.armed = true;
        port.armedFor = null;
        port.clearanceUntil = Double.NaN;
        port.armedAt = physics.getSimSeconds();
        return null;
    }

    /** Disarms a port, and withdraws any docking clearance stations gave the craft. A latch holds. */
    public void disarm(Entity vessel, int index) {
        DockingPortsComponent ports = portsMapper.get(vessel);
        DockingPort port = ports == null ? null : ports.get(index);
        if (port == null) return;
        port.armed = false;
        port.armedFor = null;
        port.clearanceUntil = Double.NaN;
        String uuid = uuidOf(vessel);
        if (uuid == null || stations == null) return;
        for (Entity station : stations) {
            for (DockingPort q : portsMapper.get(station).ports) {
                if (uuid.equals(q.armedFor) && q.isFree()) disarmPort(q);
            }
        }
    }

    /**
     * Asks the nearest station within {@link #CLEARANCE_RANGE} for clearance to dock with
     * {@code vessel}'s port {@code index}. A station that grants it arms its nearest free port of
     * the same size for this craft; its collar then reaches out for it (unless the craft's own
     * port is active and brings its collar). Without clearance the station's ports stay unarmed.
     */
    public Clearance requestClearance(Entity vessel, int index) {
        return clearanceDesk.requestClearance(vessel, index);
    }

    /**
     * Lets go at every latched port of {@code vessel}: the magnets disengage and the collar springs
     * push the craft apart. Both sides' ports disarm. Returns false if it was not docked.
     */
    public boolean undock(Entity vessel) {
        DockingPortsComponent ports = portsMapper.get(vessel);
        if (ports == null) return false;
        boolean any = false;
        for (DockingPort p : ports.ports) {
            LatchLink link = links.get(p);
            if (link == null) continue;
            latches.separate(link, vessel == link.a ? link.a : link.b);
            any = true;
        }
        return any;
    }

    /**
     * Latches two ports at once, without flying them together: {@code a} is put onto {@code b} at
     * the latched pose as soon as both have bodies, then welded. For craft that arrive already
     * docked (a freighter macro-sim brings into the bubble at its station).
     */
    public void dockInPlace(Entity a, int portA, Entity b, int portB) {
        DockingPort pa = portsMapper.get(a).get(portA);
        DockingPort pb = portsMapper.get(b).get(portB);
        LatchLink held = links.get(pa);
        if (held != null && held == links.get(pb)) return;
        // A port already latched elsewhere lets go first: a second latch on it would leave the first
        // link's weld holding the craft, unseen by undock (seed 7's Moon trip, round 6)
        if (held != null) latches.dropLink(held, true);
        if (links.get(pb) != null) latches.dropLink(links.get(pb), true);
        latches.latchRecords(a, portA, pa, b, portB, pb, DockingPort.State.DOCKED, true);
        pa.consented = true;
        pb.consented = true;
        pa.clamped = true;
        pb.clamped = true;
        LatchLink link = latches.linkFor(a, portA, pa, b, portB, pb);
        link.snap = true;
    }

    /** The craft {@code vessel} is latched to (the first, if several), or null. */
    public Entity dockedTo(Entity vessel) {
        DockingPortsComponent ports = portsMapper.get(vessel);
        if (ports == null) return null;
        for (DockingPort p : ports.ports) {
            if (p.state == DockingPort.State.DOCKED && p.partnerUuid != null) {
                Entity other = uuids.getEntity(p.partnerUuid);
                if (other != null) return other;
            }
        }
        return null;
    }

    /**
     * The door of {@code craft}'s port {@code index} as seen from across the dock: open, or shut
     * with or without power. {@link ForceMethod.DoorState#NONE} unless the port is latched.
     */
    public ForceMethod.DoorState doorState(Entity craft, int index) {
        DockingPortsComponent ports = portsMapper.get(craft);
        DockingPort port = ports == null ? null : ports.get(index);
        ShipGridComponent grid = gridMapper.get(craft);
        MountFootprint mount = port == null || grid == null || port.state != DockingPort.State.DOCKED ? null
                : DockLinks.mount(grid, port);
        if (mount == null) return ForceMethod.DoorState.NONE;
        // Forced open it stays open while latched, even in the frames after a load when its door is
        // held shut until the walkway is joined again
        if (port.forced) return ForceMethod.DoorState.OPEN;
        boolean any = false;
        for (int[] t : DockLinks.doorTiles(mount)) {
            EdgeWall door = DockingPortLayout.door(grid, mount, t[0], t[1]);
            if (door == null) continue;
            any = true;
            if (!door.blocks()) return ForceMethod.DoorState.OPEN;
        }
        if (!any) return ForceMethod.DoorState.NONE;
        return hasPower(craft, port) ? ForceMethod.DoorState.LOCKED : ForceMethod.DoorState.UNPOWERED;
    }

    /**
     * Forces the shut door of {@code craft}'s port {@code index} open, done by crew of {@code actor}
     * (the work itself is the caller's to time): it stays open while the craft are latched. Cutting
     * leaves it damaged. Reported as {@link DockingEvent.Kind#DOOR_FORCED}, a hostile act. Returns
     * null when forced, or why not.
     */
    public String forceDoor(Entity actor, Entity craft, int index, ForceMethod how) {
        ForceMethod.DoorState state = doorState(craft, index);
        if (state == ForceMethod.DoorState.NONE) return "no docked port door there";
        if (state == ForceMethod.DoorState.OPEN) return "the door is open";
        if (!how.works(state)) {
            return state == ForceMethod.DoorState.LOCKED ? "the door is powered: hack it or cut it"
                    : "the door has no power to hack: pry it or cut it";
        }
        DockingPort port = portsMapper.get(craft).get(index);
        ShipGridComponent grid = gridMapper.get(craft);
        MountFootprint mount = DockLinks.mount(grid, port);
        port.forced = true;
        DockLinks.setDoors(grid, mount, true);
        if (how.damages) {
            for (int[] t : DockLinks.doorTiles(mount)) {
                EdgeWall door = DockingPortLayout.door(grid, mount, t[0], t[1]);
                if (door != null && !door.isDestroyed()) door.integrity = Math.min(door.integrity, CUT_DOOR_INTEGRITY);
            }
        }
        event.kind = DockingEvent.Kind.DOOR_FORCED;
        event.vessel = actor;
        event.port = -1;
        event.other = craft;
        event.otherPort = index;
        event.permitted = port.permitted;
        event.method = how;
        events.dispatch(event);
        event.method = null;
        return null;
    }

    /** True if {@code a} is latched to {@code b} at any pair of ports. */
    public boolean docked(Entity a, Entity b) {
        DockingPortsComponent ports = portsMapper.get(a);
        if (ports == null || b == null) return false;
        for (DockingPort p : ports.ports) {
            if (p.state == DockingPort.State.DOCKED && p.partnerUuid != null && uuids.getEntity(p.partnerUuid) == b) return true;
        }
        return false;
    }

    /** True if {@code vessel} is latched somewhere the other side gave it docking clearance. */
    public boolean dockedWithClearance(Entity vessel) {
        DockingPortsComponent ports = portsMapper.get(vessel);
        if (ports == null) return false;
        for (DockingPort p : ports.ports) {
            // Latched to something there is (as dockedTo has it): a save can name a partner since gone
            if (p.state == DockingPort.State.DOCKED && p.permitted && p.partnerUuid != null
                    && uuids.getEntity(p.partnerUuid) != null) return true;
        }
        return false;
    }

    /** True while the pair is welded rigidly (seated), not still settling into line. */
    public boolean isRigid(Entity vessel) {
        DockingPortsComponent ports = portsMapper.get(vessel);
        if (ports == null) return false;
        for (DockingPort p : ports.ports) {
            LatchLink link = links.get(p);
            if (link != null && link.joint != null && link.rigid) return true;
        }
        return false;
    }

    /** Filtered strain on {@code vessel}'s latch as a share of its break limit (the worse of force and torque), 0 if not docked. */
    public float strain(Entity vessel) {
        DockingPortsComponent ports = portsMapper.get(vessel);
        if (ports == null) return 0f;
        float worst = 0f;
        for (DockingPort p : ports.ports) {
            LatchLink link = links.get(p);
            if (link == null) continue;
            DockingTuning t = p.tuning();
            worst = Math.max(worst, Math.max(link.strainForce / t.breakForce, link.strainTorque / t.breakTorque));
        }
        return worst;
    }

    /** Why rails warp has to wait (a collar is capturing or the craft are still settling), or null. */
    public String railsWarpBlockedReason() {
        if (vessels == null) return null;
        for (Entity e : vessels) {
            for (DockingPort p : portsMapper.get(e).ports) {
                if (p.state == DockingPort.State.CAPTURED) return "docking in progress";
            }
        }
        for (LatchLink link : linkList) {
            if (!link.rigid && link.joint != null) return "docking in progress";
        }
        return null;
    }

    /**
     * Rails warp is about to collapse the bubble ({@code true}) or has rebuilt it ({@code false}).
     * While collapsed, latches whose craft lose their bodies are kept, and collars are drawn in.
     */
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
        if (!suspended || vessels == null) return;
        for (Entity e : vessels) {
            for (DockingPort p : portsMapper.get(e).ports) {
                heads.destroyHead(p);
                if (p.state == DockingPort.State.CAPTURED) heads.releasePair(p);
            }
        }
    }

    public boolean isSuspended() {
        return suspended;
    }

    /** Latched pairs, for the bubble handoff. */
    Array<LatchLink> links() {
        return linkList;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override
    public void beforeStep(float deltaTime) {
        if (vessels == null) return;
        dt = deltaTime;
        refreshLiveJoints();
        double now = physics.getSimSeconds();
        for (int i = 0; i < vessels.size(); i++) {
            Entity e = vessels.get(i);
            DockingPortsComponent ports = portsMapper.get(e);
            Body body = bodyOf(e);
            for (int k = 0; k < ports.ports.length; k++) {
                DockingPort p = ports.ports[k];
                if (p.head != null && (body == null || !alive(p.guide))) heads.destroyHead(p);
                updateArming(e, p, now);
                if (p.state == DockingPort.State.CAPTURED && !heads.captureHeld(p)) heads.releasePair(p);
                if (p.state == DockingPort.State.DOCKED && links.get(p) == null) latches.adopt(e, k, p);
            }
            if (body == null) continue;
            hullCollars.syncCollars(body, ports);
            for (int k = 0; k < ports.ports.length; k++) heads.updateCollar(e, k, ports.ports[k], body);
        }
        for (int i = 0; i < vessels.size(); i++) {
            Entity e = vessels.get(i);
            Body body = bodyOf(e);
            if (body == null) continue;
            DockingPortsComponent ports = portsMapper.get(e);
            for (int k = 0; k < ports.ports.length; k++) capture.attract(e, k, ports.ports[k], body);
        }
        for (int i = linkList.size - 1; i >= 0; i--) {
            if (i < linkList.size) latches.updateLink(linkList.get(i));
        }
        for (int i = 0; i < vessels.size(); i++) holdAssist(vessels.get(i));
    }

    /**
     * While a collar or the seating turns a craft into line, its rotation assist must not hold it
     * back; once welded, it turns no harder than {@link DockingTuning#ASSIST_SHARE} of what the
     * latch bears.
     */
    private void holdAssist(Entity e) {
        FlightControlComponent control = controlMapper.get(e);
        if (control == null) return;
        boolean held = false;
        float limit = Float.POSITIVE_INFINITY;
        for (DockingPort p : portsMapper.get(e).ports) {
            LatchLink link = p.state == DockingPort.State.DOCKED ? links.get(p) : null;
            if (p.state == DockingPort.State.CAPTURED || (link != null && !link.rigid)) held = true;
            if (link != null && link.rigid) limit = Math.min(limit, DockingTuning.ASSIST_SHARE * link.pa.tuning().breakTorque * LatchLinks.hold(link));
        }
        control.assistHeld = held;
        control.assistLimit = limit;
    }

    /** Clearance runs out; power lost drops the magnets. */
    private void updateArming(Entity e, DockingPort p, double now) {
        if (!p.armed || !p.isFree()) return;
        if (!Double.isNaN(p.clearanceUntil) && now > p.clearanceUntil) {
            disarmPort(p);
        } else if (!p.usable() || !hasPower(e, p)) {
            disarmPort(p);
        }
    }

    static void disarmPort(DockingPort p) {
        p.armed = false;
        p.armedFor = null;
        p.clearanceUntil = Double.NaN;
        p.holdCollar = false;
        p.yieldedTo = null;
    }

    /** Angle of b minus angle of a when the two ports are latched: the faces point at each other. In (-pi, pi]. */
    static float matedAngle(DockingPort pa, DockingPort pb) {
        float angle = MathUtils.atan2(pa.normalY, pa.normalX) - MathUtils.atan2(pb.normalY, pb.normalX) + MathUtils.PI;
        return MathUtils.atan2(MathUtils.sin(angle), MathUtils.cos(angle));
    }

    /** Position of b's origin in a's frame when latched, meters. */
    static Vector2 matedOffset(DockingPort pa, DockingPort pb, Vector2 out) {
        float angle = matedAngle(pa, pb);
        float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
        float bx = pb.faceX(), by = pb.faceY();
        return out.set(pa.faceX() - (c * bx - s * by), pa.faceY() - (s * bx + c * by));
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    /**
     * The nearest free, intact port of the same size as {@code vessel}'s port {@code index} on
     * another craft within {@code range} meters, and how the two line up. {@link Approach#target}
     * is null when there is none.
     */
    public Approach approach(Entity vessel, int index, float range, Approach out) {
        out.target = null;
        out.port = -1;
        DockingPortsComponent ports = portsMapper.get(vessel);
        DockingPort port = ports == null ? null : ports.get(index);
        Body body = bodyOf(vessel);
        if (port == null || body == null || vessels == null) return out;
        Vector2 face = worldPoint(body, port.faceX(), port.faceY(), new Vector2());
        float best = range;
        for (Entity other : vessels) {
            if (other == vessel) continue;
            Body ob = bodyOf(other);
            if (ob == null) continue;
            DockingPort[] list = portsMapper.get(other).ports;
            for (int i = 0; i < list.length; i++) {
                DockingPort q = list[i];
                if (q.size != port.size || !q.isFree() || !q.usable()) continue;
                float d = worldPoint(ob, q.faceX(), q.faceY(), v1).dst(face);
                if (d < best) {
                    best = d;
                    out.target = other;
                    out.port = i;
                }
            }
        }
        if (out.target == null) return out;
        Body ob = bodyOf(out.target);
        DockingPort q = portsMapper.get(out.target).ports[out.port];
        Vector2 qFace = worldPoint(ob, q.faceX(), q.faceY(), new Vector2());
        Vector2 n = worldVector(ob, q.normalX, q.normalY, new Vector2());
        Vector2 rel = new Vector2(face).sub(qFace);
        out.distance = rel.len();
        out.lateral = rel.crs(n);
        out.angle = matedAngle(port, q) + body.getAngle() - ob.getAngle();
        out.angle = -MathUtils.atan2(MathUtils.sin(out.angle), MathUtils.cos(out.angle));
        Vector2 va = velocityAt(body, face, new Vector2());
        Vector2 vb = velocityAt(ob, qFace, new Vector2());
        out.closing = -va.sub(vb).dot(n);
        out.faceX = qFace.x;
        out.faceY = qFace.y;
        out.normalX = n.x;
        out.normalY = n.y;
        return out;
    }

    /** A ship's Box2D body, a station's kinematic body, or null when it has none in the bubble. */
    public Body bodyOf(Entity e) {
        PhysicsBodyComponent pb = bodyMapper.get(e);
        if (pb != null && pb.body != null) return pb.body;
        KinematicTransientComponent kt = transientMapper.get(e);
        return kt == null ? null : kt.body;
    }

    String uuidOf(Entity e) {
        UuidComponent id = uuidMapper.get(e);
        return id == null ? null : id.id;
    }

    /** The port's system has power, or the craft has no grid to check (a hull with a fitted port). */
    boolean hasPower(Entity vessel, DockingPort port) {
        if (!requirePower || port.gridX < 0) return true;
        ShipGridComponent grid = gridMapper.get(vessel);
        Entity system = grid == null ? null : grid.subsystemAt(port.gridX, port.gridY);
        SubsystemComponent sub = system == null ? null : subsystemMapper.get(system);
        return sub == null || (sub.powered && !sub.isDisabled());
    }

    void refreshLiveJoints() {
        liveJoints.clear();
        physics.getWorld().getJoints(jointBuffer);
        for (Joint j : jointBuffer) liveJoints.add(j);
        jointBuffer.clear();
    }

    boolean alive(Joint joint) {
        return joint != null && liveJoints.contains(joint);
    }

    /**
     * Destroys a joint unless Box2D already did (with one of its bodies, perhaps since the last
     * tick): destroying it twice would free native memory twice.
     */
    void destroyJoint(Joint joint) {
        if (joint == null) return;
        refreshLiveJoints();
        if (liveJoints.contains(joint)) {
            Body a = joint.getBodyA(), b = joint.getBodyB();
            physics.getWorld().destroyJoint(joint);
            liveJoints.remove(joint);
            // Box2D only looks for new contacts between shapes that have moved, so two that were
            // already touching under a joint that kept them from colliding would pass through each
            // other until one strayed far enough: ask it to look again now
            refilter(a);
            refilter(b);
        }
    }

    private static void refilter(Body body) {
        for (Fixture f : body.getFixtureList()) f.refilter();
    }

    static Vector2 worldPoint(Body body, float x, float y, Vector2 out) {
        Vector2 w = body.getWorldPoint(out.set(x * U, y * U));
        return out.set(w.x * M, w.y * M);
    }

    static Vector2 worldVector(Body body, float x, float y, Vector2 out) {
        Vector2 w = body.getWorldVector(out.set(x, y));
        return out.set(w.x, w.y);
    }

    /** Velocity of the body at world point {@code at} (meters), m/s. */
    static Vector2 velocityAt(Body body, Vector2 at, Vector2 out) {
        Vector2 v = body.getLinearVelocityFromWorldPoint(new Vector2(at.x * U, at.y * U));
        return out.set(v.x * M, v.y * M);
    }

    void fire(DockingEvent.Kind kind, Entity vessel, int port, Entity other, int otherPort, boolean permitted) {
        event.kind = kind;
        event.vessel = vessel;
        event.port = port;
        event.other = other;
        event.otherPort = otherPort;
        event.permitted = permitted;
        events.dispatch(event);
    }

}
