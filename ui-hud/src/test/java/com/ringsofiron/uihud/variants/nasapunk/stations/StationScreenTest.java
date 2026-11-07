package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.MissionProvider;
import com.ringsofiron.uihud.providers.TradeProvider;
import com.ringsofiron.uihud.providers.YardProvider;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.nasapunk.screens.Page;
import com.ringsofiron.uihud.variants.nasapunk.screens.ScreenModes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stations spec's displays in place of the game's windows, without a window: which display and tab each window opens, which keys they take, and how they close. */
class StationScreenTest {
    /** The game as far as the modes see it. */
    private static final class Game implements ScreenModes.Game {
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

    /** Highport with a market, a yard, a mission board and berths to hire into, or the ship away from it, a delivery held. */
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
            // a delivery held, so the contracts have something to show away from the station
            out.addHeld().title = "Deliver 40 Food to Shackleton Ice";
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
    private StationScreen modes;

    @BeforeEach
    void dockedAtHighport() {
        game = new Game();
        port = new Highport();
        game.data.trade = port;
        game.data.yard = port;
        game.data.missions = port;
        game.data.crew = port;
        modes = new StationScreen(game, new Group(), null);
    }

    private static List<String> names(Iterable<Page> pages) {
        List<String> out = new ArrayList<>();
        for (Page p : pages) out.add(p.name);
        return out;
    }

    private List<String> tabNames() {
        List<String> out = new ArrayList<>();
        for (Page p : modes.tabs()) out.add(modes.tabName(p));
        return out;
    }

    @Test
    void theMarketOpensTheStationLinkAmongTheTabsTheStationOffers() {
        assertTrue(modes.show(ClassicWindow.TRADE));
        assertEquals(StationScreen.Mode.LINK, modes.mode());
        assertEquals("MARKET", modes.page().name);
        // Highport sells no ships, so there is no shipyard tab
        assertEquals(List.of("MARKET", "YARD", "CONTRACT BOARD", "HIRING"), tabNames());
        assertTrue(modes.showing(ClassicWindow.TRADE));
        assertTrue(modes.covering());
        assertFalse(modes.slateShows(), "the station link takes the screen, not the right side");
    }

    @Test
    void theYardIsTheStationLinksAtABerthAndTheSlatesFieldRefitAwayFromIt() {
        assertTrue(modes.show(ClassicWindow.YARD));
        assertEquals(StationScreen.Mode.LINK, modes.mode());
        assertEquals("YARD", modes.page().name);
        modes.closeMode();
        port.docked = false;
        assertTrue(modes.show(ClassicWindow.YARD));
        assertEquals(StationScreen.Mode.SLATE, modes.mode());
        assertEquals("LOADOUT", modes.page().name);
        assertTrue(modes.showing(ClassicWindow.YARD));
        assertTrue(modes.slateShows());
    }

    @Test
    void theContractsAreTheBoardDockedAndTheSlatesUndocked() {
        assertTrue(modes.show(ClassicWindow.MISSIONS));
        assertEquals(StationScreen.Mode.LINK, modes.mode());
        assertEquals("MISSIONS", modes.page().name);
        modes.closeMode();
        port.docked = false;
        assertTrue(modes.show(ClassicWindow.MISSIONS));
        assertEquals(StationScreen.Mode.SLATE, modes.mode());
        assertEquals("CONTRACTS", modes.page().name);
        // away from a yard the slate carries the crew's field refit first; with nothing latched, no boarding
        assertEquals(List.of("LOADOUT", "CREW", "CONTRACTS", "LOG"), tabNames());
    }

    @Test
    void theCrewAndTheLogOpenTheSlateWhichTurnsOnTheirKeys() {
        assertTrue(modes.show(ClassicWindow.CREW));
        assertEquals(StationScreen.Mode.SLATE, modes.mode());
        assertEquals("CREW", modes.page().name);
        assertEquals(List.of("CREW", "CONTRACTS", "LOG"), tabNames());
        assertTrue(modes.show(ClassicWindow.LOG));
        assertEquals("LOG", modes.page().name);
        assertTrue(modes.showing(ClassicWindow.LOG));
        assertFalse(modes.showing(ClassicWindow.CREW));
    }

    @Test
    void theSlateLeavesTheNumberKeysToTheSeatAndTheStationLinkTurnsItsTabsOnThem() {
        modes.show(ClassicWindow.CREW);
        assertFalse(modes.keyDown(Input.Keys.NUM_2), "a helm order's key at a seat");
        assertFalse(modes.keyDown(Input.Keys.NUMPAD_1));
        assertEquals("CREW", modes.page().name);
        assertTrue(modes.keyDown(Input.Keys.DOWN), "the arrows are the page's");
        modes.closeMode();
        modes.show(ClassicWindow.TRADE);
        assertTrue(modes.keyDown(Input.Keys.NUM_2));
        assertEquals("YARD", modes.page().name);
        assertTrue(modes.keyDown(Input.Keys.NUM_4));
        assertEquals("HIRE", modes.page().name);
        assertFalse(modes.keyDown(Input.Keys.NUM_5), "one past the tabs is the game's");
    }

    @Test
    void undockingTakesTheStationLinkAwayWhateverItsTab() {
        modes.show(ClassicWindow.TRADE);
        modes.keyDown(Input.Keys.NUM_3);
        assertEquals("MISSIONS", modes.page().name);
        port.docked = false;
        modes.update(0.3f);
        assertFalse(modes.covering());
    }

    @Test
    void theControlsBoardingWithNothingLatchedAndTheShipLostCard() {
        assertTrue(modes.show(ClassicWindow.HELP));
        assertEquals(StationScreen.Mode.CONTROLS, modes.mode());
        assertFalse(modes.slateShows());
        modes.closeMode();
        // nothing latched: boarding has no page to show
        assertFalse(modes.show(ClassicWindow.BOARDING));
        assertFalse(modes.covering());
        modes.show(ClassicWindow.CREW);
        assertTrue(modes.show(ClassicWindow.GAME_OVER));
        assertTrue(modes.lostShows());
        modes.close(ClassicWindow.GAME_OVER);
        assertTrue(modes.showing(ClassicWindow.CREW));
    }

    @Test
    void aTabTheGameWontOpenIsLeftOutAndTheMapClosesTheDisplay() {
        game.barred.add(ClassicWindow.YARD);
        modes.show(ClassicWindow.TRADE);
        assertEquals(List.of("MARKET", "CONTRACT BOARD", "HIRING"), tabNames());
        game.map = true;
        modes.update(0.016f);
        assertFalse(modes.covering(), "the map closes the station link");
    }
}
