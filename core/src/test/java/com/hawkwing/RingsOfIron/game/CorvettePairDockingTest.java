package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Two of the starter corvettes docked port to port, as the game's own hulls lie. */
class CorvettePairDockingTest {
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
    void theirTailFinsHoldTheCollarATileOut() {
        // Well clear of Highport, a second corvette 1.6 m off the player's port, port to port
        world.docking.undock(game.player);
        for (int i = 0; i < 300; i++) world.update(FRAME);
        Body ship = world.docking.bodyOf(game.player);
        PhysicsManager.setPosition(ship, 3_000f, 3_000f, 0f);
        ship.setAngularVelocity(0f);
        Entity other = game.content.factory.spawnShip(GameScenario.PLAYER_SHIP, 3_000f, 3_040f, 0f);
        world.update(FRAME);
        Body ob = world.docking.bodyOf(other);
        DockingPort pp = port(game.player), op = port(other);
        Vector2 n = ship.getWorldVector(new Vector2(pp.normalX, pp.normalY)).cpy();
        float angle = MathUtils.atan2(n.y, n.x) + MathUtils.PI - MathUtils.atan2(op.normalY, op.normalX);
        float u = PhysicsManager.UNITS_PER_METER;
        Vector2 face = new Vector2(ship.getWorldPoint(new Vector2(pp.faceX() * u, pp.faceY() * u)))
                .scl(PhysicsManager.METERS_PER_UNIT).mulAdd(n, 1.6f);
        float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
        PhysicsManager.setPosition(ob, face.x - (c * op.faceX() - s * op.faceY()), face.y - (s * op.faceX() + c * op.faceY()), angle);
        Vector2 v = PhysicsManager.getVelocity(ship, new Vector2());
        PhysicsManager.setVelocity(ob, v.x, v.y);
        ob.setAngularVelocity(0f);

        PlayerDocking docking = new PlayerDocking(world, () -> game.player, msg -> { });
        try {
            docking.toggleArm();
            boolean rigid = false;
            for (int i = 0; i < 60 * 30 && !rigid; i++) {
                world.update(FRAME);
                rigid = world.docking.isRigid(game.player);
            }
            assertTrue(rigid, "latched and welded");
            // Drawn all the way in, each one's tail fins would rest on the other's hull
            assertEquals(1f, pp.lockedExtension, 1e-4f, "locked a tile out");
            assertEquals(0f, op.lockedExtension, 1e-4f);
            float turned = (ob.getAngle() - ship.getAngle()) * MathUtils.radDeg;
            assertEquals(0f, ((turned - 180f) % 360f + 540f) % 360f - 180f, 0.5f, "square, end to end");
        } finally {
            docking.dispose();
        }
    }

    private static DockingPort port(Entity e) {
        return e.getComponent(DockingPortsComponent.class).get(0);
    }
}
