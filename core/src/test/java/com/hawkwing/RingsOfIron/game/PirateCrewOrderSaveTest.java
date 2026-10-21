package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Round 10: pirate crews get the power back first ({@code crews_reset_power_first}), from the data and through a save. */
class PirateCrewOrderSaveTest {

    Path assets;
    GameWorld world;
    GameScenario game;
    GameWorld loaded;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
        if (loaded != null) loaded.dispose();
    }

    @Test
    void onlyPirateCrewsResetThePowerFirstThroughASave(@TempDir Path dir) {
        int pirates = world.standing.factions().index(HostileTraffic.PIRATES);
        assertFalse(world.crew.board(game.player).resetsPowerFirst, "our crew keep the usual order");
        int pirateBoards = boardsKeepTheirFaction(world, pirates);
        assertTrue(pirateBoards > 1, "the raiders and the base: " + pirateBoards);

        FileHandle file = new FileHandle(dir.resolve("quicksave.json").toFile());
        assertNull(game.quickSave(file));
        loaded = new GameWorld();
        GameScenario again = GameScenario.start(loaded, assets, assets.resolve("no-mods-here"));
        // Loaded over a world whose boards all keep the usual order, so the save has to carry it
        for (Entity e : loaded.engine.getEcsEngine().getEntitiesFor(Family.all(ShipTaskBoardComponent.class).get())) {
            e.getComponent(ShipTaskBoardComponent.class).resetsPowerFirst = false;
        }
        again.quickLoad(file);
        assertEquals(pirateBoards, boardsKeepTheirFaction(loaded, pirates));
    }

    @Test
    void anAcesCrewKeepTheUsualOrder() {
        // Round 10: resetting first made the aces harder (ace's chaingun raider v corvette lost one in ten)
        var hostiles = world.macroSim.hostiles();
        for (var e : hostiles.all().entrySet()) {
            if (!e.getValue().isPirate() || e.getValue().moored()) continue;
            Entity ship = world.engine.getEntity(e.getKey());
            assertTrue(ship.getComponent(ShipTaskBoardComponent.class).resetsPowerFirst);
            assertTrue(PirateAces.promote(world, ship, e.getValue(), hostiles.all().values(), 1.25, false));
            assertFalse(ship.getComponent(ShipTaskBoardComponent.class).resetsPowerFirst, e.getValue().displayName);
            return;
        }
        fail("no pirate");
    }

    /** Checks every crewed board resets the power first only if the pirates work it; returns how many they do. */
    private static int boardsKeepTheirFaction(GameWorld w, int pirates) {
        int n = 0;
        for (Entity e : w.engine.getEcsEngine().getEntitiesFor(Family.all(ShipTaskBoardComponent.class).get())) {
            ShipTaskBoardComponent b = e.getComponent(ShipTaskBoardComponent.class);
            assertEquals(b.faction == pirates, b.resetsPowerFirst, "faction " + b.faction);
            if (b.faction == pirates) n++;
        }
        return n;
    }
}
