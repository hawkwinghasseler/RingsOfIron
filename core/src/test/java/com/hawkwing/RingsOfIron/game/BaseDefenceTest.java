package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round 6 (part 4): the pirate base's own guns, the order its defence launches in, and spawn
 * loadouts drawn from the career's seed.
 */
class BaseDefenceTest {
    static final float FRAME = 1f / 60f;
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
        world.update(FRAME);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }

    private Entity ship(String name) {
        for (var e : world.macroSim.hostiles().all().entrySet()) {
            if (name.equals(e.getValue().displayName)) return world.engine.getEntity(e.getKey());
        }
        return null;
    }

    private void besideTheBase() {
        world.docking.undock(game.player);
        run(1);
        world.rescue.jumpBeside(game.player, world.engine.getEntity(world.pirateBase.uuid()), 8_000);
        run(2 * PirateBase.CHECK_SECONDS + 1);
    }

    private int batteries() {
        int n = 0;
        for (HostileComponent h : world.macroSim.hostiles().all().values()) if (HostileTraffic.BATTERY.equals(h.shipId)) n++;
        return n;
    }

    @Test
    void theLightestDefenderLaunchesFirstAndARailgunRaiderNeverLeads() {
        String lurk = game.highport;
        List<String> mixed = new ArrayList<>();
        mixed.addAll(world.hostiles.spawnWing(HostileTraffic.RAIDER, 1, lurk, "raider_railgun"));
        mixed.addAll(world.hostiles.spawnWing(HostileTraffic.RAIDER, 1, lurk, "raider_chaingun"));
        mixed.addAll(world.hostiles.spawnWing(HostileTraffic.RAIDER, 1, lurk, "raider_darts"));
        List<String> order = world.pirateBase.launchOrder(mixed);
        assertEquals(List.of(mixed.get(2), mixed.get(1), mixed.get(0)), order, "darts, chaingun, railgun: lightest first");

        // A railgun raider lighter than the rest still never leads
        List<String> heavy = new ArrayList<>();
        heavy.addAll(world.hostiles.spawnWing(HostileTraffic.RAIDER, 1, lurk, "raider_railgun"));
        heavy.addAll(world.hostiles.spawnWing(HostileTraffic.PIRATE_GUNSHIP, 1, lurk, "gunship_stock"));
        assertEquals(List.of(heavy.get(1), heavy.get(0)), world.pirateBase.launchOrder(heavy));
        // ... unless every defender carries one
        List<String> snipers = world.hostiles.spawnWing(HostileTraffic.RAIDER, 2, lurk, "raider_railgun");
        assertEquals(new java.util.HashSet<>(snipers), new java.util.HashSet<>(world.pirateBase.launchOrder(snipers)));
    }

    @Test
    void theBaseMansItsGunsAtItsFirstScrambleAndTheyHoldTheirMooring(@TempDir Path dir) {
        assertEquals(0, batteries(), "no guns before the first raid: the first weeks are as before");
        besideTheBase();
        List<String> guns = new ArrayList<>(world.pirateBase.guns());
        assertEquals(PirateBase.BATTERIES, guns.size());
        assertEquals(PirateBase.BATTERIES, batteries());
        run(5);
        for (String name : guns) {
            Entity gun = ship(name);
            HostileComponent h = gun.getComponent(HostileComponent.class);
            assertTrue(h.moored(), name + " is moored");
            assertNotNull(gun.getComponent(PhysicsBodyComponent.class), name + " is in the bubble beside the base");
            assertNull(gun.getComponent(NpcCombatComponent.class), "nothing flies it");
            assertSame(game.player, gun.getComponent(SensorComponent.class).lockedTarget, "its guns are on the player");
        }

        // A save keeps them, moored, and a later raid meets the same guns, not new ones
        FileHandle file = new FileHandle(dir.resolve("guns.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            assertEquals(guns, fresh.pirateBase.guns());
            for (int i = 0; i < 4 * PirateBase.CHECK_SECONDS / FRAME; i++) fresh.update(FRAME);
            int n = 0;
            for (HostileComponent h : fresh.macroSim.hostiles().all().values()) {
                if (!HostileTraffic.BATTERY.equals(h.shipId)) continue;
                n++;
                assertTrue(h.moored());
            }
            assertEquals(PirateBase.BATTERIES, n, "the same guns");
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void theBasesGunsAreNoBountyNoReconAndNoAce() {
        besideTheBase();
        for (String name : world.pirateBase.guns()) {
            String uuid = world.universe.uuidOf(ship(name));
            HostileComponent h = world.macroSim.hostiles().get(uuid);
            assertFalse(PirateAces.promote(world, ship(name), h, world.macroSim.hostiles().all().values(), 3, true));
            for (String station : new String[]{game.highport, game.shipyard}) {
                for (Contract c : world.contracts.offers(station)) assertNotEquals(uuid, c.targetUuid, "no contract on " + name);
            }
        }
    }

    @Test
    void spawnLoadoutsComeFromTheCareersSeed() {
        assertEquals(fits(1), fits(1), "the same seed meets the same fits");
        assertNotEquals(fits(1), fits(2), "another seed, other fits");
    }

    /** The loadouts of the pirates a seeded career starts with, and of the next ten raiders it spawns. */
    private List<String> fits(long seed) {
        GameWorld w = new GameWorld();
        try {
            GameScenario g = GameScenario.start(w, assets, assets.resolve("no-mods-here"), NewGame.seeded(seed, "patrol"));
            List<String> out = new ArrayList<>();
            for (HostileComponent h : w.macroSim.hostiles().all().values()) if (h.isPirate()) out.add(h.displayName + ":" + h.loadout);
            out.sort(null);
            for (String id : w.hostiles.raid(g.highport, 10)) out.add(w.macroSim.hostiles().get(id).loadout);
            return out;
        } finally {
            w.dispose();
        }
    }
}
