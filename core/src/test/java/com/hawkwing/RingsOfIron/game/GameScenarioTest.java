package com.hawkwing.RingsOfIron.game;

import org.junit.jupiter.api.io.TempDir;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.badlogic.gdx.files.FileHandle;
import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.BehaviorTreeComponent;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.interiorgrid.crew.ExtinguishTask;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.events.ShotFiredEvent;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.backend.AudioParameters;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.stationsdocking.BerthingComponent;
import com.badlogic.gdx.physics.box2d.Body;
import org.junit.jupiter.api.Test;

/** Plays the starting scenario headless: what the game screen runs, without the screen. */
class GameScenarioTest {
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

    @Test
    void startsWithAFlyableShipAndACrewAboard() {
        assertFalse(game.content.report.hasErrors(), game.content.report.toString());
        assertSame(game.player, world.getPlayer());
        assertNotNull(game.player.getComponent(PhysicsBodyComponent.class));
        assertNotNull(game.avatar, "the player walks the deck");
        assertSame(game.player, world.avatar.getAvatarShip());
        int aboard = 0;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(GridPositionComponent.class).ship == game.player) aboard++;
        }
        assertEquals(4, aboard, "the player and three crew");
        for (int i = 0; i < 60; i++) world.update(FRAME);
    }

    @Test
    void savesAndLoadsWithTheCrewAboard() {
        for (int i = 0; i < 60; i++) world.update(FRAME);
        assertFalse(world.macroSim.bubble().inBubble().isEmpty(), "the freighter ahead is in the bubble");
        String visitor = world.macroSim.bubble().inBubble().iterator().next();
        double[] before = new double[4];
        world.physics.getTrueState(world.engine.getEntity(visitor).getComponent(PhysicsBodyComponent.class).body, before);
        String save = world.engine.getSerializationService().saveGame();
        world.engine.getSerializationService().loadGame(save);

        PhysicsBodyComponent visitorBody = world.engine.getEntity(visitor).getComponent(PhysicsBodyComponent.class);
        assertNotNull(visitorBody, "a ship the bubble took in from the rails gets its body back");
        double[] after = new double[4];
        world.physics.getTrueState(visitorBody.body, after);
        assertEquals(before[0], after[0], 1.0);
        assertEquals(before[1], after[1], 1.0);

        Entity ship = world.getPlayer();
        assertNotNull(ship);
        int aboard = 0, thinking = 0, hands = 0;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            boolean ours = e.getComponent(GridPositionComponent.class).ship == ship;
            if (ours) aboard++;
            if (ours && e.getComponent(BehaviorTreeComponent.class) != null) thinking++;
            com.ringsofiron.aicrew.CrewMemberComponent c = e.getComponent(com.ringsofiron.aicrew.CrewMemberComponent.class);
            if (c.role == com.ringsofiron.aicrew.CrewRole.LOADER && c.home != null && c.home.getComponent(
                    com.ringsofiron.stationsdocking.StationComponent.class) != null) hands++;
        }
        assertEquals(4, aboard, "the player and three crew, back aboard the loaded ship");
        assertEquals(3, thinking, "the AI crew think again");
        assertEquals(12, hands, "every station's loaders, still working for their stations");
        for (int i = 0; i < 180; i++) world.update(FRAME);
        assertTrue(world.macroSim.bubble().inBubble().contains(visitor), "and the game runs on with it");
    }

    @Test
    void theCrewPutOutAFireAboard() {
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        List<int[]> floor = GameScenario.floorTiles(grid);
        int[] spot = floor.get(floor.size() / 2);
        Tile tile = grid.getTile(spot[0], spot[1]);
        tile.fuel = 10f;
        tile.ignite();
        tile.fireIntensity = 0.3f;
        boolean fought = false;
        for (int i = 0; i < 60 * 60 && grid.hasFire(); i++) {
            world.update(FRAME);
            for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
                if (e.getComponent(CrewMemberComponent.class).task instanceof ExtinguishTask) fought = true;
            }
        }
        assertTrue(fought, "a crew member took the firefighting job");
        assertFalse(grid.hasFire(), "the crew put the fire out");
        assertTrue(world.crewAudio.footstepsPlayed() > 0, "the firefighter's footsteps are heard");
        assertTrue(world.crewAudio.voicesPlayed() > 0, "the firefighter called out taking the job");
    }

    @Test
    void quickSaveLoadsIntoAFreshGame(@TempDir Path dir) {
        for (int i = 0; i < 60; i++) world.update(FRAME);
        FileHandle file = new FileHandle(dir.resolve("quicksave.json").toFile());
        assertNull(game.quickSave(file));
        long tick = world.engine.getTick();
        String playerId = game.player.getComponent(UuidComponent.class).id;
        int contacts = game.contacts.size;

        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        GameWorld fresh = new GameWorld();
        try {
            GameScenario restarted = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            restarted.quickLoad(file);

            assertEquals(tick, fresh.engine.getTick());
            assertSame(fresh.getPlayer(), restarted.player);
            assertEquals(playerId, restarted.player.getComponent(UuidComponent.class).id);
            assertNotNull(restarted.player.getComponent(PhysicsBodyComponent.class));
            assertNotNull(restarted.avatar);
            assertSame(restarted.player, restarted.avatar.getComponent(GridPositionComponent.class).ship);
            assertEquals(contacts, restarted.contacts.size, "the raider and the freighter, lockable again");
            for (int i = 0; i < 120; i++) fresh.update(FRAME);
            assertSame(restarted.player, fresh.getPlayer());
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void theCrewAboardSetGunneryAndTracking() {
        WeaponsComponent weapons = game.player.getComponent(WeaponsComponent.class);
        SensorComponent sensor = game.player.getComponent(SensorComponent.class);
        assertNotNull(weapons);
        assertTrue(weapons.mounts.size > 0);
        assertNotNull(sensor);
        float raiderGunnery = game.raider.getComponent(WeaponsComponent.class).mounts.first().gunnerySkill;
        // Cole the marine walks to the weapons console and mans it
        for (int i = 0; i < 60 * 20 && weapons.mounts.first().gunnerySkill < 0.75f; i++) world.update(FRAME);
        for (WeaponMountComponent m : weapons.mounts) assertEquals(0.75f, m.gunnerySkill, 1e-6f, "Cole the marine on the guns");
        assertEquals(0.5f, sensor.trackingSkill, 1e-6f);
        assertEquals(raiderGunnery, game.raider.getComponent(WeaponsComponent.class).mounts.first().gunnerySkill,
                "the raider's crew is not simulated, so it keeps its own");

        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(GridPositionComponent.class).ship == game.player) world.crew.applyDamage(e, 90f, CrewDamageSink.DamageCause.BLAST);
        }
        for (int i = 0; i < 60; i++) world.update(FRAME);
        for (WeaponMountComponent m : weapons.mounts) assertEquals(0f, m.gunnerySkill, 1e-6f, "nobody left on the guns");
        assertEquals(0f, sensor.trackingSkill, 1e-6f);
    }

    @Test
    void orbitReadoutWorksWhileFlyingInTheBubble() {
        var info = new PlayerOrbitInfo(world.universe, world.physics, world::getPlayer).getPlayerOrbit();
        assertNotNull(info);
        assertEquals("Earth", info.bodyName);
        assertEquals(400e3, info.altitude, 100.0);
        assertEquals(400e3, info.apoapsis, 100.0);
    }

    @Test
    void theFreighterDockedAheadComesIntoTheBubble() {
        for (int i = 0; i < 60; i++) world.update(FRAME);
        Entity freighter = null;
        for (String uuid : world.macroSim.bubble().inBubble()) freighter = world.engine.getEntity(uuid);
        assertNotNull(freighter, "macro-sim handed it to the bubble");
        assertNotNull(freighter.getComponent(PhysicsBodyComponent.class));
        assertNotNull(freighter.getComponent(HudMarkerComponent.class));
        assertTrue(game.contacts.contains(freighter, true));
        // It was sitting at Highport, so it comes in latched to one of its Medium ports.
        Entity highport = world.engine.getEntity(game.highport);
        assertSame(highport, world.docking.dockedTo(freighter));
        assertNull(freighter.getComponent(NpcPilotComponent.class), "a docked freighter stays put");
        for (int i = 0; i < 60; i++) world.update(FRAME);
        assertTrue(world.docking.isRigid(freighter), "welded to the station");
        assertNotNull(highport.getComponent(KinematicTransientComponent.class).body, "the station has a body");
        assertTrue(world.docking.strain(freighter) < 0.1f, "riding along easily");
    }

    @Test
    void aFreighterFliesItselfInToDockAtItsStation() {
        for (int i = 0; i < 60; i++) world.update(FRAME);
        Entity freighter = world.engine.getEntity(world.macroSim.bubble().inBubble().iterator().next());
        Entity highport = world.engine.getEntity(game.highport);
        FreighterComponent f = freighter.getComponent(FreighterComponent.class);
        // As if it were coming back to Highport from elsewhere: off its port and 400 m out
        world.docking.undock(freighter);
        freighter.remove(BerthingComponent.class);
        Body station = world.docking.bodyOf(highport), body = world.docking.bodyOf(freighter);
        Vector2 at = PhysicsManager.getPosition(station, new Vector2());
        Vector2 v = PhysicsManager.getVelocity(station, new Vector2());
        PhysicsManager.setPosition(body, at.x + 300f, at.y - 250f, 0f);
        PhysicsManager.setVelocity(body, v.x, v.y);
        f.dockedAtUuid = null;
        f.destinationUuid = game.highport;
        f.resumeState = FreighterComponent.State.REPOSITIONING;
        for (int i = 0; i < 60 * 20; i++) world.update(FRAME);
        assertEquals(BerthingComponent.Phase.APPROACH, freighter.getComponent(BerthingComponent.class).phase, "on its way in");

        // Saved and loaded on the way in, it carries on
        String id = freighter.getComponent(UuidComponent.class).id;
        world.engine.getSerializationService().loadGame(world.engine.getSerializationService().saveGame());
        freighter = world.engine.getEntity(id);
        highport = world.engine.getEntity(game.highport);
        f = freighter.getComponent(FreighterComponent.class);
        for (int i = 0; i < 60 * 300 && world.docking.dockedTo(freighter) == null; i++) world.update(FRAME);
        assertSame(highport, world.docking.dockedTo(freighter), "flew itself in and docked");
        assertEquals(BerthingComponent.Phase.BERTHED, freighter.getComponent(BerthingComponent.class).phase);
        assertEquals(game.highport, f.dockedAtUuid, "macro-sim knows it is in");
        assertNull(f.destinationUuid);
        assertEquals(FreighterComponent.State.IDLE, f.resumeState);
    }

    @Test
    void shipsComeArmedFromTheirHardpoints() {
        WeaponsComponent weapons = game.player.getComponent(WeaponsComponent.class);
        assertNotNull(weapons);
        assertEquals(5, weapons.mounts.size, "railgun, two point-defense guns, a launcher, a laser");
        java.util.Set<Entity> turrets = new java.util.HashSet<>();
        for (WeaponMountComponent m : weapons.mounts) {
            assertNotNull(m.subsystem, m.def.id + " is wired to a system");
            assertEquals(com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType.TURRET_MOUNT,
                    m.subsystem.getComponent(com.ringsofiron.interiorgrid.SubsystemComponent.class).type,
                    m.def.id + " is on a turret, not the weapons console");
            turrets.add(m.subsystem);
        }
        assertEquals(5, turrets.size(), "each weapon has a turret of its own");
        assertNotNull(game.raider.getComponent(WeaponsComponent.class));
        assertNotNull(game.player.getComponent(SensorComponent.class));
    }

    @Test
    void theGunsFireWhenThePlayerPullsTheTrigger() {
        PlayerGunnery gun = new PlayerGunnery(world, game.player);
        assertNotNull(gun.primary());
        gun.setTarget(null);
        List<ShotFiredEvent> shots = new ArrayList<>();
        world.combatEvents.shots.add((s, shot) -> {
            if (shot.shooter == game.player) shots.add(shot);
        });
        for (int i = 0; i < 30 && shots.isEmpty(); i++) {
            gun.fireGuns();
            world.update(FRAME);
        }
        assertFalse(shots.isEmpty(), "the railgun fired down the barrel with nothing locked");
        assertFalse(game.raiderHostile(), "a shot at nothing does not start a fight");
        for (int i = 0; i < 30; i++) world.update(FRAME);
    }

    @Test
    void theGunsGoQuietWhenTheReactorIsKnockedOut() {
        PlayerGunnery gun = new PlayerGunnery(world, game.player);
        gun.setTarget(null);
        int[] shots = new int[1];
        world.combatEvents.shots.add((s, shot) -> {
            if (shot.shooter == game.player) shots[0]++;
        });
        for (int i = 0; i < 60; i++) world.update(FRAME); // power routed, stores charged
        // What is left in the stores goes in a few shots; then nothing recharges them. The
        // reactor is held wrecked, or the engineer would start repairing it.
        for (int i = 0; i < 10 * 60; i++) {
            wreckReactors();
            gun.fireGuns();
            world.update(FRAME);
        }
        int afterOutage = shots[0];
        for (int i = 0; i < 10 * 60; i++) {
            wreckReactors();
            gun.fireGuns();
            world.update(FRAME);
        }
        assertEquals(afterOutage, shots[0], "with no reactor the main guns stop firing");
    }

    private void wreckReactors() {
        for (Entity e : game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == SubsystemComponent.SubsystemType.REACTOR) {
                s.health = 0f;
                s.isOperational = false;
            }
        }
    }

    @Test
    void shootingTheRaiderStartsAFightWithMissilesAndPointDefense() {
        PlayerGunnery gun = new PlayerGunnery(world, game.player);
        gun.setTarget(game.raider);
        int[] raiderShots = new int[1], intercepts = new int[1];
        boolean[] launched = new boolean[1];
        world.combatEvents.shots.add((s, shot) -> {
            if (shot.shooter == game.raider) raiderShots[0]++;
            if (shot.shooter == game.player && shot.round != null
                    && shot.round.getComponent(MissileComponent.class) != null) launched[0] = true;
        });
        world.combatEvents.intercepts.add((s, hit) -> intercepts[0]++);
        // Let the sensors build a track, then launch; the raider's guns and point defense answer.
        for (int i = 0; i < 60; i++) world.update(FRAME);
        assertTrue(gun.launchMissile(), "a launcher is loaded, powered, and the raider is locked");
        for (int i = 0; i < 600; i++) world.update(FRAME);
        assertTrue(launched[0], "the missile left the rail");
        assertTrue(game.raiderHostile(), "the raider fights back once shot at");
        assertTrue(raiderShots[0] > 0, "and its guns fire on the player");
        assertTrue(intercepts[0] > 0, "point defense engaged a missile");
    }

    @Test
    void aProvokedRaiderManeuversToFightAndDodgesMissiles() {
        PlayerGunnery gun = new PlayerGunnery(world, game.player);
        gun.setTarget(game.raider);
        for (int i = 0; i < 60; i++) world.update(FRAME);
        game.provokeRaider();
        NpcCombatComponent combat = game.raider.getComponent(NpcCombatComponent.class);
        assertNotNull(combat, "the raider has a combat pilot");
        assertSame(game.player, combat.target);

        // A missile at the raider makes it break
        assertTrue(gun.launchMissile(), "a launcher is loaded and the raider is locked");
        boolean evaded = false;
        for (int i = 0; i < 60 * 10 && !evaded; i++) {
            world.update(FRAME);
            evaded = combat.mode == NpcCombatComponent.Mode.EVADING;
        }
        assertTrue(evaded, "the raider breaks across the inbound missile");

        // Then it settles into a fight at its preferred range
        boolean thrusted = false;
        for (int i = 0; i < 60 * 30; i++) {
            world.update(FRAME);
            FlightControlComponent fc = game.raider.getComponent(FlightControlComponent.class);
            if (Math.abs(fc.forward) > 0.1f || Math.abs(fc.strafe) > 0.1f) thrusted = true;
        }
        assertTrue(thrusted, "the raider flies, not just shoots");
        Vector2 raider = PhysicsManager.getPosition(game.raider.getComponent(PhysicsBodyComponent.class).body, new Vector2());
        Vector2 player = PhysicsManager.getPosition(game.player.getComponent(PhysicsBodyComponent.class).body, new Vector2());
        assertEquals(combat.preferredRange, raider.dst(player), 300f, "fighting at its preferred range");

        String save = world.engine.getSerializationService().saveGame();
        world.engine.getSerializationService().loadGame(save);
        Entity loadedRaider = null;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(Family.all(NpcCombatComponent.class).get())) loadedRaider = e;
        assertNotNull(loadedRaider, "the fight survives a save");
        assertSame(world.getPlayer(), loadedRaider.getComponent(NpcCombatComponent.class).target);
    }

    @Test
    void railsWarpRunsTheEconomyAndComesBack() {
        for (int i = 0; i < 60; i++) world.update(FRAME);
        assertTrue(world.timeWarp.setWarp(10_000), world.engine.getRailsWarpBlockedReason());
        for (int i = 0; i < 120; i++) world.update(FRAME);
        assertTrue(world.engine.getSimSeconds() > 10_000, "warped: " + world.engine.getSimSeconds());
        world.timeWarp.setWarp(1);
        assertNotNull(game.player.getComponent(PhysicsBodyComponent.class));
        for (int i = 0; i < 60; i++) world.update(FRAME);
    }

    @Test
    void theEngineIsHeardWhenThrusting() {
        FlightControlComponent controls = game.player.getComponent(FlightControlComponent.class);
        controls.forward = 1f;
        for (int i = 0; i < 30; i++) world.update(FRAME);
        AudioEmitterComponent engine = game.player.getComponent(AudioEmitterComponent.class);
        assertNotNull(engine);
        assertEquals(AudioEventCatalog.THRUSTER, engine.eventPath);
        assertEquals(1f, world.engineAudio.getIntensity(), 1e-4f);
        assertNotNull(engine.instance, "the loop is playing");
        assertEquals(1f, engine.instance.getParameter(AudioParameters.INTENSITY, 0f), 1e-4f);

        controls.forward = 0f;
        controls.turn = 1f;
        for (int i = 0; i < 60; i++) world.update(FRAME);
        assertEquals(EngineAudio.RCS_SHARE, world.engineAudio.getIntensity(), 1e-4f, "RCS is quieter");

        controls.turn = 0f;
        for (int i = 0; i < 60; i++) world.update(FRAME);
        assertEquals(0f, world.engineAudio.getIntensity(), 1e-4f);

        // After a load the same loop carries on, not a second one.
        String save = world.engine.getSerializationService().saveGame();
        world.engine.getSerializationService().loadGame(save);
        controls = world.getPlayer().getComponent(FlightControlComponent.class);
        controls.forward = 1f;
        for (int i = 0; i < 30; i++) world.update(FRAME);
        AudioEmitterComponent loaded = world.getPlayer().getComponent(AudioEmitterComponent.class);
        assertEquals(AudioEventCatalog.THRUSTER, loaded.eventPath);
        assertEquals(1f, loaded.instance.getParameter(AudioParameters.INTENSITY, 0f), 1e-4f);
    }

    @Test
    void crewConsolesAreOnTheShipAndSurviveALoad() {
        java.util.Map<String, int[]> consoles = consolesOn(game.player);
        // The corvette's chassis has a helm, a weapons console, a reactor and life support.
        assertEquals(java.util.Set.of("helm", "weapons", "reactor", "life_support"), consoles.keySet());
        assertArrayEquals(new int[]{7, 4}, consoles.get("helm"));
        assertEquals(4, consolesOn(game.contacts.first()).size(), "the raider has its own");

        String save = world.engine.getSerializationService().saveGame();
        world.engine.getSerializationService().loadGame(save);
        Entity ship = world.getPlayer();
        assertEquals(consoles.keySet(), consolesOn(ship).keySet(), "the consoles find the loaded ship");
    }

    @Test
    void thePlayerCanTrade() {
        com.ringsofiron.macrosim.economy.TraderComponent trader =
                game.player.getComponent(com.ringsofiron.macrosim.economy.TraderComponent.class);
        com.ringsofiron.interiorgrid.CargoHoldComponent bays =
                game.player.getComponent(com.ringsofiron.interiorgrid.CargoHoldComponent.class);
        assertNotNull(trader);
        assertNotNull(bays, "the corvette has cargo bays");
        assertEquals(GameScenario.STARTING_CREDITS, trader.credits);
        assertEquals(bays.capacity(), trader.freeCargo(), 1e-9, "the hold is the bays");
        assertEquals(40, bays.capacity(), 1e-9);
    }

    @Test
    void theCorvettesTanksArePipedToItsEngineAndLifeSupport() {
        world.update(FRAME);
        com.ringsofiron.physicsexterior.components.ThrusterComponent thrusters =
                game.player.getComponent(com.ringsofiron.physicsexterior.components.ThrusterComponent.class);
        assertTrue(thrusters.fuel > 0, "the engine draws on the propellant tanks");
        assertEquals(thrusters.fuelCapacity, thrusters.fuel, 1e-3, "they start full");
        for (Entity e : game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.LIFE_SUPPORT) {
                assertTrue(com.ringsofiron.interiorgrid.TankSupply.available(s, com.ringsofiron.interiorgrid.Fluid.OXYGEN) > 0);
                assertTrue(com.ringsofiron.interiorgrid.TankSupply.available(s, com.ringsofiron.interiorgrid.Fluid.NITROGEN) > 0);
            }
        }
    }

    @Test
    void boughtGoodsSitInTheBaysAndWeighTheShipDown() {
        var trader = game.player.getComponent(com.ringsofiron.macrosim.economy.TraderComponent.class);
        var bays = game.player.getComponent(com.ringsofiron.interiorgrid.CargoHoldComponent.class);
        var body = game.player.getComponent(PhysicsBodyComponent.class).body;
        world.update(FRAME);
        float emptyMass = com.ringsofiron.physicsexterior.PhysicsManager.getMass(body);

        var station = world.macroSim.station(game.highport);
        var market = world.macroSim.market();
        String good = null;
        for (var q : market.quotes(station)) {
            if (q.available > 5) {
                good = q.commodityId;
                break;
            }
        }
        assertNotNull(good, "Highport sells something");
        trader.credits = 1e9;
        double bought = market.buy(station, trader, good, 1e6);
        var type = world.macroSim.economy().catalog().commodity(good);
        assertTrue(bought > 0);
        assertEquals(bought, bays.amount(good), 1e-9, "the goods are in the bays");
        assertTrue(bays.usedVolume() <= bays.capacity() + 1e-9);
        assertEquals(bought * type.unitVolume, bays.usedVolume(), 1e-6);

        world.update(FRAME);
        float loadedMass = com.ringsofiron.physicsexterior.PhysicsManager.getMass(body);
        assertEquals(emptyMass + bought * type.unitMass, loadedMass, loadedMass * 1e-5, "cargo weighs the ship down");

        // A load keeps the goods in the bays and their weight on the hull
        String save = world.engine.getSerializationService().saveGame();
        world.engine.getSerializationService().loadGame(save);
        Entity ship = world.getPlayer();
        world.update(FRAME);
        var loadedTrader = ship.getComponent(com.ringsofiron.macrosim.economy.TraderComponent.class);
        assertEquals(bought, loadedTrader.amount(good), 1e-9);
        assertEquals(loadedMass, com.ringsofiron.physicsexterior.PhysicsManager.getMass(
                ship.getComponent(PhysicsBodyComponent.class).body), loadedMass * 1e-5);

        assertEquals(bought, market.sell(world.macroSim.station(game.highport), loadedTrader, good, 1e6), 1e-9);
        world.update(FRAME);
        assertEquals(emptyMass, com.ringsofiron.physicsexterior.PhysicsManager.getMass(
                ship.getComponent(PhysicsBodyComponent.class).body), emptyMass * 1e-5, "selling lightens it again");
    }

    /** Console id to tile, for the consoles aboard {@code ship}. */
    private java.util.Map<String, int[]> consolesOn(Entity ship) {
        java.util.Map<String, int[]> out = new java.util.HashMap<>();
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(
                com.badlogic.ashley.core.Family.all(InteractableConsoleComponent.class).get())) {
            InteractableConsoleComponent c = e.getComponent(InteractableConsoleComponent.class);
            if (c.isOn(ship)) out.put(c.consoleId, new int[]{c.tileX, c.tileY});
        }
        return out;
    }
}
