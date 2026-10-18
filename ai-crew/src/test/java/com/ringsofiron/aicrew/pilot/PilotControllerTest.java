package com.ringsofiron.aicrew.pilot;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

/** Flies the controller on a point-mass ship (no Box2D) with the bubble origin as the parent. */
class PilotControllerTest {

    static final float DT = 1f / 60f;

    static class Sim {
        final PilotController controller = new PilotController();
        final PilotController.ShipState s = new PilotController.ShipState();
        final PilotController.Controls c = new PilotController.Controls();
        final NpcPilotComponent pilot = new NpcPilotComponent();
        double time;

        Sim() {
            s.maxAccel = 5f;          // m/s²
            s.maxReverseAccel = 2f;
            s.maxLateralAccel = 1f;
            s.maxAngularAccel = 1f;   // rad/s²
        }

        void run(double seconds) {
            for (double end = time + seconds; time < end; time += DT) {
                s.parentPosition.set(s.position);
                s.parentVelocity.set(s.velocity);
                controller.update(pilot, s, time, DT, c);
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
            }
        }
    }

    @Test
    void departsOutwardAndThenCoasts() {
        Sim sim = new Sim();
        sim.s.position.set(-2000f, 500f);
        sim.s.angle = 1f;
        sim.run(120);
        Vector2 out = new Vector2(sim.s.position).nor();
        assertTrue(sim.s.velocity.dot(out) >= sim.pilot.departSpeed - 1f, "outward speed " + sim.s.velocity.dot(out));
        assertEquals(NpcPilotComponent.Mode.COASTING, sim.pilot.mode);
    }

    @Test
    void fliesAProgradeBurnAtItsTime() {
        Sim sim = new Sim();
        sim.s.position.set(5000f, 0f);
        sim.s.velocity.set(0f, 100f);
        sim.pilot.burns.add(new PilotBurn(30, 40, 0));
        sim.pilot.departSpeed = 0f; // hold course after the burn
        sim.run(29);
        assertEquals(100f, sim.s.velocity.len(), 0.01f, "no thrust before the burn");
        assertEquals(NpcPilotComponent.Mode.ALIGNING, sim.pilot.mode);
        sim.run(30);
        assertTrue(sim.pilot.burns.isEmpty());
        assertEquals(0f, sim.s.velocity.x, 0.5f);
        assertEquals(140f, sim.s.velocity.y, 0.5f);
    }

    @Test
    void fliesARadialBurn() {
        Sim sim = new Sim();
        sim.s.position.set(5000f, 0f);
        sim.s.velocity.set(0f, 100f);
        sim.pilot.burns.add(new PilotBurn(5, 0, 20));
        sim.pilot.departSpeed = 0f;
        sim.run(5);
        Vector2 radialAtBurn = new Vector2(sim.s.position).nor();
        Vector2 before = new Vector2(sim.s.velocity);
        sim.run(35);
        assertTrue(sim.pilot.burns.isEmpty());
        Vector2 dv = new Vector2(sim.s.velocity).sub(before);
        assertEquals(20f, dv.len(), 0.5f);
        assertEquals(1f, dv.nor().dot(radialAtBurn), 0.01f, "pushed straight out from the parent");
    }

    @Test
    void seeksAPointAndStops() {
        Sim sim = new Sim();
        sim.pilot.seekTarget = new Vector2(3000f, -1500f);
        sim.run(200);
        assertTrue(sim.s.position.dst(sim.pilot.seekTarget) < 10f, "ended at " + sim.s.position);
        assertTrue(sim.s.velocity.len() < 1f, "stopped, speed " + sim.s.velocity.len());
    }
}
