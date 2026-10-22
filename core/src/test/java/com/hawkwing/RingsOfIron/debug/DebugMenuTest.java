package com.hawkwing.RingsOfIron.debug;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.moddingdata.console.ScriptConsole;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The debug menu's workings without a window: the scenario list, setting one up, and the console's commands for both. */
class DebugMenuTest {
    GameWorld world;
    GameScenario game;
    DevConsole dev;
    Path assets;
    @TempDir
    Path scripts;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        dev = new DevConsole(world, game, scripts, null);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
        DebugScenarios.unregister("test");
    }

    String run(String line) {
        dev.console.submit(line);
        List<ScriptConsole.Line> lines = dev.console.lines();
        return lines.get(lines.size() - 1).text;
    }

    @Test
    void scenariosAreListedAndFoundByIdIgnoringCase() {
        assertNotNull(DebugScenarios.find("free-flight"));
        assertSame(DebugScenarios.find("free-flight"), DebugScenarios.find(" FREE-FLIGHT "));
        assertNull(DebugScenarios.find("no-such"));
        DebugScenarios.register(new DebugScenarios.Entry("test", "Test", "a test", FreeFlightScenario::new));
        assertEquals("test", DebugScenarios.all().get(DebugScenarios.all().size() - 1).id);
        // Registering the same id again replaces it rather than listing it twice
        DebugScenarios.register(new DebugScenarios.Entry("test", "Test 2", "again", FreeFlightScenario::new));
        assertEquals(1, DebugScenarios.all().stream().filter(e -> e.id.equals("test")).count());
        assertEquals("Test 2", DebugScenarios.find("test").name);
        assertNotSame(DebugScenarios.find("test").create(), DebugScenarios.find("test").create(), "a new one for each load");
    }

    @Test
    void theConsoleLoadsScenariosThroughTheScreen() {
        List<DebugScenarios.Entry> loaded = new ArrayList<>();
        assertTrue(run("scenario('free-flight')").contains("Scenarios load in the game"), "no loader: says where they do");
        dev.setScenarioLoader(loaded::add);
        assertEquals("Loading Free flight", run("scenario('free-flight')"));
        assertEquals("Loading a new career", run("scenario('career')"));
        assertEquals(DebugScenarios.find("free-flight"), loaded.get(0));
        assertNull(loaded.get(1), "career is a new career, not a scenario");
        assertTrue(run("scenario('nope')").contains("No scenario"));
        // One line per scenario: look through them all, not just the last
        dev.console.submit("scenarios()");
        assertTrue(dev.console.lines().stream().anyMatch(l -> l.text.startsWith("free-flight: ")));
    }

    @Test
    void overlaysToggleFromTheConsoleWhetherDrawnHereOrLent() {
        new BuiltInOverlays().install(dev.overlays);
        boolean[] lent = {false};
        dev.addOverlay("heat", "lent by the deck renderer", () -> lent[0], v -> lent[0] = v);
        assertEquals(List.of("grid", "velocity", "bodies", "stats", "heat"), dev.overlays.ids());
        assertFalse(dev.overlayShown("grid"));
        assertEquals("grid shown", run("overlay('grid')"));
        assertTrue(dev.overlays.isShown("grid"));
        assertEquals("grid hidden", run("overlay('grid', 'off')"));
        assertEquals("heat shown", run("overlay('heat', true)"));
        assertTrue(lent[0], "the screen's own switch was thrown");
        assertTrue(run("overlay()").contains("heat (shown): lent by the deck renderer"));
        assertTrue(run("overlay('nope')").contains("there is grid, velocity"));
    }

    @Test
    void aScenarioSetsUpOnTheCareersWorldWithItsButtonsAndOverlays() {
        int[] presses = {0};
        boolean[] deck = {false};
        DebugScenario s = setup -> {
            setup.action("Press", "counts", () -> presses[0]++);
            setup.overlay("mine", "the scenario's own", new DebugOverlay() {
            }, true);
            setup.deckView();
            setup.console().define("hello", "hello()", "says hello", args -> "hi");
            assertSame(game.player, setup.world.getPlayer());
            assertSame(dev.actions.tools, setup.tools());
        };
        s.setUp(new ScenarioSetup(new DebugScenarios.Entry("test", "Test", "", () -> s), world, game, assets, dev,
                dev.overlays, dev.menuActions, new ScenarioSetup.Screen() {
                    @Override
                    public void setDeckView(boolean d) {
                        deck[0] = d;
                    }

                    @Override
                    public void playerShipChanged() {
                    }

                    @Override
                    public void setPointer(ScenarioPointer pointer) {
                    }
                }));
        assertTrue(deck[0]);
        assertTrue(dev.overlays.isShown("mine"));
        run("action('press')");
        assertEquals(1, presses[0]);
        assertTrue(run("action()").contains("Press: counts"));
        assertEquals("hi", run("hello()"));
    }
}
