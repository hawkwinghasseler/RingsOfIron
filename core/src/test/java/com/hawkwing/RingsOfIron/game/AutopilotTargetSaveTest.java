package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round 6: the autopilot and the helm aimed at a ship, not a station, keep their target across a
 * save and load. A ship keeps its UUID through a save (the round 5 playtest feared it did not), so
 * the orders hold it by UUID as before; these tests keep it that way.
 */
class AutopilotTargetSaveTest {
    static final float FRAME = 1f / 60f;

    @TempDir
    Path dir;
    GameWorld world, fresh;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        world = new GameWorld();
        game = GameScenario.start(world, assets(), assets().resolve("no-mods-here"));
        world.bridge.stand();
        for (int i = 0; i < 60 * 60 && world.bridge.helmsman(game.player) == null; i++) world.update(FRAME);
        assertNotNull(world.bridge.helmsman(game.player), "a crew member takes the helm");
    }

    @AfterEach
    void tearDown() {
        world.dispose();
        if (fresh != null) fresh.dispose();
    }

    @Test
    void theAutopilotStillFliesToAFreighterAfterALoad() {
        String hauler = freighter(false);
        assertNotNull(hauler, "a freighter out on the rails");
        world.nav.target.select(hauler);
        String said = world.nav.autopilot.toggle();
        assertTrue(said.startsWith("Autopilot: flying the transfer to"), said);
        int burns = world.burns.nodes().nodes.size;

        GameScenario loaded = saveAndLoad();
        assertNotNull(fresh.engine.getEntity(hauler), "the freighter keeps its UUID");
        assertEquals(hauler, fresh.nav.target.uuid(), "still the navigation target");
        AutopilotComponent a = fresh.nav.autopilot.state();
        assertNotNull(a, "the autopilot is still engaged");
        assertEquals(hauler, a.targetUuid);
        assertEquals(burns, fresh.burns.nodes().nodes.size, "the plan came back with it");

        // And it flies on: the first burn fires on time, and the autopilot is still on its way there
        int frames = 0;
        while (fresh.burns.nodes().nodes.size == burns && frames++ < 60 * 60 * 20 && fresh.getCrash() == null) {
            fresh.update(FRAME);
            AutopilotComponent s = fresh.nav.autopilot.state();
            if (s != null && s.held) fresh.nav.autopilot.toggle();
        }
        assertTrue(fresh.burns.nodes().nodes.size < burns, "a burn was flown after the load");
        AutopilotComponent after = fresh.nav.autopilot.state();
        assertTrue(after != null ? hauler.equals(after.targetUuid) : hauler.equals(
                        loaded.player.getComponent(HelmOrderComponent.class).targetUuid),
                () -> "still flying to the freighter: " + fresh.nav.autopilot.describe() + " / " + fresh.nav.helm.describe());
    }

    @Test
    void theHelmStillApproachesAShipInTheBubbleAfterALoad() {
        String hauler = freighter(true);
        assertNotNull(hauler, "a freighter in the bubble with us");
        world.nav.target.select(hauler);
        String said = world.nav.helm.give(HelmOrderComponent.Mode.APPROACH);
        assertEquals(HelmOrderComponent.Mode.APPROACH, world.nav.helm.mode(), said);

        GameScenario loaded = saveAndLoad();
        for (int i = 0; i < 60; i++) fresh.update(FRAME);
        HelmOrderComponent o = loaded.player.getComponent(HelmOrderComponent.class);
        assertEquals(HelmOrderComponent.Mode.APPROACH, o.mode, () -> "the order holds: " + fresh.nav.helm.describe());
        assertEquals(hauler, o.targetUuid);
        assertNotNull(fresh.engine.getEntity(hauler), "the freighter keeps its UUID");
    }

    // ------------------------------------------------------------------

    private static Path assets() {
        return Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
    }

    /** A freighter in the bubble, or one out on the rails. */
    private String freighter(boolean inBubble) {
        for (String u : world.macroSim.freighters().all().keySet()) {
            Entity e = world.engine.getEntity(u);
            if (e != null && (e.getComponent(PhysicsBodyComponent.class) != null) == inBubble
                    && (inBubble || world.docking.dockedTo(e) == null)) return u;
        }
        return null;
    }

    private GameScenario saveAndLoad() {
        FileHandle file = new FileHandle(dir.resolve("aim.json").toFile());
        assertNull(game.quickSave(file));
        fresh = new GameWorld();
        GameScenario loaded = GameScenario.start(fresh, assets(), dir.resolve("no-mods-here"));
        loaded.quickLoad(file);
        fresh.update(FRAME);
        return loaded;
    }
}
