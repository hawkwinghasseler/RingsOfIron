package com.ringsofiron.coreengine;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;
import com.ringsofiron.coreengine.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameEngineClockTest {
    private static final float FRAME = 1f / 60f;

    static class CountingFixed extends FixedLogicSystem {
        int runs;
        float lastDelta;
        long catchUpTicks;
        double catchUpSeconds;
        final List<String> log;
        final String name;

        CountingFixed(int priority, float step, String name, List<String> log) {
            super(priority, step);
            this.name = name;
            this.log = log;
        }

        CountingFixed(float step) {
            this(0, step, "", null);
        }

        @Override
        protected void updateFixed(float deltaTime) {
            runs++;
            lastDelta = deltaTime;
            if (log != null) log.add(name);
        }

        SimClock getClockForTest() {
            return getClock();
        }

        @Override
        protected void catchUp(long ticks, double seconds) {
            catchUpTicks += ticks;
            catchUpSeconds += seconds;
        }
    }

    static class CountingFrame extends EntitySystem {
        int runs;

        @Override
        public void update(float deltaTime) {
            runs++;
        }
    }

    @Test
    void oneTickPerSixtiethOfASecond() {
        GameEngine engine = new GameEngine();
        CountingFixed fixed = new CountingFixed(1f / 60f);
        CountingFrame frame = new CountingFrame();
        engine.addSystem(fixed);
        engine.addSystem(frame);

        for (int i = 0; i < 600; i++) engine.update(FRAME);

        assertEquals(600, engine.getTick());
        assertEquals(10.0, engine.getSimSeconds(), 1e-9);
        assertEquals(600, fixed.runs);
        assertEquals(600, frame.runs, "every-frame systems run once per frame, not per tick");
    }

    @Test
    void slowFramesRunSeveralTicksButFrameSystemsOnce() {
        GameEngine engine = new GameEngine();
        CountingFixed fixed = new CountingFixed(1f / 60f);
        CountingFrame frame = new CountingFrame();
        engine.addSystem(fixed);
        engine.addSystem(frame);

        for (int i = 0; i < 30; i++) engine.update(1f / 30f);

        assertEquals(60, engine.getTick(), 1);
        assertEquals(engine.getTick(), fixed.runs);
        assertEquals(30, frame.runs);
    }

    @Test
    void timeStepRoundsToWholeTicks() {
        GameEngine engine = new GameEngine();
        // Ashley keeps one system per class, so each needs its own subclass.
        CountingFixed hazards = new CountingFixed(0.25f) { };
        CountingFixed power = new CountingFixed(1f) { };
        engine.addSystem(hazards);
        engine.addSystem(power);

        assertEquals(15, hazards.getTickInterval());
        assertEquals(60, power.getTickInterval());

        engine.stepTicks(600);

        assertEquals(40, hazards.runs);
        assertEquals(10, power.runs);
        assertEquals(0.25f, hazards.lastDelta, 1e-6f);
        assertEquals(1f, power.lastDelta, 1e-6f);
    }

    @Test
    void fixedSystemsRunInPriorityOrder() {
        GameEngine engine = new GameEngine();
        List<String> log = new ArrayList<>();
        engine.addSystem(new CountingFixed(20, 1f / 60f, "late", log) { });
        engine.addSystem(new CountingFixed(5, 1f / 60f, "early", log) { });

        engine.stepTicks(2);

        assertEquals(List.of("early", "late", "early", "late"), log);
    }

    @Test
    void physicsWarpRunsMoreTicksPerFrame() {
        GameEngine engine = new GameEngine();
        CountingFixed fixed = new CountingFixed(1f / 60f);
        engine.addSystem(fixed);
        engine.setPhysicsWarp(10);

        for (int i = 0; i < 60; i++) engine.update(FRAME);

        assertEquals(600, engine.getTick(), 1);
        assertThrows(IllegalArgumentException.class, () -> engine.setPhysicsWarp(11));
        assertThrows(IllegalArgumentException.class, () -> engine.setPhysicsWarp(0));
    }

    @Test
    void longStallIsCappedInsteadOfSpiralling() {
        GameEngine engine = new GameEngine();
        engine.addSystem(new CountingFixed(1f / 60f));

        engine.update(5f);

        assertEquals(MAX_TICKS(), engine.getTick());
        assertTrue(engine.getDroppedTicks() >= 0);
    }

    private static long MAX_TICKS() {
        return GameEngine.MAX_TICKS_PER_FRAME_AT_1X;
    }

    @Test
    void railsWarpJumpsTicksWithOneCatchUp() {
        GameEngine engine = new GameEngine();
        CountingFixed fixed = new CountingFixed(0.25f);
        engine.addSystem(fixed);
        engine.stepTicks(60);
        int runsBefore = fixed.runs;

        long day = 24L * 3600 * SimClock.TICKS_PER_SECOND;
        assertTrue(engine.advanceRails(day));

        assertEquals(60 + day, engine.getTick());
        assertEquals(runsBefore, fixed.runs, "skipped ticks are not run one by one");
        assertEquals(day, fixed.catchUpTicks);
        assertEquals(86400.0, fixed.catchUpSeconds, 1e-6);

        // The schedule carries on from the new tick.
        engine.stepTicks(15);
        assertEquals(runsBefore + 1, fixed.runs);
    }

    @Test
    void guardRefusesRailsWarp() {
        GameEngine engine = new GameEngine();
        CountingFixed fixed = new CountingFixed(1f / 60f);
        engine.addSystem(fixed);
        boolean[] fire = {true};
        engine.addRailsWarpGuard(() -> fire[0] ? "Fire aboard" : null);

        assertFalse(engine.advanceRails(1000));
        assertEquals(0, engine.getTick());
        assertEquals("Fire aboard", engine.getRailsWarpBlockedReason());

        fire[0] = false;
        assertTrue(engine.advanceRails(1000));
        assertEquals(1000, engine.getTick());
    }

    @Test
    void railsRequestedDuringAFrameAppliesAfterIt() {
        GameEngine engine = new GameEngine();
        CountingFixed fixed = new CountingFixed(1f / 60f);
        engine.addSystem(fixed);
        EntitySystem warpController = new EntitySystem() {
            boolean done;

            @Override
            public void update(float deltaTime) {
                if (!done) done = engine.advanceRails(3600);
            }
        };
        engine.addSystem(warpController);

        engine.update(FRAME);

        assertEquals(1 + 3600, engine.getTick());
        assertEquals(3600, fixed.catchUpTicks);
    }

    @Test
    void entityRemovedDuringTickIsHandledLikeAshley() {
        GameEngine engine = new GameEngine();
        Entity doomed = engine.createEntityWithUuid();
        engine.addEntity(doomed);
        String id = EntityRef.of(doomed).getUuid();
        engine.addSystem(new FixedLogicSystem(1f / 60f) {
            @Override
            protected void updateFixed(float deltaTime) {
                if (getEngine().getEntities().size() > 0) getEngine().removeEntity(doomed);
            }
        });

        engine.stepTicks(1);

        assertEquals(0, engine.getEcsEngine().getEntities().size());
        assertNull(engine.getEntity(id));
    }

    @Test
    void renderSystemGetsAlpha() {
        GameEngine engine = new GameEngine();
        float[] seen = {-1f};
        engine.addSystem(new RenderSystem() {
            @Override
            protected void render(float deltaTime, float alpha) {
                seen[0] = alpha;
            }
        });

        engine.update(1.5f / 60f);

        assertEquals(1, engine.getTick());
        assertEquals(0.5f, seen[0], 1e-3f);
    }

    @Test
    void fixedSystemStillSelfAccumulatesInPlainAshleyEngine() {
        Engine plain = new Engine();
        CountingFixed fixed = new CountingFixed(1f / 60f);
        plain.addSystem(fixed);

        for (int i = 0; i < 120; i++) plain.update(FRAME);

        assertEquals(120, fixed.runs);
        assertNull(fixed.getClockForTest());
    }
}
