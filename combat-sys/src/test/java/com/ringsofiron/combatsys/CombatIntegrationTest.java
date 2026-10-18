package com.ringsofiron.combatsys;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.events.HullHitEvent;
import com.ringsofiron.combatsys.systems.BallisticsSystem;
import com.ringsofiron.combatsys.systems.ImpactResolutionSystem;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.WeightClass;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real Box2D world, real ballistics: rounds fly, hit hulls and wreck interiors. */
class CombatIntegrationTest {
    private static final float DT = PhysicsManager.TIME_STEP;
    private static final float TILE = 2f;
    // 16 m x 10 m hull matching an 8 x 5 grid of 2 m tiles.
    private static final float[] HULL = {-8, -5, 8, -5, 8, 5, -8, 5};

    private PhysicsManager physics;
    private Engine engine;
    private CombatEvents events;
    private CombatDefs defs;
    private BallisticsSystem ballistics;
    private final Array<HullHitEvent.Outcome> outcomes = new Array<>();

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        physics = new PhysicsManager();
        engine = new Engine();
        events = new CombatEvents();
        defs = CombatDefs.createDefault();
        engine.addSystem(new PhysicsUpdateSystem(physics));
        ballistics = new BallisticsSystem(physics, events);
        engine.addSystem(ballistics);
        engine.addSystem(new ImpactResolutionSystem(physics, events, defs, ballistics, MaterialTable.createDefault(), 42L));
        events.hullHits.add((signal, hit) -> outcomes.add(hit.outcome));
    }

    @AfterEach
    void tearDown() {
        engine.removeAllEntities();
        physics.dispose();
    }

    private Entity spawnShip(float x, float y, float angle, WeightClass hull) {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        ship.add(new PhysicsBodyComponent(physics.createPolygonBody(ship, x, y, angle, HULL, 50f,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL)));
        ship.add(TestShips.box(8, 5, TILE, hull));
        engine.addEntity(ship);
        return ship;
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    private void run(int ticks) {
        for (int i = 0; i < ticks; i++) engine.update(DT);
    }

    private int liveRounds() {
        return engine.getEntitiesFor(com.badlogic.ashley.core.Family.all(ProjectileComponent.class).get()).size();
    }

    @Test
    void railgunSlugGoesThroughAndPushesTheShip() {
        Entity ship = spawnShip(0, 0, 0, WeightClass.MEDIUM);
        ProjectileDef slug = defs.projectile("slug_tungsten_20kg");
        ballistics.spawn(slug, slug.mass, -600f, 0.5f, slug.muzzleSpeed, 0f, null);

        run(20);
        assertEquals(1, outcomes.size, "one hull hit");
        assertEquals(HullHitEvent.Outcome.OVER_PENETRATED, outcomes.first());
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        assertTrue(grid.getTile(0, 2).isHullBreach(), "entry hole on the port hull");
        assertTrue(grid.getTile(7, 2).isHullBreach(), "exit hole on the starboard hull");

        Vector2 v = PhysicsManager.getVelocity(body(ship), new Vector2());
        assertTrue(v.x > 0f, "momentum transferred along the shot: " + v);
        // Can't take more momentum than the slug had.
        float mass = PhysicsManager.getMass(body(ship));
        assertTrue(v.x * mass <= slug.mass * slug.muzzleSpeed * 1.001f);
        assertEquals(1, liveRounds(), "the slug flew on out the far side");
    }

    @Test
    void stoppedRoundTransfersAllItsMomentum() {
        Entity ship = spawnShip(0, 0, 0, WeightClass.HEAVY);
        ProjectileDef ac = defs.projectile("autocannon_30mm");
        ballistics.spawn(ac, ac.mass, -300f, 0.5f, ac.muzzleSpeed, 0f, null);
        run(30);
        assertEquals(HullHitEvent.Outcome.STOPPED, outcomes.first());
        float mass = PhysicsManager.getMass(body(ship));
        Vector2 v = PhysicsManager.getVelocity(body(ship), new Vector2());
        assertEquals(ac.mass * ac.muzzleSpeed / mass, v.x, 1e-4f);
        assertEquals(0, liveRounds());
    }

    @Test
    void fastCrossingShipIsStillHit() {
        // Ship crossing the line of fire at 1,000 m/s: moves ~17 m per tick, more than its own width.
        Entity ship = spawnShip(0, -200f, 0, WeightClass.LIGHT);
        PhysicsManager.setVelocity(body(ship), 0f, 1000f);
        ProjectileDef slug = defs.projectile("slug_tungsten_20kg");
        // Fired so ship and slug meet at the origin at t = 0.2 s.
        ballistics.spawn(slug, slug.mass, -slug.muzzleSpeed * 0.2f, 0f, slug.muzzleSpeed, 0f, null);
        run(30);
        assertFalse(outcomes.isEmpty(), "swept test caught the crossing hull");
    }

    @Test
    void ownerIsNotHitOnTheWayOut() {
        Entity ship = spawnShip(0, 0, 0, WeightClass.MEDIUM);
        ProjectileDef ac = defs.projectile("autocannon_30mm");
        // Spawned inside the owner's hull, flying out.
        ballistics.spawn(ac, ac.mass, 6f, 0f, ac.muzzleSpeed, 0f, ship);
        run(10);
        assertTrue(outcomes.isEmpty());
    }

    @Test
    void roundsLeaveTheBubble() {
        ballistics.setBubbleExitRadius(1000f);
        ProjectileDef slug = defs.projectile("slug_tungsten_20kg");
        ballistics.spawn(slug, slug.mass, 0f, 0f, slug.muzzleSpeed, 0f, null);
        run(5);
        assertEquals(1, liveRounds());
        run(20);
        assertEquals(0, liveRounds());
    }

    @Test
    void heShellDetonatesInsideAndSetsTheRoomAblaze() {
        Entity ship = spawnShip(0, 0, 0, WeightClass.LIGHT);
        ProjectileDef he = defs.projectile("shell_he_120mm");
        final int[] fires = {0};
        events.damage.add((signal, report) -> fires[0] += report.fires.size);
        ballistics.spawn(he, he.mass, -100f, 0.5f, he.muzzleSpeed, 0f, null);
        run(30);
        assertFalse(outcomes.isEmpty());
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        assertTrue(grid.getTile(0, 2).isHullBreach());
        // Overpressure plus fragments in a pressurized carpeted room.
        assertTrue(fires[0] > 0 || grid.getTile(1, 2).getIntegrity() < 1f, "the interior took damage");
    }
}
