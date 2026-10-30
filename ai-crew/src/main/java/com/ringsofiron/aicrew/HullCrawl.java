package com.ringsofiron.aicrew;

import com.ringsofiron.interiorgrid.HullSurface;
import com.ringsofiron.interiorgrid.crew.Suit;

/** Crawling along the hull for {@link EvaSystem}: tile to tile toward the goal, held back by the engines' exhaust. */
final class HullCrawl {
    private final EvaSystem sys;

    HullCrawl(EvaSystem sys) {
        this.sys = sys;
    }

    /**
     * Crawls {@code eva} along the ring toward their goal for {@code dt}. True if they push off on a
     * jetpack hop instead.
     */
    boolean crawl(EvaComponent eva, CrewMemberComponent c, HullSurface hull, float dt) {
        eva.burnWait = false;
        if (!eva.hasGoal() || !hull.isRing(eva.goalX, eva.goalY) || !hull.isRing(eva.ringX, eva.ringY)) {
            eva.path.clear();
            if (eva.hasGoal() && !hull.isRing(eva.goalX, eva.goalY)) eva.outOfReach = true;
            settle(eva, hull);
            return false;
        }
        int[] last = eva.path.size > 0 ? eva.path.peek() : null;
        boolean fresh = last == null || last[0] != eva.goalX || last[1] != eva.goalY || eva.pathHull != hull;
        if (fresh && (eva.ringX != eva.goalX || eva.ringY != eva.goalY)) {
            if (!hull.path(eva.ringX, eva.ringY, eva.goalX, eva.goalY, eva.path)) {
                eva.outOfReach = true;
                settle(eva, hull);
                return false;
            }
            eva.pathHull = hull;
            eva.stepDone = 0f;
            if (wantsHop(eva, c, hull)) {
                eva.hop = true;
                eva.path.clear();
                return true;
            }
        }
        if (eva.path.size == 0) {
            settle(eva, hull);
            return false;
        }
        // The line run out and stretched: no further this way
        if (eva.lineOut >= EvaSystem.LINE_MAX - 0.01f && eva.stretch > 0.05f) {
            eva.outOfReach = true;
            return false;
        }
        float budget = EvaSystem.CRAWL_SPEED / hull.tileSize() * dt * c.woundPace();
        while (budget > 0f && eva.path.size > 0) {
            int[] next = eva.path.first();
            hull.hug(eva.ringX, eva.ringY, sys.v2);
            float ax = sys.v2.x, ay = sys.v2.y;
            hull.hug(next[0], next[1], sys.v2);
            // Not on into the exhaust of an engine burning: they hold where they are till it stops
            if (sys.burning && sys.exhaust.burns(sys.v2.x, sys.v2.y) && !sys.exhaust.burns(eva.rx, eva.ry)) {
                eva.burnWait = true;
                return false;
            }
            float len = Math.max(1e-3f, sys.v2.dst(ax, ay));
            float left = len * (1f - eva.stepDone);
            eva.facing = (float) Math.atan2(sys.v2.y - ay, sys.v2.x - ax);
            if (budget >= left) {
                budget -= left;
                eva.ringX = next[0];
                eva.ringY = next[1];
                eva.path.removeIndex(0);
                eva.stepDone = 0f;
                eva.crawled += left;
            } else {
                eva.stepDone += budget / len;
                eva.crawled += budget;
                budget = 0f;
            }
        }
        if (eva.path.size == 0) {
            settle(eva, hull);
        } else {
            int[] next = eva.path.first();
            hull.hug(eva.ringX, eva.ringY, sys.v2);
            float ax = sys.v2.x, ay = sys.v2.y;
            hull.hug(next[0], next[1], sys.v2);
            eva.rx = ax + (sys.v2.x - ax) * eva.stepDone;
            eva.ry = ay + (sys.v2.y - ay) * eva.stepDone;
        }
        return false;
    }

    /** Puts {@code eva} at their ring tile's hug point. */
    void settle(EvaComponent eva, HullSurface hull) {
        if (!hull.isRing(eva.ringX, eva.ringY)) return;
        hull.hug(eva.ringX, eva.ringY, sys.v2);
        eva.rx = sys.v2.x;
        eva.ry = sys.v2.y;
        eva.stepDone = 0f;
    }

    /** True if the way to the goal is better flown: clear, much shorter, and the pack can spare it. */
    boolean wantsHop(EvaComponent eva, CrewMemberComponent c, HullSurface hull) {
        if (c.suitKind != Suit.Kind.EVA || c.suitJet < EvaSystem.JET_RESERVE + 2f * EvaSystem.HOP_SPEED + 1f || eva.noHops) return false;
        hull.hug(eva.ringX, eva.ringY, sys.v2);
        float ax = sys.v2.x, ay = sys.v2.y;
        hull.hug(eva.goalX, eva.goalY, sys.v2);
        float straight = sys.v2.dst(ax, ay);
        return eva.path.size - straight >= EvaSystem.HOP_SAVES && hull.clear(ax, ay, sys.v2.x, sys.v2.y);
    }
}
