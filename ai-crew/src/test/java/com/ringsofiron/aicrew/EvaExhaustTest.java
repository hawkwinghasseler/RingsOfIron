package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.HullSurface;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The exhaust of the main engines and crew outside: the ship of {@link EvaRepairTest#ship()} with
 * an engine on its right end, its nozzle facing right, its mouth at x 15 between y 3 and 5, giving
 * the corvette's engine's 1.2 MN at full. Standing still: no physics body.
 */
class EvaExhaustTest {

    static final float THRUST = 1.2e6f;

    TestShips s;
    FlightControlComponent controls;
    final List<EvaEvent.Kind> events = new ArrayList<>();

    void ship() {
        s = new TestShips(
                "############   ",
                "#..........#EEE",
                "#..........#EEE",
                "#..........#   ",
                "############   ",
                "############   ");
        s.grid.placeMount(new MountFootprint(MountFootprint.MountKind.PORT, MountFootprint.MountSize.M, 3, 4, 0));
        assertNotNull(com.ringsofiron.interiorgrid.SuitOutfit.placeLocker(s.grid, com.ringsofiron.interiorgrid.crew.Suit.Kind.EVA, 9, 4, 2));
        controls = new FlightControlComponent();
        s.ship.add(controls);
        s.ship.add(new ThrusterComponent(THRUST, 0f, 0f, 0f));
        s.crew.eva.events.add((signal, e) -> events.add(e.kind));
    }

    /** Work outside at ring tile (x, y) that takes {@code seconds}. */
    static OutsideTask job(int x, int y, float seconds, int[] done) {
        return new OutsideTask(CrewTask.Kind.FREE_COLLAR, x, y, new OutsideTask.Job() {
            @Override
            public boolean isOpen() {
                return done[0] == 0;
            }

            @Override
            public boolean workTile(HullSurface hull, int[] out) {
                out[0] = x;
                out[1] = y;
                return hull.isRing(x, y);
            }

            @Override
            public float seconds(CrewDef def) {
                return seconds;
            }

            @Override
            public void done(Entity worker) {
                done[0]++;
            }

            @Override
            public String doing() {
                return "fixing the widget";
            }

            @Override
            public String where() {
                return "the widget";
            }
        });
    }

    @Test
    void theExhaustPushesHardNearTheNozzleLessFurtherBackAndNotAtAllBesideIt() {
        ship();
        EngineExhaust x = new EngineExhaust();
        assertFalse(x.read(s.ship, s.grid), "the engines off");
        controls.forward = 1f;
        assertTrue(x.read(s.ship, s.grid));
        float[] dir = new float[2];
        float near = x.push(15.3f, 4f, dir);
        assertEquals(1f, dir[0], 1e-4f, "out along the nozzle's axis");
        assertTrue(near > 100000f, "right behind the nozzle, tons: " + near);
        float back = x.push(115f, 4f, null);
        assertTrue(back > EngineExhaust.BURN_FROM * 0.5f && back < EngineExhaust.BURN_FROM * 2f, "a hundred meters back, a few hundred newtons: " + back);
        assertTrue(x.push(16f, 6.5f, null) == 0f, "beside the cone");
        assertTrue(x.push(14f, 4f, null) == 0f, "not ahead of the mouth");
        // Its momentum through any cross-section is the thrust
        float r = 1f + 50f * (float) Math.tan(EngineExhaust.SPREAD);
        assertEquals(THRUST, x.flux(65f, 4f, null) * (float) Math.PI * r * r, THRUST * 1e-3f);
        controls.forward = 0.5f;
        x.read(s.ship, s.grid);
        assertEquals(near * 0.5f, x.push(15.3f, 4f, null), near * 1e-4f, "half the throttle, half the push");
        // Damaged, its thrust is less; switched off, none
        SubsystemComponent engine = null;
        for (MountFootprint m : s.grid.mounts) {
            SubsystemComponent sub = m.installed == null ? null : m.installed.getComponent(SubsystemComponent.class);
            if (sub != null && sub.type == SubsystemComponent.SubsystemType.ENGINE) engine = sub;
        }
        assertNotNull(engine);
        engine.switchedOn = false;
        assertFalse(x.read(s.ship, s.grid), "switched off: no exhaust");
        assertTrue(x.read(s.ship, s.grid, 1f) == false);
    }

    @Test
    void oneBehindTheNozzleWhenTheEngineLightsIsThrownOffBurnedAndTheirLineBurnsThrough() {
        ship();
        int[] done = {0};
        s.board.post(job(15, 4, 600f, done));
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        for (int i = 0; i < 6000 && !"fixing the widget on EVA".equals(s.c(e).activity); i++) s.run(0.1f);
        assertEquals("fixing the widget on EVA", s.c(e).activity, "at work behind the nozzle");
        assertTrue(s.crew.eva.behindEngines(e), "where a burn would burn them");
        controls.forward = 1f;
        s.run(0.5f);
        EvaComponent eva = EvaSystem.of(e);
        assertTrue(events.contains(EvaEvent.Kind.EXHAUST), "caught in the exhaust: " + events);
        assertTrue(events.contains(EvaEvent.Kind.LOST_GRIP), "thrown off the hull: " + events);
        assertTrue(events.contains(EvaEvent.Kind.TETHER_BURNED), "their line burned through: " + events);
        assertEquals(CrewState.DEAD, s.c(e).state, "burned to death right behind the nozzle");
        assertEquals(CrewDamageSink.DamageCause.FIRE, s.c(e).lastDamageCause);
        assertFalse(eva.tethered);
        assertTrue(eva.rvx * s.grid.tileSize > 20f, "thrown away from the ship: " + eva.rvx);
    }

    @Test
    void crawlingTheyHoldBackFromAnEngineBurningAndGoOnOnceItStops() {
        ship();
        int[] done = {0};
        // Under the engine, the short way round it past its nozzle
        s.board.post(job(13, 2, 20f, done));
        controls.forward = 1f;
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        EngineExhaust x = new EngineExhaust();
        boolean waited = false;
        for (int i = 0; i < 3000; i++) {
            s.run(0.1f);
            EvaComponent eva = EvaSystem.of(e);
            if (eva == null || !eva.outside()) continue;
            x.read(s.ship, s.grid);
            assertFalse(x.burns(eva.rx, eva.ry), "never into the exhaust: " + eva.rx + "," + eva.ry);
            if ("waiting for the main engines' burn to end".equals(s.c(e).activity)) waited = true;
            if (waited && i > 1500) break;
        }
        assertTrue(waited, "held back short of it");
        assertEquals(0, done[0]);
        assertFalse(events.contains(EvaEvent.Kind.EXHAUST));
        controls.forward = 0f;
        for (int i = 0; i < 6000 && !(done[0] > 0 && EvaSystem.of(e) == null); i++) s.run(0.1f);
        assertEquals(1, done[0], "the job done once the burn was over");
        assertNull(EvaSystem.of(e), "and back in");
        assertEquals(s.c(e).def.maxHealth, s.c(e).health, 1e-3f, "unhurt");
    }

    @Test
    void aLineRunningPastTheNozzleBurnsThroughThoughTheyAreClearOfIt() {
        ship();
        int[] done = {0};
        s.board.post(job(13, 2, 600f, done));
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        for (int i = 0; i < 6000 && !"fixing the widget on EVA".equals(s.c(e).activity); i++) s.run(0.1f);
        assertEquals("fixing the widget on EVA", s.c(e).activity, "at work under the engine, their line round its nozzle");
        assertTrue(s.crew.eva.behindEngines(e), "their line where a burn would burn through it");
        controls.forward = 1f;
        s.run(1f);
        assertTrue(events.contains(EvaEvent.Kind.TETHER_BURNED), "the line burned through: " + events);
        assertFalse(events.contains(EvaEvent.Kind.EXHAUST), "clear of it themselves");
        EvaComponent eva = EvaSystem.of(e);
        assertFalse(eva.tethered);
        assertEquals(EvaComponent.Phase.ON_HULL, eva.phase, "still on the hull");
        assertEquals(s.c(e).def.maxHealth, s.c(e).health, 1e-3f, "unhurt");
        // No line, no work: back in for a new one, round the nozzle once the burn is over
        s.run(30f);
        assertNotNull(EvaSystem.of(e), "waiting for the burn to end");
        assertEquals("waiting for the main engines' burn to end", s.c(e).activity);
        assertFalse(events.contains(EvaEvent.Kind.EXHAUST));
        controls.forward = 0f;
        for (int i = 0; i < 6000 && !events.contains(EvaEvent.Kind.BACK); i++) s.run(0.1f);
        assertTrue(events.contains(EvaEvent.Kind.BACK), "back in: " + events);
    }
}
