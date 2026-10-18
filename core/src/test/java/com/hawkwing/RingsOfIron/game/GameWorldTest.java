package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.components.OrbitComponent;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewRole;
import com.badlogic.gdx.math.Vector2;
import com.ringsofiron.audiospatial.components.AudioListenerComponent;
import com.ringsofiron.combatsys.damage.HitCause;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.moddingdata.ModContext;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.ViewMode;
import com.ringsofiron.uihud.providers.OrbitInfoProvider;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Boots the whole wired game headless and checks the modules work together. */
class GameWorldTest {
    static final double MU_EARTH = 3.986004418e14;
    static final double R_EARTH = 6.371e6;
    static final double LEO = R_EARTH + 400e3;
    static final float FRAME = 1f / 60f;

    GameWorld world;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        world = new GameWorld();
        Entity earth = world.engine.createEntity();
        earth.add(new UuidComponent("earth"));
        earth.add(new CelestialBodyComponent("Earth", MU_EARTH, R_EARTH, Double.POSITIVE_INFINITY));
        world.engine.addEntity(earth);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    Entity playerOnRails() {
        Entity ship = world.engine.createEntity();
        ship.add(new UuidComponent("player"));
        ship.add(new PlayerControllerComponent());
        ship.add(new TransformComponent());
        ship.add(new OrbitComponent("earth", KeplerOrbit.circular(MU_EARTH, LEO, 0, 0, 1)));
        world.engine.addEntity(ship);
        return ship;
    }

    /** The player flying in the bubble, which falls along a low Earth orbit. */
    Entity playerInBubble() {
        world.setAnchor("earth", KeplerOrbit.circular(MU_EARTH, LEO, 0, 0, 1));
        Entity ship = world.engine.createEntity();
        ship.add(new UuidComponent("player"));
        ship.add(new PlayerControllerComponent());
        ship.add(new TransformComponent());
        ship.add(new PhysicsBodyComponent(world.physics.createCircleBody(ship, 0f, 0f, 5f, 10f,
                CollisionCategories.CATEGORY_SHIP, (short) -1)));
        world.engine.addEntity(ship);
        return ship;
    }

    @Test
    void realTimeAdvancesOneTickPerFrame() {
        playerOnRails();
        for (int i = 0; i < 120; i++) world.update(FRAME);
        assertEquals(120, world.engine.getTick(), 1);
    }

    @Test
    void physicsWarpThroughTheController() {
        playerOnRails();
        assertTrue(world.timeWarp.setWarp(5));
        for (int i = 0; i < 60; i++) world.update(FRAME);
        assertEquals(300, world.engine.getTick(), 5);
    }

    @Test
    void railsWarpCollapsesTheBubbleAndRebuildsIt() {
        Entity ship = playerInBubble();
        assertEquals("earth", world.orbitalEvents.getAnchorParentUuid(), "anchor bridged to orbital");
        assertTrue(world.timeWarp.setWarp(1_000));
        assertTrue(world.timeWarp.isRailsWarp());
        assertNull(ship.getComponent(PhysicsBodyComponent.class), "the player is on rails during warp");
        assertNull(world.orbitalEvents.getAnchorParentUuid(), "no bubble anchor while collapsed");
        assertNotNull(world.universe.orbit(ship));

        for (int i = 0; i < 60; i++) world.update(FRAME);

        // 1 s of real time at 1000x is 1000 s of sim time, plus the 60 ordinary ticks.
        assertEquals(1_000.0, world.engine.getSimSeconds(), 2.0);
        assertEquals(world.engine.getSimSeconds(), world.universe.now(), 1e-9);

        assertTrue(world.timeWarp.setWarp(1));
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class), "back in Box2D after warp");
        assertEquals("earth", world.orbitalEvents.getAnchorParentUuid(), "rails side sees the rebuilt anchor");
        world.update(FRAME);
    }

    @Test
    void fireAboardRefusesRailsWarp() {
        Entity ship = playerOnRails();
        ShipGridComponent grid = new ShipGridComponent(4, 4, 1f);
        grid.getTile(1, 1).fireIntensity = 0.5f;
        ship.add(grid);

        assertFalse(world.timeWarp.setWarp(1_000));
        assertFalse(world.timeWarp.isRailsWarp());
        assertNotNull(world.engine.getRailsWarpBlockedReason());
    }

    @Test
    void saveAndLoadKeepsTimeAndOrbits() {
        playerInBubble();
        world.timeWarp.setWarp(1_000);
        for (int i = 0; i < 30; i++) world.update(FRAME);
        long tick = world.engine.getTick();
        String save = world.engine.getSerializationService().saveGame();

        GameWorld loaded = new GameWorld();
        try {
            loaded.engine.getSerializationService().loadGame(save);
            assertEquals(tick, loaded.engine.getTick());
            assertNotNull(loaded.getPlayer());
            assertNotNull(loaded.universe.orbit(loaded.getPlayer()));
            assertTrue(loaded.engine.getSerializationService().getLastLoadWarnings().isEmpty());
        } finally {
            loaded.dispose();
        }
    }

    @Test
    void loadsBaseDataAndSpawnsShipsFromIt() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        boolean[] registered = {false};
        GameWorld.Modules modules = new GameWorld.Modules();
        modules.contentRegistrations.add(ctx -> registered[0] = true);
        GameWorld withContent = new GameWorld(modules);
        try {
            ModContext ctx = withContent.loadContent(assets, assets.resolve("no-mods-here"));

            assertTrue(registered[0]);
            assertFalse(ctx.report.hasErrors(), ctx.report.toString());
            assertFalse(ctx.defs.ids("ship").isEmpty());
            String shipId = ctx.defs.ids("ship").iterator().next();
            assertNotNull(ctx.factory.spawnShip(shipId, 0, 0, 0));
            withContent.update(FRAME);
        } finally {
            withContent.dispose();
        }
    }

    @Test
    void crewAboardThePlayerShipRunsWithTheGame() {
        Entity ship = playerOnRails();
        ShipGridComponent grid = new ShipGridComponent(6, 6, 1f);
        ship.add(grid);
        Entity member = CrewFactory.spawn(world.crew, ship, "Ada", CrewRole.ENGINEER, 0, 2, 2);

        for (int i = 0; i < 120; i++) world.update(FRAME);

        assertNotNull(member);
        assertTrue(world.engine.getEcsEngine().getEntities().contains(member, true));
    }

    @Test
    void orbitReadoutShowsThePlayersConic() {
        playerOnRails();
        OrbitInfoProvider.OrbitInfo info = new PlayerOrbitInfo(world.universe, world::getPlayer).getPlayerOrbit();

        assertNotNull(info);
        assertEquals("Earth", info.bodyName);
        assertEquals(400e3, info.altitude, 1.0);
        assertEquals(400e3, info.periapsis, 1.0);
        assertEquals(400e3, info.apoapsis, 1.0);
        assertEquals(2 * Math.PI * Math.sqrt(LEO * LEO * LEO / MU_EARTH), info.period, 1e-3);
        assertFalse(info.impactWarning);
    }

    @Test
    void avatarFeedsTheHudAndTheInteriorAudioListener() {
        Entity ship = playerOnRails();
        ShipGridComponent grid = new ShipGridComponent(6, 6, 2f);
        ship.add(grid);
        CrewFactory.spawnPlayer(world.crew, ship, "Joe", 3, 1);

        Vector2 tile = new Vector2();
        assertSame(ship, world.avatar.getAvatarShip());
        assertTrue(world.avatar.getAvatarTile(tile));
        assertEquals(3.5f, tile.x, 1e-6f);

        world.audioListener.setMode(GameHud.toListenerMode(ViewMode.INTERIOR));
        world.update(FRAME);

        AudioListenerComponent listener = ship.getComponent(AudioListenerComponent.class);
        assertNotNull(listener);
        assertEquals(AudioListenerComponent.Mode.INTERIOR, listener.mode);
        assertEquals(grid.gridOriginLocal.x + 3.5f * 2f, listener.localX, 1e-4f);
        assertEquals(grid.gridOriginLocal.y + 1.5f * 2f, listener.localY, 1e-4f);
    }

    @Test
    void combatHurtsCrewThroughAiCrewWithMatchingCauses() {
        for (HitCause cause : HitCause.values()) {
            assertEquals(cause.name(), CrewDamageAdapter.toDamageCause(cause).name());
        }
        assertEquals(CrewDamageSink.DamageCause.OTHER, CrewDamageAdapter.toDamageCause(null));
        assertFalse(world.combatDefs.weapons.isEmpty());
    }

    static StateVector circular(double radius, double angle) {
        var s = KeplerOrbit.circular(MU_EARTH, radius, angle, 0, 1).stateAt(0);
        return new StateVector().set(s.x, s.y, s.vx, s.vy);
    }

    @Test
    void freightersTradeOnTheGamesRails() {
        String farm = world.macroSim.addStation("Farm", "farm", "earth", circular(LEO + 1_000e3, 0), 1);
        String habitat = world.macroSim.addStation("Habitat", "habitat", "earth", circular(LEO + 3_000e3, 2), 1);
        for (int i = 0; i < 4; i++) world.macroSim.addFreighter("Hauler " + i, 200, 20_000, i % 2 == 0 ? farm : habitat);
        assertNotNull(world.universe.orbit(world.engine.getEntity(farm)), "stations orbit on orbital-mechanics rails");

        assertTrue(world.engine.advanceRails(com.ringsofiron.coreengine.SimClock.secondsToTicks(3 * 86400)));
        assertEquals(world.engine.getSimSeconds(), world.macroSim.now(), 1e-6);
        int trips = 0;
        for (FreighterComponent f : world.macroSim.freighters().all().values()) trips += f.tripsCompleted;
        assertTrue(trips > 0, "freighters traded during the warp jump");
    }

    /** An NPC on rails 5 km ahead of the bubble, with a burn still planned. */
    Entity npcAhead() {
        Entity npc = world.engine.createEntity();
        npc.add(new UuidComponent("npc"));
        world.engine.addEntity(npc);
        world.macroSim.rails().attach("npc", "earth", circular(LEO, 5e3 / LEO), 0);
        world.macroSim.rails().scheduleBurn("npc", new RailsModel.Burn(600, 3, 0));
        return npc;
    }

    @Test
    void npcNearTheBubbleEntersAndIsFlownByThePilot() {
        playerInBubble();
        Entity npc = npcAhead();
        for (int i = 0; i < 10; i++) world.update(FRAME);

        assertTrue(world.macroSim.bubble().inBubble().contains("npc"));
        PhysicsBodyComponent body = npc.getComponent(PhysicsBodyComponent.class);
        assertNotNull(body, "macro-sim handed it to Box2D");
        assertEquals(5e3, body.body.getPosition().len() * com.ringsofiron.physicsexterior.PhysicsManager.METERS_PER_UNIT, 2.0,
                "placed at its true state, same tick as the anchor");
        NpcPilotComponent pilot = npc.getComponent(NpcPilotComponent.class);
        assertNotNull(pilot, "ai-crew's pilot flies it");
        assertEquals(1, pilot.burns.size());
        assertEquals(600, pilot.burns.peek().time, 1e-9);
    }

    @Test
    void railsWarpCarriesBubbleNpcsAlongAndBringsThemBack() {
        playerInBubble();
        Entity npc = npcAhead();
        for (int i = 0; i < 10; i++) world.update(FRAME);
        assertNotNull(npc.getComponent(PhysicsBodyComponent.class));

        assertTrue(world.timeWarp.setWarp(1_000));
        assertNull(npc.getComponent(PhysicsBodyComponent.class), "collapsed onto rails with the player");
        for (int i = 0; i < 60; i++) world.update(FRAME);
        assertTrue(world.timeWarp.isRailsWarp(), "a ship already alongside does not stop the warp");
        assertTrue(world.engine.getSimSeconds() > 900);

        assertTrue(world.timeWarp.setWarp(1));
        assertNotNull(npc.getComponent(PhysicsBodyComponent.class), "rebuilt next to the player");
        assertTrue(world.macroSim.bubble().inBubble().contains("npc"));
        for (int i = 0; i < 10; i++) world.update(FRAME);
        assertTrue(world.macroSim.bubble().inBubble().contains("npc"));
    }
}
