package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.shipops.AbandonShip;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * First hour's gaps: the notice log after a load, no saving a lost ship or an ended career, the
 * opening walkthrough's step dropped on a load with it turned off, and the tow's other refusals.
 */
class FirstHourSaveGapsTest {
    static final float FRAME = 1f / 60f;
    Path assets;
    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void undock() {
        if (world.docking.dockedTo(game.player) != null) world.docking.undock(game.player);
        for (int i = 0; i < 10; i++) world.update(FRAME);
    }

    private void drainPropellant(float share) {
        for (Entity e : game.player.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent t = e.getComponent(SubsystemComponent.class);
            if (t != null && t.type == SubsystemComponent.SubsystemType.TANK
                    && t.fluid == com.ringsofiron.interiorgrid.Fluid.PROPELLANT) t.contents = t.capacity * share;
        }
    }

    @Test
    void aLoadClearsTheOldGamesNotices(@TempDir Path dir) {
        FileHandle file = new FileHandle(dir.resolve("notices.json").toFile());
        assertNull(game.quickSave(file));
        world.notices.post("From the old session", NoticeLog.Kind.IMPORTANT);
        assertFalse(world.notices.showing().isEmpty());

        game.quickLoad(file);
        assertTrue(world.notices.showing().isEmpty(), "nothing old on screen");
        assertTrue(world.notices.history().isEmpty(), "nothing old in the log");
        assertNull(world.notices.latest());
    }

    @Test
    void anEndedCareerCantBeSaved(@TempDir Path dir) {
        undock();
        for (Entity e : world.crew.crewEntities()) {
            if (e.getComponent(GridPositionComponent.class).ship == game.player) {
                world.crew.applyDamage(e, 1e6f, CrewDamageSink.DamageCause.BLAST);
            }
        }
        world.update(FRAME);
        assertNotNull(world.getCareerOver());
        FileHandle file = new FileHandle(dir.resolve("over.json").toFile());
        assertEquals("the career is over", game.quickSave(file));
        assertFalse(file.exists());
    }

    @Test
    void aLostShipCantBeSaved(@TempDir Path dir) {
        undock();
        PhysicsBodyComponent pb = game.player.getComponent(PhysicsBodyComponent.class);
        world.physics.setTrueState(pb.body, GameScenario.R_EARTH - 10, 0, 0, 0);
        world.update(FRAME);
        assertNotNull(world.getCrash());
        FileHandle file = new FileHandle(dir.resolve("crash.json").toFile());
        assertEquals("the ship is lost", game.quickSave(file));
        assertFalse(file.exists());
    }

    @Test
    void withTheWalkthroughOffALoadDropsItsSavedStep(@TempDir Path dir) {
        new OpeningGuide(world.contracts, world.guides, () -> false, OpeningGuide.Step.TAKE_JOB);
        FileHandle file = new FileHandle(dir.resolve("opening.json").toFile());
        assertNull(game.quickSave(file));

        game.quickLoad(file);
        assertEquals("TAKE_JOB", world.guides.openingStep, "the save has it");
        assertNull(OpeningGuide.resume(world, () -> false, false), "turned off: not resumed");
        assertNull(world.guides.openingStep, "...and not carried into later saves");

        FileHandle again = new FileHandle(dir.resolve("again.json").toFile());
        assertNull(game.quickSave(again));
        game.quickLoad(again);
        assertNull(world.guides.openingStep);
        assertNull(OpeningGuide.resume(world, () -> false, true), "nothing to resume even with it back on");
    }

    @Test
    void aPortLatchedToSomethingGoneIsNotDocked() {
        // A save can name a partner the game no longer has: the ship is not docked anywhere then,
        // and the systems that ask where (the contract board, landing survivors) must not trip on it
        undock();
        com.ringsofiron.stationsdocking.DockingPort port =
                game.player.getComponent(com.ringsofiron.stationsdocking.DockingPortsComponent.class).ports[0];
        port.state = com.ringsofiron.stationsdocking.DockingPort.State.DOCKED;
        port.permitted = true;
        port.partnerUuid = "gone";
        assertNull(world.docking.dockedTo(game.player));
        assertFalse(world.docking.dockedWithClearance(game.player), "no clearance from nothing");
        assertDoesNotThrow(() -> world.update(FRAME));
    }

    @Test
    void noTowWhileThePodsAreAway() {
        undock();
        world.abandon.launches.dispatch(new AbandonShip.Escape(game.player, null, new ArrayList<>(), true,
                world.engine.getSimSeconds()));
        assertTrue(world.rescue.pending());
        assertEquals("the pods are away", world.beacon.refusal());
        assertTrue(world.beacon.call().startsWith("Can't call a tow"));
    }

    @Test
    void noSaveWhileTheTugIsStillBringingTheShipIn(@TempDir Path dir) {
        undock();
        drainPropellant(0.05f);
        assertNull(world.rescue.tow(game.player));
        FileHandle file = new FileHandle(dir.resolve("tow.json").toFile());
        assertEquals("the tug is still bringing you in", game.quickSave(file));
        assertFalse(file.exists());
        for (int i = 0; i < 120 && world.rescue.bringingIn(); i++) world.update(FRAME);
        assertFalse(world.rescue.bringingIn());
        assertNotNull(world.docking.dockedTo(game.player), "docked at the station");
        assertNull(game.quickSave(file));
    }

    @Test
    void noTowWithMissilesInbound() {
        undock();
        drainPropellant(0.05f);
        assertNull(world.beacon.refusal(), "stranded, and nothing else in the way");

        Entity missile = new Entity();
        MissileComponent m = new MissileComponent();
        m.target = game.player;
        missile.add(m);
        missile.add(new ProjectileComponent());
        world.engine.getEcsEngine().addEntity(missile);
        world.warnings.update();
        assertEquals(1, world.warnings.inbound());
        assertEquals("missiles are inbound; no tug will come into that", world.beacon.refusal());
    }
}
