package com.ringsofiron.physicsexterior;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.EntityFactory;
import com.ringsofiron.moddingdata.ModdingBootstrap;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.physicsexterior.modding.ShipPhysicsAssembler;
import com.ringsofiron.physicsexterior.systems.FlightControlSystem;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Ships spawned from data get a body of the right mass and engines that fly them. */
class ShipPhysicsAssemblerTest {
    private PhysicsManager physics;
    private GameEngine game;
    private DefRegistry defs;
    private EntityFactory factory;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        game = new GameEngine();
        physics = new PhysicsManager();
        game.addSystem(new FlightControlSystem(physics));
        game.addSystem(new PhysicsUpdateSystem(physics));
        defs = new DefRegistry();
        ModdingBootstrap.registerBuiltInTypes(defs);
        factory = new EntityFactory(game, defs);
        ShipPhysicsAssembler.register(factory, physics);
    }

    @AfterEach
    void tearDown() {
        game.clear();
        physics.dispose();
    }

    private void defineShip(String id, List<List<Double>> hull) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", id);
        fields.put("mass", 120000.0);
        fields.put("max_thrust", 2.4e6);
        fields.put("max_torque", 8.0e5);
        if (hull != null) fields.put("hull", hull);
        defs.define("ship", id, fields, "test");
    }

    @Test
    void spawnedShipHasHullMassAndEnginesAndFlies() {
        // The base corvette_patrol hull.
        defineShip("corvette", Arrays.asList(Arrays.asList(-5.0, -3.0), Arrays.asList(5.0, -3.0),
                Arrays.asList(6.5, 0.0), Arrays.asList(5.0, 3.0), Arrays.asList(-5.0, 3.0)));
        Entity ship = factory.spawnShip("corvette", 100f, -40f, 90f);

        Body body = ship.getComponent(PhysicsBodyComponent.class).body;
        assertNotNull(body);
        assertEquals(120000f, PhysicsManager.getMass(body), 1f);
        Vector2 p = PhysicsManager.getPosition(body, new Vector2());
        assertEquals(100f, p.x, 1e-3f);
        assertEquals(-40f, p.y, 1e-3f);
        assertEquals(MathUtils.HALF_PI, body.getAngle(), 1e-5f);
        ThrusterComponent thrusters = ship.getComponent(ThrusterComponent.class);
        assertEquals(2.4e6f, thrusters.forwardThrust, 1f);
        assertEquals(8.0e5f, thrusters.maxTorque, 1f);

        // Full main engine for one second: 2.4 MN / 120 t = 20 m/s along the nose (+Y here).
        ship.getComponent(FlightControlComponent.class).forward = 1f;
        game.stepTicks(60);
        Vector2 v = PhysicsManager.getVelocity(body, new Vector2());
        assertEquals(0f, v.x, 1e-2f);
        assertEquals(20f, v.y, 1e-2f);
        assertEquals(PhysicsManager.getPosition(body, p).y, ship.getComponent(TransformComponent.class).y, 1e-3f);
    }

    @Test
    void shipWithoutHullStillGetsABodyOfTheRightMass() {
        defineShip("blob", null);
        Entity ship = factory.spawnShip("blob", 0f, 0f, 0f);
        Body body = ship.getComponent(PhysicsBodyComponent.class).body;
        assertEquals(120000f, PhysicsManager.getMass(body), 1f);
        assertEquals(1, physics.getWorld().getBodyCount());
    }

    @Test
    void massSourceOverridesTheDefMass() {
        factory = new EntityFactory(game, defs);
        ShipPhysicsAssembler.register(factory, physics, (entity, def, out) -> {
            out[0] = 250_000f;
            out[1] = -1f;
            out[2] = 0f;
            out[3] = 4.0e6f;
            return true;
        });
        defineShip("gridded", null);
        Body body = factory.spawnShip("gridded", 0f, 0f, 0f).getComponent(PhysicsBodyComponent.class).body;
        assertEquals(250_000f, PhysicsManager.getMass(body), 1f);
        assertEquals(4.0e6f, PhysicsManager.getInertia(body), 10f);
        assertEquals(-1f, body.getLocalCenter().x * PhysicsManager.METERS_PER_UNIT, 1e-4f);
    }
}
