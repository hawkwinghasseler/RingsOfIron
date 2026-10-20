package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The hold position order as the helmsman flies it: the ship kept on its station off Highport
 * through a kick to its velocity and through minutes of drift, on the real physics and helm.
 */
class HoldPositionTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    HelmOrderComponent order;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        world.nav.target.select(game.highport);
        handOverTheHelm();
        assertTrue(world.nav.helm.give(HelmOrderComponent.Mode.HOLD_POSITION).startsWith("Helm: hold position on Highport"));
        order = game.player.getComponent(HelmOrderComponent.class);
        assertTrue(world.nav.helm.describe().startsWith("Helm: holding position on Highport"), world.nav.helm.describe());
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Tag("slow")
    @Test
    void aKickedShipComesBackToWhereItWasHeld() {
        double[] held = offset();
        assertEquals(order.holdX, held[0], 1);
        assertEquals(order.holdY, held[1], 1);
        kick(4f, -3f);
        assertTrue(relativeSpeed() > 4.5, "kicked: " + relativeSpeed());

        world.timeWarp.setWarp(10);
        double worst = 0;
        for (int i = 0; i < 60 * 30; i++) {
            world.update(FRAME);
            worst = Math.max(worst, offHold());
        }
        assertTrue(worst > 1, "the kick moved it: " + worst);
        assertTrue(worst < 50, "but not far: " + worst);
        assertTrue(offHold() < 2, "back where it was held: " + offHold());
        assertTrue(relativeSpeed() < 0.2, "and stopped there: " + relativeSpeed());
        assertEquals(HelmOrderComponent.Mode.HOLD_POSITION, world.nav.helm.mode(), "the order stands");
    }

    @Tag("slow")
    @Test
    void theNaturalDriftIsHeldOffForMinutes() {
        assertTrue(relativeSpeed() > 5, "8 km apart on one orbit, their velocities differ: " + relativeSpeed());
        world.timeWarp.setWarp(10);
        double worst = 0, settled = 0;
        for (int i = 0; i < 60 * 60; i++) {
            world.update(FRAME);
            worst = Math.max(worst, offHold());
            if (world.universe.now() > 60) settled = Math.max(settled, offHold());
        }
        assertTrue(world.universe.now() > 500, "ten minutes flown");
        assertTrue(worst < 30, "never wandered off: " + worst);
        assertTrue(settled < 2, "and kept within a couple of meters once settled: " + settled);
        assertTrue(relativeSpeed() < 0.2, relativeSpeed() + " m/s");

        // Without the order, the same ship drifts off
        world.nav.helm.give(HelmOrderComponent.Mode.NONE);
        for (int i = 0; i < 60 * 30; i++) world.update(FRAME);
        assertTrue(offHold() > 100, "drifted off: " + offHold());
    }

    // ------------------------------------------------------------------

    private void kick(float dvx, float dvy) {
        Body body = game.player.getComponent(PhysicsBodyComponent.class).body;
        Vector2 v = PhysicsManager.getVelocity(body, new Vector2());
        PhysicsManager.setVelocity(body, v.x + dvx, v.y + dvy);
    }

    private double offHold() {
        double[] o = offset();
        return Math.hypot(o[0] - order.holdX, o[1] - order.holdY);
    }

    private double relativeSpeed() {
        double[] o = offset();
        return Math.hypot(o[2], o[3]);
    }

    /** The ship relative to Highport: {x, y, vx, vy}. */
    private double[] offset() {
        StateVector q = new StateVector();
        double[] s = new double[4];
        world.physics.getTrueState(game.player.getComponent(PhysicsBodyComponent.class).body, s);
        world.nav.target.stateAt(world.universe.entity(GameScenario.EARTH), world.universe.now(), q);
        return new double[]{s[0] - q.x, s[1] - q.y, s[2] - q.vx, s[3] - q.vy};
    }

    private void handOverTheHelm() {
        world.bridge.stand();
        for (int i = 0; i < 60 * 60 && world.bridge.helmsman(game.player) == null; i++) world.update(FRAME);
        assertNotNull(world.bridge.helmsman(game.player), "Bea takes the helm");
    }
}
