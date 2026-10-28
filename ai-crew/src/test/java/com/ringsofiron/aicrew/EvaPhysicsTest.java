package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Crew on EVA on a ship that moves: {@link EvaRepairTest}'s ship as a 180 t Box2D body, its engineer
 * out at the wrecked RCS block on its underside, and the ship thrust up (+y), away from them, or down
 * into them.
 */
class EvaPhysicsTest {

    final PhysicsManager physics = new PhysicsManager();
    TestShips s;
    Body body;
    Entity crew;
    /** The ship's acceleration from its "engines", m/s2. */
    final Vector2 thrust = new Vector2();
    final List<EvaEvent.Kind> events = new ArrayList<>();
    final List<Float> amounts = new ArrayList<>();

    @BeforeAll
    static void natives() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void tearDown() {
        s.engine.removeAllEntities();
        physics.dispose();
    }

    /** The ship as a body, and its engineer out at the block, working. */
    void out() {
        s = EvaRepairTest.ship();
        s.step = PhysicsManager.TIME_STEP;
        s.grid.gridOriginLocal.set(-6f, -3f);
        body = physics.createPolygonBody(s.ship, 0f, 0f, 0f, new float[]{-6f, -3f, 6f, -3f, 6f, 3f, -6f, 3f}, 2500f,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        s.ship.add(new TransformComponent());
        s.ship.add(new PhysicsBodyComponent(body));
        s.engine.addSystem(new PhysicsUpdateSystem(physics));
        physics.addStepListener(dt -> PhysicsManager.applyForce(body, thrust.x * PhysicsManager.getMass(body), thrust.y * PhysicsManager.getMass(body)));
        s.crew.eva.attach(physics);
        s.crew.eva.events.add((signal, e) -> {
            events.add(e.kind);
            amounts.add(e.amount);
        });
        crew = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        for (int i = 0; i < 3000 && !"repairing an RCS thruster on EVA".equals(s.c(crew).activity); i++) s.run(0.1f);
        assertEquals("repairing an RCS thruster on EVA", s.c(crew).activity, "out at the block");
        assertEquals(EvaComponent.Phase.ON_HULL, eva().phase);
    }

    EvaComponent eva() {
        return EvaSystem.of(crew);
    }

    /** The ship's speed, m/s. */
    float shipSpeed() {
        return PhysicsManager.getVelocity(body, new Vector2()).len();
    }

    @Test
    void aGentleBurnAwayFromThemTheyHoldOnThrough() {
        out();
        // 2 m/s2: 360 N to hold on with, short of their grip
        thrust.set(0f, 2f);
        s.run(10f);
        assertTrue(shipSpeed() > 15f, "the ship under way: " + shipSpeed());
        assertEquals(EvaComponent.Phase.ON_HULL, eva().phase, "still holding on");
        assertFalse(events.contains(EvaEvent.Kind.LOST_GRIP));
    }

    @Test
    void aHardBurnIntoThemOnlyPressesThemToTheHull() {
        out();
        thrust.set(0f, -8f);
        s.run(5f);
        assertEquals(EvaComponent.Phase.ON_HULL, eva().phase, "the hull pushes them along with it");
        assertFalse(events.contains(EvaEvent.Kind.LOST_GRIP));
    }

    @Test
    void aBurnPastTheirGripThrowsThemOffAndTheLineHoldsThemTillTheyGetBackOn() {
        out();
        // 4 m/s2: 720 N, past their 600 N grip; within the line's 2 kN
        thrust.set(0f, 4f);
        s.run(8f);
        assertTrue(events.contains(EvaEvent.Kind.LOST_GRIP), "thrown off: " + events);
        assertEquals(EvaComponent.Phase.FREE, eva().phase);
        assertTrue(eva().tethered, "the line holds");
        assertTrue(eva().reelLocked, "the reel locked as they were thrown");
        // Swinging round the hull's corner on it, a pendulum in the burn
        float most = 0f;
        for (int i = 0; i < 60; i++) {
            s.run(0.1f);
            most = Math.max(most, eva().tension);
            assertTrue(eva().tethered, "the line holds");
            assertTrue(s.p(crew).y < -0.3f, "hanging off the hull below it: " + s.p(crew).y);
        }
        assertTrue(most > 4f * EvaSystem.MASS, "the line takes their weight in the burn, and more at the bottom of each swing: " + most);
        assertEquals(EvaComponent.Phase.FREE, eva().phase);
        thrust.set(0f, 0f);
        for (int i = 0; i < 600 && eva().phase != EvaComponent.Phase.ON_HULL; i++) s.run(0.1f);
        assertEquals(EvaComponent.Phase.ON_HULL, eva().phase, "back on the hull");
        assertTrue(events.contains(EvaEvent.Kind.CAUGHT_HULL));
        assertFalse(events.contains(EvaEvent.Kind.TETHER_SNAPPED));
        assertTrue(s.c(crew).suitJet < com.ringsofiron.interiorgrid.crew.Suit.JET_DV, "on the jetpack back");
    }

    @Test
    void aFullBurnSnapsTheLineAndLeavesThemBehind() {
        out();
        thrust.set(0f, 15f);
        for (int i = 0; i < 600 && s.engine.getEntities().contains(crew, true); i++) s.run(0.1f);
        assertTrue(events.contains(EvaEvent.Kind.LOST_GRIP), "thrown off: " + events);
        assertTrue(events.contains(EvaEvent.Kind.TETHER_SNAPPED), "the line parted: " + events);
        assertTrue(amounts.get(events.indexOf(EvaEvent.Kind.TETHER_SNAPPED)) > EvaSystem.LINE_BREAK);
        assertTrue(events.contains(EvaEvent.Kind.LEFT_BEHIND), "left behind: " + events);
        assertFalse(s.engine.getEntities().contains(crew, true), "gone from the ship's world");
        assertFalse(EvaRepairTest.block(s).works(s.grid), "the work never done");
    }

    @Test
    void theFloatingOriginsShiftsCarryThemAlong() {
        out();
        thrust.set(0f, 4f);
        s.run(3f);
        assertEquals(EvaComponent.Phase.FREE, eva().phase);
        float rx = eva().rx, ry = eva().ry;
        physics.rebaseOrigin(5000f, -3000f, 40f, -25f);
        s.run(PhysicsManager.TIME_STEP);
        assertEquals(rx, eva().rx, 0.3f, "where they were relative to the ship");
        assertEquals(ry, eva().ry, 0.3f);
        assertTrue(eva().tethered, "no jolt on the line from the shift");
    }
}
