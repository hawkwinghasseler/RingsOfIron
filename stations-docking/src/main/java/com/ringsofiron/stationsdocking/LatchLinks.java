package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.joints.RopeJointDef;
import com.badlogic.gdx.physics.box2d.joints.WeldJointDef;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;

/**
 * The latched pairs of {@link DockingSystem}: their port records, seating them into line, the weld
 * that holds them and its strain, and letting go by undocking or tearing loose.
 */
final class LatchLinks {
    private static final float U = PhysicsManager.UNITS_PER_METER;
    private static final float M = PhysicsManager.METERS_PER_UNIT;
    /** Slack in the rope that keeps seating craft from colliding, beyond how far apart they start, meters. */
    private static final float SEAT_ROPE_SLACK = 1_000f;
    /** Seconds a latch waits for both craft to have bodies (after a load) before it is dropped. */
    static final float BODY_GRACE = 5f;

    private final DockingSystem sys;
    private final Vector2 v1 = new Vector2(), v2 = new Vector2(), v3 = new Vector2(), v4 = new Vector2();

    LatchLinks(DockingSystem sys) {
        this.sys = sys;
    }

    void latchRecords(Entity a, int portA, DockingPort pa, Entity b, int portB, DockingPort pb,
                              DockingPort.State state, boolean permitted) {
        pa.state = state;
        pb.state = state;
        pa.partnerUuid = sys.uuidOf(b);
        pa.partnerPort = portB;
        pb.partnerUuid = sys.uuidOf(a);
        pb.partnerPort = portA;
        pa.permitted = permitted;
        pb.permitted = permitted;
        pa.consented = agreed(a, pa, b);
        pb.consented = agreed(b, pb, a);
        pa.clearanceUntil = Double.NaN;
        pb.clearanceUntil = Double.NaN;
        pa.clamped = false;
        pb.clamped = false;
    }

    /** {@code own}'s side wanted this dock: its port armed by its crew or for the craft now docking, or it yields to any. */
    private boolean agreed(Entity own, DockingPort p, Entity other) {
        return p.armed && (p.armedFor == null || p.armedFor.equals(sys.uuidOf(other))) || sys.yields.test(own);
    }

    /** {@code own} has struck since latching: its port door opens to the craft it is latched to. */
    private void yieldDoor(LatchLink link, Entity own, DockingPort p) {
        p.consented = true;
        ShipGridComponent grid = sys.gridMapper.get(own);
        MountFootprint mount = grid == null || link.gridLinks.size == 0 ? null : DockLinks.mount(grid, p);
        if (mount != null) DockLinks.setDoors(grid, mount, true);
    }

    /** A saved latch: find its other half and hold it again. */
    void adopt(Entity e, int index, DockingPort p) {
        Entity other = p.partnerUuid == null ? null : sys.uuids.getEntity(p.partnerUuid);
        DockingPortsComponent ports = other == null ? null : sys.portsMapper.get(other);
        DockingPort q = ports == null ? null : ports.get(p.partnerPort);
        if (q == null || q.state != DockingPort.State.DOCKED || !sys.uuidOf(e).equals(q.partnerUuid) || q.partnerPort != index) {
            p.release();
            return;
        }
        LatchLink link = linkFor(e, index, p, other, p.partnerPort, q);
        // Latched from a save, or back from the rails: the doors stay shut until the weld holds and the
        // walkway is joined again, so the opening isn't left open to space in the frames before
        if (link.gridLinks.isEmpty()) DockLinks.unjoin(link, sys.gridMapper.get(link.a), sys.gridMapper.get(link.b), true);
    }

    LatchLink linkFor(Entity a, int portA, DockingPort pa, Entity b, int portB, DockingPort pb) {
        LatchLink link = sys.links.get(pa);
        if (link != null) return link;
        link = new LatchLink(a, portA, pa, b, portB, pb);
        sys.links.put(pa, link);
        sys.links.put(pb, link);
        sys.linkList.add(link);
        return link;
    }

    void updateLink(LatchLink link) {
        Body ba = sys.bodyOf(link.a), bb = sys.bodyOf(link.b);
        if (link.joint != null && !sys.alive(link.joint)) link.joint = null;
        if (ba == null || bb == null) {
            link.joint = null;
            if (sys.suspended) return;
            link.waiting += sys.dt;
            // A craft that had its weld and lost its body left the bubble: the latch goes with it.
            if (link.hadJoint || link.waiting > BODY_GRACE) dropLink(link, true);
            return;
        }
        link.waiting = 0f;
        if (!link.pa.consented && sys.yields.test(link.a)) yieldDoor(link, link.a, link.pa);
        if (!link.pb.consented && sys.yields.test(link.b)) yieldDoor(link, link.b, link.pb);
        DockingTuning t = link.pa.tuning();
        if (link.joint == null) {
            if (link.snap) {
                // Brought in docked: an active collar stays out as far as the hulls need to clear
                if (link.pa.lockedExtension == 0f && link.pb.lockedExtension == 0f) {
                    if (link.pa.active) link.pa.lockedExtension = CollarReach.lockExtension(ba, link.pa, bb, link.pb, sys.heads.tileSize(link.a));
                    else if (link.pb.active) link.pb.lockedExtension = CollarReach.lockExtension(bb, link.pb, ba, link.pa, sys.heads.tileSize(link.b));
                }
                snap(link, ba, bb);
            }
            link.snap = false;
            link.hadJoint = true;
            if (seated(link, ba, bb)) {
                weld(link, ba, bb);
            } else {
                // The seating itself is a spring pulling face onto face (pullIntoSeat); the joint
                // only keeps the two hulls from colliding meanwhile, its rope never drawn taut
                RopeJointDef seat = new RopeJointDef();
                seat.bodyA = ba;
                seat.bodyB = bb;
                seat.localAnchorA.setZero();
                seat.localAnchorB.setZero();
                seat.maxLength = ba.getPosition().dst(bb.getPosition()) + SEAT_ROPE_SLACK * U;
                seat.collideConnected = false;
                link.joint = sys.physics.getWorld().createJoint(seat);
                link.rigid = false;
                link.seatingTime = 0f;
            }
            return;
        }
        if (!link.rigid) {
            link.seatingTime += sys.dt;
            boolean seated = seated(link, ba, bb);
            if (!seated) pullIntoSeat(link, ba, bb, t);
            if (seated || link.seatingTime > DockingTuning.SEATING_TIME) {
                sys.destroyJoint(link.joint);
                // The last of it (or, out of time, what the seating could not close) is put
                // straight, so the two decks always line up as one
                seat(link, ba, bb);
                weld(link, ba, bb);
            }
            return;
        }
        float invDt = 1f / sys.dt;
        float force = link.joint.getReactionForce(invDt).len() * M;
        float torque = Math.abs(link.joint.getReactionTorque(invDt)) * M * M;
        float k = Math.min(1f, sys.dt / DockingTuning.STRAIN_FILTER);
        float hold = hold(link);
        float breakForce = t.breakForce * hold, breakTorque = t.breakTorque * hold;
        boolean shock = force > DockingTuning.SHOCK_LIMIT * breakForce || torque > DockingTuning.SHOCK_LIMIT * breakTorque;
        // One round's hit on a ship welded to a station is a one-step spike many times the limit;
        // it counts only up to the cap, so the latch rides out ordinary hits
        link.strainForce += (Math.min(force, DockingTuning.STRAIN_SPIKE_CAP * breakForce) - link.strainForce) * k;
        link.strainTorque += (Math.min(torque, DockingTuning.STRAIN_SPIKE_CAP * breakTorque) - link.strainTorque) * k;
        if ((shock || link.strainForce > breakForce || link.strainTorque > breakTorque) && !sys.towLocked.test(link.a, link.b)) {
            tear(link);
            return;
        }
        ShipGridComponent ga = sys.gridMapper.get(link.a), gb = sys.gridMapper.get(link.b);
        if (ga != null && gb != null) PortSeals.update(link, ga, gb, sys.crossing, sys.dt);
    }

    /**
     * The seating: a critically damped spring drawing the two port faces together and the craft
     * square, as hard as {@link DockingTuning#SEAT_FORCE} of the port's working force and torque
     * at most. It pushes at the middle of each craft's mass and turns them about it, so neither
     * part throws the other: Box2D's motor joint, which did this before, pulls on a body's origin, and a ship
     * whose origin is meters from its middle was swung round and into the other hull.
     */
    private void pullIntoSeat(LatchLink link, Body ba, Body bb, DockingTuning t) {
        boolean da = ba.getType() == BodyDef.BodyType.DynamicBody, db = bb.getType() == BodyDef.BodyType.DynamicBody;
        if (!da && !db) return;
        float ma = da ? PhysicsManager.getMass(ba) : 0f, mb = db ? PhysicsManager.getMass(bb) : 0f;
        float ia = da ? PhysicsManager.getInertia(ba) : 0f, ib = db ? PhysicsManager.getInertia(bb) : 0f;
        float mass = da && db ? ma * mb / (ma + mb) : Math.max(ma, mb);
        float inertia = da && db ? ia * ib / (ia + ib) : Math.max(ia, ib);
        float k = DockingTuning.SEAT_RATE * DockingTuning.SEAT_RATE, c = 2f * DockingTuning.SEAT_RATE;
        Vector2 fa = DockingSystem.worldPoint(ba, link.pa.faceX(), link.pa.faceY(), v1);
        Vector2 fb = DockingSystem.worldPoint(bb, link.pb.faceX(), link.pb.faceY(), v2);
        Vector2 va = DockingSystem.velocityAt(ba, fa, v3);
        Vector2 vb = DockingSystem.velocityAt(bb, fb, v4);
        float fx = mass * (k * (fb.x - fa.x) + c * (vb.x - va.x));
        float fy = mass * (k * (fb.y - fa.y) + c * (vb.y - va.y));
        float most = DockingTuning.SEAT_FORCE * t.workForce, len = (float) Math.sqrt(fx * fx + fy * fy);
        if (len > most) {
            fx *= most / len;
            fy *= most / len;
        }
        float angle = bb.getAngle() - ba.getAngle() - DockingSystem.matedAngle(link.pa, link.pb);
        angle = MathUtils.atan2(MathUtils.sin(angle), MathUtils.cos(angle));
        float torque = inertia * (k * angle + c * (bb.getAngularVelocity() - ba.getAngularVelocity()));
        torque = MathUtils.clamp(torque, -DockingTuning.SEAT_FORCE * t.workTorque, DockingTuning.SEAT_FORCE * t.workTorque);
        if (da) {
            PhysicsManager.applyForce(ba, fx, fy);
            PhysicsManager.applyTorque(ba, torque);
        }
        if (db) {
            PhysicsManager.applyForce(bb, -fx, -fy);
            PhysicsManager.applyTorque(bb, -torque);
        }
    }

    /**
     * Share of its break limits a latch still bears. A collar part torn holds that much less of its
     * load. A latch made in place (a tug's or a yard's berth, a boarding latch) is held by its clamps
     * as a whole collar would be: scaled to nothing a torn-off collar tore every step, and the ship
     * could never stay at the yard that mends it; worn to a tenth by hits, a shot-up prize tore loose
     * as soon as it was boarded.
     */
    static float hold(LatchLink link) {
        float hold = Math.min(link.pa.collarIntegrity, link.pb.collarIntegrity);
        return hold <= 0f || link.pa.clamped || link.pb.clamped ? 1f : hold;
    }

    private void weld(LatchLink link, Body ba, Body bb) {
        WeldJointDef weld = new WeldJointDef();
        weld.bodyA = ba;
        weld.bodyB = bb;
        weld.localAnchorA.set(link.pa.faceX() * U, link.pa.faceY() * U);
        weld.localAnchorB.set(link.pb.faceX() * U, link.pb.faceY() * U);
        weld.referenceAngle = matedReference(link, ba, bb);
        makeWeld(link, weld);
    }

    /** Puts whichever of the pair moves freely exactly at the latched pose against the other. */
    private void seat(LatchLink link, Body ba, Body bb) {
        if (ba.getType() == BodyDef.BodyType.DynamicBody) snap(link, ba, bb);
        else if (bb.getType() == BodyDef.BodyType.DynamicBody) snapOnto(bb, link.pb, ba, link.pa);
    }

    private void makeWeld(LatchLink link, WeldJointDef weld) {
        weld.collideConnected = false;
        link.joint = sys.physics.getWorld().createJoint(weld);
        link.rigid = true;
        link.strainForce = 0f;
        link.strainTorque = 0f;
        ShipGridComponent ga = sys.gridMapper.get(link.a), gb = sys.gridMapper.get(link.b);
        if (ga != null && gb != null) DockLinks.join(link, link.a, weld.bodyA, ga, link.b, weld.bodyB, gb);
    }


    /**
     * The latched angle of b less a's, as the turn nearest to where the bodies are now: Box2D does
     * not wrap angles, so a joint told "0" between bodies at 0 and 2 pi would spin one round.
     */
    private static float matedReference(LatchLink link, Body ba, Body bb) {
        return matedReference(link.pa, ba, link.pb, bb);
    }

    /** As {@link #matedReference(LatchLink, Body, Body)}, for port {@code pa} on {@code ba} latched to {@code pb} on {@code bb}. */
    private static float matedReference(DockingPort pa, Body ba, DockingPort pb, Body bb) {
        float now = bb.getAngle() - ba.getAngle();
        float off = DockingSystem.matedAngle(pa, pb) - now;
        return now + MathUtils.atan2(MathUtils.sin(off), MathUtils.cos(off));
    }

    private boolean seated(LatchLink link, Body ba, Body bb) {
        Vector2 fa = DockingSystem.worldPoint(ba, link.pa.faceX(), link.pa.faceY(), v1);
        Vector2 fb = DockingSystem.worldPoint(bb, link.pb.faceX(), link.pb.faceY(), v2);
        float angle = bb.getAngle() - ba.getAngle() - DockingSystem.matedAngle(link.pa, link.pb);
        angle = MathUtils.atan2(MathUtils.sin(angle), MathUtils.cos(angle));
        return fa.dst(fb) < DockingTuning.SEATED_DISTANCE && Math.abs(angle) < DockingTuning.SEATED_ANGLE;
    }

    /** Puts a onto b at the latched pose, moving with b. */
    private void snap(LatchLink link, Body ba, Body bb) {
        snapOnto(ba, link.pa, bb, link.pb);
    }

    /** Puts {@code ba} with port {@code pa} face to face on {@code bb}'s port {@code pb}, moving with bb. */
    private void snapOnto(Body ba, DockingPort pa, Body bb, DockingPort pb) {
        float angleA = bb.getAngle() - DockingSystem.matedAngle(pa, pb);
        Vector2 fb = DockingSystem.worldPoint(bb, pb.faceX(), pb.faceY(), v1);
        float c = MathUtils.cos(angleA), s = MathUtils.sin(angleA);
        float fx = pa.faceX(), fy = pa.faceY();
        float ox = fb.x - (c * fx - s * fy), oy = fb.y - (s * fx + c * fy);
        Vector2 vel = DockingSystem.velocityAt(bb, v2.set(ox, oy), v3);
        PhysicsManager.setPosition(ba, ox, oy, angleA);
        PhysicsManager.setVelocity(ba, vel.x, vel.y);
        ba.setAngularVelocity(bb.getAngularVelocity());
    }

    /** Undocking: the weld lets go and the collar springs push the craft apart. */
    void separate(LatchLink link, Entity by) {
        Body ba = sys.bodyOf(link.a), bb = sys.bodyOf(link.b);
        sys.destroyJoint(link.joint);
        link.joint = null;
        if (ba != null && bb != null) {
            float ma = ba.getType() == BodyDef.BodyType.DynamicBody ? PhysicsManager.getMass(ba) : 0f;
            float mb = bb.getType() == BodyDef.BodyType.DynamicBody ? PhysicsManager.getMass(bb) : 0f;
            float mu = ma > 0f && mb > 0f ? ma * mb / (ma + mb) : Math.max(ma, mb);
            float j = mu * DockingTuning.SEPARATION_SPEED;
            Vector2 n = DockingSystem.worldVector(ba, link.pa.normalX, link.pa.normalY, v1);
            Vector2 ca = v2.set(ba.getWorldCenter()).scl(M);
            Vector2 cb = v3.set(bb.getWorldCenter()).scl(M);
            if (ma > 0f) PhysicsManager.applyImpulse(ba, -n.x * j, -n.y * j, ca.x, ca.y);
            if (mb > 0f) PhysicsManager.applyImpulse(bb, n.x * j, n.y * j, cb.x, cb.y);
        }
        DockingSystem.disarmPort(link.pa);
        DockingSystem.disarmPort(link.pb);
        boolean permitted = link.pa.permitted;
        dropLink(link, true);
        Entity other = by == link.a ? link.b : link.a;
        sys.fire(DockingEvent.Kind.UNDOCKED, by, by == link.a ? link.portA : link.portB, other,
                by == link.a ? link.portB : link.portA, permitted);
    }

    /** Past the break limit: the weld goes, both collars tear off and the hull around each port is damaged. */
    void tear(LatchLink link) {
        sys.destroyJoint(link.joint);
        link.joint = null;
        boolean permitted = link.pa.permitted;
        for (int side = 0; side < 2; side++) {
            Entity e = side == 0 ? link.a : link.b;
            DockingPort p = side == 0 ? link.pa : link.pb;
            p.collarIntegrity = 0f;
            DockingSystem.disarmPort(p);
            Body body = sys.bodyOf(e);
            if (body != null) sys.hullCollars.removeCollar(body, p);
            ShipGridComponent grid = sys.gridMapper.get(e);
            if (grid != null) PortDamage.tear(grid, p);
        }
        dropLink(link, true);
        sys.fire(DockingEvent.Kind.TORN, link.a, link.portA, link.b, link.portB, permitted);
    }

    void dropLink(LatchLink link, boolean releasePorts) {
        sys.destroyJoint(link.joint);
        link.joint = null;
        DockLinks.unjoin(link, sys.gridMapper.get(link.a), sys.gridMapper.get(link.b), true);
        sys.links.remove(link.pa);
        sys.links.remove(link.pb);
        sys.linkList.removeValue(link, true);
        if (releasePorts) {
            link.pa.release();
            link.pb.release();
        }
    }
}
