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
 * out at the wrecked RCS block on its underside, and the ship thrust up (+y), away from them, down
 * into them, or along the side they are on: held through, pressed on, thrown off to swing on the line, dragged
 * past too hard to catch hold, the line parted.
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
        // 4 m/s2: 720 N, past their 600 N grip; well within the line's 1.75 kN
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
    void aFullBurnSnapsTheLineAndLeavesThemAdriftTillTheShipComesBackForThem() {
        out();
        thrust.set(0f, 15f);
        float jetAtSnap = -1f;
        for (int i = 0; i < 600 && !events.contains(EvaEvent.Kind.ADRIFT); i++) {
            s.run(0.1f);
            if (jetAtSnap < 0f && events.contains(EvaEvent.Kind.TETHER_SNAPPED)) jetAtSnap = s.c(crew).suitJet;
        }
        assertTrue(events.contains(EvaEvent.Kind.LOST_GRIP), "thrown off: " + events);
        assertTrue(events.contains(EvaEvent.Kind.TETHER_SNAPPED), "the line parted: " + events);
        assertTrue(amounts.get(events.indexOf(EvaEvent.Kind.TETHER_SNAPPED)) > EvaSystem.LINE_RATED);
        assertTrue(events.contains(EvaEvent.Kind.ADRIFT), "adrift, past what their pack can make up: " + events);
        assertTrue(amounts.get(events.indexOf(EvaEvent.Kind.ADRIFT)) > 500f, "well astern by then");
        assertEquals(jetAtSnap, s.c(crew).suitJet, 0.01f, "no fuel spent chasing a ship under full burn");
        assertTrue(eva().stranded);
        s.run(0.1f); // the crew member's own reckoning of it, a tick after the event
        assertEquals("adrift, waiting to be picked up", s.c(crew).activity);
        thrust.set(0f, 0f);
        s.run(30f);
        assertTrue(s.engine.getEntities().contains(crew, true), "still in the world, adrift");
        assertFalse(events.contains(EvaEvent.Kind.LEFT_BEHIND));
        assertEquals(jetAtSnap, s.c(crew).suitJet, 0.01f, "nor drifting away from it");
        float[] range = new float[2];
        assertTrue(s.crew.eva.rangeOf(crew, range));
        assertTrue(range[0] > 1000f && range[1] < -100f, "far astern and falling further behind: " + range[0] + " m, " + range[1] + " m/s");
        // The ship comes back for them: 60 m off, matching their speed
        comeAlongside(60f);
        for (int i = 0; i < 3000 && !events.contains(EvaEvent.Kind.CAUGHT_HULL); i++) s.run(0.1f);
        assertTrue(events.contains(EvaEvent.Kind.CAUGHT_HULL), "jetted back and caught hold: " + events);
        assertTrue(amounts.get(events.indexOf(EvaEvent.Kind.CAUGHT_HULL)) > 40f, "after a while adrift");
        assertFalse(eva().stranded);
        for (int i = 0; i < 3000 && !events.contains(EvaEvent.Kind.BACK); i++) s.run(0.1f);
        assertTrue(events.contains(EvaEvent.Kind.BACK), "and back in: " + events);
    }

    /** Puts the ship {@code meters} off the crew member adrift, beside them and moving with them. */
    void comeAlongside(float meters) {
        float wx = eva().wx, wy = eva().wy;
        body.setTransform((wx + meters) / PhysicsManager.METERS_PER_UNIT, wy / PhysicsManager.METERS_PER_UNIT, body.getAngle());
        body.setLinearVelocity(eva().vx / PhysicsManager.METERS_PER_UNIT, eva().vy / PhysicsManager.METERS_PER_UNIT);
        body.setAngularVelocity(0f);
    }

    @Test
    void adriftTheirAirRunsOut() {
        out();
        thrust.set(0f, 15f);
        for (int i = 0; i < 600 && !events.contains(EvaEvent.Kind.ADRIFT); i++) s.run(0.1f);
        thrust.set(0f, 0f);
        s.c(crew).suitAir = 20f;
        for (int i = 0; i < 1200 && s.c(crew).state != CrewState.DEAD; i++) s.run(0.1f);
        assertTrue(events.contains(EvaEvent.Kind.AIR_OUT), "their air ran out: " + events);
        assertEquals(CrewState.DEAD, s.c(crew).state, "and they died of it");
        assertEquals(1, java.util.Collections.frequency(events, EvaEvent.Kind.AIR_OUT));
        s.run(10f);
        assertNotNull(eva(), "their body still out there");
        assertTrue(s.engine.getEntities().contains(crew, true));
    }

    @Test
    void outOfTheBubbleTheyAreLeftBehind() {
        out();
        thrust.set(0f, 15f);
        for (int i = 0; i < 600 && !events.contains(EvaEvent.Kind.ADRIFT); i++) s.run(0.1f);
        thrust.set(0f, 0f);
        // Fast forward: the ship far off along their way
        body.setTransform(body.getPosition().x, body.getPosition().y + (EvaSystem.LOST_RANGE + 1000f) / PhysicsManager.METERS_PER_UNIT,
                body.getAngle());
        s.run(0.5f);
        assertTrue(events.contains(EvaEvent.Kind.LEFT_BEHIND), "left behind: " + events);
        assertFalse(s.engine.getEntities().contains(crew, true), "gone from the ship's world");
    }

    @Test
    void aBurnAlongTheHullPastTheirGripKeepsThemOffItTillItEndsOnTheLine() {
        out();
        // 7 m/s2 along the underside, away from the corner the line wraps round: the hull drags past them
        // with 1,260 N, more than their grip holds, so they slide along it on the line; it holds
        thrust.set(7f, 0f);
        s.run(4f);
        assertEquals(1, java.util.Collections.frequency(events, EvaEvent.Kind.LOST_GRIP),
                "thrown off once, not caught and thrown off again: " + events);
        assertFalse(events.contains(EvaEvent.Kind.CAUGHT_HULL), "no taking hold of a hull dragging past that hard: " + events);
        assertEquals(EvaComponent.Phase.FREE, eva().phase);
        assertTrue(eva().tethered, "the line holds");
        assertTrue(eva().tension > 0.5f * 7f * EvaSystem.MASS, "and takes their weight in the burn: " + eva().tension);
        thrust.set(0f, 0f);
        for (int i = 0; i < 300 && !events.contains(EvaEvent.Kind.CAUGHT_HULL); i++) s.run(0.1f);
        assertEquals(1, java.util.Collections.frequency(events, EvaEvent.Kind.CAUGHT_HULL), "they take hold once it is over: " + events);
    }

    @Test
    void aFullBurnAlongTheHullPartsTheLine() {
        out();
        // 15 m/s2, the corvette's main engine: 2,700 N on the line, less what the hull's rub takes
        thrust.set(15f, 0f);
        for (int i = 0; i < 80 && eva().tethered; i++) s.run(0.05f);
        assertTrue(events.contains(EvaEvent.Kind.TETHER_SNAPPED), "the line parted: " + events);
        assertEquals(1, java.util.Collections.frequency(events, EvaEvent.Kind.LOST_GRIP), "thrown off: " + events);
        thrust.set(0f, 0f);
        // Parted at their clip: the loose line is left on the reel, which winds it in
        com.badlogic.gdx.utils.FloatArray rope = new com.badlogic.gdx.utils.FloatArray();
        s.crew.eva.ropeTiles(crew, rope);
        assertTrue(rope.size >= 4, "the loose line hangs from the reel");
        float out = eva().lineOut;
        s.run(2f);
        assertEquals(out - 2f * EvaSystem.TAKE_UP_SPEED, eva().lineOut, 0.1f, "being wound in");
        for (int i = 0; i < 300 && rope.size > 0; i++) {
            s.run(0.1f);
            s.crew.eva.ropeTiles(crew, rope);
        }
        assertEquals(0, rope.size, "all of it in");
        assertNotNull(eva(), "while they are still out");
    }

    @Test
    void aShortHardBurnPartsTheLineSoTheyComeBackInAndGoOutAgainOnANewOne() {
        out();
        thrust.set(0f, 15f);
        for (int i = 0; i < 100 && !events.contains(EvaEvent.Kind.TETHER_SNAPPED); i++) s.run(0.05f);
        thrust.set(0f, 0f);
        assertTrue(events.contains(EvaEvent.Kind.TETHER_SNAPPED), "the line parted: " + events);
        assertFalse(eva().tethered);
        s.run(1f);
        assertTrue(eva().returning, "untethered, they make for the airlock rather than finish the job");
        for (int i = 0; i < 3000 && !events.contains(EvaEvent.Kind.BACK); i++) s.run(0.1f);
        assertTrue(events.contains(EvaEvent.Kind.BACK), "back in: " + events);
        assertTrue(events.contains(EvaEvent.Kind.CAUGHT_HULL), "caught hold of the hull on the way: " + events);
        assertFalse(events.contains(EvaEvent.Kind.LEFT_BEHIND));
        assertFalse(EvaRepairTest.block(s).works(s.grid), "the job left for later");
        for (int i = 0; i < 6000 && !EvaRepairTest.block(s).works(s.grid); i++) s.run(0.1f);
        assertTrue(EvaRepairTest.block(s).works(s.grid), "done on a second trip out, on a new line");
        assertEquals(2, java.util.Collections.frequency(events, EvaEvent.Kind.OUT), "out twice: " + events);
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
