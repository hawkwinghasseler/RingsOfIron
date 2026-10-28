package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.CompartmentBuilder;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SuitOutfit;
import com.ringsofiron.interiorgrid.crew.Suit;
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
 * Boarding without a dock: a marine ordered aboard a craft standing off {@link #GAP} meters away
 * gets into an EVA suit, goes out of their own airlock with no line, over the hull, across on the
 * jetpack, round the other craft's hull to its port, forces its door from outside and goes in; and
 * with the order gone, goes back across home the same way. Both craft are Box2D bodies standing
 * still, the other one above (+y), its port on its far side.
 */
class EvaBoardingTest {
    /** Meters between the two hulls. */
    static final float GAP = 24f;
    static final int THEIRS = 1;

    final PhysicsManager physics = new PhysicsManager();
    TestShips s;
    Entity other, marine;
    ShipGridComponent otherGrid;
    final List<EvaEvent.Kind> events = new ArrayList<>();
    Job job;

    @BeforeAll
    static void natives() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void tearDown() {
        s.engine.removeAllEntities();
        physics.dispose();
    }

    /** The boarding: open while {@link #open}, the door forced after {@link #work} seconds, as {@link #how}. */
    final class Job implements BoardTask.Job {
        boolean open = true, arrived;
        float work = 5f, done;
        BoardTask.Breach how = BoardTask.Breach.OPEN;

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void arrived(Entity crew) {
            arrived = true;
        }

        @Override
        public String what() {
            return "boarding the other craft";
        }

        @Override
        public BoardTask.Breach breach(Entity crew, MountFootprint port, float dt) {
            if ((done += dt) < work) return BoardTask.Breach.WORKING;
            if (how == BoardTask.Breach.OPEN) {
                for (int y = port.originY; y < port.originY + port.height(); y++) {
                    for (int x = port.originX; x < port.originX + port.width(); x++) {
                        EdgeWall d = DockingPortLayout.door(otherGrid, port, x, y);
                        if (d != null) d.bulkheadSealed = false;
                    }
                }
            }
            return how;
        }

        @Override
        public String breaching(MountFootprint port) {
            return "cutting the airlock door";
        }
    }

    /** Ours at the origin; theirs {@link #GAP} m above it, of crew of {@link #THEIRS}, with no one aboard. */
    void ships(float gap) {
        s = EvaRepairTest.ship();
        // No wrecked block: nothing else takes them out
        s.grid.rcsThrusters.clear();
        s.grid.getTile(9, 0).setIntegrity(1f);
        s.step = PhysicsManager.TIME_STEP;
        s.grid.gridOriginLocal.set(-6f, -3f);
        body(s.ship, 0f);
        s.engine.addSystem(new PhysicsUpdateSystem(physics));
        s.crew.eva.attach(physics);
        s.crew.eva.events.add((signal, e) -> events.add(e.kind));

        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(new ChassisBlueprint(
                "############",
                "#..........#",
                "#..........#",
                "#..........#",
                "############",
                "############"));
        otherGrid = r.grid;
        // Its port on its top: the side away from ours
        otherGrid.placeMount(new MountFootprint(MountFootprint.MountKind.PORT, MountFootprint.MountSize.M, 3, 4, 0));
        assertNotNull(SuitOutfit.placeLocker(otherGrid, Suit.Kind.EVA, 9, 4, 2));
        CompartmentBuilder.rebuildIfChanged(otherGrid);
        otherGrid.gridOriginLocal.set(-6f, -3f);
        other = new Entity();
        other.add(otherGrid);
        CrewFactory.ensureBoard(other).faction = THEIRS;
        s.engine.addEntity(other);
        body(other, 6f + gap);
    }

    private Body body(Entity ship, float y) {
        Body b = physics.createPolygonBody(ship, 0f, y, 0f, new float[]{-6f, -3f, 6f, -3f, 6f, 3f, -6f, 3f}, 2500f,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        ship.add(new TransformComponent());
        ship.add(new PhysicsBodyComponent(b));
        return b;
    }

    /** The marine ordered aboard the other craft, to the middle of its room. */
    void order() {
        marine = s.spawn(CrewRole.MARINE, 0, 2, 2);
        s.c(marine).setHome(s.ship);
        job = new Job();
        assertTrue(s.board.post(new BoardTask(other, 5, 2, job, marine)));
    }

    MountFootprint theirPort() {
        return otherGrid.getTile(5, 5).mount;
    }

    float theirCabin() {
        return otherGrid.compartmentAt(5, 2).pressureRatio();
    }

    @Test
    void aMarineCrossesOnEvaForcesTheAirlockDoorAndGoesAboard() {
        ships(GAP);
        order();
        boolean suited = false, crossed = false, onTheirHull = false, breaching = false;
        int i0 = 0;
        for (int i = 0; i < 6000 && !job.arrived; i++, i0 = i) {
            s.run(0.1f);
            CrewMemberComponent c = s.c(marine);
            EvaComponent eva = EvaSystem.of(marine);
            suited |= c.suited && c.suitKind == Suit.Kind.EVA;
            if (eva != null && eva.crossing) {
                crossed = true;
                assertSame(other, s.p(marine).ship, "counted aboard the craft they cross to");
                assertFalse(eva.tethered, "no line across");
                onTheirHull |= eva.phase == EvaComponent.Phase.ON_HULL;
            }
            breaching |= "cutting the airlock door".equals(c.activity);
        }
        assertTrue(suited, "into an EVA suit");
        assertTrue(crossed && onTheirHull, "across and onto their hull");
        assertTrue(breaching, "at work on the door");
        assertTrue(job.arrived, "aboard at the spot: " + s.c(marine).activity + " " + events);
        assertSame(other, s.p(marine).ship);
        assertNull(EvaSystem.of(marine), "the EVA over, aboard");
        assertSame(s.ship, s.crew.homeOf(marine), "still working for their own ship");
        assertTrue(events.contains(EvaEvent.Kind.OUT) && events.contains(EvaEvent.Kind.CROSSING) && events.contains(EvaEvent.Kind.ABOARD),
                "out, across and aboard: " + events);
        assertFalse(events.contains(EvaEvent.Kind.LEFT_BEHIND));
        float took = i0 * 0.1f;
        assertTrue(took > 60f, "suiting up, the airlock, the crossing and the door take time: " + took + " s");
        s.run(20f);
        assertTrue(theirCabin() < 0.3f, "the door forced with the hatch open: their cabin vents " + theirCabin());

        // The boarding over, they go back across home
        job.open = false;
        for (int i = 0; i < 9000 && !(s.p(marine).ship == s.ship && EvaSystem.of(marine) == null); i++) s.run(0.1f);
        assertSame(s.ship, s.p(marine).ship, "home: " + s.c(marine).activity + " " + events);
        assertNull(EvaSystem.of(marine));
        assertTrue(events.contains(EvaEvent.Kind.BACK), "in through their own airlock: " + events);
        assertEquals(2, java.util.Collections.frequency(events, EvaEvent.Kind.CROSSING), "across and back: " + events);
    }

    @Test
    void anAirlockTakenOverCyclesThemInAndTheCabinKeepsItsAir() {
        ships(GAP);
        order();
        job.how = BoardTask.Breach.CYCLED;
        job.work = 20f;
        for (int i = 0; i < 6000 && !job.arrived; i++) s.run(0.1f);
        assertTrue(job.arrived, "aboard: " + s.c(marine).activity + " " + events);
        assertTrue(events.contains(EvaEvent.Kind.ABOARD), "in through their airlock: " + events);
        assertTrue(theirCabin() > 0.9f, "cycled in: the cabin keeps its air " + theirCabin());
    }

    @Test
    void aCraftTooFarOffIsNotCrossedTo() {
        ships(EvaSystem.CROSS_RANGE + 200f);
        order();
        s.run(60f);
        assertTrue(events.isEmpty(), "never out: " + events);
        assertSame(s.ship, s.p(marine).ship);
        assertFalse(job.arrived);
    }
}
