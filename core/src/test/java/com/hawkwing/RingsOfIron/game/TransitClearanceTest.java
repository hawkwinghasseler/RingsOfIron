package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.aicrew.pilot.PilotBurn;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.shipsystems.TransitClearance;
import com.ringsofiron.stationsdocking.BerthingComponent;
import com.ringsofiron.stationsdocking.DockingEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Ships crossing the bubble on their rails plan, past a station with the player docked at it. */
class TransitClearanceTest {
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
    void aFreighterPassingThroughKeepsClearOfTheStationAndTheShipsDockedThere() {
        // Seed 12, day 12.2: Halcyon came into the bubble on its way elsewhere, coasting at 25 m/s on a
        // line through the station the player was docked at. It flew through the berths and tore the
        // player's ship loose.
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        GameScenario game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(2026, "patrol"));
        game.beginCareer();
        for (int i = 0; i < 60; i++) world.update(1f / 60f);
        Entity station = world.docking.dockedTo(game.player);
        assertNotNull(station, "the career starts docked");
        double hold = InteriorGridData.cargoBayVolume(game.content.defs, GameScenario.FREIGHTER_SHIP);
        String id = world.macroSim.addFreighter("Halcyon", hold, 20_000, station.getComponent(UuidComponent.class).id);
        for (int i = 0; i < 30; i++) world.update(1f / 60f);
        Entity halcyon = world.engine.getEntity(id);
        Body body = world.docking.bodyOf(halcyon);
        assertNotNull(body, "the freighter is in the bubble");

        // Off the station and onto a course through it, bound somewhere else, as one on its rails plan is
        world.docking.undock(halcyon);
        halcyon.remove(BerthingComponent.class);
        halcyon.getComponent(FreighterComponent.class).destinationUuid = null;
        List<PilotBurn> later = new ArrayList<>();
        later.add(new PilotBurn(world.engine.getSimSeconds() + 1e6, 1, 0));
        halcyon.add(new NpcPilotComponent(later));
        Body ship = world.docking.bodyOf(game.player);
        Vector2 at = PhysicsManager.getPosition(ship, new Vector2());
        Body sb = world.docking.bodyOf(station);
        Vector2 v = PhysicsManager.getVelocity(sb, new Vector2());
        // In along the berth, from outside: the player's ship is the first thing in the way
        Vector2 out = new Vector2(at).sub(PhysicsManager.getPosition(sb, new Vector2())).nor();
        PhysicsManager.setPosition(body, at.x + out.x * 1500f, at.y + out.y * 1500f, out.angleRad() + (float) Math.PI);
        PhysicsManager.setVelocity(body, v.x - out.x * 25f, v.y - out.y * 25f);
        body.setAngularVelocity(0f);

        List<String> torn = new ArrayList<>();
        world.docking.events.add((sig, e) -> {
            if (e.kind == DockingEvent.Kind.TORN) torn.add(ShipNames.name(e.vessel) + " / " + ShipNames.name(e.other));
        });
        float nearest = Float.MAX_VALUE;
        Vector2 a = new Vector2(), b = new Vector2();
        for (int i = 0; i < 150 * 60; i++) {
            world.update(1f / 60f);
            nearest = Math.min(nearest, PhysicsManager.getPosition(body, a).dst(PhysicsManager.getPosition(ship, b)));
        }

        assertEquals(List.of(), torn, "nothing tears loose");
        assertSame(station, world.docking.dockedTo(game.player), "the player is still docked");
        assertTrue(nearest > TransitClearance.CLEARANCE + 20f, "the freighter passes clear of the player's ship: " + nearest + " m");
        assertTrue(PhysicsManager.getPosition(body, a).sub(at).dot(out) < -500f, "and goes on its way past: " + a);
    }
}
