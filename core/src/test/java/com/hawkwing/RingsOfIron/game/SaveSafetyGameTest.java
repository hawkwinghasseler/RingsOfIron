package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.ringsofiron.coreengine.SerializationService;
import com.ringsofiron.coreengine.components.TransientComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A load that fails leaves the game as it was; every saved component has a stable type id; and a
 * save from an older version of the game (kept in test resources) is refused, leaving the game as it
 * was.
 */
class SaveSafetyGameTest {
    static final float FRAME = 1f / 60f;
    /**
     * Docked at Highport with freighters on the rails, saved by main at b4298fb3 (format 1, class
     * names): a save from before the current format, which the game no longer reads.
     */
    static final String FORMAT1_DOCKED = "/saves/format1-docked-career.json.gz";

    Path assets;
    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        for (int i = 0; i < 90; i++) world.update(FRAME);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void aBrokenSaveIsRefusedWithTheGameUntouched(@TempDir Path dir) throws IOException {
        Entity player = game.player;
        int entities = world.engine.getEcsEngine().getEntities().size();
        long tick = world.engine.getTick();
        Path file = dir.resolve("broken.json");
        String good = world.engine.getSerializationService().saveGame();
        // Cut short, as a crash part way through writing used to leave it
        Files.writeString(file, good.substring(0, good.length() / 2));

        GameScenario.LoadFailedException e = assertThrows(GameScenario.LoadFailedException.class,
                () -> game.quickLoad(new FileHandle(file.toFile())));

        assertTrue(e.restored);
        assertFalse(e.replaced, "nothing was replaced, so nothing needs pointing at new entities");
        assertSame(player, game.player);
        assertSame(player, world.getPlayer());
        assertEquals(entities, world.engine.getEcsEngine().getEntities().size());
        assertEquals(tick, world.engine.getTick());
        assertThrows(GameScenario.LoadFailedException.class,
                () -> game.quickLoad(new FileHandle(dir.resolve("missing.json").toFile())));
        assertSame(player, game.player);
    }

    @Test
    void aSaveAModuleCantReadPutsTheGameBackAsItWas(@TempDir Path dir) throws IOException {
        String playerId = game.player.getComponent(UuidComponent.class).id;
        double credits = game.player.getComponent(TraderComponent.class).credits;
        long tick = world.engine.getTick();
        JsonValue save = new JsonReader().parse(world.engine.getSerializationService().saveGame());
        // Decodes cleanly, but physics can't make a bubble anchor of it
        save.get("modules").get("physics-exterior").get("anchor").set("nowhere");
        Path file = dir.resolve("bad-module.json");
        Files.writeString(file, save.toJson(JsonWriter.OutputType.json));
        for (int i = 0; i < 30; i++) world.update(FRAME);
        long before = world.engine.getTick();
        int entities = saved(world);

        GameScenario.LoadFailedException e = assertThrows(GameScenario.LoadFailedException.class,
                () -> game.quickLoad(new FileHandle(file.toFile())));

        assertTrue(e.replaced);
        assertTrue(e.restored, String.valueOf(e.getCause()));
        assertTrue(e.getMessage().contains("physics-exterior"), e.getMessage());
        assertEquals(before, world.engine.getTick(), "the game the load was tried from, not the save's");
        assertNotEquals(tick, before);
        assertEquals(entities, saved(world), "every saved entity back");
        assertEquals(playerId, game.player.getComponent(UuidComponent.class).id);
        assertSame(world.getPlayer(), game.player, "pointed at the put-back entities");
        assertEquals(credits, game.player.getComponent(TraderComponent.class).credits);
        for (int i = 0; i < 60; i++) world.update(FRAME);
        assertNull(world.getCrash(), "and it flies on");
    }

    @Test
    void everyComponentTheGameSavesHasAStableTypeId(@TempDir Path dir) throws IOException {
        // A fresh start, and docked at Highport with freighters out, saved and loaded back
        assertAllSavedTypesHaveIds();
        Entity highport = world.engine.getEntity(game.highport);
        com.ringsofiron.stationsdocking.DockingPortsComponent ports =
                highport.getComponent(com.ringsofiron.stationsdocking.DockingPortsComponent.class);
        int small = -1;
        for (int i = 0; i < ports.ports.length && small < 0; i++) {
            if (ports.ports[i].size == com.ringsofiron.interiorgrid.MountFootprint.MountSize.S) small = i;
        }
        world.docking.dockInPlace(game.player, 0, highport, small);
        for (int i = 0; i < 120; i++) world.update(FRAME);
        assertSame(highport, world.docking.dockedTo(game.player));
        Path file = dir.resolve("docked.json");
        Files.writeString(file, world.engine.getSerializationService().saveGame());
        List<String> warnings = game.quickLoad(new FileHandle(file.toFile()));
        assertTrue(warnings.isEmpty(), String.valueOf(warnings));
        assertNotNull(world.docking.dockedTo(game.player), "docked, as it was saved");
        assertAllSavedTypesHaveIds();
    }

    @Test
    void aSaveFromAnOlderVersionIsRefusedWithTheGameUntouched() throws IOException {
        Entity player = game.player;
        int entities = world.engine.getEcsEngine().getEntities().size();
        long tick = world.engine.getTick();

        GameScenario.LoadFailedException e = assertThrows(GameScenario.LoadFailedException.class,
                () -> game.quickLoad(fixture(FORMAT1_DOCKED)));

        assertTrue(e.getMessage().contains("older version of the game"), e.getMessage());
        assertTrue(e.restored);
        assertFalse(e.replaced, "refused before anything was replaced");
        assertSame(player, game.player);
        assertEquals(entities, world.engine.getEcsEngine().getEntities().size());
        assertEquals(tick, world.engine.getTick());
    }

    private void assertAllSavedTypesHaveIds() {
        SerializationService s = world.engine.getSerializationService();
        TreeSet<String> missing = new TreeSet<>();
        for (Entity e : world.engine.getEcsEngine().getEntities()) {
            if (e.getComponent(TransientComponent.class) != null) continue;
            for (Component c : e.getComponents()) {
                if (!s.isSkipped(c.getClass()) && !s.hasTypeId(c.getClass())) missing.add(c.getClass().getName());
            }
        }
        assertTrue(missing.isEmpty(), "give these a type id in SaveTypes: " + missing);
    }

    /** How many entities a save of {@code w} would hold. */
    private static int saved(GameWorld w) {
        int n = 0;
        for (Entity e : w.engine.getEcsEngine().getEntities()) if (e.getComponent(TransientComponent.class) == null) n++;
        return n;
    }

    /** The gzipped save {@code resource}, unpacked to a file. */
    private static FileHandle fixture(String resource) throws IOException {
        Path file = Files.createTempFile("fixture", ".json");
        file.toFile().deleteOnExit();
        try (InputStream in = new GZIPInputStream(SaveSafetyGameTest.class.getResourceAsStream(resource))) {
            Files.write(file, in.readAllBytes());
        }
        return new FileHandle(file.toFile());
    }
}
