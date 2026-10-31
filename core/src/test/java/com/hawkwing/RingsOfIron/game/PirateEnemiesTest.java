package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.career.Contract;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.LoadoutDef;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.modding.Loadouts;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.hostile.HostileTrafficController;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.shipops.Salvage;
import com.ringsofiron.shipops.ShipDisablement;
import com.ringsofiron.shipops.StationYard;
import com.ringsofiron.shipops.Tactics;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Round 3's enemies: loadouts, raiders in pairs, tactics, crossfire, aces and their bounties. */
class PirateEnemiesTest {
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
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        hostiles = world.macroSim.hostiles();
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void piratesAreArmedFromTheirLoadoutsAndRaidersFlyAloneAtFirst() {
        List<String> raiders = new ArrayList<>();
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            HostileComponent h = e.getValue();
            if (!h.isPirate()) {
                assertNull(h.loadout, "patrols keep their stock weapons");
                continue;
            }
            assertNotNull(h.loadout, h.displayName);
            LoadoutDef l = world.combatDefs.loadouts.get(h.loadout);
            assertEquals(h.shipId, l.ship);
            assertFalse(l.ace);
            WeaponsComponent w = world.engine.getEntity(e.getKey()).getComponent(WeaponsComponent.class);
            for (int i = 0; i < l.weapons.size(); i++) {
                if (!l.weapons.get(i).isEmpty()) assertEquals(l.weapons.get(i), w.mounts.get(i).def.id, h.displayName + " hardpoint " + i);
            }
            assertEquals(HostileTraffic.firepower(world.engine.getEntity(e.getKey())), h.firepower, 1e-6);
            if (HostileTraffic.RAIDER.equals(h.shipId)) raiders.add(e.getKey());
        }
        assertEquals(2, raiders.size());
        // Round 5: at a new ensign's difficulty raiders fly alone; wings come with the difficulty (EnemyWingsTest)
        for (String r : raiders) assertNull(hostiles.get(r).wingOf, "a lone raider");
        assertNull(game.raider.getComponent(HostileComponent.class), "First Play's raider is left alone");
    }

    @Test
    void loadoutsAreDrawnByWeightFromThoseTheDifficultyAllows() {
        List<String> easy = ids(Loadouts.candidates(world.combatDefs, HostileTraffic.RAIDER, 0.5, false));
        assertEquals(Arrays.asList("raider_chaingun", "raider_darts"), easy);
        List<String> hard = ids(Loadouts.candidates(world.combatDefs, HostileTraffic.RAIDER, 2, false));
        assertTrue(hard.containsAll(Arrays.asList("raider_ion", "raider_railgun")), hard.toString());
        assertFalse(hard.contains("raider_ace_railgun"), "ace refits only for aces");
        Random rng = new Random(1);
        int chainguns = 0;
        for (int i = 0; i < 4_000; i++) {
            if (Loadouts.pick(world.combatDefs, HostileTraffic.RAIDER, 0.5, false, rng).id.equals("raider_chaingun")) chainguns++;
        }
        assertEquals(5 / 8.0, chainguns / 4_000.0, 0.03, "weights 5 to 3");
    }

    @Test
    void aHeavyRailgunNeedsAMediumMount() {
        WeaponMountComponent nose = game.player.getComponent(WeaponsComponent.class).mounts.get(0);
        assertNotNull(StationYard.tooSmall(nose, world.combatDefs.weapons.get("railgun_heavy")), "the corvette's nose is small");
        assertNull(StationYard.tooSmall(nose, world.combatDefs.weapons.get("railgun_mk1")));
    }

    @Test
    void captainsPickTheirTactics() {
        Tactics.Arms missiles = new Tactics.Arms();
        missiles.launchers = true;
        missiles.guns = true;
        Tactics.Arms chaingun = new Tactics.Arms();
        chaingun.guns = true;
        chaingun.shortGunsOnly = true;
        // A railgun and autocannons, like the corvette: long and short guns
        Tactics.Arms railgun = new Tactics.Arms();
        railgun.guns = true;
        assertEquals(Tactics.Plan.STANDOFF, Tactics.choose(missiles, 1, 1, true, false));
        assertEquals(Tactics.Plan.RUSH, Tactics.choose(chaingun, 1, 1, true, false));
        assertEquals(Tactics.Plan.FLANK, Tactics.choose(railgun, 2, 1, true, false));
        assertEquals(Tactics.Plan.HOLD, Tactics.choose(railgun, 1, 1, true, false));
        assertEquals(Tactics.Plan.RUN, Tactics.choose(railgun, 0.3, 0.75, true, false), "outgunned and hit");
        assertEquals(Tactics.Plan.HOLD, Tactics.choose(railgun, 0.3, 0.85, true, false), "hit only once");
        assertEquals(Tactics.Plan.HOLD, Tactics.choose(railgun, 0.3, 1, true, false), "not yet hit");
        assertEquals(Tactics.Plan.RUN, Tactics.choose(railgun, 0.8, 0.75, true, true), "losing in a crossfire");
        assertNotEquals(Tactics.Plan.RUN, Tactics.choose(railgun, 0.3, 0.5, false, true), "patrols never run");
    }

    @Test
    void aWingmanFightsWhatItsLeaderFights() {
        String[] pair = wing();
        hostiles.get(pair[0]).targetUuid = HostileComponent.PLAYER;
        Entity lead = bringIn(pair[0], 3_000, 0);
        Entity wing = bringIn(pair[1], 3_000, 500);
        run(3);
        assertSame(game.player, lead.getComponent(SensorComponent.class).lockedTarget);
        assertSame(game.player, wing.getComponent(SensorComponent.class).lockedTarget, "its wingman joins in");
        NpcCombatComponent combat = wing.getComponent(NpcCombatComponent.class);
        assertNotNull(combat);
        // Round 5: beside its leader a wingman swings round behind the enemy, circling the other way
        assertEquals(NpcCombatComponent.Stance.FLANK, combat.stance);
        assertEquals(-1f, combat.side);
        assertEquals(1f, lead.getComponent(NpcCombatComponent.class).side);
    }

    @Test
    void underFireFromTwoSidesAPirateBreaksOffSooner() {
        String id = pirate(HostileTraffic.PIRATE_GUNSHIP);
        HostileComponent h = hostiles.get(id);
        h.targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 3_000, 0);
        run(2);
        Entity cutter = null;
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            if (!e.getValue().isPirate()) {
                cutter = bringIn(e.getKey(), 3_000, 3_000);
                break;
            }
        }
        wallsAt(ship, 0.75f);
        world.hostiles.onShotAt(ship, game.player);
        world.hostiles.onShotAt(ship, cutter);
        run(2);
        assertTrue(h.brokeOff, "at 75% hull, between the lines");
        assertTrue(h.hull > HostileTraffic.BREAK_OFF_HULL && h.hull < HostileTraffic.CROSSFIRE_BREAK_OFF_HULL);
        assertTrue(world.hostiles.notices.stream().anyMatch(n -> n.contains("breaking off")));
    }

    @Test
    void aPirateThatGetsAwayFromThePlayerBecomesAnAceWithAGrudge() {
        String id = pirate(HostileTraffic.RAIDER);
        HostileComponent h = hostiles.get(id);
        String was = h.displayName;
        // A gun runner, whatever the seed drew (round 6: fits come from the career's seed, and a prize
        // taker's ion gun is dearer than an ace's chaingun)
        com.ringsofiron.combatsys.modding.Loadouts.apply(world.engine.getEntity(id), world.combatDefs.loadouts.get("raider_chaingun"),
                world.combatDefs);
        h.loadout = "raider_chaingun";
        h.firepower = HostileTraffic.firepower(world.engine.getEntity(id));
        double firepower = h.firepower;
        h.targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 3_000, 0);
        run(2);
        world.hostiles.onExitBubble(id, world.macroSim.now());
        assertTrue(h.ace && h.grudge);
        assertEquals(1, h.escapes);
        assertTrue(Arrays.asList(PirateAces.CALLSIGNS).contains(h.displayName), h.displayName);
        assertTrue(world.combatDefs.loadouts.get(h.loadout).ace, "refitted: " + h.loadout);
        assertNotNull(PirateAces.bestWeapon(ship), "with its refit's guns");
        assertTrue(h.firepower > firepower, h.firepower + " over " + firepower);
        int crew = 0;
        for (Entity c : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (c.getComponent(GridPositionComponent.class).ship != ship) continue;
            assertTrue(c.getComponent(CrewMemberComponent.class).def.gunnerySkill >= PirateAces.ACE_SKILL);
            crew++;
        }
        assertTrue(crew > 0);
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.startsWith(was + " got away from you")),
                "the player hears of it");
    }

    @Test
    void aPirateThatRobsEnoughFreightersBecomesAnAceWithoutAGrudge() {
        String id = pirate(HostileTraffic.PIRATE_GUNSHIP);
        HostileComponent h = hostiles.get(id);
        h.plunders = PirateAces.PLUNDERS_TO_ACE - 1;
        world.hostiles.plundered(id, "x", "ore", 10, 0);
        assertFalse(h.ace);
        h.plunders = PirateAces.PLUNDERS_TO_ACE;
        world.hostiles.plundered(id, "x", "ore", 10, 0);
        assertTrue(h.ace);
        assertFalse(h.grudge);
    }

    @Test
    void noMoreThanThreeAcesAtOnce() {
        List<HostileComponent> others = new ArrayList<>();
        for (int i = 0; i < PirateAces.MAX_ACES; i++) {
            HostileComponent o = new HostileComponent("x", HostileTraffic.RAIDER, HostileTraffic.PIRATES,
                    HostileComponent.Role.PIRATE, 1);
            o.ace = true;
            others.add(o);
        }
        String id = pirate(HostileTraffic.RAIDER);
        HostileComponent h = hostiles.get(id);
        others.add(h);
        assertFalse(PirateAces.promote(world, world.engine.getEntity(id), h, others, 1, true));
        others.get(0).partDropped = true; // one beaten
        assertTrue(PirateAces.promote(world, world.engine.getEntity(id), h, others, 1, true));
    }

    @Test
    void anAcePaysAFarBiggerBounty() {
        String id = pirate(HostileTraffic.RAIDER);
        HostileComponent h = hostiles.get(id);
        assertTrue(PirateAces.promote(world, world.engine.getEntity(id), h, hostiles.all().values(), 1, false));
        game.beginCareer();
        run(2);
        Contract bounty = null;
        for (Contract c : world.contracts.offers(game.highport)) {
            if (c.type == Contract.Type.BOUNTY && id.equals(c.targetUuid)) bounty = c;
        }
        assertNotNull(bounty, "a bounty on the ace");
        assertTrue(bounty.targetName.startsWith("the ace " + h.displayName), bounty.targetName);
        double plain = Math.round(ContractBoard.BOUNTY_PAY * (1 + bounty.tier) * Difficulty.of(world));
        assertEquals(Math.round(plain * PirateAces.BOUNTY_SCALE), bounty.reward, 1e-6);
        assertEquals(6 + 3 * bounty.tier + PirateAces.BOUNTY_STANDING_PER_TIER * (1 + bounty.tier), bounty.standing, 1e-6);
    }

    @Test
    void aBeatenAceLeavesASpareOfItsBestGunCratedAboard() {
        String id = pirate(HostileTraffic.RAIDER);
        HostileComponent h = hostiles.get(id);
        Entity ship = world.engine.getEntity(id);
        assertTrue(PirateAces.promote(world, ship, h, hostiles.all().values(), 1, true));
        String rare = PirateAces.bestWeapon(ship);
        bringIn(id, 3_000, 0);
        reactors(ship, false);
        for (int i = 0; i < 10 / FRAME && !h.partDropped; i++) world.update(FRAME);
        assertTrue(h.partDropped, "beaten");
        assertFalse(h.grudge);
        assertEquals(1, ship.getComponent(CargoHoldComponent.class).amount(Salvage.PART + rare), 1e-6);
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.startsWith("The ace " + h.displayName + " is beaten")));
        assertEquals(0, PirateAces.alive(hostiles.all().values()), "its place is free");
    }

    @Test
    void anAceBeatenOnceIsNamedByNoReconOrBountyWhenItFliesAgain() {
        // Round 6: Hunt the Hunters sent the frigate to find Iron Dagny, beaten a week before, whose
        // crew had the ship going again out on the rails
        String id = pirate(HostileTraffic.RAIDER);
        HostileComponent h = hostiles.get(id);
        Entity ship = world.engine.getEntity(id);
        assertTrue(PirateAces.promote(world, ship, h, hostiles.all().values(), 1, false));
        bringIn(id, 3_000, 0);
        int giver = world.standing.sponsor();
        com.ringsofiron.macrosim.economy.EconomyNode node = world.macroSim.station(game.highport);
        assertTrue(world.contracts.recons(giver, null).stream().anyMatch(c -> id.equals(c.targetUuid)), "found while at large");
        reactors(ship, false);
        for (int i = 0; i < 10 / FRAME && !h.partDropped; i++) world.update(FRAME);
        assertTrue(h.partDropped, "beaten");
        assertTrue(world.contracts.recons(giver, null).stream().noneMatch(c -> id.equals(c.targetUuid)), "nothing to find in a beaten ship");

        reactors(ship, true);
        for (int i = 0; i < 5 / FRAME && ShipDisablement.isDisabled(ship); i++) world.update(FRAME);
        assertFalse(FirstPlay.beaten(world, ship), "flying again");
        assertTrue(FirstPlay.aceBeaten(ship));
        assertTrue(world.contracts.recons(giver, null).stream().noneMatch(c -> id.equals(c.targetUuid)), "no recon on it");
        assertTrue(world.contracts.bounties(node, giver).stream().noneMatch(c -> id.equals(c.targetUuid)), "nor a bounty");
    }

    @Test
    void anIonStunnedShipIsNotBeaten() {
        String id = pirate(HostileTraffic.RAIDER);
        Entity ship = bringIn(id, 3_000, 0);
        for (Entity e : ship.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.stunned = 30f;
        }
        for (int i = 0; i < 5 / FRAME && !ShipDisablement.isDisabled(ship); i++) world.update(FRAME);
        assertTrue(ShipDisablement.isDisabled(ship), "dark while stunned");
        assertFalse(FirstPlay.beaten(world, ship), "but no bounty is paid for it");
    }

    @Test
    void anAceAndItsWingComeBackFromASave(@TempDir Path dir) {
        String[] pair = wing();
        HostileComponent lead = hostiles.get(pair[0]);
        assertTrue(PirateAces.promote(world, world.engine.getEntity(pair[0]), lead, hostiles.all().values(), 1, true));
        lead.escapes = 2;
        String name = lead.displayName, loadout = lead.loadout;
        FileHandle file = new FileHandle(dir.resolve("quicksave.json").toFile());
        assertNull(game.quickSave(file));
        game.quickLoad(file);
        HostileComponent h = hostiles.get(pair[0]);
        assertTrue(h.ace && h.grudge && !h.partDropped);
        assertEquals(2, h.escapes);
        assertEquals(name, h.displayName);
        assertEquals(loadout, h.loadout);
        assertEquals(pair[0], hostiles.get(pair[1]).wingOf);
        assertNotNull(PirateAces.bestWeapon(world.engine.getEntity(pair[0])), "still carrying its refit");
    }

    // ---------------------------------------------------------------------------------------------

    private static List<String> ids(List<LoadoutDef> defs) {
        List<String> out = new ArrayList<>();
        for (LoadoutDef d : defs) out.add(d.id);
        return out;
    }

    /** A new pair of raiders by Highport: leader, then wingman. */
    private String[] wing() {
        List<String> ids = world.hostiles.spawnWing(HostileTraffic.RAIDER, 2, game.highport, "raider_chaingun");
        assertEquals(2, ids.size());
        assertEquals(ids.get(0), hostiles.get(ids.get(1)).wingOf);
        return new String[]{ids.get(0), ids.get(1)};
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

    private static void reactors(Entity ship, boolean on) {
        for (Entity e : ship.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = on;
        }
    }

    private String pirate(String shipId) {
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            if (e.getValue().isPirate() && shipId.equals(e.getValue().shipId)) return e.getKey();
        }
        fail("no " + shipId);
        return null;
    }

    /** Puts a hostile on the rails {@code dx, dy} meters from the bubble origin and lets it come in. */
    private Entity bringIn(String uuid, float dx, float dy) {
        double now = world.macroSim.now();
        StateVector s = world.macroSim.gateway().anchorStateAt(now, new StateVector());
        world.macroSim.rails().attach(uuid, GameScenario.EARTH, s.set(s.x + dx, s.y + dy, s.vx, s.vy), now);
        Entity ship = world.engine.getEntity(uuid);
        for (int i = 0; i < 120 && ship.getComponent(PhysicsBodyComponent.class) == null; i++) world.update(FRAME);
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class), "came into the bubble");
        return ship;
    }

    private void run(double seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }
}
