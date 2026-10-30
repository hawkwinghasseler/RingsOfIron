package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.career.Factions;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Strain between the factions, and grudges against the player fading week by week. */
class FactionTensionTest {
    static final float FRAME = 1f / 60f;
    GameWorld world;
    GameScenario game;
    FactionTension tension;
    int patrol, selene, traders, pirates;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        world = new GameWorld();
        game = GameScenario.start(world, WiderWorldTest.assets(), WiderWorldTest.assets().resolve("none"));
        tension = world.tension;
        Factions f = world.standing.factions();
        patrol = f.index("patrol");
        selene = f.index("selene");
        traders = f.index("traders");
        pirates = f.index("pirates");
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void thePlayersHostileActsStrainTheirSponsorWithTheVictim() {
        assertEquals(0f, tension.get(patrol, selene));
        world.standing.hostileAct(selene, FactionStanding.CUT_COST, "cut their door");
        assertEquals(FactionStanding.CUT_COST * FactionTension.DEED_SHARE, tension.get(patrol, selene), 1e-4);
        assertEquals(tension.get(patrol, selene), tension.get(selene, patrol));
        assertEquals(0f, tension.get(patrol, traders));
        tension.raise(patrol, pirates, 80f, null);
        assertEquals(0f, tension.get(patrol, pirates), "pirates are at war with everyone already");
    }

    @Test
    void highTensionAddsPatrolsAndRivalStationsTurnChoosy() {
        int patrols = world.hostiles.patrols("patrol"), guards = world.hostiles.patrols("selene");
        Entity forge = world.engine.getEntity(game.foundry);
        assertNull(tension.refusal(forge, game.player));
        tension.raise("patrol", "selene", FactionTension.HIGH, "A Selene freighter was searched");
        assertTrue(tension.high(patrol, selene));
        assertEquals(NoticeLog.Kind.WARNING, world.notices.latest().kind);
        assertTrue(world.notices.latest().text.startsWith("A Selene freighter was searched. Tension between"));
        world.update(FRAME);
        assertEquals(patrols + 1, world.hostiles.patrols("patrol"));
        assertEquals(guards + 1, world.hostiles.patrols("selene"));
        // Running high again before it eased sends no more
        tension.raise(patrol, selene, -1f, null);
        tension.raise(patrol, selene, 1f, null);
        world.update(FRAME);
        assertEquals(guards + 1, world.hostiles.patrols("selene"));

        assertNotNull(tension.refusal(forge, game.player), "Selene clears only friends of its own");
        assertNull(tension.refusal(world.engine.getEntity(game.depot), game.player), "the player's own stations still do");
        world.standing.reward(selene, FactionTension.RIVAL_CLEARANCE, "test");
        assertNull(tension.refusal(forge, game.player), "a friend of Selene is cleared");
    }

    @Test
    void eachWeekTensionEasesAndGrudgesFade() {
        tension.raise(patrol, selene, 10f, null);
        world.standing.penalize(selene, 3.5f, "test");
        world.standing.reward(traders, 5f, "test");
        tension.weekPasses();
        assertEquals(10f - FactionTension.RELAX_PER_WEEK, tension.get(patrol, selene), 1e-4);
        assertEquals(-2.5f, world.standing.standing(selene), 1e-4);
        assertEquals(5f, world.standing.standing(traders), 1e-4, "good standing never decays");
        for (int i = 0; i < 5; i++) tension.weekPasses();
        assertEquals(0f, world.standing.standing(selene), 1e-4, "back to zero, and no further");
        assertEquals(0f, tension.get(patrol, selene), 1e-4);
    }

    @Test
    void aWeekPassesOnTheSimClock() {
        world.standing.penalize(selene, 5f, "test");
        game.beginCareer();
        double start = world.macroSim.now();
        for (int i = 0; i < 60 * 60 && world.macroSim.now() < start + 2 * FactionTension.WEEK + 3_600; i++) {
            if (world.timeWarp.getWarp() < 100_000) world.timeWarp.setWarp(100_000);
            world.update(FRAME);
        }
        assertTrue(world.macroSim.now() >= start + 2 * FactionTension.WEEK, "two weeks passed in warp");
        assertTrue(world.macroSim.now() < start + 3 * FactionTension.WEEK);
        assertEquals(-3f, world.standing.standing(selene), 1e-4);
    }

    @Test
    void aSaveKeepsTheStrain(@TempDir Path dir) {
        tension.raise(patrol, selene, 60f, null);
        FileHandle file = new FileHandle(dir.resolve("tension.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, WiderWorldTest.assets(), WiderWorldTest.assets().resolve("none"));
            loaded.quickLoad(file);
            assertEquals(60f, fresh.tension.get(patrol, selene), 1e-4);
        } finally {
            fresh.dispose();
        }
    }
}
