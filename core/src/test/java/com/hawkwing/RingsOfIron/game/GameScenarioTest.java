package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.BehaviorTreeComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.events.ShotFiredEvent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
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
        assertEquals(4, weapons.mounts.size, "railgun, two point-defense guns, a launcher");
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
    void railsWarpRunsTheEconomyAndComesBack() {
        for (int i = 0; i < 60; i++) world.update(FRAME);
        assertTrue(world.timeWarp.setWarp(10_000), world.engine.getRailsWarpBlockedReason());
        for (int i = 0; i < 120; i++) world.update(FRAME);
        assertTrue(world.engine.getSimSeconds() > 10_000, "warped: " + world.engine.getSimSeconds());
        world.timeWarp.setWarp(1);
        assertNotNull(game.player.getComponent(PhysicsBodyComponent.class));
        for (int i = 0; i < 60; i++) world.update(FRAME);
    }
}
