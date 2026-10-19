package com.ringsofiron.coreengine;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.components.UuidComponent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntityIdsTest {

    private static String id(Entity e) {
        return e.getComponent(UuidComponent.class).id;
    }

    @Test
    void aSeededEngineHandsOutTheSameUuidsEveryRun() {
        GameEngine a = new GameEngine(), b = new GameEngine(), other = new GameEngine();
        a.getEntityIds().seed(7);
        b.getEntityIds().seed(7);
        other.getEntityIds().seed(8);
        for (int i = 0; i < 5; i++) {
            String id = id(a.createEntityWithUuid());
            assertEquals(id, id(b.createEntityWithUuid()));
            assertNotEquals(id, id(other.createEntityWithUuid()));
            assertEquals(4, UUID.fromString(id).version(), "reads as an ordinary UUID");
        }
        assertNotEquals(a.newUuid(), a.newUuid(), "never the same one twice");
    }

    @Test
    void anUnseededEngineHandsOutRandomUuids() {
        GameEngine a = new GameEngine(), b = new GameEngine();
        assertNotEquals(id(a.createEntityWithUuid()), id(b.createEntityWithUuid()));
    }

    @Test
    void aLoadedGameGoesOnWithTheUuidsItWouldHaveMade() {
        GameEngine saved = new GameEngine();
        saved.getEntityIds().seed(7);
        saved.addEntity(saved.createEntityWithUuid());
        String save = saved.getSerializationService().saveGame();
        String next = saved.newUuid();

        GameEngine loaded = new GameEngine();
        loaded.getSerializationService().loadGame(save);
        assertTrue(loaded.getEntityIds().isSeeded());
        assertEquals(next, loaded.newUuid());
    }

    @Test
    void aSaveThatDoesNotSayGoesOnWithRandomUuids() {
        GameEngine loaded = new GameEngine();
        loaded.getEntityIds().seed(7);
        loaded.getSerializationService().loadGame("{\"version\":1,\"tick\":0,\"entities\":[],\"modules\":{}}");
        assertFalse(loaded.getEntityIds().isSeeded());
    }
}
