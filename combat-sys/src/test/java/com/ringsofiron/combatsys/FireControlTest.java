package com.ringsofiron.combatsys;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.ashley.core.Family;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.events.FastPassImpactEvent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.SignatureComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.MissileDef;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.fire.Track;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.events.HullHitEvent;
import com.ringsofiron.combatsys.events.ShotFiredEvent;
import com.ringsofiron.combatsys.systems.BallisticsSystem;
import com.ringsofiron.combatsys.systems.BeamSystem;
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
    private WeaponControlSystem weaponControl;

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
        weaponControl = new WeaponControlSystem(physics, events, defs, ballistics, missiles, sensors, 11L);
        engine.addSystem(weaponControl);
        engine.addSystem(missiles);
        engine.addSystem(ballistics);
        ImpactResolutionSystem impacts = new ImpactResolutionSystem(physics, events, defs, ballistics,
                MaterialTable.createDefault(), 42L);
        engine.addSystem(impacts);
        engine.addSystem(new BeamSystem(physics, impacts));
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

    private static float wrapAngle(float a) {
        return MathUtils.atan2(MathUtils.sin(a), MathUtils.cos(a));
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
    void aGunnerHoldsFireWithAShipInTheWayAndTheTriggerStillFires() {
        Entity ship = spawnShip(0, 0, 0, 0);
        Entity target = spawnShip(1500, 0, 0, 0);
        Entity wingmate = spawnShip(750, 0, 0, 0);
        ship.getComponent(SensorComponent.class).lockedTarget = target;
        WeaponMountComponent gun = arm(ship, "railgun_mk1", 6f, 0f, 0f);
        weaponControl.setHoldFire((shooter, blocker) -> blocker == wingmate);
        run(4f);
        assertEquals(0, gun.shotsFired, "a wingmate on the line of fire");
        gun.trigger = true;
        run(DT);
        assertEquals(1, gun.shotsFired, "a pulled trigger fires regardless");
        // The wingmate drifts clear and the gunner opens up
        body(wingmate).setTransform(750 * PhysicsManager.UNITS_PER_METER, 300 * PhysicsManager.UNITS_PER_METER, 0f);
        run(2f);
        assertTrue(gun.shotsFired > 1, "a clear line: " + gun.shotsFired);
    }

    @Test
    void aGunnerFiresThroughAShipTheRuleLetsPass() {
        Entity ship = spawnShip(0, 0, 0, 0);
        Entity target = spawnShip(1500, 0, 0, 0);
        spawnShip(750, 0, 0, 0);
        ship.getComponent(SensorComponent.class).lockedTarget = target;
        WeaponMountComponent gun = arm(ship, "railgun_mk1", 6f, 0f, 0f);
        weaponControl.setHoldFire((shooter, blocker) -> false);
        run(4f);
        assertTrue(gun.shotsFired > 0);
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
    void aTurretTriggeredOutOfReachTurnsOntoItsTargetBeforeItFires() {
        // The starter corvette's laser sits on its starboard beam; a pulled trigger used to burn its
        // pulse off to the side. Its 3 km reach is a guide: past it, it turns onto the target and
        // burns toward it
        Entity ship = spawnShip(0, 0, 0, 0);
        Entity target = spawnShip(5000, 0, 0, 0);
        ship.getComponent(SensorComponent.class).lockedTarget = target;
        WeaponMountComponent laser = arm(ship, "laser_mk1", -1.5f, -1.5f, -MathUtils.HALF_PI);
        for (int i = 0; i < Math.round(3f / DT) && laser.shotsFired == 0; i++) {
            laser.trigger = true;
            engine.update(DT);
        }
        assertEquals(1, laser.shotsFired, "out of its reach, the trigger still fires");
        assertTrue(Math.abs(laser.beamDir.angleRad()) < 0.01f, "at the target, dead ahead: " + laser.beamDir);
    }

    @Test
    void aGunFiringAtWillOpensUpInsideItsEffectiveRange() {
        Entity ship = spawnShip(0, 0, 0, 0);
        Entity target = spawnShip(30_000, 0, 0, 0);
        ship.getComponent(SensorComponent.class).lockedTarget = target;
        WeaponMountComponent gun = arm(ship, "railgun_mk1", 6f, 0f, 0f);
        run(4f);
        float reach = weaponControl.effectiveRange(gun, target);
        assertTrue(reach > 3000f && reach < 30_000f, "a fair chance some km out: " + reach);
        assertEquals(0, gun.shotsFired, "at 30 km the gunner holds");
        for (int i = 0; i < 4 / DT; i++) {
            gun.trigger = true;
            engine.update(DT);
        }
        assertTrue(gun.shotsFired > 0, "the trigger fires at any range");
        int pulled = gun.shotsFired;
        body(target).setTransform((reach - 500f) * PhysicsManager.UNITS_PER_METER, 0f, 0f);
        run(6f);
        assertTrue(gun.shotsFired > pulled, "inside it the gunner opens up");
    }

    @Test
    void aHandAimedGunFiresWhereThePlayerPointsIt() {
        Entity ship = spawnShip(0, 0, 0, 0);
        WeaponMountComponent gun = arm(ship, "railgun_mk1", 6f, 0f, 0f);
        float[] fired = {Float.NaN};
        float[] worst = {0f};
        events.shots.add((s, shot) -> {
            if (Float.isNaN(fired[0])) fired[0] = shot.direction.angleRad();
            // Each shot along the line from the muzzle to the spot, however the recoil turns the ship
            float want = new com.badlogic.gdx.math.Vector2(gun.aimPoint).sub(gun.muzzle).angleRad();
            worst[0] = Math.max(worst[0], Math.abs(wrapAngle(shot.direction.angleRad() - want)));
        });
        gun.manualAim = true;
        gun.aimPoint.set(6f, 1500f);
        for (int i = 0; i < Math.round(4f / DT); i++) {
            gun.trigger = true;
            engine.update(DT);
        }
        assertTrue(gun.shotsFired > 0, "it fired at the spot");
        assertEquals(MathUtils.HALF_PI, fired[0], 0.02f, "only once the barrel had swung onto it");
        assertTrue(worst[0] < 0.02f, "every shot went at the spot: off by " + worst[0]);
    }

    @Test
    void aLauncherEmptiesItsMagazineInARapidSalvoThatSpreads() {
        Entity ship = spawnShip(0, 0, 0, 0);
        Entity target = spawnShip(8000, 0, 0, 0);
        ship.getComponent(SensorComponent.class).lockedTarget = target;
        WeaponMountComponent launcher = arm(ship, "harpoon_launcher", 0f, 4f, MathUtils.HALF_PI);
        java.util.Set<Float> angles = new java.util.HashSet<>();
        events.shots.add((s, shot) -> {
            MissileComponent m = shot.round == null ? null : shot.round.getComponent(MissileComponent.class);
            if (m != null) angles.add(m.approachAngle);
        });
        for (int i = 0; i < Math.round(6f / DT); i++) {
            launcher.trigger = true;
            engine.update(DT);
        }
        assertEquals(4, launcher.shotsFired, "its four harpoons on the trigger, a second apart");
        assertEquals(0, launcher.ammo);
        assertEquals(4, angles.size(), "each from its own side: " + angles);
        for (int i = 0; i < Math.round(4f / DT); i++) {
            launcher.trigger = true;
            engine.update(DT);
        }
        assertEquals(4, launcher.shotsFired, "and nothing more from an empty launcher");
        assertEquals(1, WeaponControlSystem.restock(launcher, 1));
        assertEquals(1, launcher.ammo, "until it is restocked");
    }

    @Test
    void aMissileGivenAnApproachAngleComesInFromTheSide() {
        Entity ship = spawnShip(0, 0, 0, 0);
        Entity target = spawnShip(7000, 0, 0, 0);
        run(1f);
        MissileDef harpoon = defs.missile("harpoon");
        Entity missile = missiles.launch(harpoon, ship, null, target, 10f, 0f, harpoon.ejectSpeed, 0f, 0f);
        missile.getComponent(MissileComponent.class).approachAngle = 30f * MathUtils.degreesToRadians;
        ProjectileComponent p = missile.getComponent(ProjectileComponent.class);
        boolean[] struck = new boolean[1];
        events.detonations.add((s, boom) -> struck[0] |= boom.target == target);
        events.impacts.add((s, hit) -> struck[0] |= hit.target == target && hit.owner == ship);
        float widest = 0f;
        for (int i = 0; i < 60 * 60 && !struck[0]; i++) {
            engine.update(DT);
            if (!struck[0]) widest = Math.max(widest, p.pos.y);
        }
        assertTrue(widest > 300f, "it swung out to the left of the line: " + widest + " m");
        assertTrue(struck[0], "and still came in on the target");
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
    void pointDefenseHoldsWhenItsMissesWouldCarryOnIntoAShipItSpares() {
        // A station out past the incoming missile: rounds that miss the missile go on into it
        Entity attacker = spawnShip(-3000, 0, 0, 0);
        Entity bystander = new Entity();
        bystander.add(new PhysicsBodyComponent(physics.createPolygonBody(bystander, -3700, 0, 0f,
                new float[] {-150, -300, 150, -300, 150, 300, -150, 300}, 50f,
                CollisionCategories.CATEGORY_STATION, CollisionCategories.MASK_ALL)));
        engine.addEntity(bystander);
        Entity defender = spawnShip(0, 0, 0, 0);
        WeaponMountComponent pd = arm(defender, "autocannon_mk1", -8f, 0f, MathUtils.PI);
        weaponControl.setHoldFire((shooter, blocker) -> blocker == bystander);
        run(1f);
        MissileDef harpoon = defs.missile("harpoon");
        Entity missile = missiles.launch(harpoon, attacker, null, defender, -2990f, 0f, harpoon.ejectSpeed, 0f, 0f);
        for (int i = 0; i < 6 * 60; i++) engine.update(DT);
        assertTrue(missile.getComponent(MissileComponent.class) != null, "the missile is still coming");
        assertEquals(0, pd.shotsFired, "no burst toward the bystander");
    }

    @Test
    void pointDefenseFiresWithOnlyTheLaunchingShipBehindTheMissile() {
        Entity attacker = spawnShip(-3000, 0, 0, 0);
        Entity defender = spawnShip(0, 0, 0, 0);
        arm(defender, "autocannon_mk1", -8f, 0f, MathUtils.PI);
        // Gunners who hold for anyone in the way still shoot back toward the ship that fired at them
        weaponControl.setHoldFire((shooter, blocker) -> true);
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
    void aFastPassRamsThroughTheHull() {
        Entity ship = spawnShip(0, 0, 0, 0);
        Entity rammer = new Entity();
        engine.addEntity(rammer);
        int[] hits = new int[1];
        events.hullHits.add((s, hit) -> {
            if (hit.ship == ship && hit.ram) hits[0]++;
        });
        FastPassImpactEvent pass = new FastPassImpactEvent();
        pass.transientEntity = rammer;
        pass.target = ship;
        pass.pointX = -8f;
        pass.pointY = 0.5f;
        pass.dirX = 1f;
        pass.relativeSpeed = 3000f;
        pass.normalX = -1f;
        physics.fastPassImpacts.dispatch(pass);
        run(0.1f);
        assertEquals(1, hits[0], "the pass resolved as one hull hit, marked as a ram");
        assertTrue(ship.getComponent(ShipGridComponent.class).getTile(0, 2).isHullBreach(), "it holed the hull");
        assertEquals(0, engine.getEntitiesFor(Family.all(ProjectileComponent.class).get()).size(),
                "the rammer is not put back in flight as a round");
    }

    @Test
    void segmentCircleFindsTheEntryFraction() {
        assertEquals(0.4f, BallisticsSystem.segmentCircle(-5f, 0f, 5f, 0f, 1f), 1e-5f);
        assertEquals(0f, BallisticsSystem.segmentCircle(0.5f, 0f, 5f, 0f, 1f));
        assertEquals(-1f, BallisticsSystem.segmentCircle(-5f, 2f, 5f, 2f, 1f));
        assertEquals(-1f, BallisticsSystem.segmentCircle(-5f, 0f, -3f, 0f, 1f), "stops short");
    }

    @Test
    void aLaserBurnsIntoTheTargetsHull() {
        Entity ship = spawnShip(0, 0, 0, 0);
        Entity target = spawnShip(1000, 0, 0, 0);
        ship.getComponent(SensorComponent.class).lockedTarget = target;
        WeaponMountComponent laser = arm(ship, "laser_mk1", 6f, 0f, 0f);
        java.util.List<HullHitEvent.Outcome> hits = new java.util.ArrayList<>();
        events.hullHits.add((s, hit) -> {
            if (hit.ship == target && hit.beam) hits.add(hit.outcome);
        });
        boolean burned = false;
        for (int i = 0; i < 4 * 60; i++) {
            engine.update(DT);
            burned |= laser.beamOn && laser.beamContact == target;
        }
        assertEquals(1, laser.shotsFired, "one pulse");
        assertTrue(burned, "the beam touched the target's hull");
        assertEquals(1000f - 8f, laser.beamEnd.x, 1f, "and stopped at it");
        // 4 MW for 2.5 s is 10 MJ: more than one medium steel tile (8 MJ) holds, if the spot holds still.
        assertTrue(hits.contains(HullHitEvent.Outcome.PENETRATED), "it burned through: " + hits);
    }

    @Test
    void gunnersWalkABurstOntoTheTarget() {
        // A dozen green gunners on fast guns at one stationary target: their first shots scatter
        // with each one's opening bias; by the tenth shot each has walked it in.
        WeaponDef gun = new WeaponDef();
        gun.id = "test_gun";
        gun.projectile = "autocannon_30mm";
        gun.cooldown = 0.1f;
        gun.slewRateDeg = 360f;
        gun.spreadRad = 0.003f;
        defs.add(gun);
        Entity ship = spawnShip(0, 0, 0, 0);
        Entity target = spawnShip(2000, 0, 0, 0);
        ship.getComponent(SensorComponent.class).lockedTarget = target;
        int guns = 12;
        java.util.Map<WeaponMountComponent, java.util.List<Float>> errors = new java.util.HashMap<>();
        for (int i = 0; i < guns; i++) {
            WeaponMountComponent m = arm(ship, "test_gun", 7f, 0f, 0f);
            m.gunnerySkill = 0f;
            errors.put(m, new java.util.ArrayList<>());
        }
        events.shots.add((s, shot) -> {
            for (WeaponMountComponent m : ship.getComponent(WeaponsComponent.class).mounts) {
                if (m.shotsFired == errors.get(m).size() + 1 && m.muzzle.dst2(shot.muzzlePoint) < 9f) {
                    float bearing = (float) Math.atan2(-shot.muzzlePoint.y, 2000f - shot.muzzlePoint.x);
                    errors.get(m).add(Math.abs(shot.direction.angleRad() - bearing));
                    return;
                }
            }
        });
        run(4f);
        float first = 0f, tenth = 0f;
        for (java.util.List<Float> e : errors.values()) {
            assertTrue(e.size() >= 10, "each gun fired a burst: " + e.size());
            first += e.get(0) / guns;
            tenth += e.get(9) / guns;
        }
        assertTrue(tenth < 0.5f * first, "walked in: first shots off by " + first + " rad, tenth by " + tenth);
    }

    @Test
    void gunsFireOnlyOnThePowerTheyGet() {
        Entity ship = spawnShip(0, 0, 0, 0);
        Entity target = spawnShip(1500, 0, 0, 0);
        ship.getComponent(SensorComponent.class).lockedTarget = target;
        WeaponMountComponent gun = arm(ship, "railgun_mk1", 6f, 0f, 0f);
        WeaponMountComponent laser = arm(ship, "laser_mk1", 6f, 1f, 0f);
        double shot = (double) gun.def.powerDraw * gun.def.cooldown;
        // A store holding two railgun shots and no recharge; the laser gets half of what it asks.
        double[] store = {2.5 * shot};
        weaponControl.setPower(new com.ringsofiron.combatsys.WeaponPower() {
            @Override
            public boolean tryDraw(WeaponMountComponent mount, double joules) {
                if (store[0] < joules) return false;
                store[0] -= joules;
                return true;
            }

            @Override
            public double draw(WeaponMountComponent mount, double joules) {
                return 0.5 * joules;
            }
        });
        float[] burned = new float[1];
        events.hullHits.add((s, hit) -> {
            if (hit.beam) burned[0] += hit.energyAbsorbed;
        });
        run(10f);
        assertEquals(2, gun.shotsFired, "two shots' worth of energy, two shots");
        assertTrue(laser.shotsFired > 0);
        assertEquals(0.5f, laser.beamStrength, 1e-6f, "the laser burns at the power it gets");
    }

    @Test
    void aLightRammerHitsSofterThanAHeavyOne() {
        boolean[] holed = new boolean[2];
        float[] mass = {1f, 0f}; // a 1 kg rammer, then one of unknown mass (as heavy as the ship)
        for (int i = 0; i < 2; i++) {
            Entity ship = spawnShip(i * 100f, 0, 0, 0);
            Entity rammer = new Entity();
            engine.addEntity(rammer);
            FastPassImpactEvent pass = new FastPassImpactEvent();
            pass.transientEntity = rammer;
            pass.target = ship;
            pass.pointX = i * 100f - 8f;
            pass.pointY = 0.5f;
            pass.dirX = 1f;
            pass.relativeSpeed = 3000f;
            pass.normalX = -1f;
            pass.transientMass = mass[i];
            physics.fastPassImpacts.dispatch(pass);
            run(0.1f);
            holed[i] = ship.getComponent(ShipGridComponent.class).getTile(0, 2).isHullBreach();
        }
        // 1 kg at 3 km/s is 4.5 MJ, half of it a penetrator: less than a medium hull tile's 8 MJ.
        assertTrue(!holed[0], "the medium hull stops a 1 kg rammer");
        assertTrue(holed[1], "but not one as heavy as the ship");
    }
}
