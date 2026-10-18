package com.hawkwing.RingsOfIron.game;

import org.junit.jupiter.api.io.TempDir;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.badlogic.gdx.files.FileHandle;
import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.BehaviorTreeComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;
import com.ringsofiron.audiospatial.backend.AudioParameters;
import com.ringsofiron.audiospatial.components.AudioEmitterComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    void theGunFiresOffTheNose() {
        PlayerGunnery gun = new PlayerGunnery(world.ballistics, world.combatDefs, null);
        assertNotNull(gun.weapon());
        Entity shot = gun.fire(game.player);
        assertNotNull(shot);
        assertNotNull(shot.getComponent(ProjectileComponent.class));
        assertNull(gun.fire(game.player), "reloading");
        for (int i = 0; i < 30; i++) world.update(FRAME);
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
