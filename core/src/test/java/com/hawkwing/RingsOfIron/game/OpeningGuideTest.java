package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.hawkwing.RingsOfIron.shell.KeyBindings;
import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** The opening walkthrough's steps, its place in a save, and guide text naming the player's keys. */
class OpeningGuideTest {
    GameWorld world;
    GameScenario game;
    boolean boardOpen;

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
    void walksFromTheBoardToTakingAJobAndThenStops() {
        OpeningGuide guide = new OpeningGuide(world.contracts, world.guides, () -> boardOpen, OpeningGuide.Step.OPEN_BOARD);
        assertEquals("OPEN_BOARD", world.guides.openingStep, "a new game saves where it is");
        assertFalse(guide.update(), "nothing done yet");

        boardOpen = true;
        assertTrue(guide.update());
        assertEquals(OpeningGuide.Step.TAKE_JOB, guide.step());
        assertEquals("TAKE_JOB", world.guides.openingStep);

        Contract job = world.contracts.offers(game.highport).get(0);
        assertNull(world.contracts.accept(job.id));
        assertTrue(guide.update());
        assertTrue(guide.finished());
        assertNull(world.guides.openingStep, "a finished walkthrough leaves nothing to resume");
        assertNull(OpeningGuide.resume(world, () -> boardOpen));
    }

    @Test
    void aSavedStepIsResumedAndStoppingClearsIt() {
        world.guides.openingStep = "TAKE_JOB";
        OpeningGuide guide = OpeningGuide.resume(world, () -> boardOpen);
        assertNotNull(guide);
        assertEquals(OpeningGuide.Step.TAKE_JOB, guide.step());
        guide.stop();
        assertTrue(guide.finished());
        assertNull(world.guides.openingStep);

        world.guides.openingStep = "NO_SUCH_STEP";
        assertNull(OpeningGuide.resume(world, () -> boardOpen), "an unknown step from an old save is dropped");
        assertNull(world.guides.openingStep);
    }

    @Test
    void guideTextNamesTheKeysThePlayerBound() {
        KeyBindings bindings = new KeyBindings();
        Function<Action, String> keys = HintRegistry.keyNames(bindings);
        OpeningGuide guide = new OpeningGuide(world.contracts, world.guides, () -> boardOpen, OpeningGuide.Step.OPEN_BOARD);
        assertTrue(guide.text(keys).contains("J opens its mission board"), guide.text(keys));
        bindings.bind(Action.MISSIONS, Input.Keys.M);
        assertTrue(guide.text(keys).contains("M opens its mission board"), guide.text(keys));

        assertEquals("press {NOT_AN_ACTION} or J", HintRegistry.withKeys("press {NOT_AN_ACTION} or {MISSIONS}",
                HintRegistry.keyNames(new KeyBindings())), "unknown names stay as written");
        assertEquals("open { brace", HintRegistry.withKeys("open { brace", keys));
        assertFalse(FirstPlay.start(world, game).objective(keys).contains("{"), "First Play names keys too");
    }
}
