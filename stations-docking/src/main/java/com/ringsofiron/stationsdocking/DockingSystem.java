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
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Joint;
import com.badlogic.gdx.physics.box2d.MassData;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.joints.DistanceJoint;
import com.badlogic.gdx.physics.box2d.joints.DistanceJointDef;
import com.badlogic.gdx.physics.box2d.joints.MotorJointDef;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJoint;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJointDef;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJoint;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef;
import com.badlogic.gdx.physics.box2d.joints.RopeJointDef;
import com.badlogic.gdx.physics.box2d.joints.WeldJointDef;
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
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.PolygonSkin;
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
 *   strain, low-pass filtered, tears both collars off past the port's break force or torque.</li>
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
    /** Where an active collar's spring is anchored, this far inside the hull behind the port, meters. */
    private static final float SPRING_BASE = 1f;
    /** Seconds a latch waits for both craft to have bodies (after a load) before it is dropped. */
    static final float BODY_GRACE = 5f;
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

    public ClearancePolicy clearance = (station, vessel) ->
            station.getComponent(StationComponent.class).grantsClearance ? null : "";

    private final PhysicsManager physics;
    private final UuidRegistry uuids;
    private final ComponentMapper<DockingPortsComponent> portsMapper = ComponentMapper.getFor(DockingPortsComponent.class);
    private final ComponentMapper<UuidComponent> uuidMapper = ComponentMapper.getFor(UuidComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> bodyMapper = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<KinematicTransientComponent> transientMapper =
            ComponentMapper.getFor(KinematicTransientComponent.class);
    private final ComponentMapper<StationComponent> stationMapper = ComponentMapper.getFor(StationComponent.class);
    private final ComponentMapper<ShipGridComponent> gridMapper = ComponentMapper.getFor(ShipGridComponent.class);
    private final ComponentMapper<SubsystemComponent> subsystemMapper = ComponentMapper.getFor(SubsystemComponent.class);
    private final ComponentMapper<FlightControlComponent> controlMapper = ComponentMapper.getFor(FlightControlComponent.class);

    /** Latched pairs, under both of their ports. */
    private final IdentityMap<DockingPort, Link> links = new IdentityMap<>();
    private final Array<Link> linkList = new Array<>();
    private final ObjectSet<Joint> liveJoints = new ObjectSet<>();
    private final Array<Joint> jointBuffer = new Array<>();
    private final Array<Fixture> fixtureBuffer = new Array<>();
    private final DockingEvent event = new DockingEvent();
    private final EntityListener vesselLifecycle = new VesselLifecycle();
    private final Vector2 v0 = new Vector2(), v1 = new Vector2(), v2 = new Vector2(), v3 = new Vector2();
    private final Vector2 v4 = new Vector2(), v5 = new Vector2(), v6 = new Vector2(), v7 = new Vector2();
    private final MassData massCopy = new MassData();
    private ImmutableArray<Entity> vessels;
    private ImmutableArray<Entity> stations;
    private boolean suspended;
    private float dt = PhysicsManager.TIME_STEP;

    /** A latched pair and the joint holding them. Runtime only; rebuilt from the ports. */
    static final class Link {
        final Entity a, b;
        final DockingPort pa, pb;
        final int portA, portB;
        Joint joint;
        boolean rigid;
        float seatingTime;
        boolean hadJoint;
        float waiting;
        float strainForce, strainTorque;
        /** Put {@link #a} straight onto {@link #b} at the latched pose before the first weld. */
        boolean snap;
        /** The walkway between the two grids while welded; see {@link DockLinks}. */
        final Array<GridLink> gridLinks = new Array<>();
        /** Seconds the pressure doors stay open for someone crossing; see {@link PortSeals}. */
        float sealHold;

        Link(Entity a, int portA, DockingPort pa, Entity b, int portB, DockingPort pb) {
            this.a = a;
            this.portA = portA;
            this.pa = pa;
            this.b = b;
            this.portB = portB;
            this.pb = pb;
        }
    }

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
     * why it cannot arm (no such port, collar torn off, no power).
     */
    public String arm(Entity vessel, int index) {
        DockingPortsComponent ports = portsMapper.get(vessel);
        DockingPort port = ports == null ? null : ports.get(index);
        if (port == null) return "no docking port";
        if (!port.intact()) return "the collar is torn off";
        if (!hasPower(vessel, port)) return "the port has no power";
        port.armed = true;
        port.armedFor = null;
        port.clearanceUntil = Double.NaN;
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
        Clearance out = new Clearance();
        DockingPortsComponent ports = portsMapper.get(vessel);
        DockingPort port = ports == null ? null : ports.get(index);
        Body body = bodyOf(vessel);
        String uuid = uuidOf(vessel);
        if (port == null || body == null || uuid == null || stations == null) return out;
        Vector2 face = worldPoint(body, port.faceX(), port.faceY(), v0);
        float best = CLEARANCE_RANGE;
        for (Entity s : stations) {
            Body sb = bodyOf(s);
            if (s == vessel || sb == null) continue;
            float d = worldPoint(sb, 0f, 0f, v1).dst(face);
            if (d < best) {
                best = d;
                out.station = s;
            }
        }
        if (out.station == null) return out;
        String refused = clearance.refuse(out.station, vessel);
        if (refused != null) {
            out.outcome = Clearance.Outcome.DENIED;
            out.reason = refused;
            return out;
        }
        DockingPortsComponent stationPorts = portsMapper.get(out.station);
        Body sb = bodyOf(out.station);
        float nearest = Float.MAX_VALUE;
        for (int i = 0; i < stationPorts.ports.length; i++) {
            DockingPort q = stationPorts.ports[i];
            if (q.size != port.size || !q.isFree() || !q.intact()) continue;
            if (q.armed && q.armedFor != null && !q.armedFor.equals(uuid)) continue;
            float d = worldPoint(sb, q.faceX(), q.faceY(), v1).dst(face);
            if (d < nearest) {
                nearest = d;
                out.port = i;
            }
        }
        if (out.port < 0) {
            out.outcome = Clearance.Outcome.NO_FREE_PORT;
            return out;
        }
        DockingPort q = stationPorts.ports[out.port];
        q.armed = true;
        q.armedFor = uuid;
        q.clearanceUntil = physics.getSimSeconds() + CLEARANCE_SECONDS;
        // Two collars reaching for each other would only push each other off: the asker's wins.
        q.holdCollar = port.active;
        out.outcome = Clearance.Outcome.GRANTED;
        return out;
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
            Link link = links.get(p);
            if (link == null) continue;
            separate(link, vessel == link.a ? link.a : link.b);
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
        latchRecords(a, portA, pa, b, portB, pb, DockingPort.State.DOCKED, true);
        pa.consented = true;
        pb.consented = true;
        Link link = linkFor(a, portA, pa, b, portB, pb);
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
            Link link = links.get(p);
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
            Link link = links.get(p);
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
        for (Link link : linkList) {
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
                destroyHead(p);
                if (p.state == DockingPort.State.CAPTURED) releasePair(p);
            }
        }
    }

    public boolean isSuspended() {
        return suspended;
    }

    /** Latched pairs, for the bubble handoff. */
    Array<Link> links() {
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
                if (p.head != null && (body == null || !alive(p.guide))) destroyHead(p);
                updateArming(e, p, now);
                if (p.state == DockingPort.State.CAPTURED && !captureHeld(p)) releasePair(p);
                if (p.state == DockingPort.State.DOCKED && links.get(p) == null) adopt(e, k, p);
            }
            if (body == null) continue;
            syncCollars(body, ports);
            for (int k = 0; k < ports.ports.length; k++) updateCollar(e, k, ports.ports[k], body);
        }
        for (int i = 0; i < vessels.size(); i++) {
            Entity e = vessels.get(i);
            Body body = bodyOf(e);
            if (body == null) continue;
            DockingPortsComponent ports = portsMapper.get(e);
            for (int k = 0; k < ports.ports.length; k++) attract(e, k, ports.ports[k], body);
        }
        for (int i = linkList.size - 1; i >= 0; i--) {
            if (i < linkList.size) updateLink(linkList.get(i));
        }
        for (int i = 0; i < vessels.size(); i++) holdAssist(vessels.get(i));
    }

    /** While a collar or the seating turns a craft into line, its rotation assist must not hold it back. */
    private void holdAssist(Entity e) {
        FlightControlComponent control = controlMapper.get(e);
        if (control == null) return;
        boolean held = false;
        for (DockingPort p : portsMapper.get(e).ports) {
            Link link = p.state == DockingPort.State.DOCKED ? links.get(p) : null;
            if (p.state == DockingPort.State.CAPTURED || (link != null && !link.rigid)) held = true;
        }
        control.assistHeld = held;
    }

    /** Clearance runs out; power lost drops the magnets. */
    private void updateArming(Entity e, DockingPort p, double now) {
        if (!p.armed || !p.isFree()) return;
        if (!Double.isNaN(p.clearanceUntil) && now > p.clearanceUntil) {
            disarmPort(p);
        } else if (!p.intact() || !hasPower(e, p)) {
            disarmPort(p);
        }
    }

    private static void disarmPort(DockingPort p) {
        p.armed = false;
        p.armedFor = null;
        p.clearanceUntil = Double.NaN;
        p.holdCollar = false;
    }

    /** Puts an active port's collar head out while armed, draws it in when not, contracts it once it has hold. */
    private void updateCollar(Entity e, int index, DockingPort p, Body body) {
        boolean wantOut = p.active && p.armed && p.intact() && !p.holdCollar && p.isFree();
        if (wantOut && p.head == null) createHead(e, p, body);
        if (p.head == null) {
            p.extension = 0f;
            return;
        }
        p.extension = p.guide.getJointTranslation() * M;
        boolean captured = p.state == DockingPort.State.CAPTURED;
        steerTilt(p, captured);
        if (captured) {
            contract(e, index, p);
            return;
        }
        if (p.spring == null) return;
        float rest = SPRING_BASE + p.collarDepth * 0.5f;
        float goal = rest + (wantOut ? p.tuning().maxExtension : 0f);
        float step = DockingTuning.EXTEND_SPEED * dt;
        p.springTarget = Math.abs(goal - p.springTarget) <= step ? goal : p.springTarget + Math.signum(goal - p.springTarget) * step;
        p.spring.setLength(p.springTarget * U);
        if (!wantOut && p.springTarget <= rest + 1e-4f && p.extension < 0.02f) destroyHead(p);
    }

    /**
     * The tilt spring: turns the head back square on its carriage. Free, it is weak enough for
     * the head's magnet to lay it flat on the other port; captured, it is what turns the craft
     * into line while the collar draws in.
     */
    private static void steerTilt(DockingPort p, boolean captured) {
        if (p.tilt == null) return;
        DockingTuning t = p.tuning();
        float torque = captured ? DockingTuning.TILT_HOLD * t.breakTorque
                : DockingTuning.TILT_FREE * t.headForce * p.width * 0.5f;
        p.tilt.setMaxMotorTorque(torque * U * U);
        p.tilt.setMotorSpeed(-p.tilt.getJointAngle() * DockingTuning.TILT_RATE);
    }

    /** How far the head is turned off its port's axis now, radians; 0 with no head. */
    static float tiltOf(DockingPort p) {
        return p.tilt == null ? 0f : p.tilt.getJointAngle();
    }

    private void createHead(Entity owner, DockingPort p, Body body) {
        DockingTuning t = p.tuning();
        float d = p.collarDepth;
        float cx = p.edgeX + p.normalX * d * 0.5f, cy = p.edgeY + p.normalY * d * 0.5f;
        Vector2 at = worldPoint(body, cx, cy, v0);
        Vector2 vel = velocityAt(body, at, v1);

        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.DynamicBody;
        def.position.set(at.x * U, at.y * U);
        def.angle = body.getAngle();
        def.allowSleep = false;
        // The carriage runs on the guide and carries the head's pivot; it has no shape of its own.
        Body carriage = physics.getWorld().createBody(def);
        MassData mass = new MassData();
        mass.mass = t.headMass * 0.25f;
        mass.I = mass.mass * (p.width * U) * (p.width * U) / 12f;
        carriage.setMassData(mass);
        carriage.setUserData(owner);
        PhysicsManager.setVelocity(carriage, vel.x, vel.y);
        carriage.setAngularVelocity(body.getAngularVelocity());
        Body head = physics.getWorld().createBody(def);
        PolygonShape shape = new PolygonShape();
        float area = PolygonSkin.insetBox(shape, p.width * 0.5f * U, d * 0.5f * U, new Vector2(),
                MathUtils.atan2(p.normalX, -p.normalY));
        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        fd.density = t.headMass / area;
        fd.friction = DockingTuning.COLLAR_FRICTION;
        fd.restitution = 0f;
        fd.filter.categoryBits = (short) (ownerCategory(body) | CollisionCategories.CATEGORY_COLLAR);
        fd.filter.maskBits = CollisionCategories.MASK_ALL;
        head.createFixture(fd);
        shape.dispose();
        head.setUserData(owner);
        PhysicsManager.setVelocity(head, vel.x, vel.y);
        head.setAngularVelocity(body.getAngularVelocity());

        PrismaticJointDef guide = new PrismaticJointDef();
        guide.bodyA = body;
        guide.bodyB = carriage;
        guide.localAnchorA.set(cx * U, cy * U);
        guide.localAnchorB.set(0f, 0f);
        guide.localAxisA.set(p.normalX, p.normalY);
        guide.referenceAngle = 0f;
        guide.enableLimit = true;
        guide.lowerTranslation = 0f;
        guide.upperTranslation = t.maxExtension * U;
        guide.collideConnected = false;
        p.guide = (PrismaticJoint) physics.getWorld().createJoint(guide);

        float rest = SPRING_BASE + d * 0.5f;
        RevoluteJointDef tilt = new RevoluteJointDef();
        tilt.bodyA = carriage;
        tilt.bodyB = head;
        tilt.localAnchorA.set(0f, 0f);
        tilt.localAnchorB.set(0f, 0f);
        tilt.referenceAngle = 0f;
        tilt.enableLimit = true;
        tilt.lowerAngle = -DockingTuning.MAX_TILT;
        tilt.upperAngle = DockingTuning.MAX_TILT;
        tilt.enableMotor = true;
        tilt.collideConnected = false;
        p.tilt = (RevoluteJoint) physics.getWorld().createJoint(tilt);

        // Slack all the way out: it is only there so the head never collides with its own hull
        RopeJointDef tether = new RopeJointDef();
        tether.bodyA = body;
        tether.bodyB = head;
        tether.localAnchorA.set(cx * U, cy * U);
        tether.localAnchorB.set(0f, 0f);
        tether.maxLength = (t.maxExtension + p.width) * U;
        tether.collideConnected = false;
        p.tether = physics.getWorld().createJoint(tether);

        DistanceJointDef spring = new DistanceJointDef();
        spring.bodyA = body;
        spring.bodyB = carriage;
        spring.localAnchorA.set((p.edgeX - p.normalX * SPRING_BASE) * U, (p.edgeY - p.normalY * SPRING_BASE) * U);
        spring.localAnchorB.set(0f, 0f);
        spring.length = rest * U;
        spring.frequencyHz = DockingTuning.SPRING_HZ;
        spring.dampingRatio = DockingTuning.SPRING_DAMPING;
        spring.collideConnected = false;
        p.spring = (DistanceJoint) physics.getWorld().createJoint(spring);
        p.springTarget = rest;
        p.head = head;
        p.carriage = carriage;
        p.extension = 0f;
        steerTilt(p, false);
    }

    /**
     * The captured collar's slider draws the craft together while its tilt spring turns them into
     * line. Home and square means latched; home but still turned waits for the spring.
     */
    private void contract(Entity e, int index, DockingPort p) {
        boolean square = Math.abs(tiltOf(p)) < DockingTuning.LATCH_ANGLE;
        if (p.extension < DockingTuning.LOCK_EXTENSION && square) {
            lock(e, index, p);
            return;
        }
        float speed = Math.min(DockingTuning.CONTRACT_SPEED, 2f * p.extension + 0.02f);
        p.guide.setMotorSpeed(p.extension < DockingTuning.LOCK_EXTENSION ? 0f : -speed * U);
    }

    private void lock(Entity e, int index, DockingPort p) {
        Entity other = uuids.getEntity(p.partnerUuid);
        DockingPortsComponent otherPorts = other == null ? null : portsMapper.get(other);
        DockingPort q = otherPorts == null ? null : otherPorts.get(p.partnerPort);
        destroyHead(p);
        if (q == null) {
            p.release();
            return;
        }
        p.state = DockingPort.State.DOCKED;
        q.state = DockingPort.State.DOCKED;
        linkFor(e, index, p, other, p.partnerPort, q);
        fire(DockingEvent.Kind.DOCKED, e, index, other, p.partnerPort, p.permitted);
    }

    /** A capture is real while the capturing head's weld to the other craft stands. */
    private boolean captureHeld(DockingPort p) {
        if (p.capture != null && alive(p.capture)) return true;
        Entity other = p.partnerUuid == null ? null : uuids.getEntity(p.partnerUuid);
        DockingPortsComponent ports = other == null ? null : portsMapper.get(other);
        DockingPort q = ports == null ? null : ports.get(p.partnerPort);
        return q != null && q.capture != null && alive(q.capture);
    }

    /** Magnets of one armed free port pull on matching free ports of other craft; contact latches or captures. */
    private void attract(Entity e, int index, DockingPort p, Body body) {
        if (!p.armed || !p.isFree() || !p.intact()) return;
        if (p.active && !p.holdCollar && p.head == null) return; // collar still coming out
        DockingTuning t = p.tuning();
        String ownUuid = uuidOf(e);
        for (int i = 0; i < vessels.size(); i++) {
            Entity o = vessels.get(i);
            if (o == e) continue;
            Body ob = bodyOf(o);
            if (ob == null) continue;
            if (p.armedFor != null && !p.armedFor.equals(uuidOf(o))) continue;
            DockingPortsComponent oPorts = portsMapper.get(o);
            for (int k = 0; k < oPorts.ports.length; k++) {
                DockingPort q = oPorts.ports[k];
                if (q.size != p.size || !q.isFree() || !q.intact()) continue;
                // An extended collar does the reaching; this port waits for it.
                if (q.head != null) continue;
                if (q.armedFor != null && !q.armedFor.equals(ownUuid) && q.armed) continue;
                if (p.head != null) {
                    // The head's frame is turned like its craft's, with its middle at the origin.
                    float hx = p.normalX * p.collarDepth * 0.5f, hy = p.normalY * p.collarDepth * 0.5f;
                    if (magnet(p.head, hx, hy, p, ob, q, t.headRange, t.headForce)
                            && aligned(p.head, hx, hy, p, ob, q, t.latchOffset)) {
                        capture(e, index, p, o, k, q);
                        return;
                    }
                } else if (magnet(body, p.faceX(), p.faceY(), p, ob, q, t.magnetRange, t.magnetForce)
                        && aligned(body, p.faceX(), p.faceY(), p, ob, q, t.latchOffset)) {
                    latch(e, index, p, o, k, q);
                    return;
                }
            }
        }
    }

    /**
     * Pulls the face at local {@code (ax, ay)} of {@code a} (a craft, or a collar head) towards
     * port {@code q}'s face on {@code b}: two magnets
     * across the face, each pulling its point towards the mirrored point on the other face, so the
     * pull also turns the ports square. Returns true if in range and facing.
     */
    private boolean magnet(Body a, float ax, float ay, DockingPort p, Body b, DockingPort q, float range, float force) {
        Vector2 fa = worldPoint(a, ax, ay, v0);
        Vector2 na = worldVector(a, p.normalX, p.normalY, v1);
        Vector2 fb = worldPoint(b, q.faceX(), q.faceY(), v2);
        Vector2 nb = worldVector(b, q.normalX, q.normalY, v3);
        if (na.dot(nb) > -MathUtils.cos(DockingTuning.MAGNET_CONE)) return false;
        float rx = fb.x - fa.x, ry = fb.y - fa.y;
        float dist = (float) Math.sqrt(rx * rx + ry * ry);
        if (dist > range) return false;
        // Each port must be in front of the other, not reaching through the hull.
        if (rx * na.x + ry * na.y < -0.5f || -(rx * nb.x + ry * nb.y) < -0.5f) return false;

        float half = Math.min(p.openingWidth, q.openingWidth) * 0.5f + 0.5f;
        float per = force * 0.5f;
        float damping = per / DockingTuning.EDDY_SPEED;
        float d0 = range / 3f;
        for (int s = -1; s <= 1; s += 2) {
            // Tangents are the normals turned left; mated faces have opposite tangents.
            float pax = fa.x - na.y * half * s, pay = fa.y + na.x * half * s;
            float pbx = fb.x + nb.y * half * s, pby = fb.y - nb.x * half * s;
            float dx = pbx - pax, dy = pby - pay;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            float fall = Math.max(0f, 1f - len / range) / (1f + (len / d0) * (len / d0));
            Vector2 va = velocityAt(a, v4.set(pax, pay), v5);
            Vector2 vb = velocityAt(b, v6.set(pbx, pby), v7);
            float fx = -damping * fall * (va.x - vb.x), fy = -damping * fall * (va.y - vb.y);
            if (len > 1e-4f) {
                fx += per * fall * dx / len;
                fy += per * fall * dy / len;
            }
            PhysicsManager.applyForce(a, fx, fy, pax, pay);
            PhysicsManager.applyForce(b, -fx, -fy, pbx, pby);
        }
        return true;
    }

    /** True when the face on {@code a} sits on port {@code q}'s face: touching, centered, square and slow. */
    private boolean aligned(Body a, float ax, float ay, DockingPort p, Body b, DockingPort q, float lateral) {
        Vector2 fa = worldPoint(a, ax, ay, v0);
        Vector2 na = worldVector(a, p.normalX, p.normalY, v1);
        Vector2 fb = worldPoint(b, q.faceX(), q.faceY(), v2);
        Vector2 nb = worldVector(b, q.normalX, q.normalY, v3);
        if (-na.dot(nb) < MathUtils.cos(DockingTuning.LATCH_ANGLE)) return false;
        float rx = fb.x - fa.x, ry = fb.y - fa.y;
        float gap = rx * na.x + ry * na.y;
        float side = Math.abs(-rx * na.y + ry * na.x);
        if (gap < -0.1f || gap > DockingTuning.LATCH_GAP || side > lateral) return false;
        Vector2 va = velocityAt(a, fa, v4);
        Vector2 vb = velocityAt(b, fb, v5);
        return Vector2.len(va.x - vb.x, va.y - vb.y) <= DockingTuning.LATCH_SPEED;
    }

    private void latch(Entity a, int portA, DockingPort pa, Entity b, int portB, DockingPort pb) {
        latchRecords(a, portA, pa, b, portB, pb, DockingPort.State.DOCKED, permittedBetween(pa, pb, a, b));
        linkFor(a, portA, pa, b, portB, pb);
        fire(DockingEvent.Kind.DOCKED, a, portA, b, portB, pa.permitted);
    }

    private void capture(Entity a, int portA, DockingPort pa, Entity b, int portB, DockingPort pb) {
        Body other = bodyOf(b);
        WeldJointDef weld = new WeldJointDef();
        // Face on face and square: the weld pulls the head flush onto the port it caught
        float hx = pa.normalX * pa.collarDepth * 0.5f, hy = pa.normalY * pa.collarDepth * 0.5f;
        weld.bodyA = pa.head;
        weld.bodyB = other;
        weld.localAnchorA.set(hx * U, hy * U);
        weld.localAnchorB.set(pb.faceX() * U, pb.faceY() * U);
        float now = other.getAngle() - pa.head.getAngle();
        float off = matedAngle(pa, pb) - now;
        weld.referenceAngle = now + MathUtils.atan2(MathUtils.sin(off), MathUtils.cos(off));
        weld.collideConnected = false;
        pa.capture = physics.getWorld().createJoint(weld);
        destroyJoint(pa.spring);
        pa.spring = null;
        pa.guide.setMaxMotorForce(0.5f * pa.tuning().breakForce * U);
        pa.guide.setMotorSpeed(0f);
        pa.guide.enableMotor(true);
        latchRecords(a, portA, pa, b, portB, pb, DockingPort.State.CAPTURED, permittedBetween(pa, pb, a, b));
        fire(DockingEvent.Kind.CAPTURED, a, portA, b, portB, pa.permitted);
    }

    /** Clearance is permission: the other side's port was armed for this craft, or by its own crew. */
    private boolean permittedBetween(DockingPort pa, DockingPort pb, Entity a, Entity b) {
        String ua = uuidOf(a), ub = uuidOf(b);
        boolean bAgreed = pb.armed && (pb.armedFor == null || pb.armedFor.equals(ua));
        boolean aAgreed = pa.armed && (pa.armedFor == null || pa.armedFor.equals(ub));
        return bAgreed && aAgreed;
    }

    private void latchRecords(Entity a, int portA, DockingPort pa, Entity b, int portB, DockingPort pb,
                              DockingPort.State state, boolean permitted) {
        pa.state = state;
        pb.state = state;
        pa.partnerUuid = uuidOf(b);
        pa.partnerPort = portB;
        pb.partnerUuid = uuidOf(a);
        pb.partnerPort = portA;
        pa.permitted = permitted;
        pb.permitted = permitted;
        pa.consented = agreed(pa, b);
        pb.consented = agreed(pb, a);
        pa.clearanceUntil = Double.NaN;
        pb.clearanceUntil = Double.NaN;
    }

    /** The port's own side wanted this dock: armed by its crew, or for the craft now docking. */
    private boolean agreed(DockingPort p, Entity other) {
        return p.armed && (p.armedFor == null || p.armedFor.equals(uuidOf(other)));
    }

    /** A saved latch: find its other half and hold it again. */
    private void adopt(Entity e, int index, DockingPort p) {
        Entity other = p.partnerUuid == null ? null : uuids.getEntity(p.partnerUuid);
        DockingPortsComponent ports = other == null ? null : portsMapper.get(other);
        DockingPort q = ports == null ? null : ports.get(p.partnerPort);
        if (q == null || q.state != DockingPort.State.DOCKED || !uuidOf(e).equals(q.partnerUuid) || q.partnerPort != index) {
            p.release();
            return;
        }
        linkFor(e, index, p, other, p.partnerPort, q);
    }

    private Link linkFor(Entity a, int portA, DockingPort pa, Entity b, int portB, DockingPort pb) {
        Link link = links.get(pa);
        if (link != null) return link;
        link = new Link(a, portA, pa, b, portB, pb);
        links.put(pa, link);
        links.put(pb, link);
        linkList.add(link);
        return link;
    }

    private void updateLink(Link link) {
        Body ba = bodyOf(link.a), bb = bodyOf(link.b);
        if (link.joint != null && !alive(link.joint)) link.joint = null;
        if (ba == null || bb == null) {
            link.joint = null;
            if (suspended) return;
            link.waiting += dt;
            // A craft that had its weld and lost its body left the bubble: the latch goes with it.
            if (link.hadJoint || link.waiting > BODY_GRACE) dropLink(link, true);
            return;
        }
        link.waiting = 0f;
        DockingTuning t = link.pa.tuning();
        if (link.joint == null) {
            if (link.snap) snap(link, ba, bb);
            link.snap = false;
            link.hadJoint = true;
            if (seated(link, ba, bb)) {
                weld(link, ba, bb);
            } else {
                MotorJointDef seat = new MotorJointDef();
                seat.bodyA = ba;
                seat.bodyB = bb;
                matedOffset(link.pa, link.pb, v0);
                seat.linearOffset.set(v0.x * U, v0.y * U);
                seat.angularOffset = matedReference(link, ba, bb);
                seat.maxForce = 0.3f * t.breakForce * U;
                seat.maxTorque = 0.3f * t.breakTorque * U * U;
                seat.correctionFactor = 0.05f;
                seat.collideConnected = false;
                link.joint = physics.getWorld().createJoint(seat);
                link.rigid = false;
                link.seatingTime = 0f;
            }
            return;
        }
        if (!link.rigid) {
            link.seatingTime += dt;
            boolean seated = seated(link, ba, bb);
            if (seated || link.seatingTime > DockingTuning.SEATING_TIME) {
                destroyJoint(link.joint);
                // The last of it (or, out of time, what the seating could not close) is put
                // straight, so the two decks always line up as one
                seat(link, ba, bb);
                weld(link, ba, bb);
            }
            return;
        }
        float invDt = 1f / dt;
        float force = link.joint.getReactionForce(invDt).len() * M;
        float torque = Math.abs(link.joint.getReactionTorque(invDt)) * M * M;
        float k = Math.min(1f, dt / DockingTuning.STRAIN_FILTER);
        link.strainForce += (force - link.strainForce) * k;
        link.strainTorque += (torque - link.strainTorque) * k;
        if ((link.strainForce > t.breakForce || link.strainTorque > t.breakTorque) && !towLocked.test(link.a, link.b)) {
            tear(link);
            return;
        }
        ShipGridComponent ga = gridMapper.get(link.a), gb = gridMapper.get(link.b);
        if (ga != null && gb != null) PortSeals.update(link, ga, gb, crossing, dt);
    }

    private void weld(Link link, Body ba, Body bb) {
        WeldJointDef weld = new WeldJointDef();
        weld.bodyA = ba;
        weld.bodyB = bb;
        weld.localAnchorA.set(link.pa.faceX() * U, link.pa.faceY() * U);
        weld.localAnchorB.set(link.pb.faceX() * U, link.pb.faceY() * U);
        weld.referenceAngle = matedReference(link, ba, bb);
        makeWeld(link, weld);
    }

    /** Puts whichever of the pair moves freely exactly at the latched pose against the other. */
    private void seat(Link link, Body ba, Body bb) {
        if (ba.getType() == BodyDef.BodyType.DynamicBody) snap(link, ba, bb);
        else if (bb.getType() == BodyDef.BodyType.DynamicBody) snapOnto(bb, link.pb, ba, link.pa);
    }

    private void makeWeld(Link link, WeldJointDef weld) {
        weld.collideConnected = false;
        link.joint = physics.getWorld().createJoint(weld);
        link.rigid = true;
        link.strainForce = 0f;
        link.strainTorque = 0f;
        ShipGridComponent ga = gridMapper.get(link.a), gb = gridMapper.get(link.b);
        if (ga != null && gb != null) DockLinks.join(link, link.a, weld.bodyA, ga, link.b, weld.bodyB, gb);
    }

    /** Angle of b minus angle of a when the two ports are latched: the faces point at each other. In (-pi, pi]. */
    static float matedAngle(DockingPort pa, DockingPort pb) {
        float angle = MathUtils.atan2(pa.normalY, pa.normalX) - MathUtils.atan2(pb.normalY, pb.normalX) + MathUtils.PI;
        return MathUtils.atan2(MathUtils.sin(angle), MathUtils.cos(angle));
    }

    /**
     * The latched angle of b less a's, as the turn nearest to where the bodies are now: Box2D does
     * not wrap angles, so a joint told "0" between bodies at 0 and 2 pi would spin one round.
     */
    private static float matedReference(Link link, Body ba, Body bb) {
        float now = bb.getAngle() - ba.getAngle();
        float off = matedAngle(link.pa, link.pb) - now;
        return now + MathUtils.atan2(MathUtils.sin(off), MathUtils.cos(off));
    }

    /** Position of b's origin in a's frame when latched, meters. */
    static Vector2 matedOffset(DockingPort pa, DockingPort pb, Vector2 out) {
        float angle = matedAngle(pa, pb);
        float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
        float bx = pb.faceX(), by = pb.faceY();
        return out.set(pa.faceX() - (c * bx - s * by), pa.faceY() - (s * bx + c * by));
    }

    private boolean seated(Link link, Body ba, Body bb) {
        Vector2 fa = worldPoint(ba, link.pa.faceX(), link.pa.faceY(), v1);
        Vector2 fb = worldPoint(bb, link.pb.faceX(), link.pb.faceY(), v2);
        float angle = bb.getAngle() - ba.getAngle() - matedAngle(link.pa, link.pb);
        angle = MathUtils.atan2(MathUtils.sin(angle), MathUtils.cos(angle));
        return fa.dst(fb) < DockingTuning.SEATED_DISTANCE && Math.abs(angle) < DockingTuning.SEATED_ANGLE;
    }

    /** Puts a onto b at the latched pose, moving with b. */
    private void snap(Link link, Body ba, Body bb) {
        snapOnto(ba, link.pa, bb, link.pb);
    }

    /** Puts {@code ba} with port {@code pa} face to face on {@code bb}'s port {@code pb}, moving with bb. */
    private void snapOnto(Body ba, DockingPort pa, Body bb, DockingPort pb) {
        float angleA = bb.getAngle() - matedAngle(pa, pb);
        Vector2 fb = worldPoint(bb, pb.faceX(), pb.faceY(), v1);
        float c = MathUtils.cos(angleA), s = MathUtils.sin(angleA);
        float fx = pa.faceX(), fy = pa.faceY();
        float ox = fb.x - (c * fx - s * fy), oy = fb.y - (s * fx + c * fy);
        Vector2 vel = velocityAt(bb, v2.set(ox, oy), v3);
        PhysicsManager.setPosition(ba, ox, oy, angleA);
        PhysicsManager.setVelocity(ba, vel.x, vel.y);
        ba.setAngularVelocity(bb.getAngularVelocity());
    }

    /** Undocking: the weld lets go and the collar springs push the craft apart. */
    private void separate(Link link, Entity by) {
        Body ba = bodyOf(link.a), bb = bodyOf(link.b);
        destroyJoint(link.joint);
        link.joint = null;
        if (ba != null && bb != null) {
            float ma = ba.getType() == BodyDef.BodyType.DynamicBody ? PhysicsManager.getMass(ba) : 0f;
            float mb = bb.getType() == BodyDef.BodyType.DynamicBody ? PhysicsManager.getMass(bb) : 0f;
            float mu = ma > 0f && mb > 0f ? ma * mb / (ma + mb) : Math.max(ma, mb);
            float j = mu * DockingTuning.SEPARATION_SPEED;
            Vector2 n = worldVector(ba, link.pa.normalX, link.pa.normalY, v1);
            Vector2 ca = v2.set(ba.getWorldCenter()).scl(M);
            Vector2 cb = v3.set(bb.getWorldCenter()).scl(M);
            if (ma > 0f) PhysicsManager.applyImpulse(ba, -n.x * j, -n.y * j, ca.x, ca.y);
            if (mb > 0f) PhysicsManager.applyImpulse(bb, n.x * j, n.y * j, cb.x, cb.y);
        }
        disarmPort(link.pa);
        disarmPort(link.pb);
        boolean permitted = link.pa.permitted;
        dropLink(link, true);
        Entity other = by == link.a ? link.b : link.a;
        fire(DockingEvent.Kind.UNDOCKED, by, by == link.a ? link.portA : link.portB, other,
                by == link.a ? link.portB : link.portA, permitted);
    }

    /** Past the break limit: the weld goes, both collars tear off and the hull around each port is damaged. */
    private void tear(Link link) {
        destroyJoint(link.joint);
        link.joint = null;
        boolean permitted = link.pa.permitted;
        for (int side = 0; side < 2; side++) {
            Entity e = side == 0 ? link.a : link.b;
            DockingPort p = side == 0 ? link.pa : link.pb;
            p.collarIntegrity = 0f;
            disarmPort(p);
            Body body = bodyOf(e);
            if (body != null) removeCollar(body, p);
            ShipGridComponent grid = gridMapper.get(e);
            if (grid != null) PortDamage.tear(grid, p);
        }
        dropLink(link, true);
        fire(DockingEvent.Kind.TORN, link.a, link.portA, link.b, link.portB, permitted);
    }

    private void dropLink(Link link, boolean releasePorts) {
        destroyJoint(link.joint);
        link.joint = null;
        DockLinks.unjoin(link, gridMapper.get(link.a), gridMapper.get(link.b), true);
        links.remove(link.pa);
        links.remove(link.pb);
        linkList.removeValue(link, true);
        if (releasePorts) {
            link.pa.release();
            link.pb.release();
        }
    }

    /** Lets go of a capture and its other half: both ports free again. */
    private void releasePair(DockingPort p) {
        Entity other = p.partnerUuid == null ? null : uuids.getEntity(p.partnerUuid);
        DockingPortsComponent ports = other == null ? null : portsMapper.get(other);
        DockingPort q = ports == null ? null : ports.get(p.partnerPort);
        if (q != null && q.state == DockingPort.State.CAPTURED) {
            destroyHead(q);
            q.release();
        }
        destroyHead(p);
        p.release();
    }

    private void destroyHead(DockingPort p) {
        if (p.head != null) physics.destroyBody(p.head);
        if (p.carriage != null) physics.destroyBody(p.carriage);
        p.head = null;
        p.carriage = null;
        p.guide = null;
        p.spring = null;
        p.tilt = null;
        p.tether = null;
        p.capture = null;
        p.extension = 0f;
    }

    // ------------------------------------------------------------------
    // Collars on the hull
    // ------------------------------------------------------------------

    /** Gives every intact port its collar fixture and takes torn ones off. Cheap when nothing changed. */
    private void syncCollars(Body body, DockingPortsComponent ports) {
        int intact = 0;
        for (DockingPort p : ports.ports) if (p.intact()) intact++;
        int collars = 0;
        for (Fixture f : body.getFixtureList()) {
            if ((f.getFilterData().categoryBits & CollisionCategories.CATEGORY_COLLAR) != 0) collars++;
        }
        if (collars == intact && !anyTornWithCollar(body, ports, collars)) return;
        boolean[] has = new boolean[ports.ports.length];
        fixtureBuffer.clear();
        for (Fixture f : body.getFixtureList()) {
            if ((f.getFilterData().categoryBits & CollisionCategories.CATEGORY_COLLAR) == 0) continue;
            int k = collarPort(f, ports);
            if (k >= 0 && ports.ports[k].intact() && !has[k]) has[k] = true;
            else fixtureBuffer.add(f);
        }
        for (Fixture f : fixtureBuffer) destroyFixtureKeepingMass(body, f);
        fixtureBuffer.clear();
        for (int k = 0; k < ports.ports.length; k++) {
            if (ports.ports[k].intact() && !has[k]) addCollar(body, ports.ports[k]);
        }
    }

    private boolean anyTornWithCollar(Body body, DockingPortsComponent ports, int collars) {
        if (collars == 0) return false;
        for (DockingPort p : ports.ports) if (!p.intact()) return true;
        return false;
    }

    /** The port whose collar this fixture is (its middle sits on the collar's middle), or -1. */
    private int collarPort(Fixture f, DockingPortsComponent ports) {
        if (!(f.getShape() instanceof PolygonShape)) return -1;
        PolygonShape poly = (PolygonShape) f.getShape();
        float cx = 0f, cy = 0f;
        int n = poly.getVertexCount();
        for (int i = 0; i < n; i++) {
            poly.getVertex(i, v0);
            cx += v0.x;
            cy += v0.y;
        }
        cx = cx / n * M;
        cy = cy / n * M;
        for (int k = 0; k < ports.ports.length; k++) {
            DockingPort p = ports.ports[k];
            float mx = p.edgeX + p.normalX * p.collarDepth * 0.5f, my = p.edgeY + p.normalY * p.collarDepth * 0.5f;
            if (Vector2.dst(cx, cy, mx, my) < p.collarDepth * 0.5f) return k;
        }
        return -1;
    }

    /** Adds the collar: half a tile deep past the hull, the port's width, no mass of its own. */
    static Fixture addCollar(Body body, DockingPort p) {
        PolygonShape shape = new PolygonShape();
        float d = p.collarDepth;
        PolygonSkin.insetBox(shape, p.width * 0.5f * U, d * 0.5f * U,
                new Vector2((p.edgeX + p.normalX * d * 0.5f) * U, (p.edgeY + p.normalY * d * 0.5f) * U),
                MathUtils.atan2(p.normalX, -p.normalY));
        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        // Density 0 leaves the craft's mass (often set from its grid) alone.
        fd.density = 0f;
        fd.friction = DockingTuning.COLLAR_FRICTION;
        fd.restitution = 0f;
        fd.filter.categoryBits = (short) (ownerCategory(body) | CollisionCategories.CATEGORY_COLLAR);
        fd.filter.maskBits = CollisionCategories.MASK_ALL;
        Fixture f = body.createFixture(fd);
        shape.dispose();
        return f;
    }

    private void removeCollar(Body body, DockingPort p) {
        DockingPortsComponent dummy = new DockingPortsComponent(p);
        fixtureBuffer.clear();
        for (Fixture f : body.getFixtureList()) {
            if ((f.getFilterData().categoryBits & CollisionCategories.CATEGORY_COLLAR) != 0 && collarPort(f, dummy) == 0) {
                fixtureBuffer.add(f);
            }
        }
        for (Fixture f : fixtureBuffer) destroyFixtureKeepingMass(body, f);
        fixtureBuffer.clear();
    }

    /** Box2D recomputes mass from densities when a fixture goes; put back what the craft had. */
    private void destroyFixtureKeepingMass(Body body, Fixture f) {
        MassData m = body.getMassData();
        massCopy.mass = m.mass;
        massCopy.center.set(m.center);
        massCopy.I = m.I;
        body.destroyFixture(f);
        if (body.getType() == BodyDef.BodyType.DynamicBody) body.setMassData(massCopy);
    }

    /** The hull's collision category: the first fixture that is not a collar. */
    private static short ownerCategory(Body body) {
        for (Fixture f : body.getFixtureList()) {
            short c = f.getFilterData().categoryBits;
            if ((c & CollisionCategories.CATEGORY_COLLAR) == 0) return c;
        }
        return CollisionCategories.CATEGORY_SHIP;
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
                if (q.size != port.size || !q.isFree() || !q.intact()) continue;
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

    private String uuidOf(Entity e) {
        UuidComponent id = uuidMapper.get(e);
        return id == null ? null : id.id;
    }

    /** The port's system has power, or the craft has no grid to check (a hull with a fitted port). */
    private boolean hasPower(Entity vessel, DockingPort port) {
        if (!requirePower || port.gridX < 0) return true;
        ShipGridComponent grid = gridMapper.get(vessel);
        Entity system = grid == null ? null : grid.subsystemAt(port.gridX, port.gridY);
        SubsystemComponent sub = system == null ? null : subsystemMapper.get(system);
        return sub == null || (sub.powered && !sub.isDisabled());
    }

    private void refreshLiveJoints() {
        liveJoints.clear();
        physics.getWorld().getJoints(jointBuffer);
        for (Joint j : jointBuffer) liveJoints.add(j);
        jointBuffer.clear();
    }

    private boolean alive(Joint joint) {
        return joint != null && liveJoints.contains(joint);
    }

    /**
     * Destroys a joint unless Box2D already did (with one of its bodies, perhaps since the last
     * tick): destroying it twice would free native memory twice.
     */
    private void destroyJoint(Joint joint) {
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

    private void fire(DockingEvent.Kind kind, Entity vessel, int port, Entity other, int otherPort, boolean permitted) {
        event.kind = kind;
        event.vessel = vessel;
        event.port = port;
        event.other = other;
        event.otherPort = otherPort;
        event.permitted = permitted;
        events.dispatch(event);
    }

    /** Drops a craft's latches when it leaves the engine or loses its ports. */
    private class VesselLifecycle implements EntityListener {
        @Override
        public void entityAdded(Entity entity) {
        }

        @Override
        public void entityRemoved(Entity entity) {
            for (int i = linkList.size - 1; i >= 0; i--) {
                Link link = linkList.get(i);
                if (link.a == entity || link.b == entity) dropLink(link, false);
            }
            DockingPortsComponent ports = portsMapper.get(entity);
            if (ports != null) for (DockingPort p : ports.ports) destroyHead(p);
        }
    }
}
