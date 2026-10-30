package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.MassData;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.joints.DistanceJoint;
import com.badlogic.gdx.physics.box2d.joints.DistanceJointDef;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJoint;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJointDef;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJoint;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef;
import com.badlogic.gdx.physics.box2d.joints.RopeJointDef;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.PolygonSkin;

/**
 * An active port's collar head for {@link DockingSystem}: put out on its slider and spring while
 * armed, reaching with its magnet, and once it has captured the other port, drawing the craft
 * together until the collar is home and latches.
 */
final class CollarHeads {
    private static final float U = PhysicsManager.UNITS_PER_METER;
    private static final float M = PhysicsManager.METERS_PER_UNIT;
    /** Where an active collar's spring is anchored, this far inside the hull behind the port, meters. */
    private static final float SPRING_BASE = 1f;

    private final DockingSystem sys;
    private final Vector2 v0 = new Vector2(), v1 = new Vector2(), v6 = new Vector2();

    CollarHeads(DockingSystem sys) {
        this.sys = sys;
    }

    /** Puts an active port's collar head out while armed, draws it in when not, contracts it once it has hold. */
    void updateCollar(Entity e, int index, DockingPort p, Body body) {
        // Drawn in for another craft's collar that has since stopped reaching: out again
        if (p.yieldedTo != null && (!p.yieldedTo.armed || !p.isFree())) {
            if (p.isFree()) p.holdCollar = false;
            p.yieldedTo = null;
        }
        boolean wantOut = p.active && p.armed && p.usable() && !p.holdCollar && p.isFree();
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
        float step = DockingTuning.EXTEND_SPEED * sys.dt;
        p.springTarget = Math.abs(goal - p.springTarget) <= step ? goal : p.springTarget + Math.signum(goal - p.springTarget) * step;
        p.spring.setLength(p.springTarget * U);
        if (!wantOut && p.springTarget <= rest + 1e-4f && p.extension < 0.02f) destroyHead(p);
    }

    /**
     * The tilt spring: turns the head back square on its carriage. Free, it is weak enough for
     * the head's magnet to lay it flat on the other port; captured, it is what turns the craft
     * into line while the collar draws in.
     */
    static void steerTilt(DockingPort p, boolean captured) {
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

    void createHead(Entity owner, DockingPort p, Body body) {
        DockingTuning t = p.tuning();
        float d = p.collarDepth;
        float cx = p.edgeX + p.normalX * d * 0.5f, cy = p.edgeY + p.normalY * d * 0.5f;
        Vector2 at = DockingSystem.worldPoint(body, cx, cy, v0);
        Vector2 vel = DockingSystem.velocityAt(body, at, v1);

        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.DynamicBody;
        def.position.set(at.x * U, at.y * U);
        def.angle = body.getAngle();
        def.allowSleep = false;
        // The carriage runs on the guide and carries the head's pivot; it has no shape of its own.
        Body carriage = sys.physics.getWorld().createBody(def);
        MassData mass = new MassData();
        mass.mass = t.headMass * 0.25f;
        mass.I = mass.mass * (p.width * U) * (p.width * U) / 12f;
        carriage.setMassData(mass);
        carriage.setUserData(owner);
        PhysicsManager.setVelocity(carriage, vel.x, vel.y);
        carriage.setAngularVelocity(body.getAngularVelocity());
        Body head = sys.physics.getWorld().createBody(def);
        PolygonShape shape = new PolygonShape();
        float area = PolygonSkin.insetBox(shape, p.width * 0.5f * U, d * 0.5f * U, new Vector2(),
                MathUtils.atan2(p.normalX, -p.normalY));
        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        fd.density = t.headMass / area;
        fd.friction = DockingTuning.COLLAR_FRICTION;
        fd.restitution = 0f;
        fd.filter.categoryBits = (short) (HullCollars.ownerCategory(body) | CollisionCategories.CATEGORY_COLLAR);
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
        p.guide = (PrismaticJoint) sys.physics.getWorld().createJoint(guide);

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
        p.tilt = (RevoluteJoint) sys.physics.getWorld().createJoint(tilt);

        // Slack all the way out: it is only there so the head never collides with its own hull
        RopeJointDef tether = new RopeJointDef();
        tether.bodyA = body;
        tether.bodyB = head;
        tether.localAnchorA.set(cx * U, cy * U);
        tether.localAnchorB.set(0f, 0f);
        tether.maxLength = (t.maxExtension + p.width) * U;
        tether.collideConnected = false;
        p.tether = sys.physics.getWorld().createJoint(tether);

        DistanceJointDef spring = new DistanceJointDef();
        spring.bodyA = body;
        spring.bodyB = carriage;
        spring.localAnchorA.set((p.edgeX - p.normalX * SPRING_BASE) * U, (p.edgeY - p.normalY * SPRING_BASE) * U);
        spring.localAnchorB.set(0f, 0f);
        spring.length = rest * U;
        spring.frequencyHz = DockingTuning.SPRING_HZ;
        spring.dampingRatio = DockingTuning.SPRING_DAMPING;
        spring.collideConnected = false;
        p.spring = (DistanceJoint) sys.physics.getWorld().createJoint(spring);
        p.springTarget = rest;
        p.head = head;
        p.carriage = carriage;
        p.extension = 0f;
        steerTilt(p, false);
    }

    /**
     * The captured collar's slider draws the craft together while its tilt spring turns them into
     * line. Home and square means latched; home but still turned waits for the spring. Home is
     * {@link DockingPort#lockAt} out: all the way in, or a whole number of tiles out where drawing
     * in further would put the hulls into each other ({@link CollarReach}). A collar held off
     * short of home by something that was not foreseen stops and locks at the next whole tile out;
     * one held turned at home goes a tile further out.
     */
    void contract(Entity e, int index, DockingPort p) {
        boolean square = Math.abs(tiltOf(p)) < DockingTuning.LATCH_ANGLE;
        float home = p.lockAt + DockingTuning.LOCK_EXTENSION;
        boolean short_ = p.lockAt > 0f && p.extension < p.lockAt - DockingTuning.LOCK_TOLERANCE;
        if (p.extension < home && !short_) {
            if (square) {
                lock(e, index, p);
                return;
            }
            drive(e, p, 0f);
            // Home but held turned (the hulls touching, by something not foreseen): out a tile more
            float tile = tileSize(e);
            if ((p.stalled += sys.dt) > DockingTuning.STALL_TIME && p.lockAt + tile <= CollarReach.maxTiles(p, tile) * tile + 1e-4f) {
                p.lockAt += tile;
                p.stalled = 0f;
                p.stalledAt = p.extension;
            }
            return;
        }
        if (short_) {
            // Caught nearer than it locks: out again to a whole tile
            drive(e, p, Math.min(DockingTuning.CONTRACT_SPEED, 2f * (p.lockAt - p.extension) + 0.02f));
            return;
        }
        if (p.extension < p.stalledAt - DockingTuning.STALL_PROGRESS) {
            p.stalledAt = p.extension;
            p.stalled = 0f;
        } else if ((p.stalled += sys.dt) > DockingTuning.STALL_TIME) {
            // Held off by the hulls: locks at the next whole tile out (as far as the collar reaches)
            float tile = tileSize(e);
            int tiles = (int) Math.ceil((p.extension - DockingTuning.LOCK_EXTENSION) / tile - 1e-3f);
            p.lockAt = Math.min(CollarReach.maxTiles(p, tile), Math.max(tiles, 0)) * tile;
            p.stalled = 0f;
            p.stalledAt = p.extension;
        }
        float speed = Math.min(DockingTuning.CONTRACT_SPEED, 2f * (p.extension - p.lockAt) + 0.02f);
        drive(e, p, -speed);
    }

    /**
     * Brings the captured collar's extension to change at {@code rate} m/s (negative drawing in):
     * a push along the port's axis at the middle of each craft's mass, easing in over
     * {@link DockingTuning#DRIVE_TIME} and no harder than {@link DockingTuning#DRIVE_FORCE} of the
     * break force. The slider only carries the head; were it the motor, its pull would come at the
     * port, and on a ship whose port is meters from its middle it turned the ship harder than the
     * tilt spring could hold it square (a ship latched right after undocking swung in a few degrees).
     */
    void drive(Entity e, DockingPort p, float rate) {
        Body own = sys.bodyOf(e);
        Entity other = p.partnerUuid == null ? null : sys.uuids.getEntity(p.partnerUuid);
        Body far = other == null ? null : sys.bodyOf(other);
        if (own == null || far == null) return;
        boolean dOwn = own.getType() == BodyDef.BodyType.DynamicBody, dFar = far.getType() == BodyDef.BodyType.DynamicBody;
        if (!dOwn && !dFar) return;
        float mOwn = dOwn ? PhysicsManager.getMass(own) : 0f, mFar = dFar ? PhysicsManager.getMass(far) : 0f;
        float mass = dOwn && dFar ? mOwn * mFar / (mOwn + mFar) : Math.max(mOwn, mFar);
        // The extension grows as the craft draw apart along the port's axis
        float now = p.guide.getJointSpeed() * M;
        float push = MathUtils.clamp(mass * (rate - now) / DockingTuning.DRIVE_TIME,
                -DockingTuning.DRIVE_FORCE * p.tuning().breakForce, DockingTuning.DRIVE_FORCE * p.tuning().breakForce);
        Vector2 n = DockingSystem.worldVector(own, p.normalX, p.normalY, v6);
        // Pushing the craft apart along n lengthens the collar
        if (dOwn) PhysicsManager.applyForce(own, -n.x * push, -n.y * push);
        if (dFar) PhysicsManager.applyForce(far, n.x * push, n.y * push);
    }

    /** A craft's tile size, meters: what its collar locks out in whole numbers of. 1 for a craft without a grid. */
    float tileSize(Entity e) {
        ShipGridComponent grid = sys.gridMapper.get(e);
        return grid == null ? 1f : grid.tileSize;
    }

    void lock(Entity e, int index, DockingPort p) {
        Entity other = sys.uuids.getEntity(p.partnerUuid);
        DockingPortsComponent otherPorts = other == null ? null : sys.portsMapper.get(other);
        DockingPort q = otherPorts == null ? null : otherPorts.get(p.partnerPort);
        destroyHead(p);
        if (q == null) {
            p.release();
            return;
        }
        p.state = DockingPort.State.DOCKED;
        q.state = DockingPort.State.DOCKED;
        p.lockedExtension = p.lockAt;
        q.lockedExtension = 0f;
        sys.latches.linkFor(e, index, p, other, p.partnerPort, q);
        sys.fire(DockingEvent.Kind.DOCKED, e, index, other, p.partnerPort, p.permitted);
    }

    /** A capture is real while the capturing head's weld to the other craft stands. */
    boolean captureHeld(DockingPort p) {
        if (p.capture != null && sys.alive(p.capture)) return true;
        Entity other = p.partnerUuid == null ? null : sys.uuids.getEntity(p.partnerUuid);
        DockingPortsComponent ports = other == null ? null : sys.portsMapper.get(other);
        DockingPort q = ports == null ? null : ports.get(p.partnerPort);
        return q != null && q.capture != null && sys.alive(q.capture);
    }

    /** Lets go of a capture and its other half: both ports free again. */
    void releasePair(DockingPort p) {
        Entity other = p.partnerUuid == null ? null : sys.uuids.getEntity(p.partnerUuid);
        DockingPortsComponent ports = other == null ? null : sys.portsMapper.get(other);
        DockingPort q = ports == null ? null : ports.get(p.partnerPort);
        if (q != null && q.state == DockingPort.State.CAPTURED) {
            destroyHead(q);
            q.release();
        }
        destroyHead(p);
        p.release();
    }

    void destroyHead(DockingPort p) {
        if (p.head != null) sys.physics.destroyBody(p.head);
        if (p.carriage != null) sys.physics.destroyBody(p.carriage);
        p.head = null;
        p.carriage = null;
        p.guide = null;
        p.spring = null;
        p.tilt = null;
        p.tether = null;
        p.capture = null;
        p.extension = 0f;
    }
}
