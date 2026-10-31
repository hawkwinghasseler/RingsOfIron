package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.joints.WeldJointDef;
import com.ringsofiron.physicsexterior.PhysicsManager;

/**
 * Magnetic capture for {@link DockingSystem}: an armed free port's magnets pull on matching free
 * ports of other craft, and a face that comes to rest on the other port latches, or a collar head
 * that does captures it.
 */
final class MagneticCapture {
    private static final float U = PhysicsManager.UNITS_PER_METER;

    private final DockingSystem sys;
    private final Vector2 v0 = new Vector2(), v1 = new Vector2(), v2 = new Vector2(), v3 = new Vector2();
    private final Vector2 v4 = new Vector2(), v5 = new Vector2(), v6 = new Vector2(), v7 = new Vector2();

    MagneticCapture(DockingSystem sys) {
        this.sys = sys;
    }

    /** Magnets of one armed free port pull on matching free ports of other craft; contact latches or captures. */
    void attract(Entity e, int index, DockingPort p, Body body) {
        if (!p.armed || !p.isFree() || !p.usable()) return;
        if (p.active && !p.holdCollar && p.head == null) return; // collar still coming out
        DockingTuning t = p.tuning();
        String ownUuid = sys.uuidOf(e);
        for (int i = 0; i < sys.vessels.size(); i++) {
            Entity o = sys.vessels.get(i);
            if (o == e) continue;
            Body ob = sys.bodyOf(o);
            if (ob == null) continue;
            if (p.armedFor != null && !p.armedFor.equals(sys.uuidOf(o))) continue;
            DockingPortsComponent oPorts = sys.portsMapper.get(o);
            for (int k = 0; k < oPorts.ports.length; k++) {
                DockingPort q = oPorts.ports[k];
                if (q.size != p.size || !q.isFree() || !q.usable()) continue;
                // An extended collar does the reaching; this port waits for it. Two collars
                // reaching for each other would only push each other off: the one armed first
                // reaches, and the other draws in and waits for it as a passive port would.
                if (q.head != null) {
                    if (p.head != null && q.yieldedTo == null && reachesFirst(e, p, o, q)
                            && facing(p.head, p.normalX * p.collarDepth * 0.5f, p.normalY * p.collarDepth * 0.5f, p, ob, q,
                            t.headRange + q.tuning().maxExtension)) {
                        q.holdCollar = true;
                        q.yieldedTo = p;
                    }
                    continue;
                }
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

    /** Of two armed active ports reaching for each other, true if {@code p} (of {@code a}) keeps its collar out. */
    private boolean reachesFirst(Entity a, DockingPort p, Entity b, DockingPort q) {
        boolean pt = !Double.isNaN(p.armedAt), qt = !Double.isNaN(q.armedAt);
        if (pt != qt) return pt;
        if (pt && p.armedAt != q.armedAt) return p.armedAt < q.armedAt;
        // Armed together, or both before a load: the same craft wins every time
        return sys.uuidOf(a).compareTo(sys.uuidOf(b)) < 0;
    }

    /** True if the port faces at {@code a}'s point and {@code b}'s port {@code q} face each other within {@code range} meters. */
    private boolean facing(Body a, float ax, float ay, DockingPort p, Body b, DockingPort q, float range) {
        Vector2 fa = DockingSystem.worldPoint(a, ax, ay, v0);
        Vector2 na = DockingSystem.worldVector(a, p.normalX, p.normalY, v1);
        Vector2 fb = DockingSystem.worldPoint(b, q.faceX(), q.faceY(), v2);
        Vector2 nb = DockingSystem.worldVector(b, q.normalX, q.normalY, v3);
        if (na.dot(nb) > -MathUtils.cos(DockingTuning.MAGNET_CONE)) return false;
        float rx = fb.x - fa.x, ry = fb.y - fa.y;
        if (rx * rx + ry * ry > range * range) return false;
        return rx * na.x + ry * na.y >= -0.5f && -(rx * nb.x + ry * nb.y) >= -0.5f;
    }

    /**
     * Pulls the face at local {@code (ax, ay)} of {@code a} (a craft, or a collar head) towards
     * port {@code q}'s face on {@code b}: two magnets
     * across the face, each pulling its point towards the mirrored point on the other face, so the
     * pull also turns the ports square. Returns true if in range and facing.
     */
    private boolean magnet(Body a, float ax, float ay, DockingPort p, Body b, DockingPort q, float range, float force) {
        Vector2 fa = DockingSystem.worldPoint(a, ax, ay, v0);
        Vector2 na = DockingSystem.worldVector(a, p.normalX, p.normalY, v1);
        Vector2 fb = DockingSystem.worldPoint(b, q.faceX(), q.faceY(), v2);
        Vector2 nb = DockingSystem.worldVector(b, q.normalX, q.normalY, v3);
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
            Vector2 va = DockingSystem.velocityAt(a, v4.set(pax, pay), v5);
            Vector2 vb = DockingSystem.velocityAt(b, v6.set(pbx, pby), v7);
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
        Vector2 fa = DockingSystem.worldPoint(a, ax, ay, v0);
        Vector2 na = DockingSystem.worldVector(a, p.normalX, p.normalY, v1);
        Vector2 fb = DockingSystem.worldPoint(b, q.faceX(), q.faceY(), v2);
        Vector2 nb = DockingSystem.worldVector(b, q.normalX, q.normalY, v3);
        if (-na.dot(nb) < MathUtils.cos(DockingTuning.LATCH_ANGLE)) return false;
        float rx = fb.x - fa.x, ry = fb.y - fa.y;
        float gap = rx * na.x + ry * na.y;
        float side = Math.abs(-rx * na.y + ry * na.x);
        if (gap < -0.1f || gap > DockingTuning.LATCH_GAP || side > lateral) return false;
        Vector2 va = DockingSystem.velocityAt(a, fa, v4);
        Vector2 vb = DockingSystem.velocityAt(b, fb, v5);
        return Vector2.len(va.x - vb.x, va.y - vb.y) <= DockingTuning.LATCH_SPEED;
    }

    void latch(Entity a, int portA, DockingPort pa, Entity b, int portB, DockingPort pb) {
        sys.latches.latchRecords(a, portA, pa, b, portB, pb, DockingPort.State.DOCKED, permittedBetween(pa, pb, a, b));
        sys.latches.linkFor(a, portA, pa, b, portB, pb);
        sys.fire(DockingEvent.Kind.DOCKED, a, portA, b, portB, pa.permitted);
    }

    void capture(Entity a, int portA, DockingPort pa, Entity b, int portB, DockingPort pb) {
        Body other = sys.bodyOf(b);
        WeldJointDef weld = new WeldJointDef();
        // Face on face and square: the weld pulls the head flush onto the port it caught
        float hx = pa.normalX * pa.collarDepth * 0.5f, hy = pa.normalY * pa.collarDepth * 0.5f;
        weld.bodyA = pa.head;
        weld.bodyB = other;
        weld.localAnchorA.set(hx * U, hy * U);
        weld.localAnchorB.set(pb.faceX() * U, pb.faceY() * U);
        float now = other.getAngle() - pa.head.getAngle();
        float off = DockingSystem.matedAngle(pa, pb) - now;
        weld.referenceAngle = now + MathUtils.atan2(MathUtils.sin(off), MathUtils.cos(off));
        weld.collideConnected = false;
        pa.capture = sys.physics.getWorld().createJoint(weld);
        sys.destroyJoint(pa.spring);
        pa.spring = null;
        // The slider runs free: drive() draws the craft in
        pa.guide.enableMotor(false);
        sys.latches.latchRecords(a, portA, pa, b, portB, pb, DockingPort.State.CAPTURED, permittedBetween(pa, pb, a, b));
        // How far out the collar will lock: in all the way unless the hulls would meet
        pa.lockAt = CollarReach.lockExtension(sys.bodyOf(a), pa, other, pb, sys.heads.tileSize(a));
        pa.stalled = 0f;
        pa.stalledAt = pa.extension;
        sys.fire(DockingEvent.Kind.CAPTURED, a, portA, b, portB, pa.permitted);
    }

    /** Clearance is permission: the other side's port was armed for this craft, or by its own crew. */
    private boolean permittedBetween(DockingPort pa, DockingPort pb, Entity a, Entity b) {
        String ua = sys.uuidOf(a), ub = sys.uuidOf(b);
        boolean bAgreed = pb.armed && (pb.armedFor == null || pb.armedFor.equals(ua)) || sys.yields.test(b);
        boolean aAgreed = pa.armed && (pa.armedFor == null || pa.armedFor.equals(ub)) || sys.yields.test(a);
        return bAgreed && aAgreed;
    }
}
