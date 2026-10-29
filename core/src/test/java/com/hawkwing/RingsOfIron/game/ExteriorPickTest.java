package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.physicsexterior.PhysicsManager;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A click on the outside view picks the ship or station under it, never the player's own ship. */
class ExteriorPickTest {
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
        world.update(1f / 60f);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private Vector2 at(Entity e) {
        Body body = world.docking.bodyOf(e);
        assertNotNull(body);
        return PhysicsManager.getPosition(body, new Vector2());
    }

    @Test
    void aClickOnAShipPicksIt() {
        assertNotNull(game.raider);
        Vector2 raider = at(game.raider);
        assertSame(game.raider, new ExteriorPick(world).at(raider.x, raider.y, 0.5f, game.player));
    }

    @Test
    void aClickNearATinyShipStillPicksItButNotFarOff() {
        Vector2 raider = at(game.raider);
        // Zoomed far out (20 m a pixel), a click a few pixels off the raider still picks it
        float mpp = 20f;
        assertSame(game.raider, new ExteriorPick(world).at(raider.x + 5f * mpp, raider.y, mpp, game.player));
        // Zoomed in, the same miss in meters picks nothing there
        assertNotSame(game.raider, new ExteriorPick(world).at(raider.x + 100f, raider.y + 100f, 0.1f, game.player));
    }

    @Test
    void theStationIsPickedButNeverThePlayersOwnShip() {
        Entity station = world.engine.getEntity(game.highport);
        assertNotNull(station);
        Vector2 port = at(station);
        assertSame(station, new ExteriorPick(world).at(port.x, port.y, 0.5f, game.player));
        Vector2 me = at(game.player);
        Entity picked = new ExteriorPick(world).at(me.x, me.y, 0.05f, game.player);
        assertNotSame(game.player, picked);
    }
}
