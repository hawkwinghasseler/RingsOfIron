package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** When the game autosaves, the warnings it posts, and First Play's progress kept in a save. */
class AutosaveAndWarningsTest {
    GameWorld world;
    GameScenario game;
    Path assets;

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

    @Test
    void autosavesComeOnDockingAndEveryTenMinutesOfFlightOutsideWarp() {
        AutosaveClock clock = new AutosaveClock();
        assertNull(clock.update(true, false, 0), "not for the state the game starts in");
        assertNull(clock.update(true, false, 1000), "nor for sitting docked");
        assertNull(clock.update(false, false, 1100), "undocking is saved on the key, before it");
        assertNull(clock.update(false, true, 5000), "warp does not count");
        assertNull(clock.update(false, false, 5400), "100 s before warp and 400 after");
        assertEquals("in flight", clock.update(false, false, 5700));
        assertNull(clock.update(false, false, 5800));
        assertEquals("docked", clock.update(true, false, 5900));
        assertEquals(0, clock.flown(), 1e-9);
    }

    @Test
    void lowPropellantWarnsOnceUntilTheTanksAreFilledAgain() {
        FlightWarnings w = world.warnings;
        w.checkPropellant(0.5f, 1000);
        assertNull(world.notices.latest());
        w.checkPropellant(0.19f, 380);
        assertEquals(NoticeLog.Kind.WARNING, world.notices.latest().kind);
        assertTrue(world.notices.latest().text.startsWith("Propellant low: 19%"), world.notices.latest().text);
        w.checkPropellant(0.1f, 200);
        assertEquals(1, world.notices.history().size(), "once");
        w.checkPropellant(0.3f, 600);
        w.checkPropellant(0.15f, 300);
        assertEquals(2, world.notices.history().size(), "again after a refill");
        w.checkPropellant(Float.NaN, 0);
        assertEquals(2, world.notices.history().size(), "a ship with no tanks says nothing");
    }

    @Test
    void enoughToGetHomeIsNotLow() {
        FlightWarnings w = world.warnings;
        w.returnPropellantKg = () -> 150;
        w.checkPropellant(0.15f, 300);
        assertNull(world.notices.latest(), "below 20% but enough for the trip home");
        w.checkPropellant(0.05f, 100);
        assertNotNull(world.notices.latest());
    }

    @Test
    void aFirstPlaySaveLoadsBackAtItsObjective(@TempDir Path dir) {
        FirstPlay play = FirstPlay.start(world, game);
        assertEquals(FirstPlay.Step.ACCEPT.name(), world.guides.firstPlayStep);
        world.contracts.accept(world.contracts.offers(game.highport).get(0).id);
        world.update(1f / 60f);
        play.update();
        assertEquals(FirstPlay.Step.UNDOCK, play.step());
        FileHandle file = new FileHandle(dir.resolve("fp.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            assertNull(FirstPlay.resume(fresh, loaded), "a new free game is not a first play");
            loaded.quickLoad(file);
            FirstPlay resumed = FirstPlay.resume(fresh, loaded);
            assertNotNull(resumed);
            assertEquals(FirstPlay.Step.UNDOCK, resumed.step());
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void aFreeGameSaveLeavesFirstPlay(@TempDir Path dir) {
        FileHandle file = new FileHandle(dir.resolve("free.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            FirstPlay.start(fresh, loaded);
            loaded.quickLoad(file);
            assertNull(FirstPlay.resume(fresh, loaded));
        } finally {
            fresh.dispose();
        }
    }
}
