package com.ringsofiron.uihud.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.providers.TargetingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NavMarkerFrameTest {
    private World world;

    @BeforeEach
    void setUp() {
        GdxNativesLoader.load();
        world = new World(new Vector2(), true);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private Entity ship(float vx, float vy) {
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.DynamicBody;
        Body body = world.createBody(def);
        PhysicsManager.setVelocity(body, vx, vy);
        Entity e = new Entity();
        e.add(new PhysicsBodyComponent(body));
        return e;
    }

    private static TargetingProvider locked(Entity target) {
        return new TargetingProvider() {
            public Entity getLockedTarget() { return target; }
            public boolean getLeadPoint(Vector2 out) { return false; }
            public float getTimeToImpact() { return Float.NaN; }
        };
    }

    @Test
    void aLockedTargetWithABodySetsTheFrame() {
        Entity player = ship(0f, 7_000f), target = ship(0f, 7_000f);
        assertSame(target, NavMarkerSystem.relativeTarget(locked(target), player));
    }

    @Test
    void noTargetOrOneWithoutABodyLeavesTheOrbitFrame() {
        Entity player = ship(0f, 0f);
        assertNull(NavMarkerSystem.relativeTarget(null, player), "no fire control");
        assertNull(NavMarkerSystem.relativeTarget(locked(null), player), "nothing locked");
        assertNull(NavMarkerSystem.relativeTarget(locked(new Entity()), player), "on rails, no body to read");
        assertNull(NavMarkerSystem.relativeTarget(locked(player), player), "never relative to itself");
    }

    @Test
    void relativeProgradePointsAtTheGapClosing() {
        // Both falling along +y at orbital speed; the player gains on the target along +x
        Vector2 v = new Vector2(), tv = new Vector2();
        Entity player = ship(3f, 7_000f), target = ship(0f, 7_000f);
        PhysicsManager.getVelocity(player.getComponent(PhysicsBodyComponent.class).body, v);
        PhysicsManager.getVelocity(target.getComponent(PhysicsBodyComponent.class).body, tv);
        assertEquals(0f, NavMarkerSystem.progradeAngle(v.x - tv.x, v.y - tv.y, 0, 0), 1e-3f);
        assertTrue(Float.isNaN(NavMarkerSystem.progradeAngle(tv.x - tv.x, tv.y - tv.y, 0, 0)), "matched: no direction");
    }
}
