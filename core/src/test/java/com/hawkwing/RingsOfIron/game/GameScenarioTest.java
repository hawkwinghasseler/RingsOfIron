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
        int aboard = 0, thinking = 0;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(GridPositionComponent.class).ship == ship) aboard++;
            if (e.getComponent(BehaviorTreeComponent.class) != null) thinking++;
        }
        assertEquals(4, aboard, "the player and three crew, back aboard the loaded ship");
        assertEquals(3, thinking, "the AI crew think again");
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
        for (int i = 0; i < 60; i++) world.update(FRAME);
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
        assertNotNull(freighter.getComponent(NpcPilotComponent.class));
        assertNotNull(freighter.getComponent(HudMarkerComponent.class));
        assertTrue(game.contacts.contains(freighter, true));
    }

    @Test
    void shipsComeArmedFromTheirHardpoints() {
        WeaponsComponent weapons = game.player.getComponent(WeaponsComponent.class);
        assertNotNull(weapons);
        assertEquals(5, weapons.mounts.size, "railgun, two point-defense guns, a launcher, a laser");
        for (WeaponMountComponent m : weapons.mounts) assertNotNull(m.subsystem, m.def.id + " is wired to a system");
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
        // The corvette's chassis has a helm, a reactor and life support, and no weapons console.
        assertEquals(java.util.Set.of("helm", "reactor", "life_support"), consoles.keySet());
        assertArrayEquals(new int[]{1, 4}, consoles.get("helm"));
        assertEquals(3, consolesOn(game.contacts.first()).size(), "the raider has its own");

        String save = world.engine.getSerializationService().saveGame();
        world.engine.getSerializationService().loadGame(save);
        Entity ship = world.getPlayer();
        assertEquals(consoles.keySet(), consolesOn(ship).keySet(), "the consoles find the loaded ship");
    }

    @Test
    void thePlayerCanTrade() {
        com.ringsofiron.macrosim.economy.TraderComponent trader =
                game.player.getComponent(com.ringsofiron.macrosim.economy.TraderComponent.class);
        assertNotNull(trader);
        assertEquals(GameScenario.STARTING_CREDITS, trader.credits);
        assertEquals(GameScenario.STARTING_HOLD, trader.freeCargo());
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
