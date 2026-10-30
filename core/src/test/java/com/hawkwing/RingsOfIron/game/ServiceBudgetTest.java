package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.career.ServiceBudget;
import com.ringsofiron.macrosim.economy.TraderComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** The weekly rank budget and the difficulty, in the starting scenario. */
class ServiceBudgetTest {
    GameWorld world;
    GameScenario game;
    double now;
    ServiceBudget budget;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        // The same budget on a clock the test sets
        budget = new ServiceBudget(world.standing, world::getPlayer, () -> now);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    double credits() {
        return game.player.getComponent(TraderComponent.class).credits;
    }

    @Test
    void theBudgetIsPaidAWeekAfterTheCareerStartsAndWeeklyAfter() {
        double start = credits();
        assertEquals(0, budget.pay(), "nothing at the start");
        now = ServiceBudget.WEEK - 1;
        assertEquals(0, budget.pay());
        now = ServiceBudget.WEEK;
        assertEquals(2_000, budget.pay(), 1e-6, "an ensign's week");
        assertEquals(start + 2_000, credits(), 1e-6);
        assertTrue(world.standing.notices.peek().contains("+2,000 cr"), world.standing.notices.peek());
        assertEquals(ServiceBudget.WEEK, budget.untilNextPay(), 1e-6);
    }

    @Test
    void warpingThroughWeeksPaysEachAtTheRankHeld() {
        budget.pay();
        world.standing.reward("patrol", 15f, "test"); // Lieutenant
        now = 3 * ServiceBudget.WEEK + 10;
        assertEquals(3 * 7_500, budget.pay(), 1e-6);
        assertEquals(22_500, world.standing.record().budgetPaid, 1e-6);
    }

    @Test
    void theHudLineNamesRankBudgetAndPayday() {
        budget.pay();
        now = ServiceBudget.WEEK - 3_600 * 5;
        assertEquals("Ensign   Budget 2,000 cr/wk in 0d 05h", budget.describe());
    }

    @Test
    void theGameRunsItsOwnBudgetOnTheSimClock() {
        for (int i = 0; i < 120; i++) world.update(1f / 60f);
        assertTrue(world.standing.record().nextPay > 0, "a payday is set");
    }

    @Test
    void aNewEnsignIsAtDifficultyOneAndRankAndWealthRaiseIt() {
        assertEquals(1.0, Difficulty.of(world), 1e-9, "the starter corvette and credits");
        assertTrue(Difficulty.shipValue(game.player) > 100_000, "hull and weapons count");
        assertEquals(1.5, Difficulty.of(2, 0), 1e-9);
        assertEquals(1.5, Difficulty.of(0, 2 * Difficulty.BASE_WORTH), 1e-9, "a doubling of net worth");
        FactionStandingTest.skipTimeInGrade(world.standing);
        world.standing.reward("patrol", 100f, "test");
        assertEquals(2.0, Difficulty.of(world), 1e-9, "a captain in the same ship");
    }
}
