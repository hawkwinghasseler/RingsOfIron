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
