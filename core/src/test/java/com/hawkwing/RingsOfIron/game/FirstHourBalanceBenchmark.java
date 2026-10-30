package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.career.Contract;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Playtest benchmark: the first hour's balance targets (round 2's playtest, round2-playtest.md had a
 * 7 s first fight the raider never answered, one laser selling for over twice the bounty and a
 * promotion after one fight), measured on the real new game. Was {@code BalancePacingTest}; out of
 * the build since the targets describe the scripted player's game. Run with
 * {@code ./gradlew :core:playtestBenchmark --tests '*FirstHourBalanceBenchmark'}; the tester's
 * checklist is {@code documentation/playtest/Balance benchmarks.md}.
 */
@Tag("playtest")
class FirstHourBalanceBenchmark {
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
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    private float range() {
        return PhysicsManager.getPosition(body(game.raider), new Vector2())
                .dst(PhysicsManager.getPosition(body(game.player), new Vector2()));
    }

    /** Puts the player {@code meters} off the raider's beam, moving with it: the flying, done by hand. */
    private void flyTo(float meters) {
        Vector2 at = PhysicsManager.getPosition(body(game.raider), new Vector2());
        Vector2 v = PhysicsManager.getVelocity(body(game.raider), new Vector2());
        PhysicsManager.setPosition(body(game.player), at.x, at.y + meters, -90f * MathUtils.degRad);
        PhysicsManager.setVelocity(body(game.player), v.x, v.y);
    }

    private Contract raiderBounty() {
        for (Contract c : world.contracts.offers(game.highport)) {
            if (c.type == Contract.Type.BOUNTY && world.universe.uuidOf(game.raider).equals(c.targetUuid)) return c;
        }
        return null;
    }

    @Test
    void theFirstRaiderComesInFiringAndTheFightLastsUnderFire() {
        BalanceReport report = new BalanceReport("First fight");
        game.beginCareer();
        run(2f);
        assertTrue(world.docking.undock(game.player));
        run(1f);
        // First Play wakes the raider once the player is clear of Highport, some way off from it
        flyTo(6_000f);
        run(0.05f);
        int[] raiderShots = {0};
        float[] firstRaiderShot = {-1f}, t = {0f};
        world.combatEvents.shots.add((s, shot) -> {
            if (shot.shooter == game.raider && shot.target == game.player) {
                raiderShots[0]++;
                if (firstRaiderShot[0] < 0f) firstRaiderShot[0] = t[0];
            }
        });
        // Hits are counted on the hull: a chaingun round that only scars the armour sends no damage report
        int[] playerHits = {0};
        world.combatEvents.hullHits.add((s, h) -> {
            if (h.ship == game.player) playerHits[0]++;
        });
        game.provokeRaider();
        assertTrue(game.raiderHostile());

        // The player locks it and holds the guns from 4 km in, with a missile whenever one is loaded
        PlayerGunnery gun = new PlayerGunnery(world, game.player);
        gun.setTarget(game.raider);
        float openedFire = -1f;
        boolean heldOut = true;
        for (; t[0] < 240f && !ShipDisablement.isDisabled(game.raider) && heldOut; t[0] += FRAME) {
            if (openedFire < 0f && range() < 4_000f) openedFire = t[0];
            if (openedFire >= 0f) {
                gun.fireGuns();
                gun.launchMissile();
            }
            gun.update(FRAME);
            world.update(FRAME);
            heldOut = !ShipDisablement.isDisabled(game.player);
        }
        String said = String.format(Locale.ROOT, "raider fired %d times, first at %.1f s; the player opened fire at %.1f s;"
                + " the fight ended at %.1f s; the player's ship was hit %d times, hull at %.2f", raiderShots[0],
                firstRaiderShot[0], openedFire, t[0], playerHits[0], HostileTraffic.hull(game.player));
        System.out.println("First fight: " + said);
        report.expect("The raider is outgunned by the starting corvette",
                HostileTraffic.firepower(game.raider) < HostileTraffic.firepower(game.player),
                String.format(Locale.ROOT, "%.0f against %.0f", HostileTraffic.firepower(game.raider), HostileTraffic.firepower(game.player)));
        report.expect("The player's ship holds out", heldOut, heldOut ? "held" : "disabled at " + String.format(Locale.ROOT, "%.1f s", t[0]));
        report.expect("The raider is beaten inside 4 minutes", ShipDisablement.isDisabled(game.raider), String.format(Locale.ROOT, "%.1f s", t[0]));
        report.expect("The raider shoots first", raiderShots[0] > 0 && firstRaiderShot[0] < openedFire,
                String.format(Locale.ROOT, "%d rounds, first at %.1f s, the player at %.1f s", raiderShots[0], firstRaiderShot[0], openedFire));
        report.atLeast("The raider lands hits on the hull", playerHits[0], 1);
        // Was 7 s from the player's first shot, with the raider asleep and three slugs through its
        // nose shutting its reactor down; now the player's guns take a good while, under fire
        report.atLeast("Seconds from the player's first shot to the raider beaten", t[0] - openedFire, 20);
        report.note("Player hull after the fight", String.format(Locale.ROOT, "%.2f", HostileTraffic.hull(game.player)));
        report.finish();
    }

    @Test
    void oneStrippedGunPaysLessThanHalfTheBountyAndAWholeRaiderAboutTwo() {
        BalanceReport report = new BalanceReport("Salvage against the bounty");
        game.beginCareer();
        run(2f);
        Contract bounty = raiderBounty();
        assertNotNull(bounty);
        // The raider's best gun, the laser: was 11,250 cr against a 5,000 cr bounty
        double laser = world.salvage.salePrice(Salvage.PART + "laser_mk1");
        report.note("First raider's bounty, cr", String.format(Locale.ROOT, "%,.0f", bounty.reward));
        report.range("The raider's laser, stripped and sold, in bounties", laser / bounty.reward, 0, 0.5);
        double all = 0;
        for (Salvage.Part p : world.salvage.parts(game.raider)) all += world.salvage.salePrice(p.part);
        report.range("The whole raider stripped and sold, in bounties", all / bounty.reward, 1, 3);
        report.finish();
    }

    @Test
    void aBountyAndItsKillAreNotAPromotionButTwoAre() {
        BalanceReport report = new BalanceReport("Fights to Lieutenant");
        game.beginCareer();
        run(2f);
        Contract bounty = raiderBounty();
        assertNotNull(bounty);
        int sponsor = world.standing.sponsor();
        // A first fight: the bounty's standing and the kill's (was 6 + 15 = 21, past Lieutenant's 15)
        world.standing.reward(sponsor, bounty.standing, "bounty");
        world.standing.onShipDestroyed(game.raider);
        float one = world.standing.standing(sponsor);
        report.expect("Still an ensign after one bounty and its kill", world.standing.record().rank == 0,
                String.format(Locale.ROOT, "%.1f standing, %s", one, world.standing.describeRank()));
        world.standing.reward(sponsor, bounty.standing, "bounty");
        world.standing.onShipDestroyed(game.raider);
        report.expect("A Lieutenant after the second", world.standing.record().rank == 1,
                String.format(Locale.ROOT, "%.1f standing, %s", world.standing.standing(sponsor), world.standing.describeRank()));
        report.note("Standing a Lieutenant needs", String.format(Locale.ROOT, "%.1f", world.standing.ranks().get(1).standing));
        report.finish();
    }
}
