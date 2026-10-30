package com.ringsofiron.aicrew.btree;

import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;

/** Closes to weapon range with line of sight and shoots the target until it drops. */
public class EngageHostile extends CrewLeaf {
    private float repathTimer;

    @Override
    public void start() {
        repathTimer = 0f;
    }

    @Override
    public Status execute() {
        CrewAgent a = agent();
        CrewMemberComponent t = a.service.crew(a.target);
        if (t == null || !t.isConscious()) return Status.SUCCEEDED;
        GridPositionComponent tp = a.service.position(a.target);
        float dist = a.service.distance(a.pos, tp);
        boolean sight = a.service.canSee(a.pos, tp);
        a.crew.fireCooldown -= a.dt;
        if (sight && dist <= a.crew.def.weaponRange) {
            a.pos.stop();
            a.crew.activity = "shooting " + t.name;
            if (a.crew.fireCooldown <= 0f) {
                a.crew.fireCooldown = a.crew.def.fireInterval;
                if (a.service.random.nextFloat() < a.crew.def.hitChance) {
                    a.service.applyDamage(a.target, a.crew.def.weaponDamage, CrewDamageSink.DamageCause.SMALL_ARMS);
                    a.crew.record().pendingHits++;
                    if (!t.isConscious()) a.crew.record().pendingDowns++;
                }
            }
            return Status.RUNNING;
        }
        a.crew.activity = "closing on " + t.name;
        if (tp.ship != a.pos.ship) {
            // Aboard a docked craft: over to it, to beside them
            CrewAgent.Travel go = a.travel(tp.ship, CrewTasks.near(a, tp.ship, tp.tileX(), tp.tileY(), 1, 1));
            return go == CrewAgent.Travel.UNREACHABLE ? Status.FAILED : Status.RUNNING;
        }
        repathTimer -= a.dt;
        if (!a.pos.isMoving() || a.pos.pathBlocked || repathTimer <= 0f) {
            repathTimer = 1f;
            if (a.findPath(a.pos.tileX(), a.pos.tileY(), tp.tileX(), tp.tileY(), a.pathScratch)) {
                // Stop one tile short: no need to share the target's tile
                if (a.pathScratch.size > 1) a.pathScratch.pop();
                a.pos.setPath(a.pathScratch);
            } else {
                return Status.FAILED;
            }
        }
        return Status.RUNNING;
    }

    @Override
    public void end() {
        agent().pos.stop();
    }
}
