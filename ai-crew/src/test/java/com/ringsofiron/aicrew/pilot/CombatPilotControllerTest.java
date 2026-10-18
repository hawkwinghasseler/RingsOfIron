package com.ringsofiron.aicrew.pilot;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

/** Flies the combat pilot on a point-mass ship against a point-mass target. */
class CombatPilotControllerTest {

    static final float DT = 1f / 60f;

    static class Sim {
        final CombatPilotController controller = new CombatPilotController();
        final PilotController.ShipState s = new PilotController.ShipState();
        final PilotController.Controls c = new PilotController.Controls();
        final NpcCombatComponent combat = new NpcCombatComponent();
        final Vector2 targetPos = new Vector2(), targetVel = new Vector2();
        Vector2 missilePos, missileVel;

        Sim() {
            s.maxAccel = 10f;
            s.maxReverseAccel = 5f;
            s.maxLateralAccel = 5f;
            s.maxAngularAccel = 2f;
        }

        void run(double seconds) {
            for (double t = 0; t < seconds; t += DT) {
                controller.update(combat, s, targetPos, targetVel, missilePos, missileVel, DT, c);
                float along = c.forward >= 0f ? c.forward * s.maxAccel : c.forward * s.maxReverseAccel;
                float across = c.strafe * s.maxLateralAccel;
                float cos = MathUtils.cos(s.angle), sin = MathUtils.sin(s.angle);
                s.velocity.add((along * cos - across * sin) * DT, (along * sin + across * cos) * DT);
                s.position.mulAdd(s.velocity, DT);
                float alpha = c.turn * s.maxAngularAccel;
                if (c.rotationAssist && c.turn == 0f) {
                    alpha = MathUtils.clamp(-s.angularVelocity / DT, -s.maxAngularAccel, s.maxAngularAccel);
                }
                s.angularVelocity += alpha * DT;
                s.angle += s.angularVelocity * DT;
                targetPos.mulAdd(targetVel, DT);
                if (missilePos != null) missilePos.mulAdd(missileVel, DT);
            }
        }

        float range() {
            return s.position.dst(targetPos);
        }
    }

    @Test
    void closesToItsRangeAndHoldsItWithTheNoseOnTheTarget() {
        Sim sim = new Sim();
        sim.s.position.set(6000f, 0f);
        sim.run(240);
        assertEquals(sim.combat.preferredRange, sim.range(), 100f, "holding the range band");
        assertEquals(NpcCombatComponent.Mode.HOLDING, sim.combat.mode);
        float bearing = new Vector2(sim.targetPos).sub(sim.s.position).angleRad();
        assertEquals(0f, PilotController.wrap(bearing - sim.s.angle), 0.1f, "nose on the target");
        assertTrue(sim.s.velocity.len() > 5f, "circling, not parked");
    }

    @Test
    void opensRangeOnATargetThatComesTooClose() {
        Sim sim = new Sim();
        sim.s.position.set(300f, 0f);
        sim.run(120);
        assertTrue(sim.range() > sim.combat.preferredRange - 150f, "backed off to " + sim.range());
    }

    @Test
    void keepsPaceWithAMovingTarget() {
        Sim sim = new Sim();
        sim.s.position.set(1500f, 0f);
        sim.targetVel.set(0f, 60f);
        sim.run(240);
        assertEquals(sim.combat.preferredRange, sim.range(), 150f);
    }

    @Test
    void breaksAcrossAnInboundMissile() {
        Sim sim = new Sim();
        sim.s.position.set(1500f, 0f);
        sim.missilePos = new Vector2(1500f, 2500f);
        sim.missileVel = new Vector2(0f, -300f);
        sim.run(1);
        assertEquals(NpcCombatComponent.Mode.EVADING, sim.combat.mode);
        sim.run(4);
        assertTrue(Math.abs(sim.s.velocity.x) > 15f, "sliding across the missile's line at " + sim.s.velocity);
        assertTrue(Math.abs(sim.s.velocity.x) > 3f * Math.abs(sim.s.velocity.y));
    }

    @Test
    void ignoresAMissileThatIsNotClosing() {
        Sim sim = new Sim();
        sim.s.position.set(1500f, 0f);
        sim.missilePos = new Vector2(1500f, 2500f);
        sim.missileVel = new Vector2(0f, 300f);
        sim.run(1);
        assertNotEquals(NpcCombatComponent.Mode.EVADING, sim.combat.mode);
    }
}
