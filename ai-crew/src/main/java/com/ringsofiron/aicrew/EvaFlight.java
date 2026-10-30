package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.HullSurface;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.interiorgrid.crew.Suit;

/**
 * Flying free for {@link EvaSystem}: the line's pull and the jetpack, then the hull in the way, and
 * taking hold of it again.
 */
final class EvaFlight {
    private final EvaSystem sys;

    EvaFlight(EvaSystem sys) {
        this.sys = sys;
    }

    /** A tick off the hull: the line's pull and the jetpack, then the hull in the way. */
    void fly(Entity e, EvaComponent eva, CrewMemberComponent c, ShipGridComponent grid, HullSurface hull, float dt) {
        float fx = 0f, fy = 0f;
        // The line pulls along its last segment, toward the reel
        if (eva.tethered && eva.tension > 0f && eva.rope != null) {
            int n = eva.rope.length;
            float dx = eva.rope[n - 4] - eva.wx, dy = eva.rope[n - 3] - eva.wy;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 1e-4f) {
                fx += eva.tension * dx / d;
                fy += eva.tension * dy / d;
            }
        }
        float mass = EvaSystem.mass(eva);
        // The bubble's tidal gravity: the ship falls the same way, so it tells only on one adrift far off
        if (sys.physics != null) {
            sys.physics.getTidalField().accelAt(eva.wx, eva.wy, sys.v2);
            fx += sys.v2.x * mass;
            fy += sys.v2.y * mass;
        }
        // An engine's exhaust
        fx += sys.pushX;
        fy += sys.pushY;
        boolean conscious = c.isConscious();
        int[] target = null;
        eva.jetting = false;
        eva.climbing = false;
        eva.waiting = false;
        eva.shortOfFuel = !eva.tethered && (!conscious || !c.suited || c.suitKind != Suit.Kind.EVA || c.suitJet <= 0f);
        boolean canJet = conscious && c.suited && c.suitKind == Suit.Kind.EVA && c.suitJet > 0f;
        // Out to a shipmate adrift: while they are still there to take hold of
        EvaComponent chased = EvaSystem.of(eva.chase);
        if (chased != null && (!canJet || !chased.outside() || chased.heldBy != null || !chased.worldValid)) chased = null;
        if (chased == null) eva.chase = null;
        if (canJet && chased == null) {
            target = eva.hop && eva.hasGoal() && hull.isRing(eva.goalX, eva.goalY) ? new int[]{eva.goalX, eva.goalY}
                    : hull.nearest(eva.rx, eva.ry);
        } else if (conscious && eva.tethered && chased == null) {
            eva.climbing = true;
        }
        if (target != null || chased != null) {
            if (chased != null) {
                sys.a[0] = chased.wx;
                sys.a[1] = chased.wy;
                sys.b[0] = chased.vx;
                sys.b[1] = chased.vy;
            } else {
                hull.hug(target[0], target[1], sys.v2);
                sys.frame.toWorld(sys.v2.x, sys.v2.y, sys.a);
                sys.frame.velocityAt(sys.a[0], sys.a[1], sys.b);
            }
            float rx = sys.a[0] - eva.wx, ry = sys.a[1] - eva.wy;
            float rvx = eva.vx - sys.b[0], rvy = eva.vy - sys.b[1];
            float dist = (float) Math.sqrt(rx * rx + ry * ry);
            float maxA = EvaSystem.JET_THRUST / mass;
            // Far out with no line, faster than a hop, or the way back would outlast their air; out from
            // the hull on a line, no faster than the reel pays out without locking; across to another
            // craft, briskly
            float cruise = eva.crossing ? EvaSystem.CROSS_SPEED : eva.tethered ? EvaSystem.HOP_SPEED : Math.max(EvaSystem.HOP_SPEED, Math.min(EvaSystem.CRUISE_MAX, dist / EvaSystem.CRUISE_SECONDS));
            if (chased != null) cruise = Math.min(cruise, EvaSystem.REEL_LOCK_SPEED * 0.8f);
            float speed = Math.min(cruise, (float) Math.sqrt(2f * maxA * 0.6f * dist));
            float wantX = dist > 0.05f ? rx / dist * speed : 0f, wantY = dist > 0.05f ? ry / dist * speed : 0f;
            float ax = (wantX - rvx) * 1.5f, ay = (wantY - rvy) * 1.5f;
            if (!eva.tethered) {
                // Only if the pack can bring them back from here, and the ship isn't drawing away under
                // thrust the pack can't match: chasing it would only empty the pack
                float need = (float) Math.sqrt((wantX - rvx) * (wantX - rvx) + (wantY - rvy) * (wantY - rvy)) + speed + EvaSystem.JET_SPARE;
                float shipA = (float) Math.sqrt(eva.shipAx * eva.shipAx + eva.shipAy * eva.shipAy);
                eva.shortOfFuel = need > c.suitJet;
                eva.waiting = eva.shortOfFuel || shipA > maxA * EvaSystem.CHASE_SHARE;
                if (eva.waiting) ax = ay = 0f;
            }
            float am = (float) Math.sqrt(ax * ax + ay * ay);
            if (am > maxA) {
                ax *= maxA / am;
                ay *= maxA / am;
                am = maxA;
            }
            // The pack's delta-v is reckoned on its wearer alone: a shipmate in tow doubles what it takes
            float spend = am * dt * mass / EvaSystem.MASS;
            if (spend > c.suitJet) {
                float k = c.suitJet / spend;
                ax *= k;
                ay *= k;
                spend = c.suitJet;
            }
            c.suitJet = Math.max(0f, c.suitJet - spend);
            fx += ax * mass;
            fy += ay * mass;
            eva.jetting = spend > 0f;
            if (eva.jetting) {
                sys.frame.vecToTiles(ax, ay, sys.c4);
                float jet = (float) Math.sqrt(sys.c4[0] * sys.c4[0] + sys.c4[1] * sys.c4[1]);
                eva.jetX = sys.c4[0] / jet;
                eva.jetY = sys.c4[1] / jet;
            }
            if (!eva.waiting) eva.facing = (float) Math.atan2(ry, rx) - sys.frame.angle;
            float rel = (float) Math.sqrt(rvx * rvx + rvy * rvy);
            if (chased != null) {
                // There and slow enough: they take hold of them
                if (dist < EvaSystem.TAKE_HOLD && rel < EvaSystem.GRAB_SPEED) sys.tow.takeInTow(e, eva, eva.chase, chased);
                target = null;
            }
            // There, slow enough to take hold, and the hull not dragging past their grip
            if (target != null && dist < 0.3f && rel < EvaSystem.GRAB_SPEED && eva.regrab <= 0f && hull.surface(sys.v2.x, sys.v2.y, sys.q)) {
                sys.frame.vecToWorld(sys.q[2], sys.q[3], sys.c4);
                float len = (float) Math.sqrt(sys.c4[0] * sys.c4[0] + sys.c4[1] * sys.c4[1]);
                if (canHold(eva, sys.a[0], sys.a[1], sys.c4[0] / len, sys.c4[1] / len)) {
                    grab(e, eva, target[0], target[1], hull);
                    return;
                }
            }
        }
        eva.vx += fx / mass * dt;
        eva.vy += fy / mass * dt;
        eva.wx += eva.vx * dt;
        eva.wy += eva.vy * dt;
        contact(e, eva, c, grid, hull);
    }

    /** The hull in the way: stops the speed into it and some along it, hurts past {@link EvaSystem#HURT_SPEED}, and is caught hold of if slow. */
    void contact(Entity e, EvaComponent eva, CrewMemberComponent c, ShipGridComponent grid, HullSurface hull) {
        sys.frame.toTiles(eva.wx, eva.wy, sys.a);
        float qx = sys.a[0], qy = sys.a[1];
        float r = EvaSystem.RADIUS / grid.tileSize;
        float px, py, nx, ny;
        if (hull.solid((int) Math.floor(qx), (int) Math.floor(qy))) {
            if (!hull.pushOut(qx, qy, sys.b)) return;
            px = sys.b[0];
            py = sys.b[1];
            nx = px - qx;
            ny = py - qy;
            float len = (float) Math.sqrt(nx * nx + ny * ny);
            if (len < 1e-5f) return;
            nx /= len;
            ny /= len;
            px += nx * r;
            py += ny * r;
        } else {
            if (!hull.surface(qx, qy, sys.b)) return;
            float dx = qx - sys.b[0], dy = qy - sys.b[1];
            if (dx * dx + dy * dy >= r * r) return;
            nx = sys.b[2];
            ny = sys.b[3];
            px = sys.b[0] + nx * r;
            py = sys.b[1] + ny * r;
        }
        sys.frame.toWorld(px, py, sys.a);
        eva.wx = sys.a[0];
        eva.wy = sys.a[1];
        sys.frame.vecToWorld(nx, ny, sys.c4);
        float len = (float) Math.sqrt(sys.c4[0] * sys.c4[0] + sys.c4[1] * sys.c4[1]);
        float nwx = sys.c4[0] / len, nwy = sys.c4[1] / len;
        sys.frame.velocityAt(eva.wx, eva.wy, sys.b);
        float rvx = eva.vx - sys.b[0], rvy = eva.vy - sys.b[1];
        float vn = rvx * nwx + rvy * nwy;
        if (vn < 0f) {
            float impact = -vn;
            rvx -= vn * nwx;
            rvy -= vn * nwy;
            // Friction along the hull, up to half the blow
            float vt = (float) Math.sqrt(rvx * rvx + rvy * rvy);
            if (vt > 1e-5f) {
                float k = Math.max(0f, vt - 0.5f * impact) / vt;
                rvx *= k;
                rvy *= k;
            }
            eva.vx = sys.b[0] + rvx;
            eva.vy = sys.b[1] + rvy;
            if (impact > EvaSystem.HURT_SPEED) sys.service.applyDamage(e, (impact - EvaSystem.HURT_SPEED) * EvaSystem.HURT_PER_MPS, CrewDamageSink.DamageCause.IMPACT);
        }
        float rel = (float) Math.sqrt(rvx * rvx + rvy * rvy);
        if (c.isConscious() && eva.regrab <= 0f && eva.chase == null && rel < EvaSystem.GRAB_SPEED && canHold(eva, eva.wx, eva.wy, nwx, nwy)) {
            int[] ring = hull.nearest(px, py);
            if (ring != null) grab(e, eva, ring[0], ring[1], hull);
        }
    }

    /**
     * True if a crew member could keep hold of the hull at world point ({@code wx}, {@code wy}), its
     * outward normal ({@code nx}, {@code ny}): the force that would keep them moving with it, but for
     * the hull's push, within their grip. Their line doesn't count: it pulls one way and then another
     * as they swing, so nobody can lean on it to take hold. A hull dragging harder than that past them
     * (a hard burn still going) can't be caught; they hang on the line until it eases.
     */
    boolean canHold(EvaComponent eva, float wx, float wy, float nx, float ny) {
        float rx = wx - sys.frame.cx, ry = wy - sys.frame.cy, w2 = sys.frame.omega * sys.frame.omega, mass = EvaSystem.mass(eva);
        float fx = mass * (eva.shipAx - eva.shipAlpha * ry - w2 * rx);
        float fy = mass * (eva.shipAy + eva.shipAlpha * rx - w2 * ry);
        float fn = fx * nx + fy * ny;
        float tx = fx - fn * nx, ty = fy - fn * ny, pull = Math.min(0f, fn);
        return tx * tx + ty * ty + pull * pull <= EvaSystem.GRIP * EvaSystem.GRIP;
    }

    void grab(Entity e, EvaComponent eva, int x, int y, HullSurface hull) {
        eva.phase = EvaComponent.Phase.ON_HULL;
        eva.ringX = x;
        eva.ringY = y;
        eva.path.clear();
        eva.stepDone = 0f;
        eva.hop = false;
        eva.strain = 0f;
        eva.hullVValid = false;
        eva.jetting = false;
        eva.climbing = false;
        // Where they took hold, short of the ring tile's hug point
        sys.frame.toTiles(eva.wx, eva.wy, sys.a);
        sys.crawling.settle(eva, hull);
        eva.slideX = sys.a[0] - eva.rx;
        eva.slideY = sys.a[1] - eva.ry;
        eva.rx = sys.a[0];
        eva.ry = sys.a[1];
        if (eva.fell) {
            eva.fell = false;
            sys.emit(EvaEvent.Kind.CAUGHT_HULL, e, sys.service.position(e).ship, eva.adrift);
        }
        eva.adrift = 0f;
        eva.stranded = false;
    }
}
