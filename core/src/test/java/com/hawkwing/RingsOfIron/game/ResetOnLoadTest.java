package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.GdxNativesLoader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A load starts the world's own state again from scratch through {@link GameWorld#resetForLoad()}. */
class ResetOnLoadTest {
    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    GameWorld world;

    @BeforeEach
    void setUp() {
        world = new GameWorld();
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    /** A system with a reset that is never registered keeps the last game's state over a load. */
    @Test
    void everyResettableTheWorldHoldsIsRegistered() throws IllegalAccessException {
        for (Field f : GameWorld.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !Resettable.class.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true);
            Object system = f.get(world);
            assertTrue(world.resettablesForTest().stream().anyMatch(r -> r == system),
                    f.getName() + " is Resettable but not registered with addResettable");
        }
    }

    @Test
    void resetForLoadForgetsTheLastGame() {
        world.notices.post("from the last game");
        world.guides.firstPlayStep = "DOCK";
        world.events.seed = 42;

        world.resetForLoad();

        assertTrue(world.notices.history().isEmpty());
        assertNull(world.guides.firstPlayStep);
        assertEquals(WorldEvents.DEFAULT_SEED, world.events.seed);
    }
}
