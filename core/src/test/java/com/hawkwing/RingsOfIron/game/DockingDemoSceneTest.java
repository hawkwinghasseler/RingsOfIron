package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** Plays every scene of the docking demo headless, the way a player would with the autopilot. */
class DockingDemoSceneTest {
    static final float FRAME = 1f / 60f;

    DockingDemoScene demo;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        demo = new DockingDemoScene(Paths.get(System.getProperty("user.dir")).getParent().resolve("assets"));
    }

    @AfterEach
    void tearDown() {
        demo.dispose();
    }

    private boolean runUntilDocked(float seconds) {
        for (int i = 0; i < seconds * 60 && !demo.world.docking.isRigid(demo.player); i++) demo.update(FRAME);
        return demo.world.docking.isRigid(demo.player);
    }

    @Test
    void passivePortsLatchOnceBothAreArmed() {
        demo.load(DockingDemoScene.Scene.PASSIVE);
        assertEquals("Port armed", demo.docking.toggleArm());
        demo.autopilot = true;
        assertTrue(runUntilDocked(120f), demo.notice());
        assertSame(demo.target, demo.world.docking.dockedTo(demo.player));
    }

    @Test
    void theActiveCollarCatchesTheOtherShip() {
        demo.load(DockingDemoScene.Scene.ACTIVE);
        demo.autopilot = true;
        for (int i = 0; i < 60 * 60 && demo.autopilot; i++) demo.update(FRAME);
        assertEquals("Port armed", demo.docking.toggleArm());
        boolean captured = false;
        for (int i = 0; i < 60 * 60 && !demo.world.docking.isRigid(demo.player); i++) {
            demo.update(FRAME);
            captured |= DockingDemoScene.port(demo.player, 0).state == DockingPort.State.CAPTURED;
        }
        assertTrue(captured, "the collar had hold before the latch");
        assertTrue(demo.world.docking.isRigid(demo.player), demo.notice());
    }

    @Test
    void theStationGivesClearanceAndDrawsTheShipIn() {
        demo.load(DockingDemoScene.Scene.CLEARANCE);
        assertTrue(demo.docking.toggleArm().contains("gives clearance"));
        demo.autopilot = true;
        assertTrue(runUntilDocked(120f), demo.notice());
        assertEquals("Docked at Outpost Kepler", demo.notice());
        assertTrue(demo.world.docking.dockedWithClearance(demo.player));
    }

    @Test
    void aRefusingStationKeepsItsCollarInButTheShipsMagnetsStillLatch() {
        demo.load(DockingDemoScene.Scene.REFUSED);
        assertEquals("Outpost Kepler refuses docking clearance; port armed", demo.docking.toggleArm());
        for (DockingPort p : demo.target.getComponent(com.ringsofiron.stationsdocking.DockingPortsComponent.class).ports) {
            assertFalse(p.armed, "no station port arms");
        }
        demo.autopilot = true;
        assertTrue(runUntilDocked(120f), demo.notice());
        assertEquals("Docked at Outpost Kepler (without clearance)", demo.notice());
        assertFalse(demo.world.docking.dockedWithClearance(demo.player));
    }

    @Test
    void pullingAwayWhileDockedTearsThePortOff() {
        demo.load(DockingDemoScene.Scene.TEAR);
        assertTrue(demo.world.docking.isRigid(demo.player), "starts docked");
        FlightControlComponent controls = demo.player.getComponent(FlightControlComponent.class);
        for (int i = 0; i < 60 * 5 && demo.world.docking.dockedTo(demo.player) != null; i++) {
            controls.forward = 1f;
            demo.update(FRAME);
        }
        assertNull(demo.world.docking.dockedTo(demo.player), "torn loose");
        assertTrue(demo.notice().startsWith("Torn loose from Outpost Kepler"), demo.notice());
        DockingPort port = DockingDemoScene.port(demo.player, 0);
        assertFalse(port.intact());
        assertEquals("Can't arm the port: the collar is torn off", demo.docking.toggleArm());
    }

    /** Gap between the two docked collar faces along the player's port normal, m: below zero they overlap. */
    private float faceGap(DockingPort mine, DockingPort theirs) {
        com.badlogic.gdx.physics.box2d.Body a = demo.world.docking.bodyOf(demo.player);
        com.badlogic.gdx.physics.box2d.Body b = demo.world.docking.bodyOf(demo.target);
        float u = com.ringsofiron.physicsexterior.PhysicsManager.UNITS_PER_METER;
        com.badlogic.gdx.math.Vector2 fa = a.getWorldPoint(new com.badlogic.gdx.math.Vector2(mine.faceX() * u, mine.faceY() * u)).cpy();
        com.badlogic.gdx.math.Vector2 fb = b.getWorldPoint(new com.badlogic.gdx.math.Vector2(theirs.faceX() * u, theirs.faceY() * u)).cpy();
        com.badlogic.gdx.math.Vector2 n = a.getWorldVector(new com.badlogic.gdx.math.Vector2(mine.normalX, mine.normalY)).cpy();
        return fb.sub(fa).dot(n) / u;
    }

    @Test
    void afterUndockingThePortsCollideAgainAtOnce() {
        demo.load(DockingDemoScene.Scene.TEAR);
        DockingPort mine = DockingDemoScene.port(demo.player, 0);
        DockingPort theirs = DockingDemoScene.port(demo.target, DockingDemoScene.smallPort(demo.target));
        assertEquals("Undocked from Outpost Kepler", demo.docking.undock());
        for (int i = 0; i < 60; i++) demo.update(FRAME);
        assertTrue(faceGap(mine, theirs) > 0.05f, "the springs pushed the craft apart: " + faceGap(mine, theirs));
        // Back in, straight at the outpost's port
        com.badlogic.gdx.physics.box2d.Body body = demo.world.docking.bodyOf(demo.player);
        float least = Float.MAX_VALUE;
        for (int i = 0; i < 60 * 4; i++) {
            com.badlogic.gdx.math.Vector2 n = body.getWorldVector(new com.badlogic.gdx.math.Vector2(mine.normalX, mine.normalY)).cpy();
            com.ringsofiron.physicsexterior.PhysicsManager.setVelocity(body, n.x * 1.5f, n.y * 1.5f);
            body.setAngularVelocity(0f);
            demo.update(FRAME);
            least = Math.min(least, faceGap(mine, theirs));
        }
        assertTrue(least > -0.05f, "the collars stopped each other, not passed through: " + least);
    }

    @Test
    void theHubHasAHaulerBerthedAndRoomForThePlayer() {
        demo.load(DockingDemoScene.Scene.HUB);
        Entity hauler = null;
        for (Entity e : demo.world.engine.getEcsEngine().getEntitiesFor(
                com.badlogic.ashley.core.Family.all(com.ringsofiron.uihud.components.HudMarkerComponent.class).get())) {
            if ("Hauler Kestrel".equals(e.getComponent(com.ringsofiron.uihud.components.HudMarkerComponent.class).label)) hauler = e;
        }
        assertNotNull(hauler);
        assertSame(demo.target, demo.world.docking.dockedTo(hauler));
        assertTrue(demo.world.docking.isRigid(hauler));
        assertTrue(demo.docking.toggleArm().contains("gives clearance"));
        demo.autopilot = true;
        assertTrue(runUntilDocked(120f), demo.notice());
    }

    @Test
    void everySceneLoadsAgainCleanly() {
        for (DockingDemoScene.Scene s : DockingDemoScene.Scene.values()) {
            demo.load(s);
            demo.update(FRAME);
            assertNotNull(demo.world.docking.bodyOf(demo.player), s.title);
        }
    }
}
