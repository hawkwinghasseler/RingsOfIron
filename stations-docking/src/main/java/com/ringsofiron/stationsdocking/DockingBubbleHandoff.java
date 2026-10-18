package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.orbital.warp.BubbleHandoff;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;

/**
 * Wraps the bubble handoff so docked craft go through rails warp as one body.
 *
 * <p>Before the collapse it notes, for each docked pair, where the follower sits in the leader's
 * frame (a station always leads; of two ships, the heavier), then lets the wrapped handoff put
 * every body on rails, and gives each follower its leader's orbit, so the pair stays together.
 * Before the rebuild it moves each follower's orbit back to its docked spot beside the leader as
 * the leader is now, so both come back into Box2D latched, and the {@link DockingSystem} welds them
 * again on the next tick. Collars still moving are drawn in; a capture under way is dropped.
 */
public class DockingBubbleHandoff implements BubbleHandoff {
    private static final Family RIDERS = Family.all(DockedRideComponent.class).get();

    private final ComponentMapper<DockedRideComponent> rideMapper = ComponentMapper.getFor(DockedRideComponent.class);
    private final ComponentMapper<OrbitComponent> orbitMapper = ComponentMapper.getFor(OrbitComponent.class);
    private final ComponentMapper<KinematicTransientComponent> transientMapper =
            ComponentMapper.getFor(KinematicTransientComponent.class);
    private final ComponentMapper<UuidComponent> uuidMapper = ComponentMapper.getFor(UuidComponent.class);

    private final BubbleHandoff delegate;
    private final DockingSystem docking;
    private final PhysicsManager physics;
    private final Engine engine;
    private final UuidRegistry uuids;
    private final Array<Entity> buffer = new Array<>();
    private final StateVector state = new StateVector();
    private final double[] trajectory = new double[4];
    private final Vector2 tmp = new Vector2();

    public DockingBubbleHandoff(BubbleHandoff delegate, DockingSystem docking, PhysicsManager physics,
                                Engine engine, UuidRegistry uuids) {
        this.delegate = delegate;
        this.docking = docking;
        this.physics = physics;
        this.engine = engine;
        this.uuids = uuids;
    }

    @Override
    public void collapseToRails() {
        docking.setSuspended(true);
        for (DockingSystem.Link link : docking.links()) noteRide(link);
        delegate.collapseToRails();
        double t = physics.getSimSeconds();
        for (Entity e : engine.getEntitiesFor(RIDERS)) buffer.add(e);
        for (Entity follower : buffer) {
            DockedRideComponent ride = rideMapper.get(follower);
            OrbitComponent orbit = orbitMapper.get(follower);
            Entity leader = uuids.getEntity(ride.leaderUuid);
            if (orbit == null || leader == null || !leaderState(leader, orbit.mu, t)) continue;
            orbit.setOrbit(KeplerOrbit.fromState(state.x, state.y, state.vx, state.vy, orbit.mu, t));
            orbit.attitude = leaderAttitude(leader, t) + ride.angleOffset;
            orbit.angularVelocity = leaderSpin(leader);
        }
        buffer.clear();
    }

    @Override
    public void rebuildFromRails() {
        double t = physics.getSimSeconds();
        for (Entity e : engine.getEntitiesFor(RIDERS)) buffer.add(e);
        for (Entity follower : buffer) {
            DockedRideComponent ride = rideMapper.get(follower);
            OrbitComponent orbit = orbitMapper.get(follower);
            Entity leader = uuids.getEntity(ride.leaderUuid);
            if (orbit == null || leader == null || !leaderState(leader, orbit.mu, t)) continue;
            double angle = leaderAttitude(leader, t);
            double spin = leaderSpin(leader);
            double c = Math.cos(angle), s = Math.sin(angle);
            double rx = c * ride.offsetX - s * ride.offsetY, ry = s * ride.offsetX + c * ride.offsetY;
            orbit.setOrbit(KeplerOrbit.fromState(state.x + rx, state.y + ry,
                    state.vx - spin * ry, state.vy + spin * rx, orbit.mu, t));
            orbit.attitude = angle + ride.angleOffset;
            orbit.angularVelocity = spin;
        }
        delegate.rebuildFromRails();
        for (Entity follower : buffer) follower.remove(DockedRideComponent.class);
        buffer.clear();
        docking.setSuspended(false);
    }

    /** Puts a ride on the follower of a docked pair that both have bodies. */
    private void noteRide(DockingSystem.Link link) {
        Body ba = docking.bodyOf(link.a), bb = docking.bodyOf(link.b);
        if (ba == null || bb == null) return;
        boolean aDynamic = ba.getType() == BodyDef.BodyType.DynamicBody;
        boolean bDynamic = bb.getType() == BodyDef.BodyType.DynamicBody;
        if (!aDynamic && !bDynamic) return;
        boolean aLeads = !aDynamic || (bDynamic && PhysicsManager.getMass(ba) >= PhysicsManager.getMass(bb));
        Entity leader = aLeads ? link.a : link.b, follower = aLeads ? link.b : link.a;
        Body lb = aLeads ? ba : bb, fb = aLeads ? bb : ba;
        if (rideMapper.has(follower) || rideMapper.has(leader)) return;
        UuidComponent leaderId = uuidMapper.get(leader);
        if (leaderId == null) return;
        Vector2 local = lb.getLocalPoint(tmp.set(fb.getPosition()));
        float angle = fb.getAngle() - lb.getAngle();
        follower.add(new DockedRideComponent(leaderId.id, local.x * PhysicsManager.METERS_PER_UNIT,
                local.y * PhysicsManager.METERS_PER_UNIT, MathUtils.atan2(MathUtils.sin(angle), MathUtils.cos(angle))));
    }

    /** The leader's true state at {@code t} into {@link #state}: a station's from its trajectory, a ship's from its orbit. */
    private boolean leaderState(Entity leader, double mu, double t) {
        KinematicTransientComponent kt = transientMapper.get(leader);
        if (kt != null && kt.trajectory != null) {
            kt.trajectory.stateAt(t, trajectory);
            state.set(trajectory[0], trajectory[1], trajectory[2], trajectory[3]);
            return true;
        }
        OrbitComponent orbit = orbitMapper.get(leader);
        if (orbit == null) return false;
        orbit.stateAt(t, state);
        return true;
    }

    private double leaderAttitude(Entity leader, double t) {
        KinematicTransientComponent kt = transientMapper.get(leader);
        if (kt != null && kt.trajectory != null) return kt.angle;
        OrbitComponent orbit = orbitMapper.get(leader);
        return orbit == null ? 0 : orbit.attitudeAt(t);
    }

    private double leaderSpin(Entity leader) {
        KinematicTransientComponent kt = transientMapper.get(leader);
        if (kt != null && kt.trajectory != null) return 0;
        OrbitComponent orbit = orbitMapper.get(leader);
        return orbit == null ? 0 : orbit.angularVelocity;
    }
}
