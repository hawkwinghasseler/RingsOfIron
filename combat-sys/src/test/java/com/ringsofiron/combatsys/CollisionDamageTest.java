package com.ringsofiron.combatsys;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.events.DamageReport;
import com.ringsofiron.combatsys.events.HullHitEvent;
import com.ringsofiron.combatsys.systems.BallisticsSystem;
import com.ringsofiron.combatsys.systems.CollisionDamageSystem;
import com.ringsofiron.combatsys.systems.ImpactResolutionSystem;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.HullShape;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.WeightClass;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hull on hull (section 5.12): two corvette-sized ships, 16 x 6 tiles of 1 m and 195 t like the
 * starter corvette, flown nose to nose in a real Box2D world.
 */
class CollisionDamageTest {
    private static final float DT = PhysicsManager.TIME_STEP;
    private static final float CORVETTE_MASS = 195_100f;

    private PhysicsManager physics;
    private Engine engine;
    private CombatEvents events;
    private CollisionDamageSystem collisions;
    private final Array<HullHitEvent> hits = new Array<>();
    private final Array<DamageReport> reports = new Array<>();

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        physics = new PhysicsManager();
        engine = new Engine();
        events = new CombatEvents();
        engine.addSystem(new PhysicsUpdateSystem(physics));
        BallisticsSystem ballistics = new BallisticsSystem(physics, events);
        engine.addSystem(ballistics);
        ImpactResolutionSystem impacts = new ImpactResolutionSystem(physics, events, CombatDefs.createDefault(), ballistics,
                MaterialTable.createDefault(), 42L);
        engine.addSystem(impacts);
        collisions = new CollisionDamageSystem(physics, events, impacts);
        engine.addSystem(collisions);
        events.hullHits.add((signal, hit) -> {
            HullHitEvent copy = new HullHitEvent();
            copy.ship = hit.ship;
            copy.ram = hit.ram;
            copy.outcome = hit.outcome;
            hits.add(copy);
        });
        events.damage.add((signal, r) -> {
            DamageReport copy = new DamageReport();
            copy.ship = r.ship;
            copy.breaches.addAll(r.breaches);
            copy.crushed.addAll(r.crushed);
            reports.add(copy);
        });
    }

    @AfterEach
    void tearDown() {
        engine.removeAllEntities();
        physics.dispose();
    }

    private Entity corvette(float x, float angle, float vx) {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        ShipGridComponent grid = TestShips.box(16, 6, 1f, WeightClass.MEDIUM);
        Body body = physics.createPolygonBody(ship, x, 0f, angle, HullShape.pieces(grid), 1f,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        PhysicsManager.setMassData(body, CORVETTE_MASS, 0f, 0f, CORVETTE_MASS * (16 * 16 + 6 * 6) / 12f);
        PhysicsManager.setVelocity(body, vx, 0f);
        ship.add(new PhysicsBodyComponent(body));
        ship.add(grid);
        engine.addEntity(ship);
        return ship;
    }

    /** Flies two corvettes nose to nose, closing at {@code speed}, for two seconds. */
    private Entity[] headOn(float speed) {
        Entity a = corvette(-12f, 0f, speed / 2f);
        Entity b = corvette(12f, (float) Math.PI, -speed / 2f);
        for (int i = 0; i < 120; i++) engine.update(DT);
        return new Entity[]{a, b};
    }

    private static ShipGridComponent grid(Entity e) {
        return e.getComponent(ShipGridComponent.class);
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    private static int damaged(ShipGridComponent g) {
        int n = 0;
        for (int y = 0; y < g.height; y++) for (int x = 0; x < g.width; x++) if (g.getTile(x, y).getIntegrity() < 1f) n++;
        return n;
    }

    private static int crushed(ShipGridComponent g) {
        int n = 0;
        for (int y = 0; y < g.height; y++) for (int x = 0; x < g.width; x++) if (g.getTile(x, y).isCrushed()) n++;
        return n;
    }

    private static float worst(ShipGridComponent g) {
        float w = 1f;
        for (int y = 0; y < g.height; y++) for (int x = 0; x < g.width; x++) w = Math.min(w, g.getTile(x, y).getIntegrity());
        return 1f - w;
    }

    @Test
    void underTwentyFiveMetresASecondHullsJustBump() {
        Entity[] s = headOn(24f);
        for (Entity e : s) {
            assertEquals(0, damaged(grid(e)), "no tile scratched");
            assertTrue(grid(e).dents == null, "no dent");
        }
        assertTrue(hits.isEmpty());
        // They bounced apart, as Box2D does
        assertTrue(PhysicsManager.getVelocity(body(s[0]), new Vector2()).x < 0f);
    }

    @Test
    void atFiftyTheDamageIsLightButSpreadsOverSeveralTiles() {
        Entity[] s = headOn(50f);
        for (Entity e : s) {
            ShipGridComponent g = grid(e);
            assertTrue(damaged(g) >= 5, "several tiles round where they met: " + damaged(g));
            assertEquals(0, crushed(g), "nothing crushed");
            float worst = worst(g);
            assertTrue(worst > 0.15f && worst < Tile.LEAK_START, "light: the worst tile lost " + worst);
            assertTrue(g.dents != null, "the hull art shows a dent");
        }
        assertEquals(2, hits.size, "one hull hit on each");
        assertTrue(hits.first().ram);
        for (DamageReport r : reports) assertEquals(0, r.breaches.size, "no breach");
        assertEquals(collisions.last.worstA, collisions.last.worstB, 1e-3f, "same hulls, same damage");
    }

    @Test
    void atAHundredTilesCrushAndTheHullsOverlapWhereTheyWere() {
        Entity[] s = headOn(100f);
        for (Entity e : s) {
            ShipGridComponent g = grid(e);
            assertTrue(crushed(g) >= 2, "a few tiles crushed: " + crushed(g));
            assertTrue(damaged(g) >= 8, "and more round them dented: " + damaged(g));
            assertFalse(g.hullShapeDirty, "the hull was reshaped");
        }
        // Where the bows met no longer collides: the other ship's nose sits inside the old hull line
        for (Entity e : s) {
            Vector2 bow = body(e).getWorldPoint(new Vector2(7.5f, 0.5f).scl(PhysicsManager.UNITS_PER_METER));
            for (Fixture f : body(e).getFixtureList()) assertFalse(f.testPoint(bow), "the crushed bow is not solid");
            Vector2 aft = body(e).getWorldPoint(new Vector2(-7.5f, 0.5f).scl(PhysicsManager.UNITS_PER_METER));
            boolean solid = false;
            for (Fixture f : body(e).getFixtureList()) solid |= f.testPoint(aft);
            assertTrue(solid, "the stern still is");
        }
        boolean breached = false;
        for (DamageReport r : reports) breached |= r.crushed.size > 0 && r.breaches.size >= r.crushed.size;
        assertTrue(breached, "crushed tiles are open to space");
    }

    @Test
    void harderCrashesDoMoreDamage() {
        float last = 0f;
        for (float speed : new float[]{30f, 50f, 100f, 200f}) {
            tearDown();
            hits.clear();
            reports.clear();
            setUp();
            Entity[] s = headOn(speed);
            ShipGridComponent g = grid(s[0]);
            float total = 0f;
            for (int y = 0; y < g.height; y++) for (int x = 0; x < g.width; x++) total += 1f - g.getTile(x, y).getIntegrity();
            assertTrue(total > last, speed + " m/s did more than the speed before: " + total + " vs " + last);
            last = total;
            System.out.printf("%.0f m/s: damaged %d, crushed %d, worst %.2f, lost %.1f tiles%n", speed, damaged(g),
                    crushed(g), worst(g), total);
        }
    }

    @Test
    void theCrashTradesMomentumAndKeepsIt() {
        Entity a = corvette(-12f, 0f, 50f);
        Entity b = corvette(12f, (float) Math.PI, 0f);
        float before = CORVETTE_MASS * 50f;
        for (int i = 0; i < 120; i++) engine.update(DT);
        float after = PhysicsManager.getMass(body(a)) * PhysicsManager.getVelocity(body(a), new Vector2()).x
                + PhysicsManager.getMass(body(b)) * PhysicsManager.getVelocity(body(b), new Vector2()).x;
        assertEquals(before, after, before * 0.01f, "momentum kept");
        assertTrue(PhysicsManager.getVelocity(body(b), new Vector2()).x > 20f, "the struck ship was shoved away");
        assertEquals(CORVETTE_MASS, PhysicsManager.getMass(body(a)), 1f, "reshaping keeps the mass");
    }

    @Test
    void aHullWithCrushedTilesCollidesWithoutThem() {
        ShipGridComponent g = TestShips.box(4, 3, 1f, WeightClass.MEDIUM);
        List<float[]> whole = HullShape.pieces(g);
        g.getTile(3, 1).setIntegrity(0f);
        g.getTile(3, 1).setCrushed(true);
        List<float[]> pieces = HullShape.pieces(g);
        float area = 0f;
        for (float[] p : pieces) area += (p[2] - p[0]) * (p[5] - p[1]);
        assertEquals(11f, area, 1e-4f, "the crushed tile is left out");
        assertEquals(1, whole.size());
        assertTrue(g.getTile(3, 1).isHullBreach() && !g.getTile(3, 1).isOpen());
    }
}
