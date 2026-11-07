package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.MissionProvider;
import com.ringsofiron.uihud.providers.TradeProvider;
import com.ringsofiron.uihud.providers.YardProvider;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.nasapunk.screens.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The context spec's modes in place of the game's windows, without a window: which mode and tab each window opens, and how they close. */
class ModeScreenTest {
    /** The game as far as the modes see it. */
    private static final class Game implements ModeScreen.Game {
        final HudData data = new HudData();
        final EnumSet<ClassicWindow> barred = EnumSet.noneOf(ClassicWindow.class);
        final List<ClassicWindow> opened = new ArrayList<>();
        boolean map, console;

        @Override
        public HudData data() {
            return data;
        }

        @Override
        public boolean available(ClassicWindow w) {
            return !barred.contains(w);
        }

        @Override
        public boolean open(ClassicWindow w) {
            opened.add(w);
            return true;
        }

        @Override
        public boolean mapOpen() {
            return map;
        }

        @Override
        public void closeMap() {
            map = false;
        }

        @Override
        public boolean classicWindowOpen() {
            return console;
        }

        @Override
        public float width() {
            return 1280f;
        }

        @Override
        public float height() {
            return 800f;
        }
    }

    /** Highport with a market, a yard, a mission board and berths to hire into, or the ship away from it. */
    private static final class Highport implements TradeProvider, YardProvider, MissionProvider, CrewProvider {
        boolean docked = true;

        @Override
        public void collect(TradeBoard out) {
            if (!docked) return;
            out.stationName = "Highport";
            out.credits = 5000;
            out.cargoCapacity = 40;
            out.hasHold = true;
            TradeLine l = out.addLine();
            l.commodityId = "food";
            l.name = "Food";
            l.available = 100;
            l.buyPrice = 50;
        }

        @Override
        public double buy(String commodityId, double units) {
            return units;
        }

        @Override
        public double sell(String commodityId, double units) {
            return 0;
        }

        @Override
        public void collect(YardBoard out) {
            if (docked) out.stationName = "Highport";
            else out.field = true;
        }

        @Override
        public String orderRepairs() {
            return null;
        }

        @Override
        public String orderRefit(int hardpoint, String weaponId, boolean used) {
            return null;
        }

        @Override
        public void collect(MissionBoard out) {
            if (docked) out.stationName = "Highport";
        }

        @Override
        public String accept(long id) {
            return null;
        }

        @Override
        public String abandon(long id) {
            return null;
        }

        @Override
        public void collect(CrewSheet out) {
            out.shipName = "Kestrel";
            out.summary = "2 / 4 berths";
            if (docked) out.stationName = "Highport";
        }

        @Override
        public String hire(String key) {
            return null;
        }

        @Override
        public String dismiss(long id) {
            return null;
        }

        @Override
        public String takePrisoners() {
            return null;
        }

        @Override
        public String settlePrisoner(long id) {
            return null;
        }

        @Override
        public String recruit(long id) {
            return null;
        }
    }

    private Game game;
    private Highport port;
    private ModeScreen modes;

    @BeforeEach
    void dockedAtHighport() {
        game = new Game();
        port = new Highport();
        game.data.trade = port;
        game.data.yard = port;
        game.data.missions = port;
        game.data.crew = port;
        modes = new ModeScreen(game, new Group(), null);
    }

    private static List<String> names(Iterable<Page> pages) {
        List<String> out = new ArrayList<>();
        for (Page p : pages) out.add(p.name);
        return out;
    }

    @Test
    void theMarketOpensTheStationOnItsTabAmongTheTabsTheStationOffers() {
        assertTrue(modes.show(ClassicWindow.TRADE));
        assertEquals(ModeScreen.Mode.STATION, modes.mode());
        assertEquals("MARKET", modes.page().name);
        // Highport sells no ships, so there is no shipyard tab
        assertEquals(List.of("MARKET", "YARD", "MISSIONS", "HIRE"), names(modes.tabsNow()));
        assertTrue(modes.showing(ClassicWindow.TRADE));
        assertFalse(modes.showing(ClassicWindow.YARD));
        assertTrue(modes.covering());
    }

    @Test
    void theNumberKeysTurnTheTabsAndOnePastThemIsLeftToTheGame() {
        modes.show(ClassicWindow.TRADE);
        assertTrue(modes.keyDown(Input.Keys.NUM_2));
        assertEquals("YARD", modes.page().name);
        assertTrue(modes.showing(ClassicWindow.YARD));
        assertTrue(modes.keyDown(Input.Keys.NUMPAD_4));
        assertEquals("HIRE", modes.page().name);
        assertTrue(modes.showing(ClassicWindow.CREW));
        assertFalse(modes.keyDown(Input.Keys.NUM_5), "a helm order's key");
        assertEquals("HIRE", modes.page().name);
        // the arrows are the page's
        assertTrue(modes.keyDown(Input.Keys.DOWN));
    }

    @Test
    void aWindowClosesTheModeOnlyWhileItsTabShows() {
        modes.show(ClassicWindow.TRADE);
        modes.keyDown(Input.Keys.NUM_2);
        modes.close(ClassicWindow.TRADE);
        assertTrue(modes.covering(), "the market's key turns to the market; it closes only what shows");
        modes.close(ClassicWindow.YARD);
        assertFalse(modes.covering());
        assertNull(modes.mode());
        assertFalse(modes.showing(ClassicWindow.YARD));
    }

    @Test
    void undockingTakesTheStationPanelAwayWhateverItsTab() {
        modes.show(ClassicWindow.TRADE);
        modes.keyDown(Input.Keys.NUM_3);
        port.docked = false;
        modes.update(0.3f);
        assertFalse(modes.covering());
    }

    @Test
    void theYardIsTheStationsAtABerthAndTheCrewsFieldRefitAwayFromIt() {
        assertTrue(modes.show(ClassicWindow.YARD));
        assertEquals(ModeScreen.Mode.STATION, modes.mode());
        assertEquals("YARD", modes.page().name);
        modes.closeMode();
        port.docked = false;
        assertTrue(modes.show(ClassicWindow.YARD));
        assertEquals(ModeScreen.Mode.SHIP, modes.mode());
        assertEquals("LOADOUT", modes.page().name);
        assertEquals(List.of("CREW", "LOADOUT"), names(modes.tabsNow()));
        assertTrue(modes.showing(ClassicWindow.YARD));
    }

    @Test
    void theContractsAreTheStationsMissionsTabWhileItShowsAndTheRecordsOtherwise() {
        modes.show(ClassicWindow.TRADE);
        modes.show(ClassicWindow.MISSIONS);
        assertEquals(ModeScreen.Mode.STATION, modes.mode());
        assertEquals("MISSIONS", modes.page().name);
        modes.closeMode();
        modes.show(ClassicWindow.MISSIONS);
        assertEquals(ModeScreen.Mode.RECORD, modes.mode());
        assertEquals("CONTRACTS", modes.page().name);
        assertEquals(List.of("CONTRACTS", "LOG"), names(modes.tabsNow()));
        modes.show(ClassicWindow.LOG);
        assertEquals("LOG", modes.page().name);
        assertTrue(modes.showing(ClassicWindow.LOG));
        assertFalse(modes.showing(ClassicWindow.MISSIONS));
    }

    @Test
    void theCrewTheControlsAndAWindowTheGameWontOpen() {
        assertTrue(modes.show(ClassicWindow.CREW));
        assertEquals(ModeScreen.Mode.SHIP, modes.mode());
        assertEquals("CREW", modes.page().name);
        assertTrue(modes.show(ClassicWindow.HELP));
        assertEquals(ModeScreen.Mode.CONTROLS, modes.mode());
        modes.closeMode();
        // nothing latched: boarding has no page to show
        assertFalse(modes.show(ClassicWindow.BOARDING));
        assertFalse(modes.covering());
        // a tab the game won't open is left out
        game.barred.add(ClassicWindow.YARD);
        modes.show(ClassicWindow.TRADE);
        assertEquals(List.of("MARKET", "MISSIONS", "HIRE"), names(modes.tabsNow()));
    }

    @Test
    void theMapAndTheModesTakeTheScreenOneAtATime() {
        game.map = true;
        modes.show(ClassicWindow.LOG);
        assertFalse(game.map, "the log closes the map");
        game.map = true;
        modes.update(0.016f);
        assertFalse(modes.covering(), "the map closes the log");
        modes.show(ClassicWindow.CREW);
        game.console = true;
        modes.update(0.016f);
        assertFalse(modes.covering(), "a console's window closes the mode");
    }

    @Test
    void theFeedCarriesAnOrdersWordsThenTheGamesNewNotices() {
        modes.show(ClassicWindow.TRADE);
        game.data.messages.add("Station: an old notice", Level.ADVISORY, 1.0);
        game.data.messages.posted();
        modes.say("STATION", "Ordered 10 Food: the loaders are carrying it aboard");
        modes.update(0.016f);
        assertEquals("STATION Ordered 10 Food: the loaders are carrying it aboard", modes.feedLine());
        // the game fills the feed again newest first
        game.data.messages.clear();
        game.data.messages.add("Helm: Highport gives clearance", Level.ADVISORY, 2.0);
        game.data.messages.add("Station: an old notice", Level.ADVISORY, 1.0);
        game.data.messages.posted();
        modes.update(0.016f);
        assertEquals("HELM Highport gives clearance", modes.feedLine());
        modes.update(5.1f);
        assertEquals("", modes.feedLine());
    }

    @Test
    void theShipLostCardShowsOverWhateverIsOpen() {
        modes.show(ClassicWindow.CREW);
        assertTrue(modes.show(ClassicWindow.GAME_OVER));
        assertTrue(modes.showing(ClassicWindow.GAME_OVER));
        assertTrue(modes.lostShows());
        modes.close(ClassicWindow.GAME_OVER);
        assertFalse(modes.lostShows());
        assertTrue(modes.showing(ClassicWindow.CREW));
    }
}
