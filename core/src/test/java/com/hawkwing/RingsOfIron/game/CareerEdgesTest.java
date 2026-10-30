package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.career.Factions;
import com.ringsofiron.career.RankLadder;
import com.ringsofiron.career.ServiceRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The edges of the player's career: the limits of standing and rank, the thresholds that turn a
 * faction against the player, the budget's timing across a save, a damaged save repaired on
 * load, and difficulty.
 */
class CareerEdgesTest {
    Path assets;
    GameWorld world;
    GameScenario game;
    FactionStanding standing;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        standing = world.standing;
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void standingStaysWithinItsLimitsAndTheTopRankIsReachable() {
        FactionStandingTest.skipTimeInGrade(standing);
        standing.reward("patrol", 500f, "test");
        assertEquals(FactionStanding.MAX, standing.standing("patrol"), 1e-4);
        assertEquals(standing.ranks().size() - 1, standing.record().rank, "all the way up the ladder");
        assertEquals("Captain", standing.rank().name);
        standing.penalize("selene", 500f, "test");
        assertEquals(FactionStanding.MIN, standing.standing("selene"), 1e-4);
    }

    @Test
    void theLadderHoldsItsEnds() {
        RankLadder ranks = standing.ranks();
        assertEquals("Ensign", ranks.get(-3).name);
        assertEquals("Captain", ranks.get(99).name);
        assertEquals(0, ranks.earnedBy(-40f), "below the first rung is still the first rank");
        assertEquals(1, ranks.earnedBy(15f), "a threshold met exactly earns the rank");
        assertEquals(0, ranks.earnedBy(14.99f));
        assertTrue(ranks.mayRequisition(0, "no_such_ship"), "a ship the data cannot size is allowed");
    }

    @Test
    void stationsRefuseBelowFifteenAndCrewFightAtFifty() {
        int selene = standing.factions().index("selene");
        var mine = world.engine.getEntity(game.lunarMine);

        standing.penalize("selene", 15f, "test");
        assertNull(standing.refusal(mine, game.player), "exactly -15 is still welcome");
        standing.penalize("selene", 0.5f, "test");
        assertNotNull(standing.refusal(mine, game.player));
        assertNull(standing.refusal(mine, null), "only the player is refused on standing");

        assertFalse(standing.hostile(FactionStanding.PLAYER, selene));
        standing.penalize("selene", 34.5f, "test");
        assertEquals(FactionStanding.HOSTILE_AT, standing.standing("selene"), 1e-4);
        assertTrue(standing.hostile(FactionStanding.PLAYER, selene), "at -50 their crew fight the player's");
        assertTrue(standing.hostile(selene, FactionStanding.PLAYER), "both ways");
    }

    @Test
    void piratesFightEveryoneButFactionsLeaveUnalignedCrewAlone() {
        Factions f = standing.factions();
        int pirates = f.index("pirates"), selene = f.index("selene"), unaligned = 1;
        assertFalse(f.known(unaligned));
        assertTrue(standing.hostile(pirates, unaligned));
        assertFalse(standing.hostile(selene, unaligned));
        assertTrue(standing.hostile(pirates, FactionStanding.PLAYER), "even at standing 0");
    }

    @Test
    void unknownFactionsAreIgnored() {
        standing.reward("no_such_faction", 10f, "test");
        standing.penalize(-1, 10f, "test");
        standing.reward(FactionStanding.PLAYER, 10f, "test");
        assertEquals(0, standing.record().rank);
        assertEquals(0, standing.record().factions.length, "nothing recorded");
    }

    @Test
    void paydayAndWhatWasPaidSurviveASave(@TempDir Path dir) {
        standing.record().nextPay = 123_456;
        standing.record().budgetPaid = 7_000;
        standing.reward("selene", 3f, "test");
        FileHandle file = new FileHandle(dir.resolve("pay.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            ServiceRecord r = fresh.standing.record();
            assertEquals(123_456, r.nextPay, 1e-6, "the next payday is where it was");
            assertEquals(7_000, r.budgetPaid, 1e-6);
            assertEquals("patrol", r.sponsor);
            assertEquals(3f, fresh.standing.standing("selene"), 1e-4);
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void aDamagedCareerIsRepairedOnLoad() {
        standing.reward("selene", 5f, "test");
        standing.read(new Json(), new JsonReader().parse(
                "{record:{sponsor:nobody,rank:42,factions:[patrol,selene],standing:[1.0],nextPay:10}}"));
        ServiceRecord r = standing.record();
        assertEquals("patrol", r.sponsor, "an unknown sponsor becomes the data's");
        assertEquals(standing.ranks().size() - 1, r.rank, "the rank is held on the ladder");
        assertEquals(0, r.factions.length, "standing that doesn't line up is dropped");
        assertEquals(0f, standing.standing("selene"), 1e-4);
        assertEquals(10, r.nextPay, 1e-6);

        standing.read(new Json(), new JsonReader().parse("{}"));
        assertEquals(0, standing.record().rank, "no record at all is a fresh career");
        assertEquals("patrol", standing.record().sponsor);
    }

    @Test
    void aFreshCareerForgetsGrievances() {
        var mine = world.engine.getEntity(game.lunarMine);
        standing.penalize("selene", 30f, "smuggled past their customs");
        assertEquals("you smuggled past their customs", standing.refusal(mine, game.player));
        standing.reset();
        assertNull(standing.refusal(mine, game.player));
        assertEquals(0, standing.record().rank);
    }

    @Test
    void difficultyGrowsWithRankAndEachDoublingOfWorth() {
        assertEquals(1.0, Difficulty.of(0, 0), 1e-9);
        assertEquals(1.0, Difficulty.of(0, Difficulty.BASE_WORTH), 1e-9, "worth counts only above the base");
        assertEquals(1.0, Difficulty.of(-2, 0), 1e-9, "never below 1");
        assertEquals(1.5, Difficulty.of(2, 0), 1e-9);
        assertEquals(1.5, Difficulty.of(0, 2 * Difficulty.BASE_WORTH), 1e-9);
        assertEquals(2.75, Difficulty.of(1, 8 * Difficulty.BASE_WORTH), 1e-9);
        assertEquals(0, Difficulty.netWorth(null), 1e-9);
        assertTrue(Difficulty.shipValue(game.player) > 0, "the starter has a hull and guns");
        assertEquals(Difficulty.of(0, Difficulty.netWorth(game.player)), Difficulty.of(world), 1e-9);
    }
}
