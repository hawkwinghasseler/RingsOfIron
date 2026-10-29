package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.DockingEvent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Freighters that come into the bubble at a station with no port free for them. */
class StationTrafficTest {
    GameWorld world;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void tearDown() {
        if (world != null) world.dispose();
    }

    @Test
    void freightersWithNoFreePortWaitApartNotPiledOnEachOther() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        GameScenario game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        double hold = InteriorGridData.cargoBayVolume(game.content.defs, GameScenario.FREIGHTER_SHIP);
        List<String> extra = new ArrayList<>();
        // Far more than Highport has Medium ports
        for (int i = 0; i < 12; i++) extra.add(world.macroSim.addFreighter("Waiting " + i, hold, 20_000, game.highport));
        for (int i = 0; i < 30; i++) world.update(1f / 60f);

        List<Vector2> waiting = new ArrayList<>();
        for (String uuid : extra) {
            Entity e = world.engine.getEntity(uuid);
            PhysicsBodyComponent pb = e == null ? null : e.getComponent(PhysicsBodyComponent.class);
            if (pb == null || pb.body == null || world.docking.dockedTo(e) != null) continue;
            waiting.add(PhysicsManager.getPosition(pb.body, new Vector2()));
        }
        assertTrue(waiting.size() >= 6, "most found no port and wait outside: " + waiting.size());
        for (int i = 0; i < waiting.size(); i++) {
            for (int j = i + 1; j < waiting.size(); j++) {
                assertTrue(waiting.get(i).dst(waiting.get(j)) > StationTraffic.NOSE_X * 2,
                        "freighters " + i + " and " + j + " overlap at " + waiting.get(i));
            }
        }
    }

    @Test
    void freightersWaitingOutsideKeepClearOfTheOnesDocked() {
        // Seed 2026: Deepwell Depot has one Medium port and four haulers at it. Jumping beside it
        // (as a tow does) brings them all in at once: one latches, three wait outside. They used to
        // wait within the docked one's length and fly off across the station, and tore it loose.
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        GameScenario game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(2026, "patrol"));
        game.beginCareer();
        for (int i = 0; i < 60; i++) world.update(1f / 60f);
        world.docking.undock(game.player);
        for (int i = 0; i < 600; i++) world.update(1f / 60f);
        List<String> torn = new ArrayList<>();
        world.docking.events.add((sig, e) -> {
            if (e.kind == DockingEvent.Kind.TORN) torn.add(PlayerDocking.name(e.vessel) + " / " + PlayerDocking.name(e.other));
        });
        Entity deepwell = null;
        for (EconomyNode n : world.macroSim.stations()) {
            if ("Deepwell Depot".equals(n.displayName)) deepwell = world.engine.getEntity(n.stationUuid);
        }
        world.rescue.jumpBeside(game.player, deepwell, 2_000);
        for (int i = 0; i < 20 * 60; i++) world.update(1f / 60f);

        assertEquals(List.of(), torn, "nothing tears loose");
        int docked = 0, outside = 0;
        for (String id : world.macroSim.bubble().inBubble()) {
            Entity e = world.engine.getEntity(id);
            if (e == null || e.getComponent(FreighterComponent.class) == null) continue;
            if (world.docking.dockedTo(e) == deepwell) docked++;
            else outside++;
        }
        assertTrue(docked >= 1 && outside >= 1, "one docked and others waiting: " + docked + " docked, " + outside + " not");
    }

    @Test
    void freightersAtAStationWearTheHaulersHullNotABox() {
        // They came in as a plain 40 m box drawn as a green outline, its port 20 m out on its nose
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        GameScenario game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        double hold = InteriorGridData.cargoBayVolume(game.content.defs, GameScenario.FREIGHTER_SHIP);
        String uuid = world.macroSim.addFreighter("Docked", hold, 20_000, game.highport);
        for (int i = 0; i < 30; i++) world.update(1f / 60f);

        Entity freighter = world.engine.getEntity(uuid);
        FreighterHull hull = world.freighterHullOf(freighter);
        assertNotNull(hull, "a freighter with no grid wears the Hauler's hull");
        assertEquals("freighter", hull.chassis);
        assertNull(freighter.getComponent(ShipGridComponent.class), "but has no interior of its own");
        assertNull(world.freighterHullOf(game.player), "a ship with a grid wears its own");

        Body body = freighter.getComponent(PhysicsBodyComponent.class).body;
        float bow = -Float.MAX_VALUE;
        int pieces = 0;
        Vector2 v = new Vector2();
        for (Fixture f : body.getFixtureList()) {
            // Not the collar it is latched by
            if (f.isSensor() || (f.getFilterData().categoryBits & CollisionCategories.CATEGORY_COLLAR) != 0) continue;
            pieces++;
            PolygonShape p = (PolygonShape) f.getShape();
            for (int i = 0; i < p.getVertexCount(); i++) {
                p.getVertex(i, v);
                bow = Math.max(bow, v.x * PhysicsManager.METERS_PER_UNIT);
            }
        }
        assertEquals(hull.pieces.size(), pieces, "shaped like the Hauler's layout");
        assertEquals(10f, bow, 0.25f, "its bow where the Hauler's is");
        assertEquals(GameBubbleGateway.DEFAULT_HULL_MASS, PhysicsManager.getMass(body), 1f, "and weighing what it did, so it flies as before");

        DockingPort port = freighter.getComponent(DockingPortsComponent.class).ports[0];
        assertEquals(10f, port.edgeX, 0.01f, "its port in the bow");
        assertEquals(0f, port.edgeY, 0.01f);
        assertNotNull(world.docking.dockedTo(freighter), "and docked by it");
    }
}
