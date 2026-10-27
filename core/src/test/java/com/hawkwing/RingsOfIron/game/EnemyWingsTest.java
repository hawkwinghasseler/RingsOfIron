package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.hostile.HostileTrafficController;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Round 5's enemies (planning/round5-plan.md, part 4): wings sized by the difficulty that fight and
 * break off together, kiting and flanking, the pirates' own gunship, an ace on a gunship once the
 * player flies one, and an ace's bounty on the boards the day it appears. {@link EnemySurveyTest}
 * fights them.
 */
class EnemyWingsTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    HostileTrafficController hostiles;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(2026, "patrol"));
        hostiles = world.macroSim.hostiles();
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void wingsGrowWithTheDifficulty() {
        assertEquals(1, Difficulty.wingSize(1.0), "a new ensign meets lone raiders");
        assertEquals(2, Difficulty.wingSize(Difficulty.PAIRS));
        assertEquals(3, Difficulty.wingSize(Difficulty.THREES));
        assertEquals(HostileTraffic.raidersFor(1.5), Difficulty.raiders(1.5));
        assertEquals(HostileTraffic.gunshipsFor(2.5), Difficulty.gunships(2.5));

        world.hostiles.difficulty = () -> 5.0;
        world.hostiles.topUpNow();
        Map<String, Integer> wings = new HashMap<>();
        for (HostileComponent h : hostiles.all().values()) if (h.wingOf != null) wings.merge(h.wingOf, 1, Integer::sum);
        assertFalse(wings.isEmpty(), "raiders fly in wings now");
        for (Map.Entry<String, Integer> w : wings.entrySet()) {
            assertTrue(w.getValue() <= 2, "no more than three to a wing: " + w);
            assertEquals(HostileTraffic.RAIDER, hostiles.get(w.getKey()).shipId, "led by a raider");
        }
        assertTrue(wings.containsValue(2), "a wing of three: " + wings);
    }

    @Test
    void aWingBreaksOffWithItsLeaderAndFiresAsItGoes() {
        // The hull is set by hand here: the player's crew hold fire (round 11's crewed guns would add their own)
        world.gunCrews.holdFire = true;
        List<String> ids = world.hostiles.spawnWing(HostileTraffic.RAIDER, 3, game.highport, "raider_chaingun");
        assertEquals(3, ids.size());
        HostileComponent lead = hostiles.get(ids.get(0));
        lead.targetUuid = HostileComponent.PLAYER;
        // Inside their chainguns' reach (round 7: a wingman still on its run in fights on instead)
        Entity leader = bringIn(ids.get(0), 2_500, 0);
        Entity a = bringIn(ids.get(1), 2_500, 400);
        Entity b = bringIn(ids.get(2), 2_500, -400);
        run(3);
        for (Entity w : new Entity[]{a, b}) {
            assertSame(game.player, w.getComponent(SensorComponent.class).lockedTarget, "the wing fights together");
            assertEquals(NpcCombatComponent.Stance.FLANK, w.getComponent(NpcCombatComponent.class).stance);
        }
        wallsAt(leader, 0.6f);
        // In reach the leader holds to fire first (round 10), then goes
        run(HostileTraffic.BREAK_OFF_HOLD + 3);
        assertTrue(lead.brokeOff, "the leader breaks off");
        for (int i = 1; i < 3; i++) {
            HostileComponent h = hostiles.get(ids.get(i));
            assertTrue(h.brokeOff, h.displayName + " breaks off with it");
            assertTrue(h.hull > HostileTraffic.BREAK_OFF_HULL, "though it was not hurt");
        }
        assertTrue(world.hostiles.notices.stream().anyMatch(n -> n.endsWith("is breaking off with its leader")));
        for (Entity w : new Entity[]{leader, a, b}) {
            assertNull(w.getComponent(NpcCombatComponent.class), "done fighting");
            assertSame(game.player, w.getComponent(SensorComponent.class).lockedTarget, "but firing as it goes");
        }
    }

    @Test
    void aWingmanStillOnItsRunInFightsOnWithoutItsLeader() {
        List<String> ids = world.hostiles.spawnWing(HostileTraffic.RAIDER, 2, game.highport, "raider_chaingun");
        HostileComponent lead = hostiles.get(ids.get(0));
        lead.targetUuid = HostileComponent.PLAYER;
        HostileComponent wing = hostiles.get(ids.get(1));
        wing.targetUuid = HostileComponent.PLAYER;
        // Out past its chaingun's 3 km, as a frigate's railguns beat a leader (round 6: its wingman went home unfired)
        Entity leader = bringIn(ids.get(0), 6_000, 0);
        Entity w = bringIn(ids.get(1), 6_000, 400);
        run(3);
        wallsAt(leader, 0.6f);
        run(3);
        assertTrue(lead.brokeOff, "the leader breaks off");
        assertFalse(wing.brokeOff, "its wingman has not fired yet, so it keeps coming");
        assertNull(wing.wingOf, "and fights on alone");
        assertNotNull(w.getComponent(NpcCombatComponent.class));
        assertEquals(NpcCombatComponent.Stance.RUSH, w.getComponent(NpcCombatComponent.class).stance, "closing to its guns' reach");
        assertTrue(world.hostiles.notices.stream().anyMatch(n -> n.endsWith("fights on without its leader")));
    }

    @Test
    void aSniperKitesAndAShortGunnedShipWithDartsClosesIn() {
        List<String> ids = world.hostiles.spawnWing(HostileTraffic.RAIDER, 1, game.highport, "raider_railgun");
        hostiles.get(ids.get(0)).targetUuid = HostileComponent.PLAYER;
        Entity sniper = bringIn(ids.get(0), 3_000, 0);
        run(3);
        NpcCombatComponent combat = sniper.getComponent(NpcCombatComponent.class);
        assertEquals(NpcCombatComponent.Stance.KITE, combat.stance, "railgun against the corvette's autocannons");
        assertEquals(Tactics.KITE_RANGE, combat.preferredRange, 1f);

        Tactics.Arms railgun = new Tactics.Arms();
        railgun.guns = true;
        railgun.longGunsOnly = true;
        assertEquals(Tactics.Plan.FLANK, Tactics.choose(railgun, 2, 1, true, false), "a plain sniper that clearly wins goes round");
        assertEquals(Tactics.Plan.KITE, Tactics.choose(railgun, 2, 1, false, false, true, false), "an ace keeps its range");
        Tactics.Arms brawler = new Tactics.Arms();
        brawler.guns = true;
        brawler.shortGunsOnly = true;
        brawler.launchers = true;
        assertEquals(Tactics.Plan.RUSH, Tactics.choose(brawler, 0.5, 1, true, false), "no standing off out of its guns' reach");
        Tactics.Arms reach = new Tactics.Arms();
        reach.reach = 3_000f;
        assertEquals(3_000f * Tactics.KITE_REACH, Tactics.kiteRange(reach), 1e-3f);
    }

    @Test
    void piratesFlyTheirOwnGunshipAndPatrolsTheirs() {
        int pirates = 0;
        for (HostileComponent h : hostiles.all().values()) {
            if (h.isPirate() && HostileTraffic.gunshipHull(h.shipId)) {
                assertEquals(HostileTraffic.PIRATE_GUNSHIP, h.shipId);
                assertEquals(HostileTraffic.PIRATE_GUNSHIP, world.combatDefs.loadouts.get(h.loadout).ship);
                pirates++;
            } else if (!h.isPirate()) {
                assertEquals(HostileTraffic.GUNSHIP, h.shipId, "the patrols keep the gunship");
            }
        }
        assertEquals(HostileTraffic.gunshipsFor(1), pirates);
        // The railgun fits come with the difficulty
        assertTrue(com.ringsofiron.combatsys.modding.Loadouts.candidates(world.combatDefs, HostileTraffic.PIRATE_GUNSHIP, 1.0, false)
                .stream().noneMatch(l -> l.id.equals("gunship_line") || l.id.equals("gunship_heavy_railgun")));
        assertTrue(com.ringsofiron.combatsys.modding.Loadouts.candidates(world.combatDefs, HostileTraffic.PIRATE_GUNSHIP, 2.0, false)
                .stream().anyMatch(l -> l.id.equals("gunship_heavy_railgun")));
    }

    @Test
    void anAceTakesAPirateGunshipOnceThePlayerFliesOne() {
        game.beginCareer();
        run(2);
        world.hostiles.topUpNow();
        assertFalse(Difficulty.fliesAtLeast(world, HostileTraffic.PIRATE_GUNSHIP), "the corvette is smaller");
        assertEquals(0, PirateAces.alive(hostiles.all().values()));

        // A Lieutenant Commander buys the gunship
        assertTrue(world.docking.undock(game.player));
        run(1);
        assertTrue(game.dockAt(game.shipyard));
        run(2);
        float need = world.standing.ranks().get(2).standing - world.standing.standing(world.standing.sponsor());
        world.standing.reward(world.standing.sponsor(), need + 0.5f, "test");
        game.player.getComponent(TraderComponent.class).credits = 400_000;
        String said = world.shipyard.buy("patrol_gunship");
        assertTrue(said.startsWith("Bought"), said);
        assertTrue(Difficulty.fliesAtLeast(world, HostileTraffic.PIRATE_GUNSHIP));

        world.hostiles.topUpNow();
        String ace = null;
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            if (e.getValue().ace) ace = e.getKey();
        }
        assertNotNull(ace, "an ace flies a pirate gunship");
        HostileComponent h = hostiles.get(ace);
        assertEquals(HostileTraffic.PIRATE_GUNSHIP, h.shipId);
        assertTrue(world.combatDefs.loadouts.get(h.loadout).ace, "on an ace's refit: " + h.loadout);
        assertFalse(h.grudge);
        String name = h.displayName;
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.contains("the ace " + name + " flies a pirate gunship")));
        assertTrue(bountyOn(ace), "its bounty is up at once");
        // Only the one
        world.hostiles.topUpNow();
        assertEquals(1, PirateAces.alive(hostiles.all().values()));
    }

    @Test
    void anAcesBountyIsPostedTheDayItAppears() {
        game.beginCareer();
        run(2);
        String id = null;
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            if (e.getValue().isPirate() && HostileTraffic.RAIDER.equals(e.getValue().shipId)) id = e.getKey();
        }
        assertNotNull(id);
        // Today's boards are up before it becomes an ace
        world.contracts.offers(game.highport);
        assertFalse(aceBountyOn(id));
        HostileComponent h = hostiles.get(id);
        h.targetUuid = HostileComponent.PLAYER;
        bringIn(id, 3_000, 0);
        run(2);
        world.hostiles.onExitBubble(id, world.macroSim.now());
        assertTrue(h.ace, "it got away");
        assertTrue(aceBountyOn(id), "the bounty on the ace is on Highport's board today");
        Contract c = null;
        for (Contract o : world.contracts.offers(game.highport)) if (id.equals(o.targetUuid)) c = o;
        assertTrue(c.targetName.startsWith("the ace " + h.displayName), c.targetName);
        // The next day's board takes it up, once
        double nextDay = (Math.floor(world.engine.getSimSeconds() / ContractBoard.DAY) + 1) * ContractBoard.DAY;
        assertEquals(nextDay, c.deadline, 1e-6);
        world.macroSim.advanceTo(nextDay + 60);
        for (int i = 0; i < 5; i++) world.update(FRAME);
        int listed = 0;
        for (Contract o : world.contracts.offers(game.highport)) if (id.equals(o.targetUuid)) listed++;
        assertTrue(listed <= 1, listed + " offers on the ace");
    }

    // ---------------------------------------------------------------------------------------------

    private boolean bountyOn(String uuid) {
        for (String s : new String[]{game.highport, game.shipyard}) {
            for (Contract c : world.contracts.offers(s)) if (c.type == Contract.Type.BOUNTY && uuid.equals(c.targetUuid)) return true;
        }
        return false;
    }

    private boolean aceBountyOn(String uuid) {
        for (Contract c : world.contracts.offers(game.highport)) {
            if (c.type == Contract.Type.BOUNTY && uuid.equals(c.targetUuid) && c.targetName.startsWith("the ace")) return true;
        }
        return false;
    }

    private Entity bringIn(String uuid, float dx, float dy) {
        double now = world.macroSim.now();
        StateVector s = world.macroSim.gateway().anchorStateAt(now, new StateVector());
        world.macroSim.rails().attach(uuid, world.macroSim.gateway().anchorParentUuid(), s.set(s.x + dx, s.y + dy, s.vx, s.vy), now);
        Entity ship = world.engine.getEntity(uuid);
        for (int i = 0; i < 120 && ship.getComponent(PhysicsBodyComponent.class) == null; i++) world.update(FRAME);
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class), "came into the bubble");
        return ship;
    }

    private static void wallsAt(Entity ship, float integrity) {
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null && t.isWall()) t.setIntegrity(integrity);
            }
        }
    }

    private void run(double seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }
}
