package com.ringsofiron.macrosim.bubble;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.Trajectory;
import com.ringsofiron.macrosim.rails.stub.StubKeplerOrbit;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.anchor.AnchorModel;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

/**
 * {@link BubbleGateway} over physics-exterior's {@link PhysicsManager}: true-state handoffs with
 * {@code setTrueState}/{@code getTrueState}, fast passes as {@link KinematicTransientComponent}.
 *
 * <p>Body destruction goes through {@code PhysicsUpdateSystem}, which destroys an entity's body when
 * its {@link PhysicsBodyComponent} is removed, so that system must be in the engine.
 * True states are read at the physics manager's current tick; the {@code t} arguments are the same
 * sim time on the main thread.
 */
public class PhysicsBubbleGateway implements BubbleGateway {

    /** Builds an entity's Box2D body (fixtures, mass) at the bubble origin; the gateway then places it. */
    public interface BodyBuilder {
        Body build(Entity entity);
    }

    /** Sweep half-width for fast passes when nothing better is known, in meters. */
    public static final float DEFAULT_TRANSIENT_RADIUS = 50f;

    private final PhysicsManager physics;
    private final UuidRegistry uuids;
    private final String parentUuid;
    private final BodyBuilder bodies;
    private final double[] buf = new double[4];

    public PhysicsBubbleGateway(PhysicsManager physics, UuidRegistry uuids, String parentUuid, BodyBuilder bodies) {
        this.physics = physics;
        this.uuids = uuids;
        this.parentUuid = parentUuid;
        this.bodies = bodies;
    }

    @Override
    public String anchorParentUuid() {
        return parentUuid;
    }

    @Override
    public StateVector anchorStateAt(double t, StateVector out) {
        physics.getAnchorModel().stateAt(t, buf);
        return out.set(buf[0], buf[1], buf[2], buf[3]);
    }

    /**
     * The anchor's free fall from its current state, as an immutable conic. Exact for a free-falling
     * anchor until the next floating-origin rebase; predictions are refreshed far more often.
     */
    @Override
    public Trajectory anchorTrajectory() {
        AnchorModel anchor = physics.getAnchorModel();
        double now = physics.getSimSeconds();
        StateVector s = anchorStateAt(now, new StateVector());
        if (anchor.getMu() <= 0) return new Coasting(s, now);
        return StubKeplerOrbit.fromState(anchor.getMu(), s, now);
    }

    @Override
    public void spawnFromTrueState(String uuid, StateVector s, double t) {
        Entity entity = require(uuid);
        Body body = bodies.build(entity);
        physics.setTrueState(body, s.x, s.y, s.vx, s.vy);
        entity.add(new PhysicsBodyComponent(body));
    }

    @Override
    public StateVector trueStateOf(String uuid, double t, StateVector out) {
        PhysicsBodyComponent pb = require(uuid).getComponent(PhysicsBodyComponent.class);
        physics.getTrueState(pb.body, buf);
        return out.set(buf[0], buf[1], buf[2], buf[3]);
    }

    @Override
    public void destroyBody(String uuid) {
        require(uuid).remove(PhysicsBodyComponent.class);
    }

    @Override
    public void beginKinematicTransient(String uuid, Trajectory path) {
        StateVector scratch = new StateVector();
        require(uuid).add(new KinematicTransientComponent((time, out) -> {
            path.stateAt(time, scratch);
            out[0] = scratch.x;
            out[1] = scratch.y;
            out[2] = scratch.vx;
            out[3] = scratch.vy;
        }, DEFAULT_TRANSIENT_RADIUS));
    }

    @Override
    public void endKinematicTransient(String uuid) {
        require(uuid).remove(KinematicTransientComponent.class);
    }

    private Entity require(String uuid) {
        Entity e = uuids.getEntity(uuid);
        if (e == null) throw new IllegalArgumentException("No entity with UUID " + uuid);
        return e;
    }

    /** Straight-line anchor motion when the bubble feels no gravity. */
    private static final class Coasting implements Trajectory {
        private final double x, y, vx, vy, t0;

        Coasting(StateVector s, double t0) {
            this.x = s.x;
            this.y = s.y;
            this.vx = s.vx;
            this.vy = s.vy;
            this.t0 = t0;
        }

        @Override
        public StateVector stateAt(double t, StateVector out) {
            double dt = t - t0;
            return out.set(x + vx * dt, y + vy * dt, vx, vy);
        }

        @Override
        public double minRadius() {
            return 0;
        }

        @Override
        public double maxRadius() {
            return Double.POSITIVE_INFINITY;
        }

        @Override
        public double maxSpeed() {
            return Math.hypot(vx, vy);
        }
    }
}
