package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.moddingdata.defs.ShipDef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The crew and interior scenarios headless, in a career world as the debug menu loads them. */
class CrewInteriorScenesTest {
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

    private void run(float seconds, Runnable after) {
        for (int i = 0; i < seconds * 60; i++) {
            world.update(FRAME);
            after.run();
        }
    }

    private CrewMemberComponent crew(Entity e) {
        return world.crew.crew(e);
    }

    @Test
    void theInteriorScenarioSendsTheRaiderAwayAndPinsTheShip() {
        Entity raider = game.raider;
        String raiderId = raider.getComponent(com.ringsofiron.coreengine.components.UuidComponent.class).id;
        InteriorScene scene = new InteriorScene(game, null, null);
        assertNull(game.raider);
        assertFalse(game.contacts.contains(raider, true));
        run(0.1f, scene::update);
        // By its uuid: the engine pools entities, so the raider's may already be someone else's
        assertNull(world.engine.getEntity(raiderId), "the raider is gone");
        assertEquals(GameScenario.PLAYER_SHIP, scene.hull);
        Body body = WorldDebug.body(game.player);
        Vector2 home = body.getPosition().cpy();
        body.setLinearVelocity(5f, 0f);
        run(2f, scene::update);
        assertEquals(home.x, body.getPosition().x, 1e-3f);
        assertEquals(home.y, body.getPosition().y, 1e-3f);
        boolean medic = false;
        for (Entity e : scene.debug.crewAboard(game.player)) medic |= crew(e).role == CrewRole.MEDIC;
        assertTrue(medic, "a medic aboard");
    }

    @Test
    void everyHullTakesThePlayerAndTheirCrewAcross() {
        InteriorScene scene = new InteriorScene(game, null, null);
        Entity avatar = game.avatar;
        List<String> hulls = scene.hulls();
        assertTrue(hulls.size() >= 6, hulls.toString());
        for (String id : hulls) {
            Entity old = game.player;
            String said = scene.setHull(id);
            assertFalse(said.startsWith("No hull"), said);
            assertNotSame(old, game.player, id);
            assertSame(game.player, world.getPlayer(), id);
            assertEquals(id, scene.hull);
            assertSame(game.player, world.crew.position(avatar).ship, id + ": the player's character came across");
            ShipDef def = world.getContent().defs.get("ship", id, ShipDef.class);
            List<Entity> aboard = scene.debug.crewAboard(game.player);
            assertTrue(aboard.size() >= def.crew, id + ": " + aboard.size() + " of " + def.crew);
            boolean medic = false;
            for (Entity e : aboard) medic |= crew(e).role == CrewRole.MEDIC;
            assertTrue(medic, id + ": a medic aboard");
            run(0.5f, scene::update);
        }
        scene.setHull(hulls.get(0));
        scene.cycle(1);
        assertEquals(hulls.get(1), scene.hull);
        scene.cycle(-2);
        assertEquals(hulls.get(hulls.size() - 1), scene.hull);
        assertTrue(scene.setHull("no_such_hull").startsWith("No hull"));
    }

    @Test
    void anAttackerOpensFireAndGoesAgain() {
        InteriorScene scene = new InteriorScene(game, null, null);
        assertTrue(scene.attack(GameScenario.RAIDER_SHIP, 90f).endsWith("attacking"));
        assertNotNull(scene.attacker);
        float before = structure(game.player);
        run(30f, scene::update);
        assertTrue(structure(game.player) < before - 0.05f, "structure " + before + " -> " + structure(game.player));
        // Swapping hulls keeps it on the new ship
        scene.setHull(GameScenario.PLAYER_SHIP);
        assertSame(game.player, scene.attacker.getComponent(com.ringsofiron.combatsys.components.SensorComponent.class).lockedTarget);
        scene.ceaseFire();
        assertNull(scene.attacker);
        run(1f, scene::update);
    }

    @Test
    void theDeckToolsWoundSpawnAndBurnThroughTheGame() {
        InteriorScene scene = new InteriorScene(game, null, null);
        DeckTools tools = new DeckTools(world.crew, scene.debug);
        Entity ship = game.player;
        ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
        Entity someone = scene.debug.crewAboard(ship).get(1);
        GridPositionComponent p = world.crew.position(someone);
        Vector2 at = p.toShipLocal(g, new Vector2());
        tools.tool = DeckTools.Tool.WOUND;
        String hurt = tools.apply(ship, at, at);
        assertTrue(hurt.startsWith("Wound crew at"), hurt);
        assertSame(someone, tools.touched);
        assertNotEquals(Wound.NONE, crew(someone).wound);

        int[] free = scene.debug.freeTile(ship);
        Vector2 tile = g.tileToLocal(free[0], free[1]).cpy();
        tools.tool = DeckTools.Tool.SPAWN;
        tools.alternate();
        int before = scene.debug.crewAboard(ship).size();
        String boarded = tools.apply(ship, tile, tile);
        assertTrue(boarded.endsWith(" boards"), boarded);
        assertEquals(before + 1, scene.debug.crewAboard(ship).size());
        assertNotEquals(world.crew.board(ship).faction, crew(tools.touched).faction);

        tools.tool = DeckTools.Tool.REMOVE;
        Entity boarder = tools.touched;
        assertTrue(tools.apply(ship, tile, tile).endsWith("taken off"));
        run(0.1f, scene::update);
        assertFalse(scene.debug.crewAboard(ship).contains(boarder));
    }

    @Test
    void theCrewScenarioPlaysItsTroubleAndTheCrewAnswer() {
        CrewScene scene = new CrewScene(game, null);
        Entity ship = game.player;
        assertNull(game.raider);
        // Whether anyone aboard took a job off the task board while the trouble played
        boolean[] busy = {false};
        Runnable step = () -> {
            scene.update();
            for (Entity e : scene.interior.debug.crewAboard(ship)) busy[0] |= crew(e).task != null;
        };
        run(CrewScene.FIRE_AT + 0.2f, step);
        assertNotNull(scene.fireAt, "a fire was started");
        run(CrewScene.REACTOR_AT - CrewScene.FIRE_AT, step);
        assertNotNull(scene.reactor);
        assertTrue(scene.reactor.getComponent(SubsystemComponent.class).health <= CrewScene.REACTOR_LEFT + 1e-3f);
        run(CrewScene.BREACH_AT - CrewScene.REACTOR_AT, step);
        assertNotNull(scene.breachAt, "the hull was breached");
        run(CrewScene.BOARDERS_AT - CrewScene.BREACH_AT, step);
        assertEquals(CrewScene.BOARDER_COUNT, scene.boarders.size());
        assertTrue(scene.finished());
        for (Entity b : scene.boarders) assertNotEquals(world.crew.board(ship).faction, crew(b).faction);
        assertTrue(busy[0], "the crew went to work on it: " + scene.interior.watch.lines());
        // Again from the top, on a fresh corvette
        scene.restart();
        assertNotSame(ship, game.player);
        assertFalse(scene.finished());
        assertNull(scene.fireAt);
        run(CrewScene.FIRE_AT + 0.2f, scene::update);
        assertNotNull(scene.fireAt);
    }

    private static float structure(Entity ship) {
        ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
        float sum = 0f;
        for (int y = 0; y < g.height; y++)
            for (int x = 0; x < g.width; x++) if (g.getTile(x, y).isWall()) sum += g.getTile(x, y).getIntegrity();
        return sum;
    }
}
