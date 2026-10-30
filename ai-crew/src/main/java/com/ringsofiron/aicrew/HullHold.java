package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.HullSurface;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/**
 * Holding on to the hull for {@link EvaSystem}: crawling on toward the goal (or hopping), and
 * holding on while the hull's motion allows, or torn off it when it pulls past their grip.
 */
final class HullHold {
    private final EvaSystem sys;

    HullHold(EvaSystem sys) {
        this.sys = sys;
    }

    /** A tick on the hull: crawls on toward the goal (or hops), and holds on if the hull's motion allows. */
    void hold(Entity e, EvaComponent eva, CrewMemberComponent c, GridPositionComponent p, ShipGridComponent grid,
              HullSurface hull, float dt) {
        float fromX = eva.rx, fromY = eva.ry;
        // Off on a hop, or out to a shipmate adrift: the jetpack takes over from the hull point's motion
        if (eva.chase != null || sys.crawling.crawl(eva, c, hull, dt)) {
            eva.slideX = eva.slideY = 0f;
            eva.path.clear();
            sys.frame.toWorld(eva.rx, eva.ry, sys.a);
            sys.frame.velocityAt(sys.a[0], sys.a[1], sys.b);
            eva.wx = sys.a[0];
            eva.wy = sys.a[1];
            eva.vx = sys.b[0];
            eva.vy = sys.b[1];
            eva.phase = EvaComponent.Phase.FREE;
            eva.hullVValid = false;
            return;
        }
        // Just caught hold short of the hug point: they pull themselves over to it
        if (eva.slideX != 0f || eva.slideY != 0f) {
            float len = (float) Math.sqrt(eva.slideX * eva.slideX + eva.slideY * eva.slideY);
            float ease = EvaSystem.CRAWL_SPEED / hull.tileSize() * dt;
            float k = len > ease ? (len - ease) / len : 0f;
            eva.slideX *= k;
            eva.slideY *= k;
            eva.rx += eva.slideX;
            eva.ry += eva.slideY;
        }
        eva.rvx = (eva.rx - fromX) / dt;
        eva.rvy = (eva.ry - fromY) / dt;
        sys.frame.toWorld(eva.rx, eva.ry, sys.a);
        sys.frame.velocityAt(sys.a[0], sys.a[1], sys.b);
        // What keeps them with the hull point: its acceleration times their mass, less what their line
        // pulls. The hull pushing them along its outward normal does that part itself; the rest, a pull
        // away from it or a drag along it, their hold has to give.
        float fx = 0f, fy = 0f, mass = EvaSystem.mass(eva);
        if (eva.hullVValid) {
            fx = mass * (sys.b[0] - eva.hullVx) / dt;
            fy = mass * (sys.b[1] - eva.hullVy) / dt;
        }
        if (eva.tethered && eva.tension > 0f && eva.rope != null) {
            int n = eva.rope.length;
            float dx = eva.rope[n - 4] - sys.a[0], dy = eva.rope[n - 3] - sys.a[1];
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 1e-4f) {
                fx -= eva.tension * dx / d;
                fy -= eva.tension * dy / d;
            }
        }
        // And what an engine's exhaust pushes them with
        fx -= sys.pushX;
        fy -= sys.pushY;
        float need = (float) Math.sqrt(fx * fx + fy * fy);
        if (hull.surface(eva.rx, eva.ry, sys.q)) {
            sys.frame.vecToWorld(sys.q[2], sys.q[3], sys.c4);
            float len = (float) Math.sqrt(sys.c4[0] * sys.c4[0] + sys.c4[1] * sys.c4[1]);
            float nx = sys.c4[0] / len, ny = sys.c4[1] / len;
            float fn = fx * nx + fy * ny;
            float tx = fx - fn * nx, ty = fy - fn * ny, pull = Math.min(0f, fn);
            need = (float) Math.sqrt(tx * tx + ty * ty + pull * pull);
        }
        eva.hullVx = sys.b[0];
        eva.hullVy = sys.b[1];
        eva.hullVValid = true;
        sys.frame.vecToWorld(eva.rvx, eva.rvy, sys.c4);
        eva.wx = sys.a[0];
        eva.wy = sys.a[1];
        eva.vx = sys.b[0] + sys.c4[0];
        eva.vy = sys.b[1] + sys.c4[1];
        // Past their grip the strain builds, and their spare grip eases it: a jolt that would take more
        // than GRIP_SLIP seconds of their full grip, or a pull well past it for a moment, tears them off
        eva.strain = Math.max(0f, eva.strain + (need - EvaSystem.GRIP) * dt);
        if (eva.strain > EvaSystem.GRIP * EvaSystem.GRIP_SLIP) letGo(e, eva, p, need / mass);
    }

    void letGo(Entity e, EvaComponent eva, GridPositionComponent p, float accel) {
        eva.phase = EvaComponent.Phase.FREE;
        eva.slideX = eva.slideY = 0f;
        eva.hop = false;
        eva.path.clear();
        eva.stepDone = 0f;
        eva.strain = 0f;
        eva.hullVValid = false;
        eva.fell = true;
        eva.regrab = EvaSystem.REGRAB_SECONDS;
        sys.emit(EvaEvent.Kind.LOST_GRIP, e, p.ship, accel);
    }
}
