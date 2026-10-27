package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.hostile.HostileTrafficController;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.orbital.warp.TimeWarpController;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pirates and patrols as the game spawns, flies and fights them. */
class HostileTrafficGameTest {
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
    void aNewGameHasPiratesAndPatrolsOnTheRails() {
        int raiders = 0, gunships = 0, patrols = 0;
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            HostileComponent h = e.getValue();
            Entity ship = world.engine.getEntity(e.getKey());
            assertNotNull(ship.getComponent(ShipGridComponent.class), h.displayName + " has its decks");
            assertNull(ship.getComponent(PhysicsBodyComponent.class), h.displayName + " is on the rails");
            assertTrue(world.macroSim.rails().isOnRails(e.getKey()));
            assertTrue(h.firepower > 0, h.displayName + " is armed");
            ShipTaskBoardComponent board = ship.getComponent(ShipTaskBoardComponent.class);
            assertNotNull(board, h.displayName + " has a crew");
            if (h.isPirate()) {
                assertEquals(HostileTraffic.PIRATE_CREW, board.faction);
                if (HostileTraffic.PIRATE_GUNSHIP.equals(h.shipId)) gunships++;
                else raiders++;
            } else {
                patrols++;
            }
        }
        assertEquals(HostileTraffic.raidersFor(1), raiders);
        assertEquals(HostileTraffic.gunshipsFor(1), gunships);
        assertEquals(2, patrols, "the sponsor's and Selene's");
        assertNull(game.raider.getComponent(HostileComponent.class), "the scripted raider stays for First Play");
    }

    @Test
    void moreDifficultyMeansMorePirates() {
        assertTrue(HostileTraffic.raidersFor(2) > HostileTraffic.raidersFor(1));
        assertTrue(HostileTraffic.gunshipsFor(2) > HostileTraffic.gunshipsFor(1));
        assertEquals(0, HostileTraffic.gunshipsFor(0));
    }

    @Test
    void aHostileComesIntoTheBubbleOnItsOwnLayout() {
        String id = pirate(HostileTraffic.PIRATE_GUNSHIP);
        Entity ship = bringIn(id, 3_000, 0);
        PhysicsBodyComponent pb = ship.getComponent(PhysicsBodyComponent.class);
        assertEquals(world.shipMass(ship).mass, pb.body.getMass(), 1f, "weighs what its decks do");
        HudMarkerComponent marker = ship.getComponent(HudMarkerComponent.class);
        assertEquals(HudMarkerComponent.Kind.HOSTILE, marker.kind);
        assertEquals(hostiles.get(id).displayName, marker.label);
        assertTrue(game.contacts.contains(ship, true), "and lockable");
    }

    @Test
    void aPirateHuntingThePlayerFightsThem() {
        String id = pirate(HostileTraffic.PIRATE_GUNSHIP);
        hostiles.get(id).targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 3_000, 0);
        run(2);
        assertSame(game.player, ship.getComponent(NpcCombatComponent.class).target);
        assertSame(game.player, ship.getComponent(SensorComponent.class).lockedTarget);
    }

    @Test
    void aPirateLeavesABeatenPlayerDriftingRatherThanFiringOn() {
        // Round 6: the railgun ace went on shooting a disabled corvette, and its wounded, to pieces
        String id = pirate(HostileTraffic.PIRATE_GUNSHIP);
        HostileComponent h = hostiles.get(id);
        h.targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 3_000, 0);
        run(2);
        assertSame(game.player, ship.getComponent(SensorComponent.class).lockedTarget);
        // Beaten: its reactor down
        for (Entity e : game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = false;
        }
        for (int i = 0; i < 60 && !ShipDisablement.isDisabled(game.player); i++) world.update(FRAME);
        // Then hit, too badly to board (round 7's PirateBoarding) though not enough to break off
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null && t.isWall()) t.setIntegrity(0.68f);
            }
        }
        run(3);
        assertTrue(ShipDisablement.isDisabled(game.player));
        assertTrue(h.brokeOff);
        assertNull(ship.getComponent(SensorComponent.class).lockedTarget, "its guns go cold");
        assertNull(ship.getComponent(NpcCombatComponent.class));
        assertNotNull(ship.getComponent(NpcPilotComponent.class), "and it flies off");
        assertTrue(world.hostiles.notices.stream().anyMatch(n -> n.contains("leaves you drifting")));
    }

    @Test
    void aPirateFightsOnWhileThePlayerIsDarkFromAnIonStun() {
        // Seed 2026's ion raider stunned the corvette's reactor and flew off as if it had won
        // First Play's raider, 1.3 km off with its point defense up, would draw the gunship's fire
        // (as in PirateBoardingTest)
        world.engine.removeEntity(game.raider);
        world.update(FRAME);
        String id = pirate(HostileTraffic.PIRATE_GUNSHIP);
        HostileComponent h = hostiles.get(id);
        h.targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 3_000, 0);
        run(2);
        for (Entity e : game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.stunned = 20f;
        }
        run(3);
        assertTrue(ShipDisablement.isDisabled(game.player), "dark while the stun lasts");
        assertFalse(h.brokeOff, "a stun is not a beaten ship");
        assertSame(game.player, ship.getComponent(SensorComponent.class).lockedTarget);
    }

    @Test
    void aPirateBreaksOffBeforeItIsBeatenEnoughToSurrender() {
        String id = pirate(HostileTraffic.PIRATE_GUNSHIP);
        HostileComponent h = hostiles.get(id);
        h.targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 6_000, 0); // out of its guns' reach: in it, it holds to fire first (round 10)
        run(2);
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null && t.isWall()) t.setIntegrity(0.6f);
            }
        }
        run(2);
        assertTrue(h.brokeOff);
        assertTrue(h.hull < HostileTraffic.BREAK_OFF_HULL);
        assertNull(ship.getComponent(NpcCombatComponent.class), "stops fighting");
        NpcPilotComponent pilot = ship.getComponent(NpcPilotComponent.class);
        assertNotNull(pilot, "and flies off");
        assertTrue(pilot.burns.isEmpty() && pilot.seekTarget == null);
        assertTrue(world.hostiles.notices.stream().anyMatch(n -> n.contains("breaking off")));
    }

    @Test
    void aRaiderWornToItsLineAsItReachesItsGunsFiresBeforeItBreaksOff() {
        // Round 10: survey seed 1's raider reached 0.65 of its hull just as it crossed its chaingun's 3 km
        // from the frigate, and turned away without a shot
        String id = pirate(HostileTraffic.RAIDER);
        HostileComponent h = hostiles.get(id);
        h.targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 3_000, 0);
        float reach = Tactics.arms(ship).reach;
        int[] fired = new int[1];
        double[] firstShot = {Double.NaN};
        world.combatEvents.shots.add((sig, shot) -> {
            if (shot.shooter != ship) return;
            fired[0]++;
            if (Double.isNaN(firstShot[0])) firstShot[0] = world.engine.getSimSeconds();
        });
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null && t.isWall()) t.setIntegrity(0.6f);
            }
        }
        double brokeOffAt = Double.NaN;
        for (int i = 0; i < 60 * 30 && Double.isNaN(brokeOffAt); i++) {
            world.update(FRAME);
            if (h.brokeOff) brokeOffAt = world.engine.getSimSeconds();
        }
        assertTrue(h.hull < HostileTraffic.BREAK_OFF_HULL, "below its line from the start: " + h.hull);
        assertTrue(reach >= 3_000f, "in its guns' reach: " + reach);
        assertFalse(Double.isNaN(brokeOffAt), "it breaks off in the end");
        assertTrue(fired[0] > 0 && firstShot[0] < brokeOffAt, "it fired first: " + fired[0] + " rounds, the first at "
                + firstShot[0] + ", off at " + brokeOffAt);
    }

    @Test
    void aPirateInReachWaitsForTheResetItsCrewHaveInHandBeforeItStrikes() {
        // Round 10: survey seed 1's raider struck for silenced guns as it crossed its chaingun's 3 km from
        // the frigate, its engineer a moment from resetting the turret mount
        String id = pirate(HostileTraffic.RAIDER);
        hostiles.get(id).targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 2_000, 0);
        run(2);
        ShipTaskBoardComponent board = ship.getComponent(ShipTaskBoardComponent.class);
        assertTrue(board.resetsPowerFirst, "a pirate crew");
        java.util.List<Entity> guns = new java.util.ArrayList<>();
        for (com.ringsofiron.combatsys.components.WeaponMountComponent m
                : ship.getComponent(com.ringsofiron.combatsys.components.WeaponsComponent.class).mounts) {
            if (!m.def.pointDefense && m.subsystem != null) guns.add(m.subsystem);
        }
        assertFalse(guns.isEmpty());
        com.ringsofiron.interiorgrid.crew.ResetShortTask reset = new com.ringsofiron.interiorgrid.crew.ResetShortTask(guns.get(0), 0, 0);
        board.post(reset);
        board.claim(reset, new Entity());
        for (int i = 0; i < 120; i++) {
            for (Entity g : guns) {
                SubsystemComponent s = g.getComponent(SubsystemComponent.class);
                s.shorted = true;
                s.isOperational = false;
            }
            world.update(FRAME);
        }
        assertNotEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(ship),
                "its crew are at the reset");
        board.resetsPowerFirst = false; // as any crew but the pirates'
        for (int i = 0; i < 120 && ShipDisablement.reason(ship) == null; i++) {
            for (Entity g : guns) {
                SubsystemComponent s = g.getComponent(SubsystemComponent.class);
                s.shorted = true;
                s.isOperational = false;
            }
            world.update(FRAME);
        }
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(ship));
    }

    @Test
    void theHoldToFireComesThroughASave() {
        String id = pirate(HostileTraffic.RAIDER);
        HostileComponent h = hostiles.get(id);
        h.targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 3_000, 0);
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null && t.isWall()) t.setIntegrity(0.6f);
            }
        }
        run(3);
        assertFalse(h.brokeOff, "holding to fire");
        Double since = world.hostiles.holdingSince(id);
        assertNotNull(since);
        com.badlogic.gdx.utils.Json json = new com.badlogic.gdx.utils.Json();
        java.io.StringWriter out = new java.io.StringWriter();
        json.setWriter(out);
        json.writeObjectStart();
        world.hostiles.write(json);
        json.writeObjectEnd();
        world.hostiles.read(json, new com.badlogic.gdx.utils.JsonReader().parse(out.toString()));
        assertEquals(since, world.hostiles.holdingSince(id), "the hold's start, not a fresh hold");
    }

    @Test
    void aPatrolEngagesAPirateInTheBubble() {
        String pirate = pirate(HostileTraffic.RAIDER);
        String patrol = null;
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            if (!e.getValue().isPirate()) patrol = e.getKey();
        }
        Entity raider = bringIn(pirate, 4_000, 1_500);
        Entity cutter = bringIn(patrol, 4_000, -1_500);
        run(2);
        assertSame(raider, cutter.getComponent(NpcCombatComponent.class).target);
        assertSame(raider, cutter.getComponent(SensorComponent.class).lockedTarget);
        // ...and the raider, shot at, fights back
        world.hostiles.onShotAt(raider, cutter);
        run(2);
        assertSame(cutter, raider.getComponent(NpcCombatComponent.class).target);
    }

    @Test
    void aPatrolHitByThePlayersStrayFireKeepsToThePirate() {
        // Round 5: a cutter hit once in the player's fight with an ace turned on the player, and
        // followed them to Highport's berth with its railgun
        String pirate = pirate(HostileTraffic.RAIDER);
        String patrol = null;
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            if (!e.getValue().isPirate()) patrol = e.getKey();
        }
        Entity raider = bringIn(pirate, 4_000, 1_500);
        Entity cutter = bringIn(patrol, 4_000, -1_500);
        run(2);
        world.hostiles.onShotAt(cutter, game.player);
        run(2);
        assertSame(raider, cutter.getComponent(NpcCombatComponent.class).target);
        assertSame(raider, cutter.getComponent(SensorComponent.class).lockedTarget);
    }

    @Test
    void aPirateHitByAnotherPiratesStrayFireKeepsToItsTarget() {
        // Round 7: the base's two batteries, moored in a line, turned on each other after one's
        // rounds hit the other, and landed nothing on the player for the rest of the raid
        String a = null, b = null;
        for (Map.Entry<String, HostileComponent> e : hostiles.all().entrySet()) {
            if (!e.getValue().isPirate()) continue;
            if (a == null) a = e.getKey();
            else if (b == null) b = e.getKey();
        }
        assertNotNull(b, "two pirates");
        hostiles.get(a).targetUuid = HostileComponent.PLAYER;
        hostiles.get(b).targetUuid = HostileComponent.PLAYER;
        Entity first = bringIn(a, 3_000, 500);
        Entity second = bringIn(b, 3_000, -500);
        run(2);
        assertSame(game.player, second.getComponent(NpcCombatComponent.class).target);
        world.hostiles.onShotAt(second, first);
        run(2);
        assertSame(game.player, second.getComponent(NpcCombatComponent.class).target, "keeps to the player");
        assertSame(game.player, second.getComponent(SensorComponent.class).lockedTarget);
    }

    @Test
    void aPirateWithDryTanksStrikesInAFight() {
        // Round 7, seed 12: an ace that had robbed eleven freighters met the gunship with empty
        // tanks and drifted 400 s, neither side beaten
        String pirate = pirate(HostileTraffic.RAIDER);
        hostiles.get(pirate).targetUuid = HostileComponent.PLAYER;
        Entity raider = bringIn(pirate, 3_000, 0);
        run(2);
        assertNotNull(raider.getComponent(NpcCombatComponent.class), "it fights");
        assertFalse(ShipDisablement.isDisabled(raider));
        for (SubsystemComponent tank : com.ringsofiron.interiorgrid.TankSupply.tanks(
                raider.getComponent(ShipSubsystemRegistryComponent.class), com.ringsofiron.interiorgrid.Fluid.PROPELLANT)) {
            tank.contents = 0;
        }
        for (int i = 0; i < 6 / FRAME && !ShipDisablement.isDisabled(raider); i++) world.update(FRAME);
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(raider));
    }

    @Test
    void aPirateWhoseEnginesLosePowerIsNotDry() {
        // Round 8: a slug that shorts a raider's reactor leaves its engines unfed, which read as out of
        // fuel; raiders struck for dry tanks at 4 km with full ones, their chaingun never fired
        String pirate = pirate(HostileTraffic.RAIDER);
        hostiles.get(pirate).targetUuid = HostileComponent.PLAYER;
        Entity raider = bringIn(pirate, 3_000, 0);
        run(2);
        for (Entity e : raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == SubsystemComponent.SubsystemType.ENGINE) s.switchedOn = false;
        }
        run(1);
        assertTrue(raider.getComponent(com.ringsofiron.physicsexterior.components.ThrusterComponent.class).isOutOfFuel(),
                "its engines draw nothing");
        run(5);
        assertFalse(ShipDisablement.isDisabled(raider), "its tanks are full: " + ShipDisablement.reason(raider));
    }

    @Test
    void aPirateTakesAFreightersCargoAlongsideAndLeaves() {
        for (int i = 0; i < 60; i++) world.update(FRAME);
        Entity freighter = null;
        for (String uuid : world.macroSim.bubble().inBubble()) {
            Entity e = world.engine.getEntity(uuid);
            if (e.getComponent(FreighterComponent.class) != null) freighter = e;
        }
        assertNotNull(freighter, "the freighter docked ahead is in the bubble");
        FreighterComponent f = freighter.getComponent(FreighterComponent.class);
        f.state = FreighterComponent.State.IN_BUBBLE;
        f.resumeState = FreighterComponent.State.TO_DELIVERY;
        f.destinationUuid = game.depot;
        f.cargoCommodity = "water";
        f.cargoQuantity = 50;

        String id = pirate(HostileTraffic.RAIDER);
        HostileComponent h = hostiles.get(id);
        h.targetUuid = freighter.getComponent(UuidComponent.class).id;
        h.ambushStationUuid = game.depot;
        float[] at = bubbleOffsetOf(freighter);
        Entity ship = bringIn(id, at[0], at[1] - 700);
        run(HostileTraffic.PLUNDER_SECONDS + 5);

        assertEquals(0, f.cargoQuantity, 1e-9, "the hold is empty");
        assertEquals(1, h.plunders);
        assertEquals("water", h.lootCommodity);
        assertTrue(h.brokeOff, "and the pirate leaves");
        assertNull(ship.getComponent(SensorComponent.class).lockedTarget, "without firing a shot");
        assertTrue(world.hostiles.notices.stream().anyMatch(n -> n.contains("robbed")));
    }

    @Test
    void aPirateGoingForThePlayerStopsTimeWarp() {
        for (int i = 0; i < 60; i++) world.update(FRAME);
        hostiles.playerGraceUntil = 0;
        // No pirate dares the starter corvette at the start (round 5); one with a grudge does
        hostiles.get(pirate(HostileTraffic.PIRATE_GUNSHIP)).grudge = true;
        assertTrue(world.timeWarp.setWarp(1_000), world.engine.getRailsWarpBlockedReason());
        TimeWarpController.DropReason[] reason = {null};
        world.timeWarp.changes.add((signal, change) -> {
            if (!change.rails && reason[0] == null) reason[0] = change.reason;
        });
        for (int i = 0; i < 600 && world.timeWarp.isRailsWarp(); i++) world.update(FRAME);
        assertFalse(world.timeWarp.isRailsWarp(), "dropped out of warp");
        assertEquals(TimeWarpController.DropReason.EXTERNAL, reason[0]);
        assertTrue(hostiles.all().values().stream().anyMatch(HostileComponent::huntingPlayer));
        assertTrue(world.hostiles.notices.stream().anyMatch(n -> n.contains("intercept you")));
        // Warping on again is the player's call
        assertTrue(world.timeWarp.setWarp(100));
        world.update(FRAME);
        assertTrue(world.timeWarp.isRailsWarp());
    }

    @Test
    void hostilesComeBackFromASave(@TempDir Path dir) {
        String id = pirate(HostileTraffic.RAIDER);
        hostiles.get(id).plunders = 2;
        for (int i = 0; i < 60; i++) world.update(FRAME);
        int count = hostiles.all().size();
        FileHandle file = new FileHandle(dir.resolve("quicksave.json").toFile());
        assertNull(game.quickSave(file));
        game.quickLoad(file);
        assertEquals(count, hostiles.all().size());
        HostileComponent h = hostiles.get(id);
        assertNotNull(h);
        assertEquals(2, h.plunders);
        Entity ship = world.engine.getEntity(id);
        assertNotNull(ship.getComponent(ShipGridComponent.class));
        assertNull(ship.getComponent(PhysicsBodyComponent.class));
        assertNull(game.raider.getComponent(HostileComponent.class));
        for (int i = 0; i < 60; i++) world.update(FRAME);
    }

    // ---------------------------------------------------------------------------------------------

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
        assertEquals(HostileComponent.State.IN_BUBBLE, hostiles.get(uuid).state);
        return ship;
    }

    /** Where a body in the bubble is, from the bubble origin, m. */
    private float[] bubbleOffsetOf(Entity e) {
        double now = world.macroSim.now();
        StateVector s = world.macroSim.gateway().trueStateOf(e.getComponent(UuidComponent.class).id, now,
                new StateVector());
        StateVector o = world.macroSim.gateway().anchorStateAt(now, new StateVector());
        return new float[]{(float) (s.x - o.x), (float) (s.y - o.y)};
    }

    private void run(double seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }
}
