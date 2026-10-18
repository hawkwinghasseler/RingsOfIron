package com.ringsofiron.aicrew.pilot;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.physicsexterior.systems.FlightControlSystem;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The pilot flying a real Box2D body through physics-exterior's flight systems. */
class NpcPilotSystemTest {

    private static final float DT = PhysicsManager.TIME_STEP;
    private static final float[] BOX = {-10, -5, 10, -5, 10, 5, -10, 5};

    private final PhysicsManager physics = new PhysicsManager();
    private final Engine engine = new Engine();
    private double time;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void tearDown() {
        engine.removeAllEntities();
        physics.dispose();
    }

    private Entity freighter(float x, float y, float vx, float vy) {
        engine.addSystem(new NpcPilotSystem(PilotReference.bubbleOnly(() -> time)));
        engine.addSystem(new FlightControlSystem(physics));
        engine.addSystem(new PhysicsUpdateSystem(physics));
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        ship.add(new PhysicsBodyComponent(physics.createPolygonBody(ship, x, y, 0f, BOX, 10f,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL)));
        PhysicsManager.setVelocity(ship.getComponent(PhysicsBodyComponent.class).body, vx, vy);
        // 2,000 kg: 5 m/s² main engine, 2 m/s² retro, 1 m/s² lateral
        ship.add(new ThrusterComponent(10_000f, 4_000f, 2_000f, 200_000f));
        ship.add(new FlightControlComponent());
        engine.addEntity(ship);
        return ship;
    }

    private void run(double seconds) {
        for (double end = time + seconds; time < end; time += DT) engine.update(DT);
    }

    @Test
    void fliesItsBurnThenLeavesTheBubble() {
        Entity ship = freighter(3000f, 0f, 0f, 50f);
        NpcPilotComponent pilot = new NpcPilotComponent();
        pilot.burns.add(new PilotBurn(10, 30, 0));
        ship.add(pilot);
        run(9);
        Vector2 v = PhysicsManager.getVelocity(ship.getComponent(PhysicsBodyComponent.class).body, new Vector2());
        assertEquals(50f, v.len(), 0.5f, "coasts until the burn");
        run(20);
        assertTrue(pilot.burns.isEmpty(), "burn flown");
        run(60);
        Vector2 p = PhysicsManager.getPosition(ship.getComponent(PhysicsBodyComponent.class).body, new Vector2());
        PhysicsManager.getVelocity(ship.getComponent(PhysicsBodyComponent.class).body, v);
        assertTrue(v.dot(p.nor()) >= pilot.departSpeed - 2f, "heading out of the bubble at " + v.dot(p));
    }
}
