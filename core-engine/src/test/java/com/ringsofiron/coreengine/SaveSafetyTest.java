package com.ringsofiron.coreengine;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SerializationException;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import org.junit.jupiter.api.Test;

/** Stable type ids, and loads that refuse a bad save before they touch the game. */
class SaveSafetyTest {
    public static class CargoComponent implements Component {
        public int crates;
    }

    public static class RenamedCargoComponent implements Component {
        public int crates;
    }

    private static GameEngine shipAt(float x) {
        GameEngine engine = new GameEngine();
        Entity ship = engine.createEntityWithUuid();
        ship.add(new TransformComponent(x, 2f, 0f));
        engine.addEntity(ship);
        engine.stepTicks(10);
        return engine;
    }

    @Test
    void componentsAreSavedUnderTheirTypeIdAndClassesWithoutOneUnderTheirName() {
        GameEngine engine = shipAt(1f);
        engine.getSerializationService().registerType("test.Cargo", CargoComponent.class);
        Entity e = engine.createEntityWithUuid();
        e.add(new CargoComponent());
        engine.addEntity(e);

        String save = engine.getSerializationService().saveGame();

        assertTrue(save.contains("\"core.Transform\""), save);
        assertTrue(save.contains("\"core.Uuid\""), save);
        assertTrue(save.contains("\"test.Cargo\""), save);
        assertFalse(save.contains(TransformComponent.class.getName()), save);
        assertTrue(save.contains("\"version\": " + SerializationService.FORMAT_VERSION), save);
    }

    @Test
    void componentsKeyedByClassNameLoad() {
        String save = "{\"version\":" + SerializationService.FORMAT_VERSION + ",\"tick\":7,\"entities\":[{"
            + "\"" + UuidComponent.class.getName() + "\":{\"id\":\"a\"},"
            + "\"" + TransformComponent.class.getName() + "\":{\"x\":3,\"y\":4,\"rotation\":0}}],\"modules\":{}}";
        GameEngine engine = new GameEngine();

        engine.getSerializationService().loadGame(save);

        assertTrue(engine.getSerializationService().getLastLoadWarnings().isEmpty(),
            String.valueOf(engine.getSerializationService().getLastLoadWarnings()));
        assertEquals(3f, engine.getEntity("a").getComponent(TransformComponent.class).x);
    }

    @Test
    void anAliasReadsARenamedClassFromAnOldSave() {
        String save = "{\"version\":" + SerializationService.FORMAT_VERSION + ",\"tick\":0,\"entities\":[{"
            + "\"core.Uuid\":{\"id\":\"a\"},"
            + "\"com.example.OldCargoComponent\":{\"crates\":12}}],\"modules\":{}}";
        GameEngine engine = new GameEngine();
        engine.getSerializationService().registerType("test.Cargo", RenamedCargoComponent.class);
        engine.getSerializationService().registerAlias("com.example.OldCargoComponent", RenamedCargoComponent.class);

        engine.getSerializationService().loadGame(save);

        assertEquals(12, engine.getEntity("a").getComponent(RenamedCargoComponent.class).crates);
    }

    @Test
    void anIdBelongsToOneClassAndAClassToOneId() {
        SerializationService s = new GameEngine().getSerializationService();
        s.registerType("test.Cargo", CargoComponent.class);
        s.registerType("test.Cargo", CargoComponent.class);

        assertThrows(IllegalArgumentException.class, () -> s.registerType("test.Cargo", RenamedCargoComponent.class));
        assertThrows(IllegalArgumentException.class, () -> s.registerType("test.Crates", CargoComponent.class));
        assertThrows(IllegalArgumentException.class, () -> s.registerType("core.Transform", CargoComponent.class));
    }

    @Test
    void aSaveThatCantBeReadLeavesTheGameAsItWas() {
        String[] bad = {
            "{ not json",
            "[]",
            "{\"version\":" + (SerializationService.FORMAT_VERSION + 1) + ",\"entities\":[]}",
            // From before the current format
            "{\"version\":" + (SerializationService.FORMAT_VERSION - 1) + ",\"tick\":1,\"entities\":[]}",
            // Read as far as the second entity before the bad number
            "{\"version\":" + SerializationService.FORMAT_VERSION + ",\"tick\":1,\"entities\":[{\"core.Uuid\":{\"id\":\"b\"}},"
                + "{\"core.Transform\":{\"x\":\"far away\"}}]}",
        };
        for (String save : bad) {
            GameEngine engine = shipAt(5f);
            Entity ship = engine.getEcsEngine().getEntities().first();

            assertThrows(SerializationException.class, () -> engine.getSerializationService().loadGame(save), save);

            assertEquals(10, engine.getTick(), save);
            assertEquals(1, engine.getEcsEngine().getEntities().size(), save);
            assertSame(ship, engine.getEcsEngine().getEntities().first(), save);
            assertEquals(5f, ship.getComponent(TransformComponent.class).x, save);
        }
    }

    @Test
    void preparingALoadTouchesNothingAndTellsWhatWillBeLeftOut() {
        String save = "{\"version\":" + SerializationService.FORMAT_VERSION + ",\"tick\":3,\"entities\":[{\"core.Uuid\":{\"id\":\"c\"},"
            + "\"gone.Mod\":{}}],\"modules\":{\"gone-mod\":{\"x\":1}}}";
        GameEngine engine = shipAt(5f);

        SerializationService.PreparedLoad load = engine.getSerializationService().prepareLoad(save);

        assertEquals(10, engine.getTick());
        assertNull(engine.getEntity("c"));
        assertEquals(2, load.getWarnings().size(), String.valueOf(load.getWarnings()));
        engine.getSerializationService().applyLoad(load);
        assertEquals(3, engine.getTick());
        assertNotNull(engine.getEntity("c"));
        assertEquals(load.getWarnings(), engine.getSerializationService().getLastLoadWarnings());
        assertThrows(IllegalStateException.class, () -> engine.getSerializationService().applyLoad(load));
    }

    @Test
    void aModuleThatCantReadItsPartNamesItself() {
        GameEngine engine = shipAt(5f);
        engine.getSerializationService().addParticipant(new SaveParticipant() {
            @Override
            public String saveKey() {
                return "fussy";
            }

            @Override
            public void write(Json json) {
            }

            @Override
            public void read(Json json, JsonValue data) {
                throw new IllegalStateException("no anchor");
            }
        });
        String save = engine.getSerializationService().saveGame();

        SerializationException e = assertThrows(SerializationException.class,
            () -> engine.getSerializationService().loadGame(save));
        assertTrue(e.getMessage().contains("fussy") && e.getMessage().contains("no anchor"), e.getMessage());
    }
}
