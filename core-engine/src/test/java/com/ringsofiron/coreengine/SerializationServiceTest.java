package com.ringsofiron.coreengine;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SerializationException;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.components.TransientComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import org.junit.jupiter.api.Test;

class SerializationServiceTest {
    public static class TargetComponent implements Component {
        public EntityRef target;
        public Array<EntityRef> wingmen = new Array<>();
        public String label;
    }

    public static class RuntimeHandleComponent implements Component {
        public Object nativeHandle = new Object();
    }

    public static class BadComponent implements Component {
        public Entity target;
    }

    public static class Lock {
        public Entity target;
    }

    public static class NestedBadComponent implements Component {
        public final Array<Lock> locks = new Array<>();
    }

    private static final ComponentMapper<TransformComponent> TRANSFORM = ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<TargetComponent> TARGET = ComponentMapper.getFor(TargetComponent.class);
    private static final ComponentMapper<UuidComponent> UUID = ComponentMapper.getFor(UuidComponent.class);

    @Test
    void roundTripKeepsTickComponentsAndReferences() {
        GameEngine engine = new GameEngine();
        engine.stepTicks(1234);

        Entity enemy = engine.createEntityWithUuid();
        enemy.add(new TransformComponent(100f, -50f, 90f));
        engine.addEntity(enemy);
        Entity wing = engine.createEntityWithUuid();
        engine.addEntity(wing);

        Entity player = engine.createEntityWithUuid();
        player.add(new PlayerControllerComponent());
        player.add(new TransformComponent(1f, 2f, 3f));
        TargetComponent target = new TargetComponent();
        target.target = EntityRef.of(enemy);
        target.wingmen.add(EntityRef.of(wing));
        target.label = "attack";
        player.add(target);
        engine.addEntity(player);

        Entity particle = engine.createEntity();
        particle.add(new TransientComponent());
        engine.addEntity(particle);

        String enemyId = UUID.get(enemy).id;
        String playerId = UUID.get(player).id;
        String save = engine.getSerializationService().saveGame();
        assertTrue(save.contains(enemyId));

        GameEngine loaded = new GameEngine();
        loaded.getSerializationService().loadGame(save);

        assertEquals(1234, loaded.getTick());
        assertEquals(3, loaded.getEcsEngine().getEntities().size(), "transient entity is not saved");
        assertTrue(loaded.getSerializationService().getLastLoadWarnings().isEmpty());

        Entity loadedPlayer = loaded.getEntity(playerId);
        assertNotNull(loadedPlayer);
        assertNotNull(loadedPlayer.getComponent(PlayerControllerComponent.class));
        assertEquals(2f, TRANSFORM.get(loadedPlayer).y);
        TargetComponent loadedTarget = TARGET.get(loadedPlayer);
        assertEquals("attack", loadedTarget.label);

        Entity loadedEnemy = loadedTarget.target.get(loaded.getUuidRegistry());
        assertSame(loaded.getEntity(enemyId), loadedEnemy);
        assertEquals(100f, TRANSFORM.get(loadedEnemy).x);
        assertNotNull(loadedTarget.wingmen.first().get(loaded.getUuidRegistry()));
    }

    @Test
    void loadReplacesExistingState() {
        GameEngine engine = new GameEngine();
        engine.addEntity(engine.createEntityWithUuid());
        String save = engine.getSerializationService().saveGame();
        engine.addEntity(engine.createEntityWithUuid());

        engine.getSerializationService().loadGame(save);

        assertEquals(1, engine.getEcsEngine().getEntities().size());
        assertEquals(1, engine.getUuidRegistry().size());
    }

    @Test
    void skippedComponentsAreNotWritten() {
        GameEngine engine = new GameEngine();
        engine.getSerializationService().skipComponent(RuntimeHandleComponent.class);
        Entity e = engine.createEntityWithUuid();
        e.add(new RuntimeHandleComponent());
        engine.addEntity(e);

        String save = engine.getSerializationService().saveGame();

        assertFalse(save.contains("RuntimeHandleComponent"));
    }

    @Test
    void rawEntityFieldIsRejected() {
        GameEngine engine = new GameEngine();
        Entity e = engine.createEntityWithUuid();
        BadComponent bad = new BadComponent();
        bad.target = e;
        e.add(bad);
        engine.addEntity(e);

        assertThrows(SerializationException.class, () -> engine.getSerializationService().saveGame());
    }

    @Test
    void rawEntityInANestedObjectIsRejected() {
        GameEngine engine = new GameEngine();
        Entity e = engine.createEntityWithUuid();
        NestedBadComponent bad = new NestedBadComponent();
        bad.locks.add(new Lock());
        bad.locks.first().target = e;
        e.add(bad);
        engine.addEntity(e);

        SerializationException ex = assertThrows(SerializationException.class,
            () -> engine.getSerializationService().saveGame());
        assertTrue(ex.getMessage().contains("locks[0].target"), ex.getMessage());
    }

    @Test
    void danglingReferenceAndUnknownClassAreReportedNotFatal() {
        String save = "{\"version\":1,\"tick\":5,\"entities\":[{"
            + "\"com.ringsofiron.coreengine.components.UuidComponent\":{\"id\":\"a\"},"
            + "\"com.ringsofiron.coreengine.SerializationServiceTest$TargetComponent\":{\"target\":\"missing\"},"
            + "\"com.example.mod.GoneComponent\":{\"x\":1}}],\"modules\":{}}";
        GameEngine engine = new GameEngine();

        engine.getSerializationService().loadGame(save);

        assertEquals(5, engine.getTick());
        assertNotNull(engine.getEntity("a"));
        assertEquals(2, engine.getSerializationService().getLastLoadWarnings().size());
    }

    @Test
    void participantsSaveModuleState() {
        class Queue implements SaveParticipant {
            long nextEventTick;
            EntityRef subject;

            @Override
            public String saveKey() {
                return "test.queue";
            }

            @Override
            public void write(Json json) {
                json.writeValue("nextEventTick", nextEventTick);
                json.writeValue("subject", subject, EntityRef.class);
            }

            @Override
            public void read(Json json, JsonValue data) {
                nextEventTick = data.getLong("nextEventTick");
                subject = json.readValue(EntityRef.class, data.get("subject"));
            }
        }
        GameEngine engine = new GameEngine();
        Entity ship = engine.createEntityWithUuid();
        engine.addEntity(ship);
        Queue queue = new Queue();
        queue.nextEventTick = 99_000_000_000L;
        queue.subject = EntityRef.of(ship);
        engine.getSerializationService().addParticipant(queue);
        String save = engine.getSerializationService().saveGame();

        GameEngine loaded = new GameEngine();
        Queue loadedQueue = new Queue();
        loaded.getSerializationService().addParticipant(loadedQueue);
        loaded.getSerializationService().loadGame(save);

        assertEquals(99_000_000_000L, loadedQueue.nextEventTick);
        assertNotNull(loadedQueue.subject.get(loaded.getUuidRegistry()));
    }

    @Test
    void newerFormatIsRefused() {
        GameEngine engine = new GameEngine();
        assertThrows(SerializationException.class,
            () -> engine.getSerializationService().loadGame("{\"version\":99,\"tick\":0,\"entities\":[]}"));
    }

    public static class Summary {
        public String name;
        public long savedAt;
    }

    @Test
    void aSummaryOpensTheSaveAndIsReadWithoutTheRest(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        GameEngine engine = new GameEngine();
        engine.stepTicks(77);
        for (int i = 0; i < 3; i++) {
            Entity e = engine.createEntityWithUuid();
            e.add(new TransformComponent(i, i, 0f));
            engine.addEntity(e);
        }
        Summary summary = new Summary();
        summary.name = "Brace } and \"quote\" {";
        summary.savedAt = 1234L;
        com.badlogic.gdx.files.FileHandle file = new com.badlogic.gdx.files.FileHandle(dir.resolve("save.json").toFile());
        engine.getSerializationService().saveToFile(file, summary);

        JsonValue read = SerializationService.readSummary(file);
        assertNotNull(read);
        assertEquals(summary.name, read.getString("name"));
        assertEquals(1234L, read.getLong("savedAt"));

        GameEngine loaded = new GameEngine();
        loaded.getSerializationService().loadFromFile(file);
        assertEquals(77, loaded.getTick());
        assertEquals(3, loaded.getEcsEngine().getEntities().size());
        assertTrue(loaded.getSerializationService().getLastLoadWarnings().isEmpty(), "the summary is no part of the game");

        engine.getSerializationService().saveToFile(file);
        assertNull(SerializationService.readSummary(file), "a save without one");
        assertNull(SerializationService.readSummary(file.sibling("missing.json")), "no file");
        file.writeString("{summary:{name:", false);
        assertNull(SerializationService.readSummary(file), "cut short");
    }
}
