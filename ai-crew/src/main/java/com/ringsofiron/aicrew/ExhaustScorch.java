package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;

/** The exhaust of a ship's main engines on someone outside it, for {@link EvaSystem}: its push and its burn. */
final class ExhaustScorch {
    private final EvaSystem sys;

    ExhaustScorch(EvaSystem sys) {
        this.sys = sys;
    }

    /**
     * The exhaust of their ship's main engines on {@code eva} this step, if they are in it ({@link
     * EngineExhaust}): its push, for {@link HullHold#hold} and {@link EvaFlight#fly}; its burn; and said so when it
     * first catches them.
     */
    void scorch(Entity e, EvaComponent eva, CrewMemberComponent c, GridPositionComponent p, float dt) {
        float push = sys.burning ? sys.exhaust.push(eva.rx, eva.ry, sys.c4) : 0f;
        eva.exhaust = push;
        sys.pushX = sys.pushY = 0f;
        if (push <= 0f) {
            eva.scorched = false;
            return;
        }
        // Along the ship's axes, into the world's
        sys.pushX = (sys.frame.cos * sys.c4[0] - sys.frame.sin * sys.c4[1]) * push;
        sys.pushY = (sys.frame.sin * sys.c4[0] + sys.frame.cos * sys.c4[1]) * push;
        if (push > EngineExhaust.BURN_FROM) {
            if (!eva.scorched && c.state != CrewState.DEAD) {
                eva.scorched = true;
                sys.emit(EvaEvent.Kind.EXHAUST, e, p.ship, push);
            }
            sys.service.applyDamage(e, EngineExhaust.burn(push) * dt, CrewDamageSink.DamageCause.FIRE);
        } else if (push < EngineExhaust.BURN_FROM * 0.5f) {
            eva.scorched = false;
        }
    }
}
