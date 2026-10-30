package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Playtest benchmark for Joe's speed rule (Oct 2026): within a mount size, a faster round means less
 * damage a second, so a railgun is the gun to have at range and a cannon the gun to have in a brawl.
 * The First Play corvette keeps only its nose mount, fitted in turn with each Small gun, and fights
 * the First Play raider from a held range: each frame the corvette is put back that far off along
 * the line between them (its velocity is left alone, so the raider's own manoeuvres are what the
 * gunner has to lead). The raider fights back with its chaingun, whose reach is 3 km.
 * Run with {@code ./gradlew :core:playtestBenchmark --tests '*WeaponDuelBenchmark'}; the tester's
 * checklist is the "Weapons" section of {@code documentation/playtest/Balance benchmarks.md}.
 */
@Tag("playtest")
class WeaponDuelBenchmark {
    static final float FRAME = 1f / 60f;
    /** Longest duel, sim seconds. */
    static final float DUEL_SECONDS = 120f;
    static final float LONG = 4_000f, CLOSE = 1_500f;
    static final String[] GUNS = {"chaingun_mk1", "coilgun_mk1", "railgun_mk1"};

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    /** One duel's outcome. */
    static final class Duel {
        boolean beaten;
        float seconds;
        float raiderHull, ourHull;
        int shots, hits;

        /** Seconds to beat the raider (struck, disarmed or wrecked); infinite when the duel ran out first. */
        double timeToBeat() {
            return beaten ? seconds : Double.POSITIVE_INFINITY;
        }

        String describe() {
            return String.format(Locale.ROOT, "%s in %.0f s; raider hull %.2f, ours %.2f; %d of %d hit",
                    beaten ? "beaten" : "not beaten", seconds, raiderHull, ourHull, hits, shots);
        }
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    /** The corvette, its nose gun swapped for {@code gunId} and every other mount taken off, against the raider at {@code range}. */
    static Duel fight(String gunId, float range) {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        GameWorld world = new GameWorld();
        try {
            GameScenario game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
            game.beginCareer();
            for (int i = 0; i < 2f / FRAME; i++) world.update(FRAME);
            assertTrue(world.docking.undock(game.player));
            for (int i = 0; i < 1f / FRAME; i++) world.update(FRAME);

            WeaponsComponent weapons = game.player.getComponent(WeaponsComponent.class);
            while (weapons.mounts.size > 1) weapons.mounts.removeIndex(weapons.mounts.size - 1);
            WeaponDef def = world.combatDefs.weapons.get(gunId);
            assertNotNull(def, gunId);
            WeaponMountComponent nose = weapons.mounts.get(0);
            nose.def = def;
            nose.cooldown = def.cooldown;
            nose.biasSet = false;

            Vector2 at = PhysicsManager.getPosition(body(game.raider), new Vector2());
            Vector2 v = PhysicsManager.getVelocity(body(game.raider), new Vector2());
            PhysicsManager.setPosition(body(game.player), at.x, at.y + range, -90f * MathUtils.degRad);
            PhysicsManager.setVelocity(body(game.player), v.x, v.y);
            world.update(FRAME);
            game.provokeRaider();

            Duel d = new Duel();
            world.combatEvents.shots.add((s, shot) -> {
                if (shot.shooter == game.player) d.shots++;
            });
            world.combatEvents.hullHits.add((s, h) -> {
                if (h.ship == game.raider) d.hits++;
            });
            PlayerGunnery gun = new PlayerGunnery(world.bridge, world.combatDefs, world.sensors, world.weaponControl, world.missiles, game.player);
            gun.setTarget(game.raider);
            Vector2 raider = new Vector2(), ours = new Vector2();
            float t = 0f;
            for (; t < DUEL_SECONDS && !ShipDisablement.isDisabled(game.raider) && !ShipDisablement.isDisabled(game.player); t += FRAME) {
                gun.fireGuns();
                gun.update(FRAME);
                world.update(FRAME);
                // Hold the range: back off (or close) along the line between the ships
                PhysicsManager.getPosition(body(game.raider), raider);
                PhysicsManager.getPosition(body(game.player), ours);
                Vector2 off = ours.sub(raider);
                if (off.len2() < 1f) off.set(0f, 1f);
                off.setLength(range).add(raider);
                PhysicsManager.setPosition(body(game.player), off.x, off.y, body(game.player).getAngle());
            }
            d.seconds = t;
            d.beaten = ShipDisablement.isDisabled(game.raider);
            d.raiderHull = (float) HostileTraffic.hull(game.raider);
            d.ourHull = (float) HostileTraffic.hull(game.player);
            return d;
        } finally {
            world.dispose();
        }
    }

    private static String secs(Duel d) {
        return d.beaten ? String.format(Locale.ROOT, "%.0f s", d.seconds) : "not beaten";
    }

    @Test
    void theRailgunWinsAtRangeAndTheCannonUpClose() {
        BalanceReport report = new BalanceReport("Weapon duel");
        Map<String, Duel> far = new LinkedHashMap<>(), near = new LinkedHashMap<>();
        for (String g : GUNS) {
            far.put(g, fight(g, LONG));
            near.put(g, fight(g, CLOSE));
            System.out.println("Duel " + g + " from 4 km: " + far.get(g).describe() + "; from 1.5 km: " + near.get(g).describe());
            report.note(g + " from 4 km", far.get(g).describe());
            report.note(g + " from 1.5 km", near.get(g).describe());
        }
        Duel railFar = far.get("railgun_mk1"), cannonFar = far.get("chaingun_mk1"), coilFar = far.get("coilgun_mk1");
        Duel railNear = near.get("railgun_mk1"), cannonNear = near.get("chaingun_mk1"), coilNear = near.get("coilgun_mk1");
        report.expect("From 4 km: the railgun beats the raider soonest, the coilgun next, the chaingun last",
                railFar.timeToBeat() < coilFar.timeToBeat() && coilFar.timeToBeat() < cannonFar.timeToBeat(),
                String.format(Locale.ROOT, "%s, %s, %s", secs(railFar), secs(coilFar), secs(cannonFar)));
        report.expect("From 1.5 km: the chaingun beats the raider soonest, the coilgun next, the railgun last",
                cannonNear.timeToBeat() < coilNear.timeToBeat() && coilNear.timeToBeat() < railNear.timeToBeat(),
                String.format(Locale.ROOT, "%s, %s, %s", secs(cannonNear), secs(coilNear), secs(railNear)));
        double railHits = railFar.shots == 0 ? 0 : (double) railFar.hits / railFar.shots;
        double cannonHits = cannonFar.shots == 0 ? 0 : (double) cannonFar.hits / cannonFar.shots;
        report.expect("From 4 km the railgun lands a bigger share of its rounds than the chaingun", railHits > cannonHits,
                String.format(Locale.ROOT, "%.2f against %.2f", railHits, cannonHits));
        report.finish();
    }
}
