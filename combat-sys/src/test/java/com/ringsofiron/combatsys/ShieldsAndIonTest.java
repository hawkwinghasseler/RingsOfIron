package com.ringsofiron.combatsys;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.SignatureComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.damage.IonStun;
import com.ringsofiron.combatsys.damage.Shields;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.DamageType;
import com.ringsofiron.combatsys.defs.Fuse;
import com.ringsofiron.combatsys.defs.MissileDef;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.events.HullHitEvent;
import com.ringsofiron.combatsys.systems.BallisticsSystem;
import com.ringsofiron.combatsys.systems.ImpactResolutionSystem;
import com.ringsofiron.combatsys.systems.MissileGuidanceSystem;
import com.ringsofiron.combatsys.systems.SensorTrackingSystem;
import com.ringsofiron.combatsys.systems.WeaponControlSystem;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.PowerCircuit;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round 3's combat effects: shields that take part of a hit, ion bolts that stun, and flak that bursts. */
class ShieldsAndIonTest {
    private static final float DT = PhysicsManager.TIME_STEP;
    private static final float TILE = 2f;
    private static final float[] HULL = {-8, -5, 8, -5, 8, 5, -8, 5};

    private PhysicsManager physics;
    private Engine engine;
    private CombatEvents events;
    private CombatDefs defs;
    private BallisticsSystem ballistics;
    private MissileGuidanceSystem missiles;
    private final List<HullHitEvent> hits = new ArrayList<>();

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
        SensorTrackingSystem sensors = new SensorTrackingSystem(physics, 7L);
        missiles = new MissileGuidanceSystem(physics, events, defs, ballistics, sensors);
        engine.addSystem(sensors);
        engine.addSystem(new WeaponControlSystem(physics, events, defs, ballistics, missiles, sensors, 11L));
        engine.addSystem(missiles);
        engine.addSystem(ballistics);
        engine.addSystem(new com.ringsofiron.combatsys.shields.ShieldSystem(physics));
        engine.addSystem(new ImpactResolutionSystem(physics, events, defs, ballistics, MaterialTable.createDefault(), 42L));
        events.hullHits.add((signal, hit) -> {
            HullHitEvent copy = new HullHitEvent();
            copy.ship = hit.ship;
            copy.outcome = hit.outcome;
            copy.energyAbsorbed = hit.energyAbsorbed;
            copy.shielded = hit.shielded;
            copy.ion = hit.ion;
            hits.add(copy);
        });
    }

    @AfterEach
    void tearDown() {
        engine.removeAllEntities();
        physics.dispose();
    }

    private Entity spawnShip(float x, float y) {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        ship.add(new PhysicsBodyComponent(physics.createPolygonBody(ship, x, y, 0f, HULL, 50f,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL)));
        ship.add(TestShips.box(8, 5, TILE, WeightClass.MEDIUM));
        ship.add(new ShipSubsystemRegistryComponent(ship));
        SensorComponent sensor = new SensorComponent();
        sensor.trackingSkill = 1f;
        ship.add(sensor);
        ship.add(new SignatureComponent(1f, 1f, 8f));
        engine.addEntity(ship);
        return ship;
    }

    /** Installs a working system at (x, y) of {@code ship}, on a circuit holding {@code stored} joules. */
    private SubsystemComponent install(Entity ship, SubsystemComponent.SubsystemType type, int x, int y, double stored) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        Entity e = TestShips.install(grid, type, MountFootprint.MountSize.S, x, y);
        ship.getComponent(ShipSubsystemRegistryComponent.class).registerSubsystem(e);
        SubsystemComponent s = e.getComponent(SubsystemComponent.class);
        PowerCircuit c = new PowerCircuit();
        c.capacity = Math.max(stored, 1);
        c.stored = stored;
        s.circuit = c;
        s.powered = true;
        s.isOperational = true;
        return s;
    }

    private void run(float seconds) {
        int ticks = Math.round(seconds / DT);
        for (int i = 0; i < ticks; i++) engine.update(DT);
    }

    static float hullLeft(ShipGridComponent grid) {
        float sum = 0f;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t.wallType == Tile.WallType.HULL) sum += t.getIntegrity();
            }
        }
        return sum;
    }

    @Test
    void aShieldIsUpOnlyWhileItsEmitterWorks() {
        Entity ship = spawnShip(0, 0);
        SubsystemComponent emitter = install(ship, SubsystemComponent.SubsystemType.SHIELD_EMITTER, 3, 2, 1e9);
        run(0.1f);
        assertTrue(Shields.up(ship), "a working emitter holds its cloud");
        emitter.isOperational = false;
        run(0.1f);
        assertFalse(Shields.up(ship), "switched off, its particles drift loose");
    }

    @Test
    void anEmitterThatCannotPayToHoldItsCloudDropsIt() {
        Entity ship = spawnShip(0, 0);
        SubsystemComponent emitter = install(ship, SubsystemComponent.SubsystemType.SHIELD_EMITTER, 3, 2, 1e3);
        emitter.circuit.capacity = 1e3;
        run(0.2f);
        assertFalse(Shields.up(ship), "1 kJ doesn't hold 300 kg for long");
    }

    @Test
    void aShieldedHullTakesLessFromTheSameRound() {
        Entity bare = spawnShip(0, 0);
        Entity shielded = spawnShip(0, 200);
        install(shielded, SubsystemComponent.SubsystemType.SHIELD_EMITTER, 3, 2, 1e9);
        ProjectileDef round = defs.projectile("autocannon_30mm");
        ballistics.spawn(round, round.mass, -300f, 0.5f, round.muzzleSpeed, 0f, null);
        ballistics.spawn(round, round.mass, -300f, 200.5f, round.muzzleSpeed, 0f, null);
        run(0.4f);
        HullHitEvent onBare = hits.stream().filter(h -> h.ship == bare).findFirst().orElseThrow();
        HullHitEvent onShielded = hits.stream().filter(h -> h.ship == shielded).findFirst().orElseThrow();
        assertTrue(onShielded.energyAbsorbed < onBare.energyAbsorbed,
                "shielded hull took " + onShielded.energyAbsorbed + " J against the bare one's " + onBare.energyAbsorbed);
        assertTrue(onShielded.shielded > 0f, "the hit says the shield took part");
        assertEquals(0f, onBare.shielded);
    }

    @Test
    void anIonBoltStunsTheSystemsNearWhereItLandsAndBreaksNothing() {
        Entity ship = spawnShip(0, 0);
        // Entry is the port hull at tile (0, 2); a reactor just inside, and a turret mount across the ship
        SubsystemComponent near = install(ship, SubsystemComponent.SubsystemType.REACTOR, 1, 2, 1e7);
        SubsystemComponent far = install(ship, SubsystemComponent.SubsystemType.TURRET_MOUNT, 6, 2, 1e7);
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        float before = hullLeft(grid);
        ProjectileDef bolt = ionBolt();
        ballistics.spawn(bolt, bolt.mass, -300f, 0.5f, bolt.muzzleSpeed, 0f, null);
        run(0.3f);
        assertTrue(hits.stream().anyMatch(h -> h.ship == ship && h.ion), "an ion hit");
        assertTrue(near.stunned >= 10f && near.stunned <= 30f, "the reactor by the hit is stunned: " + near.stunned);
        assertEquals(0f, far.stunned, "the mount 10 m off is not");
        assertEquals(before, hullLeft(grid), 1e-6f, "and nothing is broken");
        assertTrue(grid.gridDirty, "power reroutes");
        assertTrue(IonStun.anyStunned(ship));
    }

    @Test
    void flakBurstsNearAMissileItWouldHaveMissed() {
        Entity attacker = spawnShip(-3000, 0);
        Entity defender = spawnShip(0, 0);
        WeaponDef flak = new WeaponDef();
        flak.id = "flak_test";
        flak.mount = WeaponDef.MountType.TURRET;
        flak.slewRateDeg = 140f;
        flak.firingArcDeg = 300f;
        flak.cooldown = 0.35f;
        flak.pointDefense = true;
        flak.maxRange = 3000f;
        // Wild aim: an autocannon this inaccurate would never touch a 1 m missile
        flak.spreadRad = 0.02f;
        ProjectileDef shell = new ProjectileDef();
        shell.id = "flak_shell_test";
        shell.mass = 1.5f;
        shell.muzzleSpeed = 1100f;
        shell.penetrationFactor = 0.3f;
        shell.fuse = Fuse.proximity(25f);
        shell.maxLifetime = 4f;
        defs.add(shell);
        flak.projectile = shell.id;
        defs.add(flak);
        WeaponsComponent weapons = new WeaponsComponent();
        WeaponMountComponent mount = weapons.add(new WeaponMountComponent(flak, -8f, 0f, MathUtils.PI));
        mount.gunnerySkill = 0f;
        defender.add(weapons);
        run(1f);
        MissileDef harpoon = defs.missile("harpoon");
        missiles.launch(harpoon, attacker, null, defender, -2990f, 0f, harpoon.ejectSpeed, 0f, 0f);
        boolean[] killed = new boolean[1];
        events.intercepts.add((s, hit) -> killed[0] |= hit.destroyed && hit.shooter == defender);
        for (int i = 0; i < 40 * 60 && !killed[0]; i++) engine.update(DT);
        assertTrue(killed[0], "a flak burst took the missile");
    }

    private ProjectileDef ionBolt() {
        ProjectileDef bolt = new ProjectileDef();
        bolt.id = "ion_bolt_test";
        bolt.mass = 0.1f;
        bolt.muzzleSpeed = 3000f;
        bolt.damageType = DamageType.ION;
        bolt.penetrationFactor = 0.5f;
        bolt.stun.radiusTiles = 2f;
        bolt.stun.minSeconds = 10f;
        bolt.stun.maxSeconds = 30f;
        defs.add(bolt);
        return bolt;
    }
}
