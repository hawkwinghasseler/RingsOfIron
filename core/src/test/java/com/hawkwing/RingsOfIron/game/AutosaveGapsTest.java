package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.hawkwing.RingsOfIron.shell.SaveSlots;
import com.hawkwing.RingsOfIron.shell.SaveSummary;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Autosave's gaps: no autosave of a ship disabled, abandoned, crashed or out of a career, and loading the latest one. */
class AutosaveGapsTest {
    static final float FRAME = 1f / 60f;
    Path assets;
    GameWorld world;
    GameScenario game;
    AutosaveClock clock;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        clock = new AutosaveClock();
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void beginDocked() {
        game.beginCareer();
        for (int i = 0; i < 10; i++) world.update(FRAME);
        assertNotNull(world.playerStation(), "the career starts docked");
    }

    private void undock() {
        if (world.docking.dockedTo(game.player) != null) world.docking.undock(game.player);
        for (int i = 0; i < 10; i++) world.update(FRAME);
        assertNull(world.playerStation());
    }

    /** Sets the clock so that more than a flight interval has passed since the last save. */
    private void flightSaveOverdue() {
        clock.restart(false, world.engine.getSimSeconds() - AutosaveClock.FLIGHT_INTERVAL - 1);
    }

    private void disable(DisabledComponent.Reason reason) {
        DisabledComponent d = new DisabledComponent();
        d.reason = reason;
        game.player.add(d);
    }

    private double credits(GameScenario g) {
        return g.player.getComponent(TraderComponent.class).credits;
    }

    @Test
    void aShipInGoodOrderIsAutosavedInFlightAndOnDocking() {
        beginDocked();
        clock.restart(false, world.engine.getSimSeconds());
        assertEquals("docked", clock.due(world, game.player));
        undock();
        flightSaveOverdue();
        assertEquals("in flight", clock.due(world, game.player));
    }

    @Test
    void aDisabledShipIsNeverAutosavedThoughItsClockRuns() {
        beginDocked();
        clock.restart(false, world.engine.getSimSeconds());
        disable(DisabledComponent.Reason.NO_POWER);
        assertNull(clock.due(world, game.player), "not on docking");

        undock();
        disable(DisabledComponent.Reason.HULL);
        flightSaveOverdue();
        assertNull(clock.due(world, game.player), "not in flight");
        assertEquals(0, clock.flown(), 1e-9, "the save it skipped was counted");
        game.player.remove(DisabledComponent.class);
        assertNull(clock.due(world, game.player), "so recovering does not save at once");
    }

    @Test
    void aShipBeingAbandonedIsNeverAutosaved() {
        undock();
        assertNull(world.abandon.order(game.player));
        assertTrue(AbandonShip.abandoning(game.player));
        flightSaveOverdue();
        assertNull(clock.due(world, game.player));
    }

    @Test
    void aCrashedShipIsNeverAutosavedAndItsClockStops() {
        undock();
        PhysicsBodyComponent pb = game.player.getComponent(PhysicsBodyComponent.class);
        world.physics.setTrueState(pb.body, GameScenario.R_EARTH - 10, 0, 0, 0);
        world.update(FRAME);
        assertNotNull(world.getCrash());
        flightSaveOverdue();
        assertNull(clock.due(world, game.player));
        world.clearCrash();
        assertNull(ShipDisablement.reason(game.player));
        assertEquals("in flight", clock.due(world, game.player), "the stopped clock still owed that save");
    }

    @Test
    void anEndedCareerIsNeverAutosaved() {
        undock();
        for (com.badlogic.ashley.core.Entity e : world.crew.crewEntities()) {
            if (e.getComponent(GridPositionComponent.class).ship == game.player) {
                world.crew.applyDamage(e, 1e6f, CrewDamageSink.DamageCause.BLAST);
            }
        }
        world.update(FRAME);
        assertNotNull(world.getCareerOver());
        flightSaveOverdue();
        assertNull(clock.due(world, game.player));
    }

    @Test
    void continueAndTheLostShipScreenLoadTheLatestAutosaveNotAnOlderOne(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(new FileHandle(dir.toFile()));
        FileHandle quick = slots.quickSave();
        SaveSummary q = SaveSummary.of(game, SaveSlots.QUICK_SAVE_NAME);
        q.savedAt = 1000;
        assertNull(game.quickSave(quick, q));

        // Four autosaves, as the game makes them: the fourth goes over the first
        undock();
        double[] simAt = new double[4];
        int[] slotOf = new int[4];
        for (int i = 0; i < 4; i++) {
            for (int f = 0; f < 30; f++) world.update(FRAME);
            game.player.getComponent(TraderComponent.class).credits = 1000 * (i + 1);
            int n = slots.nextAutosave();
            FileHandle file = slots.autosave(n);
            SaveSummary s = SaveSummary.of(game, SaveSlots.autosaveName(n));
            s.savedAt = 2000 + i;
            assertNull(game.quickSave(file, s));
            simAt[i] = world.engine.getSimSeconds();
            slotOf[i] = n;
        }
        assertEquals(1, slotOf[3], "the oldest slot reused");

        SaveSummary latest = slots.latestAutosave();
        assertEquals(SaveSlots.autosaveName(1), latest.name);
        assertEquals(latest.file, slots.latest().file, "Continue picks the same save");
        assertEquals(simAt[3], latest.gameSeconds, 1e-6);

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(latest.file);
            assertEquals(4000, credits(loaded), 1e-6, "the fourth save's credits");
            assertEquals(simAt[3], fresh.engine.getSimSeconds(), 1e-3, "...and its time");
            assertTrue(fresh.engine.getSimSeconds() > simAt[2], "not the third save's");

            loaded.quickLoad(slots.autosave(slotOf[2]));
            assertEquals(3000, credits(loaded), 1e-6, "an older one still holds its own state");
        } finally {
            fresh.dispose();
        }
    }
}
