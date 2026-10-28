package com.ringsofiron.physicsexterior;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.components.UuidComponent;
import org.junit.jupiter.api.Test;

class StableBodyOrderTest {

    @Test
    void bodiesSortByTheirEntitysUuidThenWhereTheyAre() {
        GdxNativesLoader.load();
        World world = new World(new Vector2(), true);
        try {
            String[] ids = {"c", "a", "b"};
            for (String id : ids) {
                Entity e = new Entity();
                e.add(new UuidComponent(id));
                body(world, 0f, 0f).setUserData(e);
            }
            // Two with no entity, told apart by where they are
            body(world, 5f, 0f);
            body(world, 2f, 0f);
            Array<Body> bodies = new Array<>();
            world.getBodies(bodies);
            PhysicsManager.inStableOrder(bodies);
            assertEquals(2f, bodies.get(0).getPosition().x, 1e-6f);
            assertEquals(5f, bodies.get(1).getPosition().x, 1e-6f);
            String[] sorted = {"a", "b", "c"};
            for (int i = 0; i < 3; i++) {
                assertEquals(sorted[i], ((Entity) bodies.get(i + 2).getUserData()).getComponent(UuidComponent.class).id);
            }
        } finally {
            world.dispose();
        }
    }

    private static Body body(World world, float x, float y) {
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.DynamicBody;
        def.position.set(x, y);
        return world.createBody(def);
    }
}
