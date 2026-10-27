package com.ringsofiron.combatsys;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.DamageType;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.events.CombatEvents;
import com.ringsofiron.combatsys.events.HullHitEvent;
import com.ringsofiron.combatsys.systems.BallisticsSystem;
import com.ringsofiron.combatsys.systems.ImpactResolutionSystem;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.MaterialTable;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.PowerCircuit;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A shield soaking an ion bolt: by the share of the bolt it took, the bolt stuns nothing. */
class IonShieldSoakTest {
    private static final float DT = PhysicsManager.TIME_STEP;
    private static final float TILE = 2f;
    private static final float[] HULL = {-8, -5, 8, -5, 8, 5, -8, 5};
    private static final int BOLTS = 40;

    private PhysicsManager physics;
    private Engine engine;
    private CombatDefs defs;
    private BallisticsSystem ballistics;
    private final List<HullHitEvent> hits = new ArrayList<>();

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        physics = new PhysicsManager();
        engine = new Engine();
        CombatEvents events = new CombatEvents();
        defs = CombatDefs.createDefault();
        engine.addSystem(new PhysicsUpdateSystem(physics));
        ballistics = new BallisticsSystem(physics, events);
        engine.addSystem(ballistics);
        engine.addSystem(new com.ringsofiron.combatsys.shields.ShieldSystem(physics));
        engine.addSystem(new ImpactResolutionSystem(physics, events, defs, ballistics, MaterialTable.createDefault(), 42L));
        events.hullHits.add((signal, hit) -> {
            HullHitEvent copy = new HullHitEvent();
            copy.ship = hit.ship;
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

    private Entity spawnShip() {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        ship.add(new PhysicsBodyComponent(physics.createPolygonBody(ship, 0f, 0f, 0f, HULL, 50f,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL)));
        ship.add(TestShips.box(8, 5, TILE, WeightClass.MEDIUM));
        ship.add(new ShipSubsystemRegistryComponent(ship));
        engine.addEntity(ship);
        return ship;
    }

    private SubsystemComponent install(Entity ship, SubsystemComponent.SubsystemType type, int x, int y) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        Entity e = TestShips.install(grid, type, MountFootprint.MountSize.S, x, y);
        ship.getComponent(ShipSubsystemRegistryComponent.class).registerSubsystem(e);
        SubsystemComponent s = e.getComponent(SubsystemComponent.class);
        PowerCircuit c = new PowerCircuit();
        c.capacity = 1e9;
        c.stored = 1e9;
        s.circuit = c;
        s.powered = true;
        s.isOperational = true;
        return s;
    }

    private void run(float seconds) {
        int ticks = Math.round(seconds / DT);
        for (int i = 0; i < ticks; i++) engine.update(DT);
    }

    @Test
    void aShieldedShipShrugsOffSomeIonBoltsByTheShareItTakes() {
        Entity ship = spawnShip();
        // The bolt lands on the port hull by tile (0, 2): a reactor just inside, two emitters well clear of it
        SubsystemComponent reactor = install(ship, SubsystemComponent.SubsystemType.REACTOR, 1, 2);
        install(ship, SubsystemComponent.SubsystemType.SHIELD_EMITTER, 6, 1);
        install(ship, SubsystemComponent.SubsystemType.SHIELD_EMITTER, 6, 3);
        ProjectileDef bolt = ionBolt();
        Body body = ship.getComponent(PhysicsBodyComponent.class).body;
        int stunned = 0;
        for (int i = 0; i < BOLTS; i++) {
            body.setTransform(0f, 0f, 0f);
            body.setLinearVelocity(0f, 0f);
            body.setAngularVelocity(0f);
            reactor.stunned = 0f;
            int before = hits.size();
            // From 3 km the emitters see each bolt a second out and gather a region in its path
            ballistics.spawn(bolt, bolt.mass, -3000f, 0.5f, bolt.muzzleSpeed, 0f, null);
            run(1.1f);
            assertEquals(before + 1, hits.size(), "bolt " + i + " hit");
            HullHitEvent hit = hits.get(before);
            assertTrue(hit.ion && hit.shielded > 0f, "the shields took part of bolt " + i);
            if (reactor.stunned > 0f) stunned++;
        }
        // A gathered region takes most of each bolt, so about three in four are soaked
        int soaked = BOLTS - stunned;
        assertTrue(stunned > 0, "some bolts get through and stun");
        assertTrue(soaked > stunned, soaked + " soaked, " + stunned + " stunned");
        assertEquals(0.75, soaked / (double) BOLTS, 0.2, soaked + " of " + BOLTS + " soaked");
    }

    private ProjectileDef ionBolt() {
        ProjectileDef bolt = new ProjectileDef();
        bolt.id = "ion_bolt_soak_test";
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
