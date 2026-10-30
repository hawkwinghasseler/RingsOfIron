package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.HullSurface;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;

/** The line for {@link EvaSystem}: the rope's shape, the reel, the pull, and a snap. */
final class EvaTether {
    private final EvaSystem sys;
    private final float[] qa = new float[2];
    private float[] ropeScratch = new float[2 * EvaSystem.ROPE_SEGMENTS + 2];

    EvaTether(EvaSystem sys) {
        this.sys = sys;
    }

    /** A tick of the line: the rope's shape, the reel, the pull, and a snap. */
    void rope(Entity e, EvaComponent eva, CrewMemberComponent c, GridPositionComponent p, ShipGridComponent grid,
              HullSurface hull, MountFootprint port, float dt) {
        int n = EvaSystem.ROPE_SEGMENTS;
        EvaSystem.reel(port, sys.a);
        sys.frame.toWorld(sys.a[0], sys.a[1], sys.b);
        float ax = sys.b[0], ay = sys.b[1];
        if (eva.rope == null) layRope(eva, ax, ay);
        float[] r = eva.rope;
        sys.ropeSwing.swing(r, eva.ropePrev, ax, ay, true, eva.wx, eva.wy, Math.max(0.05f, eva.lineOut) / n, hull, dt);
        float length = 0f;
        for (int i = 0; i < n; i++) {
            float dx = r[2 * i + 2] - r[2 * i], dy = r[2 * i + 3] - r[2 * i + 1];
            length += (float) Math.sqrt(dx * dx + dy * dy);
        }
        // Through the exhaust of an engine burning, it burns through
        if (sys.burning && sys.exhaust.burnsThrough(ropeInTiles(eva.rope, sys.frame))) {
            eva.tethered = false;
            eva.tension = 0f;
            eva.reelLocked = false;
            sys.emit(EvaEvent.Kind.TETHER_BURNED, e, p.ship, 0f);
            return;
        }
        float stretch = length - eva.lineOut;
        float straight = (float) Math.sqrt((eva.wx - ax) * (eva.wx - ax) + (eva.wy - ay) * (eva.wy - ay));
        // The reel: pays out as the line is drawn, unless drawn too fast (it locks), and takes up slack;
        // or winds in one who can't climb, while their weight on the line is no more than its motor pulls
        boolean winched = eva.phase == EvaComponent.Phase.FREE && (eva.climbing || !c.isConscious());
        if (stretch > 0f && !eva.reelLocked && !winched && eva.lineOut < EvaSystem.LINE_MAX) {
            // Locks on a line snatched out by one going faster than it pays out: not on one pushed out
            // slowly (a hop, a push off to a shipmate), whose rope only shivers as it lifts off the hull
            sys.frame.velocityAt(eva.wx, eva.wy, sys.c4);
            float rel = (float) Math.sqrt((eva.vx - sys.c4[0]) * (eva.vx - sys.c4[0]) + (eva.vy - sys.c4[1]) * (eva.vy - sys.c4[1]));
            if (eva.phase == EvaComponent.Phase.FREE && stretch / dt > EvaSystem.REEL_LOCK_SPEED && rel > EvaSystem.REEL_LOCK_SPEED && eva.lineOut > 1f) {
                eva.reelLocked = true;
            } else {
                float out = Math.min(stretch, EvaSystem.LINE_MAX - eva.lineOut);
                eva.lineOut += out;
                stretch -= out;
            }
        }
        if (winched) {
            if (eva.tension < EvaSystem.WINCH_PULL) eva.lineOut = Math.max(0.3f, eva.lineOut - (c.isConscious() ? EvaSystem.CLIMB_SPEED : EvaSystem.REEL_SPEED) * dt);
        } else if (!eva.reelLocked && stretch < 0f && eva.lineOut > straight + EvaSystem.SLACK) {
            eva.lineOut = Math.max(straight + EvaSystem.SLACK, eva.lineOut - EvaSystem.TAKE_UP_SPEED * dt);
        }
        if (eva.reelLocked) {
            eva.slackTime = stretch < 0f ? eva.slackTime + dt : 0f;
            if (eva.slackTime > 0.5f || eva.phase == EvaComponent.Phase.ON_HULL) {
                eva.reelLocked = false;
                eva.slackTime = 0f;
            }
        }
        // Its pull: the stretch, and the line's own damping as it stretches; it parts once the stretch
        // alone has been past what it is rated for long enough
        float before = eva.stretchF;
        eva.stretchF += (stretch - eva.stretchF) * Math.min(1f, dt / EvaSystem.STRETCH_TAU);
        float tension = 0f, elastic = 0f;
        if (eva.stretchF > 0f) {
            float rate = (eva.stretchF - Math.max(0f, before)) / dt;
            elastic = EvaSystem.LINE_K * eva.stretchF;
            tension = Math.max(0f, elastic + EvaSystem.LINE_C * rate);
        }
        eva.stretch = stretch;
        eva.tension = tension;
        eva.lineStrain = Math.max(0f, eva.lineStrain + (elastic - EvaSystem.LINE_RATED) * dt);
        if (eva.lineStrain > EvaSystem.LINE_GIVE) {
            eva.tethered = false;
            eva.tension = 0f;
            eva.reelLocked = false;
            sys.emit(EvaEvent.Kind.TETHER_SNAPPED, e, p.ship, tension);
            return;
        }
        // The same pull on the ship, at the reel, along the line's first segment
        if (tension > 0f && sys.frame.body != null) {
            float dx = r[2] - r[0], dy = r[3] - r[1];
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 1e-4f) {
                float[] f = sys.pulls.get(sys.frame.body);
                if (f == null) sys.pulls.put(sys.frame.body, f = new float[4]);
                f[0] += tension * dx / d;
                f[1] += tension * dy / d;
                f[2] = ax;
                f[3] = ay;
            }
        }
    }

    /**
     * A tick of a parted line: it parted at the crew member's clip, so the whole of it is left on the
     * reel, its loose end free, and the reel's spring winds it in; gone once it is.
     */
    void reelIn(EvaComponent eva, HullSurface hull, MountFootprint port, float dt) {
        eva.lineOut -= EvaSystem.TAKE_UP_SPEED * dt;
        if (eva.lineOut <= 0.1f) {
            eva.rope = null;
            return;
        }
        EvaSystem.reel(port, sys.a);
        sys.frame.toWorld(sys.a[0], sys.a[1], sys.b);
        sys.ropeSwing.swing(eva.rope, eva.ropePrev, sys.b[0], sys.b[1], false, 0f, 0f, eva.lineOut / EvaSystem.ROPE_SEGMENTS, hull, dt);
    }

    /** Lays the rope straight from the reel to the crew member, at rest relative to the hull. */
    void layRope(EvaComponent eva, float ax, float ay) {
        int n = EvaSystem.ROPE_SEGMENTS;
        eva.rope = new float[2 * n + 2];
        eva.ropePrev = new float[2 * n + 2];
        float straight = (float) Math.sqrt((eva.wx - ax) * (eva.wx - ax) + (eva.wy - ay) * (eva.wy - ay));
        if (eva.lineOut < straight) eva.lineOut = Math.min(EvaSystem.LINE_MAX, straight + EvaSystem.SLACK);
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            float x = ax + (eva.wx - ax) * t, y = ay + (eva.wy - ay) * t;
            eva.rope[2 * i] = x;
            eva.rope[2 * i + 1] = y;
            sys.frame.velocityAt(x, y, sys.c4);
            eva.ropePrev[2 * i] = x - sys.c4[0] * EvaSystem.STEP;
            eva.ropePrev[2 * i + 1] = y - sys.c4[1] * EvaSystem.STEP;
        }
        eva.stretch = eva.stretchF = 0f;
    }

    /** {@code rope}, world meters, in {@code f}'s tiles, into a scratch array. */
    float[] ropeInTiles(float[] rope, ShipFrame f) {
        if (ropeScratch.length != rope.length) ropeScratch = new float[rope.length];
        for (int i = 0; i < rope.length; i += 2) {
            f.toTiles(rope[i], rope[i + 1], qa);
            ropeScratch[i] = qa[0];
            ropeScratch[i + 1] = qa[1];
        }
        return ropeScratch;
    }
}
