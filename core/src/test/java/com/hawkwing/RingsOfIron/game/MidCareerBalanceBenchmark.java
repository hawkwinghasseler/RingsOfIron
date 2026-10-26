package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.combatsys.defs.LoadoutDef;
import com.ringsofiron.combatsys.modding.Loadouts;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Playtest benchmark: the mid career's balance targets, measured on the real new game (seed 2026).
 * Round 4's balance pass (planning/round4-balance.md: a prize paid ten bounties, the gunship was forty
 * weeks of pay away, the ace's gun barely hurt), the frigate's price (round 5) and the pirate base's
 * defence against the frigate (round 10). Was in {@code Round4BalanceTest}, {@code Round5LateCareerTest}
 * and {@code BaseDefenceTest}; out of the build since the targets describe the scripted player's game.
 * Run with {@code ./gradlew :core:playtestBenchmark --tests '*MidCareerBalanceBenchmark'}; the
 * tester's checklist is {@code documentation/playtest/Balance benchmarks.md}.
 */
@Tag("playtest")
class MidCareerBalanceBenchmark {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(2026, "patrol"));
        game.beginCareer();
        run(120);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    @Test
    void aRaiderTakenAsAPrizePaysOneToTwoOfItsBounties() {
        BalanceReport report = new BalanceReport("Prize against the bounty");
        Contract bounty = null;
        for (Contract c : world.contracts.offers(game.highport)) {
            if (c.type == Contract.Type.BOUNTY && world.universe.uuidOf(game.raider).equals(c.targetUuid)) bounty = c;
        }
        assertNotNull(bounty);
        double prize = Shipyard.prizeValue(game.raider);
        report.note("First raider's bounty, cr", String.format(Locale.ROOT, "%,.0f", bounty.reward));
        report.range("The raider towed home as a prize, in bounties", prize / bounty.reward, 1, 2);
        report.expect("Stripping a prize first pays less than taking it whole", Shipyard.STRIPPED_PRIZE_SHARE < Shipyard.PRIZE_SHARE,
                Shipyard.STRIPPED_PRIZE_SHARE + " against " + Shipyard.PRIZE_SHARE);
        report.finish();
    }

    @Test
    void theGunshipIsAFewWeeksOfALieutenantCommandersPayAfterTheCorvettesTradeIn() {
        BalanceReport report = new BalanceReport("Gunship price");
        assertTrue(world.docking.undock(game.player));
        run(60);
        assertTrue(game.dockAt(game.shipyard));
        run(120);
        Shipyard.Listing gunship = world.shipyard.listing("patrol_gunship");
        assertNotNull(gunship);
        double cost = world.shipyard.cost(gunship, game.player);
        double pay = world.standing.ranks().get(2).budget;
        // Round 3 had it at 207,587 net against 5,000 a week: about 40 weeks
        report.note("Gunship after the corvette's trade-in, cr", String.format(Locale.ROOT, "%,.0f", cost));
        report.range("Weeks of a Lieutenant Commander's pay", cost / pay, 4, 8);
        report.atLeast("List price, cr (over the gunship's 304,000 value)", gunship.price, 304_001);
        report.finish();
    }

    /**
     * Since the speed rule (Oct 2026) a Mk2 fires its Mk1's round: an ace's chaingun is the better
     * mount, holding tighter and slewing faster, not a heavier round.
     */
    @Test
    void anAcesChaingunHoldsTighterThanTheGunItReplaces() {
        BalanceReport report = new BalanceReport("Ace chaingun");
        LoadoutDef refit = Loadouts.pick(world.combatDefs, HostileTraffic.RAIDER, 1.0, true, new Random(1));
        assertNotNull(refit);
        report.expect("Below difficulty 1.5 an ace raider is refitted with the chaingun", "raider_ace_chaingun".equals(refit.id), refit.id);
        var mk1 = world.combatDefs.weapons.get("chaingun_mk1");
        var mk2 = world.combatDefs.weapons.get("chaingun_mk2");
        report.atMost("Chaingun Mk2's aim spread in the Mk1's", mk2.spreadRad / mk1.spreadRad, 0.8);
        report.atLeast("Chaingun Mk2's slew in the Mk1's", mk2.slewRateDeg / mk1.slewRateDeg, 1.25);
        report.finish();
    }

    @Test
    void theFrigateCostsALittleOverItsValueAsTheGunshipDoes() {
        BalanceReport report = new BalanceReport("Frigate price");
        Shipyard.Listing frigate = null;
        for (Shipyard.Listing l : world.shipyard.listings(world.engine.getEntity(game.shipyard))) {
            if ("frigate_patrol".equals(l.shipId)) frigate = l;
        }
        assertNotNull(frigate);
        Entity hull = game.content.factory.spawnShip("frigate_patrol", 0f, 0f, 0f);
        double value = Difficulty.shipValue(hull);
        world.engine.getEcsEngine().removeEntity(hull);
        report.note("Frigate's value, cr", String.format(Locale.ROOT, "%,.0f", value));
        report.range("Frigate's list price over its value", frigate.price / value, 1.0001, 1.05);
        report.expect("A Commander's ship", frigate.rank == 3, "rank " + frigate.rank);
        report.finish();
    }

    /**
     * Round 10: a defence with no railgun marks a frigate holding where {@link RaidRoutine} holds it (the
     * survey's no-railgun row, seed 10: three chaingun raiders and two stock batteries, which took 0.00
     * of hull off on round 9's integration). Was {@code BaseDefenceTest.aDefenceWithNoRailgunMarksAFrigate}.
     */
    @Test
    void aDefenceWithNoRailgunMarksAFrigate() {
        BalanceReport report = new BalanceReport("Base defence against the frigate");
        EnemySurveyTest.Result r = EnemySurveyTest.fight(10, EnemySurveyTest.FRIGATE, EnemySurveyTest.BASE, 0, EnemySurveyTest.NO_RAILGUN);
        System.out.println("RESULT " + r);
        assertFalse(r.loadouts.contains("railgun"), r.toString());
        report.atLeast("Hull the raid takes off the frigate", r.hull0 - r.hull1, 0.05);
        report.finish();
    }
}
