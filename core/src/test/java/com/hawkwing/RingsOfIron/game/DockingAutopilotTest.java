package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.orbital.math.StateVector;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The helm's dock order, flown on the real physics, helm and docking: from 2 km off Highport the
 * helmsman asks for clearance, flies the corvette's port in to the one Highport gives, and latches;
 * and the player can take the helm back mid-approach.
 */
class DockingAutopilotTest {
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
        world.nav.target.select(game.highport);
        world.bridge.stand();
        for (int i = 0; i < 60 * 60 && world.bridge.helmsman(game.player) == null; i++) world.update(FRAME);
        assertNotNull(world.bridge.helmsman(game.player), "a crew member takes the helm");
        placeOffHighport(2_000);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void theHelmDocksAtHighportFromTwoKilometers() {
        String said = world.nav.helm.give(HelmOrderComponent.Mode.DOCK);
        assertEquals("Helm: dock at Highport", said);
        world.timeWarp.setWarp(10);
        double t0 = world.engine.getSimSeconds();
        boolean docked = false;
        for (int i = 0; i < 60 * 300 && !docked; i++) {
            world.update(FRAME);
            docked = world.docking.docked(game.player, world.engine.getEntity(game.highport));
        }
        world.timeWarp.setWarp(1);
        assertTrue(docked, "latched at Highport; the helm says: " + world.nav.helm.describe());
        assertTrue(world.docking.dockedWithClearance(game.player), "with Highport's clearance");
        run(10);
        assertEquals(HelmOrderComponent.Mode.NONE, world.nav.helm.mode(), "the order is done");
        assertTrue(noticed("Helm: docked at Highport"));
        assertTrue(world.engine.getSimSeconds() - t0 < 1_500, "in under 25 minutes: " + (world.engine.getSimSeconds() - t0));
    }

    @Test
    void takingTheHelmStopsTheAutopilotAndCancellingEndsIt() {
        world.nav.helm.give(HelmOrderComponent.Mode.DOCK);
        world.timeWarp.setWarp(10);
        run(60 * 3);
        world.timeWarp.setWarp(1);
        assertTrue(String.valueOf(world.nav.helm.describe()).startsWith("Helm: docking at Highport"),
                () -> world.nav.helm.describe() + " " + world.notices.history().stream().map(n -> n.text).toList());
        double flown = distance();
        assertTrue(flown < 1_995, "it set off: " + flown);

        // The player sits down at the helm: the order waits and the ship is theirs to fly
        world.bridge.playerAlwaysAtHelm = true;
        run(10);
        assertTrue(world.nav.helm.describe().contains("waits: you have the helm"), world.nav.helm.describe());
        assertEquals(0f, game.player.getComponent(com.ringsofiron.physicsexterior.components.FlightControlComponent.class).forward, 1e-6f,
                "the autopilot leaves the controls to the player");
        world.bridge.playerAlwaysAtHelm = false;

        assertEquals("Helm order cancelled", world.nav.helm.give(HelmOrderComponent.Mode.NONE));
        assertEquals(HelmOrderComponent.Mode.NONE, world.nav.helm.mode());
    }

    @Test
    void theOrderIsRefusedWithoutAFreePortOrTooFarOut() {
        placeOffHighport(80_000);
        assertTrue(world.nav.helm.give(HelmOrderComponent.Mode.DOCK).startsWith("Highport is too far for the helm to dock at"));
        // A ship with no free port of our size
        DockingPortsComponent ports = world.engine.getEntity(game.highport).getComponent(DockingPortsComponent.class);
        DockingPortsComponent own = game.player.getComponent(DockingPortsComponent.class);
        for (DockingPort p : ports.ports) if (p.size == own.selectedPort().size) p.collarIntegrity = 0f;
        placeOffHighport(2_000);
        assertTrue(world.nav.helm.give(HelmOrderComponent.Mode.DOCK).startsWith("Highport has no free"),
                world.nav.helm.give(HelmOrderComponent.Mode.DOCK));
    }

    // ------------------------------------------------------------------

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    private boolean noticed(String text) {
        return world.notices.history().stream().anyMatch(n -> n.text.startsWith(text));
    }

    /** Puts the ship {@code meters} from Highport's middle, behind it along its orbit, at its velocity. */
    private void placeOffHighport(double meters) {
        StateVector q = highport();
        double speed = Math.hypot(q.vx, q.vy);
        double ux = q.vx / speed, uy = q.vy / speed;
        world.physics.setTrueState(game.player.getComponent(PhysicsBodyComponent.class).body,
                q.x - ux * meters, q.y - uy * meters, q.vx, q.vy);
        run(2);
    }

    private double distance() {
        StateVector q = highport();
        double[] s = new double[4];
        world.physics.getTrueState(game.player.getComponent(PhysicsBodyComponent.class).body, s);
        return Math.hypot(s[0] - q.x, s[1] - q.y);
    }

    private StateVector highport() {
        StateVector q = new StateVector();
        world.nav.target.stateAt(world.universe.entity(GameScenario.EARTH), world.universe.now(), q);
        return q;
    }
}
