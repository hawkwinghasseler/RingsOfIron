package com.ringsofiron.combatsys;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.SignatureComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.MissileDef;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.fire.Track;
import com.ringsofiron.combatsys.systems.BallisticsSystem;
import com.ringsofiron.combatsys.systems.ImpactResolutionSystem;
import com.ringsofiron.combatsys.systems.MissileGuidanceSystem;
import com.ringsofiron.combatsys.systems.SensorTrackingSystem;
import com.ringsofiron.combatsys.systems.WeaponControlSystem;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.MaterialTable;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sensors, turrets, missiles and point defense together in a real Box2D world
 * (combat-sys/Design.md sections 3 and 4).
 */
class FireControlTest {
    private static final float DT = PhysicsManager.TIME_STEP;
    private static final float TILE = 2f;
    private static final float[] HULL = {-8, -5, 8, -5, 8, 5, -8, 5};

    private PhysicsManager physics;
    private Engine engine;
    private CombatEvents events;
    private CombatDefs defs;
    private SensorTrackingSystem sensors;
    private MissileGuidanceSystem missiles;
    private BallisticsSystem ballistics;

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
        sensors = new SensorTrackingSystem(physics, 7L);
        missiles = new MissileGuidanceSystem(physics, events, defs, ballistics, sensors);
        engine.addSystem(sensors);
        engine.addSystem(new WeaponControlSystem(physics, events, defs, ballistics, missiles, sensors, 11L));
        engine.addSystem(missiles);
        engine.addSystem(ballistics);
        engine.addSystem(new ImpactResolutionSystem(physics, events, defs, ballistics, MaterialTable.createDefault(), 42L));
    }

    @AfterEach
    void tearDown() {
        engine.removeAllEntities();
        physics.dispose();
    }

    private Entity spawnShip(float x, float y, float vx, float vy) {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        ship.add(new PhysicsBodyComponent(physics.createPolygonBody(ship, x, y, 0f, HULL, 50f,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL)));
        ship.add(TestShips.box(8, 5, TILE, WeightClass.MEDIUM));
        SensorComponent sensor = new SensorComponent();
        sensor.trackingSkill = 1f;
        ship.add(sensor);
        ship.add(new SignatureComponent(1f, 1f, 8f));
        engine.addEntity(ship);
        PhysicsManager.setVelocity(body(ship), vx, vy);
        return ship;
    }

    private WeaponMountComponent arm(Entity ship, String weapon, float x, float y, float facing) {
        WeaponsComponent weapons = ship.getComponent(WeaponsComponent.class);
        if (weapons == null) {
            weapons = new WeaponsComponent();
            ship.add(weapons);
        }
        WeaponMountComponent m = weapons.add(new WeaponMountComponent(defs.weapon(weapon), x, y, facing));
        m.gunnerySkill = 1f;
        return m;
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    private void run(float seconds) {
        int ticks = Math.round(seconds / DT);
        for (int i = 0; i < ticks; i++) engine.update(DT);
    }

    @Test
    void trackLearnsATargetsVelocity() {
        Entity ship = spawnShip(0, 0, 0, 0);
        Entity target = spawnShip(2000, 0, 0, 50);
        run(4f);
        Track track = ship.getComponent(SensorComponent.class).trackFor(target);
        assertNotNull(track, "the target is in sensor range");
        assertEquals(50f, track.v.y, 5f, "velocity estimate: " + track.v);
        assertEquals(0f, track.v.x, 5f, "velocity estimate: " + track.v);
    }

    @Test
    void turretLeadsAndHitsACrossingShip() {
        Entity ship = spawnShip(0, 0, 0, 0);
        Entity target = spawnShip(1500, 0, 0, 40);
        ship.getComponent(SensorComponent.class).lockedTarget = target;
        WeaponMountComponent gun = arm(ship, "railgun_mk1", 6f, 0f, 0f);
        int[] hits = new int[1];
        events.hullHits.add((s, hit) -> {
            if (hit.ship == target) hits[0]++;
        });
        run(6f);
        assertTrue(gun.shotsFired > 0, "the gunner opened fire");
        assertTrue(hits[0] > 0, "and led the crossing target onto the hull: " + gun.shotsFired + " shots");
    }

    @Test
    void turretHoldsFireOnATargetOutsideItsArc() {
        Entity ship = spawnShip(0, 0, 0, 0);
        Entity target = spawnShip(-1500, 0, 0, 0);
        ship.getComponent(SensorComponent.class).lockedTarget = target;
        // The railgun's 270 degree arc leaves a blind spot dead astern.
        WeaponMountComponent gun = arm(ship, "railgun_mk1", 6f, 0f, 0f);
        run(4f);
        assertEquals(0, gun.shotsFired);
        assertTrue(Math.abs(gun.aim) <= 135f * MathUtils.degreesToRadians + 1e-3f, "barrel stays in its arc: " + gun.aim);
    }

    @Test
    void missileRunsDownAManeuveringTarget() {
        Entity ship = spawnShip(0, 0, 0, 0);
        Entity target = spawnShip(3000, 0, 0, 40);
        run(1f); // a track for the datalink
        MissileDef harpoon = defs.missile("harpoon");
        Entity missile = missiles.launch(harpoon, ship, null, target, 10f, 0f, harpoon.ejectSpeed, 0f, 0f);
        boolean[] struck = new boolean[1];
        events.detonations.add((s, boom) -> struck[0] |= boom.target == target);
        events.impacts.add((s, hit) -> struck[0] |= hit.target == target && hit.owner == ship);
        for (int i = 0; i < 40 * 60 && !struck[0]; i++) {
            // Accelerate the target sideways so only a guided missile catches it.
            PhysicsManager.applyForce(body(target), 0f, 5f * PhysicsManager.getMass(body(target)));
            engine.update(DT);
        }
        assertTrue(struck[0], "the warhead went off on the target; missile phase "
                + missile.getComponent(MissileComponent.class).phase);
    }

    @Test
    void pointDefenseShootsDownAnIncomingMissile() {
        Entity attacker = spawnShip(-3000, 0, 0, 0);
        Entity defender = spawnShip(0, 0, 0, 0);
        arm(defender, "autocannon_mk1", -8f, 0f, MathUtils.PI);
        run(1f);
        MissileDef harpoon = defs.missile("harpoon");
        missiles.launch(harpoon, attacker, null, defender, -2990f, 0f, harpoon.ejectSpeed, 0f, 0f);
        boolean[] killed = new boolean[1];
        events.intercepts.add((s, hit) -> killed[0] |= hit.destroyed && hit.shooter == defender);
        for (int i = 0; i < 40 * 60 && !killed[0]; i++) engine.update(DT);
        assertTrue(killed[0], "point defense broke the missile up");
    }

    @Test
    void aBrightDecoyStealsTheSeekersLock() {
        Entity ship = spawnShip(0, 0, 0, 0);
        Entity target = spawnShip(4000, 0, 0, 0);
        Entity decoy = missiles.dropDecoy(target, 3900f, 20f, 0f, 0f, 5f, 5f, 60f);
        run(1f);
        MissileDef harpoon = defs.missile("harpoon");
        Entity missile = missiles.launch(harpoon, ship, null, target, 10f, 0f, harpoon.ejectSpeed, 0f, 0f);
        boolean lured = false;
        for (int i = 0; i < 10 * 60 && !lured; i++) {
            engine.update(DT);
            MissileComponent m = missile.getComponent(MissileComponent.class);
            lured = m != null && m.seekerLock == decoy;
        }
        assertTrue(lured, "the seeker locked the decoy, which outshines the ship");
    }

    @Test
    void segmentCircleFindsTheEntryFraction() {
        assertEquals(0.4f, BallisticsSystem.segmentCircle(-5f, 0f, 5f, 0f, 1f), 1e-5f);
        assertEquals(0f, BallisticsSystem.segmentCircle(0.5f, 0f, 5f, 0f, 1f));
        assertEquals(-1f, BallisticsSystem.segmentCircle(-5f, 2f, 5f, 2f, 1f));
        assertEquals(-1f, BallisticsSystem.segmentCircle(-5f, 0f, -3f, 0f, 1f), "stops short");
    }
}
