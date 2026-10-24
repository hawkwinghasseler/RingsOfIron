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
        world.rescue.jumpBeside(game.player, world.engine.getEntity(world.pirateBase.uuid()), RaidRoutine.JUMP_IN);
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
    void theDefenceFightsOnWhenItsLeaderIsBeatenWhileTheBaseStands() {
        // A rich player is a harder one: a lone defender and a wing of two or more behind it
        game.player.getComponent(com.ringsofiron.macrosim.economy.TraderComponent.class).credits = 50_000_000;
        besideTheBase();
        List<String> wing = new ArrayList<>(world.pirateBase.defenders());
        assertTrue(wing.size() >= 3, "a wing behind the first defender: " + wing);
        run((float) PirateBase.SECOND_WAVE_SECONDS + 2 * PirateBase.CHECK_SECONDS + 1);
        Entity leader = ship(wing.get(1));
        assertNotNull(leader.getComponent(PhysicsBodyComponent.class), "the second wave is out after " + PirateBase.SECOND_WAVE_SECONDS + " s");
        var grid = leader.getComponent(com.ringsofiron.interiorgrid.ShipGridComponent.class);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                var t = grid.getTile(x, y);
                if (t != null && t.isWall()) t.setIntegrity(0.6f);
            }
        }
        run(3);
        assertTrue(leader.getComponent(HostileComponent.class).brokeOff, "the wing's leader breaks off");
        for (String name : wing.subList(2, wing.size())) {
            HostileComponent h = ship(name).getComponent(HostileComponent.class);
            assertFalse(h.brokeOff, name + " stays to defend the base");
            assertNull(h.wingOf, name + " fights on alone");
            assertTrue(world.pirateBase.defending(name));
        }
        assertFalse(world.pirateBase.defending(leader.getComponent(HostileComponent.class).displayName + " (not one)"));
    }

    @Test
    void theBasesDartsInFlightGoBallisticWhenItStrikes() {
        besideTheBase();
        Entity gun = ship(world.pirateBase.guns().get(0));
        var dart = world.combatDefs.missile("dart");
        var at = com.ringsofiron.physicsexterior.PhysicsManager.getPosition(
                game.player.getComponent(PhysicsBodyComponent.class).body, new com.badlogic.gdx.math.Vector2());
        Entity ours = world.missiles.launch(dart, gun, null, game.player, at.x + 3_000, at.y, -100, 0, (float) Math.PI);
        // Another's dart, not the base's, still homes
        Entity other = world.missiles.launch(dart, world.engine.getEntity(world.pirateBase.uuid()), null, game.player,
                at.x, at.y + 3_000, 0, -100, (float) -Math.PI / 2);
        run(1);
        CombatThreats threats = new CombatThreats(world);
        var frame = new com.ringsofiron.uihud.providers.ThreatProvider.ThreatFrame();
        threats.collect(frame);
        assertTrue(frame.incoming() >= 2, "both darts inbound, with any the defence has fired");

        world.pirateBase.fall("a test");
        run(1);
        var m = ours.getComponent(com.ringsofiron.combatsys.components.MissileComponent.class);
        assertTrue(m.guidanceCut);
        assertNull(m.target);
        assertEquals(0f, m.throttle);
        assertEquals(com.ringsofiron.combatsys.components.MissileComponent.Phase.COAST, m.phase);
        frame.clear();
        threats.collect(frame);
        assertEquals(1, frame.incoming(), "only the other dart is inbound once the base strikes: its own all go ballistic");
        assertFalse(other.getComponent(com.ringsofiron.combatsys.components.MissileComponent.class).guidanceCut);
    }

    @Test
    void aStruckBasesGunsStrikeWithItEvenWhenProvoked() {
        besideTheBase();
        run(5);
        // Round 8, real window: two batteries the player had hit went on launching darts after the fall
        for (String name : world.pirateBase.guns()) world.hostiles.onShotAt(ship(name), game.player);
        world.pirateBase.fall("a test");
        run(5);
        for (String name : world.pirateBase.guns()) {
            assertTrue(world.pirateBase.silenced(name));
            assertNull(ship(name).getComponent(SensorComponent.class).lockedTarget, name + " is off the player");
        }
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
    void aBatteryWithoutARailgunReachesAShipHoldingWhereTheRaidHolds() {
        // Round 10: moored 1.5 km apart, and stepped back round the body rather than along the base's
        // path, the nearer battery stood 4 to 6 km from where the raid holds, out of its chainguns' reach,
        // so only a railgun ever landed on the frigate (0.00 to 0.04 of hull a raid)
        besideTheBase();
        List<Entity> guns = new ArrayList<>();
        for (String name : world.pirateBase.guns()) guns.add(ship(name));
        int[] hits = new int[1];
        world.combatEvents.impacts.add((sig, hit) -> {
            if (hit.target == game.player && guns.contains(hit.owner) && hit.def != null && hit.def.id.startsWith("autocannon")) hits[0]++;
        });
        float reach = world.combatDefs.weapons.get("chaingun_mk2").maxRange;
        var at = game.player.getComponent(PhysicsBodyComponent.class).body.getPosition();
        float nearest = Float.MAX_VALUE;
        for (Entity gun : guns) {
            nearest = Math.min(nearest, gun.getComponent(PhysicsBodyComponent.class).body.getPosition().dst(at)
                    * com.ringsofiron.physicsexterior.PhysicsManager.METERS_PER_UNIT);
        }
        assertTrue(nearest < reach, String.format("a battery within a 40 mm chaingun's %.0f m: the nearest is %.0f m off", reach, nearest));
        run(20);
        assertTrue(hits[0] > 0, "a battery's chaingun rounds land on a ship holding where the raid holds");
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
