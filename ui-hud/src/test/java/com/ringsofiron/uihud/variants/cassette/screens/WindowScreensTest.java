package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider;
import com.ringsofiron.uihud.providers.GameOverProvider.GameOver;
import com.ringsofiron.uihud.providers.KeyHintProvider.HelpFrame;
import com.ringsofiron.uihud.providers.MissionProvider;
import com.ringsofiron.uihud.providers.MissionProvider.MissionBoard;
import com.ringsofiron.uihud.providers.YardProvider;
import com.ringsofiron.uihud.providers.YardProvider.YardBoard;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of the Yard Standard's windows that need no window: what the boards read from the
 * providers' lines, a list's selection and soft keys with their guard, a console's tabs, the controls
 * cards' sections, the telex's rows, and the numbers as the tubes write them.
 */
class WindowScreensTest {

    @Test
    void theCareerLineGivesTheRankTheStationsOwnerAndTheStanding() {
        Boards b = new Boards();
        HudData d = new HudData();
        d.missions = missions(out -> {
            out.stationName = "Highport";
            out.career = "Lieutenant, Terran Patrol Authority.  Standing with Terran Patrol Authority +42";
        });
        b.refresh(d);
        assertEquals("Lieutenant, Terran Patrol Authority", b.rank);
        assertEquals("Terran Patrol Authority", b.owner);
        assertEquals("TERRAN PATROL AUTHORITY", b.ownerCaps);
        assertEquals("TPA", b.badge);
        assertTrue(b.hasStanding);
        assertEquals(42, b.standing);
        assertEquals("HIGHPORT", b.stationCaps);
        assertTrue(b.docked());
        // Away from a station the board gives the rank alone
        d.missions = missions(out -> out.career = "Cadet");
        b.refresh(d);
        assertEquals("Cadet", b.rank);
        assertEquals("", b.owner);
        assertEquals("", b.badge);
        assertFalse(b.hasStanding);
        assertFalse(b.docked());
    }

    @Test
    void aStandingBelowZeroKeepsItsSign() {
        Boards b = new Boards();
        HudData d = new HudData();
        d.missions = missions(out -> out.career = "Captain, Free Traders.  Standing with Belt Free Traders' Union -7");
        b.refresh(d);
        assertEquals(-7, b.standing);
        assertEquals("BFTU", b.badge);
    }

    @Test
    void theTanksLineGivesEachTankAndHowFullItIs() {
        Boards b = new Boards();
        HudData d = new HudData();
        d.yard = yard(out -> {
            out.stationName = "Highport";
            out.tanks = "Propellant 96%, oxygen 100%, no water";
        });
        b.refresh(d);
        assertEquals(2, b.tanks.size, "a part with no share is left out");
        assertEquals("PROPELLANT", b.tanks.get(0));
        assertEquals("OXYGEN", b.tanks.get(1));
        assertEquals(0.96f, b.tankFull.get(0), 1e-4f);
        assertEquals(1f, b.tankFull.get(1), 1e-4f);
    }

    @Test
    void anOwnersBadgeTakesTheCapitalsOfItsWordsAndLeavesOutTheShortOnes() {
        assertEquals("TPA", Boards.initials("Terran Patrol Authority"));
        assertEquals("UB", Boards.initials("Union of the Belt"));
        assertEquals("CMRC", Boards.initials("Ceres Mining and Refining Combine"), "four letters at most");
        assertEquals("", Boards.initials(""));
    }

    @Test
    void aListsSelectionSkipsTheHeadingsAndStopsAtTheEnds() {
        Rows r = new Rows(true, false, false, true, false);
        r.refresh(null);
        assertEquals(1, r.sel, "the first row that isn't a heading");
        r.key(Input.Keys.DOWN);
        assertEquals(2, r.sel);
        r.key(Input.Keys.DOWN);
        assertEquals(4, r.sel, "over the heading");
        r.key(Input.Keys.DOWN);
        assertEquals(4, r.sel, "the last row stays");
        r.key(Input.Keys.UP);
        assertEquals(2, r.sel);
        r.key(Input.Keys.HOME);
        assertEquals(1, r.sel);
        r.key(Input.Keys.END);
        assertEquals(4, r.sel);
        // The list shrinks under it: the selection moves to a row still there
        r.heads = new boolean[]{true, false, false};
        r.refresh(null);
        assertEquals(2, r.sel);
    }

    @Test
    void aRowsSecondLineIsPassedOverAndBelongsToTheRowAboveIt() {
        // A heading, then two people of two lines each
        Rows r = new Rows(true, false, false, false, false);
        r.seconds = new boolean[]{false, false, true, false, true};
        r.refresh(null);
        assertEquals(1, r.sel);
        r.key(Input.Keys.DOWN);
        assertEquals(3, r.sel, "over the first one's second line");
        r.key(Input.Keys.DOWN);
        assertEquals(3, r.sel, "the last one's second line is no row of its own");
        assertEquals(3, r.owner(4));
        assertEquals(1, r.owner(2));
        assertEquals(0, r.owner(0), "a heading is its own");
        r.key(Input.Keys.UP);
        assertEquals(1, r.sel);
    }

    @Test
    void aSidePanelStepsItsTabsWithTabAndLeavesTheNumberKeysToTheSeat() {
        Boards boards = new Boards();
        BigConsole c = new BigConsole(BigConsole.Livery.SHIP, "SLATE", boards).side();
        Rows a = new Rows(false), b = new Rows(false), e = new Rows(false);
        a.side = true;
        b.side = true;
        c.add(a).add(b).add(e);
        c.show(a, new HudData());
        assertTrue(c.isSide());
        assertTrue(c.hangsAtSide(), "a page that fits hangs at the right");
        assertFalse(c.key(Input.Keys.NUM_2), "the helm's orders stay the seat's");
        assertSame(a, c.current());
        assertTrue(c.key(Input.Keys.TAB));
        assertSame(b, c.current());
        assertTrue(c.key(Input.Keys.TAB));
        assertSame(e, c.current());
        assertFalse(c.hangsAtSide(), "a page that needs the room goes across the screen");
        assertTrue(c.key(Input.Keys.TAB));
        assertSame(a, c.current(), "round the row");
        // The modes' console keeps its number keys
        BigConsole wide = new BigConsole(BigConsole.Livery.SHIP, "SHIP", boards);
        Rows f = new Rows(false), g = new Rows(false);
        f.side = true;
        wide.add(f).add(g);
        wide.show(f, new HudData());
        assertFalse(wide.hangsAtSide());
        assertTrue(wide.key(Input.Keys.NUM_2));
        assertSame(g, wide.current());
    }

    @Test
    void thePanelsArrangeTheWindowsAsTheStationLinkAndTheSlate() {
        WindowScreens w = new WindowScreens();
        BigConsole market = w.consoleFor(ClassicWindow.TRADE), crew = w.consoleFor(ClassicWindow.CREW);
        assertFalse(market.isSide(), "the Context spec's modes by default");
        assertTrue(market != crew);
        w.panels(true);
        BigConsole link = w.consoleFor(ClassicWindow.TRADE), slate = w.consoleFor(ClassicWindow.CREW);
        assertTrue(link.isSide());
        assertTrue(slate.isSide());
        assertTrue(link != slate);
        assertSame(link, w.consoleFor(ClassicWindow.YARD), "the yard is a tab of the link");
        assertSame(slate, w.consoleFor(ClassicWindow.MISSIONS), "away from a station, J is the slate's contracts");
        assertSame(slate, w.consoleFor(ClassicWindow.LOG));
        assertSame(slate, w.consoleFor(ClassicWindow.BOARDING));
        assertEquals("BOARD", w.boardLabel(), "the spec's word for the station's tab");
        // And back: the modes' consoles, their own words for the tabs
        w.panels(false);
        assertFalse(w.consoleFor(ClassicWindow.TRADE).isSide());
        assertEquals("MISSIONS", w.boardLabel());
    }

    @Test
    void boardingAloneCanHangBesideTheViewUnderTheModes() {
        WindowScreens w = new WindowScreens();
        assertFalse(w.consoleFor(ClassicWindow.BOARDING).isSide(), "across the screen by default");
        w.sideBoarding(true);
        BigConsole brd = w.consoleFor(ClassicWindow.BOARDING);
        assertTrue(brd.isSide(), "the Task spec's BRD, down the right side");
        assertEquals(WindowScreens.BOARDING_SIDE_W, brd.sideW(), 0.01f);
        assertTrue(brd.sideW() <= 360f * 1.25f, "the wireframe's 360 by no more than the quarter an artist may add");
        assertFalse(w.consoleFor(ClassicWindow.TRADE).isSide(), "the other windows stay across the screen");
        assertFalse(w.consoleFor(ClassicWindow.CREW).isSide());
        assertFalse(w.consoleFor(ClassicWindow.LOG).isSide());
        assertTrue(brd != w.consoleFor(ClassicWindow.CREW), "boarding keeps a console of its own");
        assertEquals(BigConsole.SIDE_W, new BigConsole(BigConsole.Livery.SHIP, "SLATE", new Boards()).side().sideW(), 0.01f,
                "the Stations spec's panels keep their 480");
        // A HUD whose units leave it less room hangs it narrower, never wider than its own
        w.boardingWidth(386f);
        assertEquals(386f, brd.sideW(), 0.01f);
        w.boardingWidth(600f);
        assertEquals(WindowScreens.BOARDING_SIDE_W, brd.sideW(), 0.01f);
        WindowScreens across = new WindowScreens();
        across.boardingWidth(386f);
        assertFalse(across.consoleFor(ClassicWindow.BOARDING).isSide(), "across the screen it takes no width");
    }

    @Test
    void aDeckConsoleStaysTheGamesUnlessAHudAsksForTheModesAnswer() {
        WindowScreens w = new WindowScreens();
        assertFalse(w.replaces(ClassicWindow.CONSOLE), "the game's own console window by default");
        assertTrue(w.replaces(ClassicWindow.CREW));
        w.consoles(WindowScreens.Consoles.MODES);
        assertTrue(w.replaces(ClassicWindow.CONSOLE), "the Context spec's: a seat, or Ship › Systems");
        assertFalse(w.showing(ClassicWindow.CONSOLE), "it leaves no console screen up");
        w.consoles(null);
        assertFalse(w.replaces(ClassicWindow.CONSOLE), "none is the game's again");
        // E sits the captain at the three seats (Context 4.3); the reactor and life support open Systems
        assertTrue(WindowScreens.seated("helm"));
        assertTrue(WindowScreens.seated("weapons"));
        assertTrue(WindowScreens.seated("turret_mount"));
        assertFalse(WindowScreens.seated("reactor"));
        assertFalse(WindowScreens.seated("life_support"));
        assertFalse(WindowScreens.seated(""));
    }

    @Test
    void aPartsStateGoesBesideItsValueAsFarAsTheSidePanelsWidthAllows() {
        assertEquals(BoardingPage.NARROW_STATE, BoardingPage.stateRoom(BigConsole.SIDE_W), "480 wide: 16 columns");
        assertEquals(14, BoardingPage.stateRoom(WindowScreens.BOARDING_SIDE_W), "450 wide: 14, so a longer one goes under it");
        assertEquals(7, BoardingPage.stateRoom(386f), "386, as the Task HUD hangs it at 1280: 7, its tube's room past the value");
        // A page too short for the other ship's plate has the list alone: the Task HUD's panel under a crisis
        // placard at 1280 x 800 is 362 tall, its title bar, a row of tabs and its foot taking 130 of it
        float chrome = BigConsole.SIDE_TITLE_H + 6f + BigConsole.SIDE_TAB_ROW + 2f + BigConsole.SIDE_FOOT_H;
        assertTrue(BoardingPage.listOnly(362f - chrome));
        assertFalse(BoardingPage.listOnly(642f - chrome), "the whole height under the visor's slot has the plate");
        assertFalse(BoardingPage.listOnly(592f - chrome), "and the Stations slate over its band, the shortest it hangs at 800 tall");
        assertEquals(BoardingPage.NARROW_STATE, BoardingPage.stateRoom(1280f), "across the screen it has its own column");
    }

    @Test
    void theFootGivesEachTabTheKeyThatOpensItThere() {
        Boards boards = new Boards();
        CrewPage crew = new CrewPage(false), hire = new CrewPage(true);
        assertEquals("I", crew.windowKey());
        assertNull(hire.windowKey(), "I opens the slate's crew, not the hiring hall");
        MissionsPage held = new MissionsPage(true), board = new MissionsPage(false);
        held.refresh(boards);
        board.refresh(boards);
        assertEquals("J", held.windowKey(), "away from a station J opens the contracts held");
        assertEquals("J", board.windowKey());
        boards.missions.stationName = "Highport";
        held.refresh(boards);
        assertNull(held.windowKey(), "docked, J opens the station's board");
        assertEquals("J", board.windowKey());
    }

    @Test
    void theTerminalsQuantityIsWhatEachTradeKeyGives() {
        Boards boards = new Boards();
        boards.trade.stationName = "Highport";
        boards.trade.credits = 5000;
        boards.trade.hasHold = true;
        boards.trade.cargoCapacity = 40;
        com.ringsofiron.uihud.providers.TradeProvider.TradeLine food = boards.trade.addLine();
        food.commodityId = "food";
        food.name = "Food";
        food.available = 935.6;
        food.buyPrice = 38;
        food.sellPrice = 30;
        food.stationBuys = true;
        food.held = 4;
        food.onOrder = 6;
        MarketPage m = new MarketPage();
        m.refresh(boards);
        assertEquals(0, m.sel);
        assertEquals(1.0, m.units(food, true), 1e-9, "one at a time to start");
        assertEquals(1.0, m.units(food, false), 1e-9);
        m.control(MarketPage.Q10);
        assertEquals(MarketPage.Q10, m.qty);
        assertEquals(MarketPage.BUY, m.focus, "the keys go on to the trade it can make");
        assertEquals(10.0, m.units(food, true), 1e-9);
        assertEquals(4.0, m.units(food, false), 1e-9, "what is held; what is coming aboard isn't yet");
        m.control(MarketPage.QALL);
        assertEquals(131.0, m.units(food, true), 1e-9, "all it can pay for: 5,000 at 38 a unit");
        food.onOrder = -3;
        assertEquals(1.0, m.units(food, false), 1e-9, "what is held less what is on its way off");
        // With nothing to pay with, Enter is SELL
        boards.trade.credits = 0;
        m.focus = -1;
        assertEquals(MarketPage.SELL, m.action());
        assertEquals("935", MarketPage.wholeUnits(935.6));
        assertEquals("1,559", MarketPage.wholeUnits(1559.4));
        assertEquals("1", MarketPage.wholeUnits(0.97));
        assertEquals("<1", MarketPage.wholeUnits(0.4));
    }

    @Test
    void aSentenceBreaksAtAWordForItsSecondLine() {
        String eva = "EVA: crew go out for work outside as it comes";
        int at = CrewPage.breakAt(eva, 42);
        assertEquals("EVA: crew go out for work outside as it", eva.substring(0, at));
        assertEquals("comes", CrewPage.rest(eva, 42));
        assertEquals(eva.length(), CrewPage.breakAt(eva, 60), "whole where it fits");
        assertEquals("", CrewPage.rest(eva, 60));
        assertEquals(8, CrewPage.breakAt("Unbroken-sentence", 8), "cut where there is no word to break at");
    }

    @Test
    void aGuardedKeyGivesItsOrderOnlyOnTheSecondPressAndDropsWhenTheRowChanges() {
        Rows r = new Rows(false, false);
        r.guarded = true;
        r.refresh(null);
        r.key(Input.Keys.ENTER);
        assertEquals(0, r.presses, "the first press only lifts the guard");
        assertTrue(r.guardUp(0));
        assertEquals("Press SELL again: it can't be undone", r.said.peek(), "and says what the second does");
        r.key(Input.Keys.ENTER);
        assertEquals(1, r.presses, "the second gives the order");
        assertEquals(0, r.pressed);
        assertFalse(r.guardUp(0));
        // Lifted, then another row: the guard is down again
        r.key(Input.Keys.ENTER);
        assertTrue(r.guardUp(0));
        r.key(Input.Keys.DOWN);
        assertFalse(r.guardUp(0));
        r.key(Input.Keys.ENTER);
        assertEquals(1, r.presses);
        // And down again as the page comes up afresh
        r.shown();
        assertFalse(r.guardUp(0));
        // A key may say its own words
        r.ask = "Let Cole go? Press again: Cole leaves the ship for good";
        r.key(Input.Keys.ENTER);
        assertEquals(r.ask, r.said.peek());
    }

    @Test
    void boardingListsThePartsMostValuableFirstAndANobodyAboardCraftNeedsNoParty() {
        Array<BoardingProvider.Part> parts = new Array<>();
        String[] names = {"Coilgun", "Reactor", "Laser Mk1", "Harpoon Launcher"};
        double[] values = {750, 2000, 2250, 1500};
        for (int i = 0; i < names.length; i++) {
            BoardingProvider.Part p = new BoardingProvider.Part();
            p.name = names[i];
            p.value = values[i];
            parts.add(p);
        }
        parts.get(2).off = true;
        Array<Integer> order = new Array<>();
        BoardingPage.mostValuableFirst(parts, order);
        assertEquals("Reactor", parts.get(order.get(0)).name);
        assertEquals("Harpoon Launcher", parts.get(order.get(1)).name);
        assertEquals("Coilgun", parts.get(order.get(2)).name);
        assertEquals("Laser Mk1", parts.get(order.get(3)).name, "a part already off goes last");

        BoardingProvider.BoardingBoard bb = new BoardingProvider.BoardingBoard();
        bb.state = "Disabled: reactor down";
        bb.disabled = true;
        bb.salvage = true;
        assertTrue(BoardingPage.state(bb).startsWith("Nobody aboard"));
        bb.taken = true;
        bb.state = "Taken";
        assertEquals("Taken", BoardingPage.state(bb));
        // Defended, the game's own words
        bb.taken = false;
        bb.salvage = false;
        bb.defenders = 2;
        bb.state = "Disabled: crew down";
        assertEquals("Disabled: crew down", BoardingPage.state(bb));
    }

    @Test
    void boardingOffersOnlyTheOrdersOfItsPhaseAndSaysWhatTheLinkCantReachBeforeAPartIsMarked() {
        BoardingProvider.BoardingBoard want = new BoardingProvider.BoardingBoard();
        HudData d = new HudData();
        d.boarding = boarding(want);
        Boards b = new Boards();
        BoardingPage page = new BoardingPage();
        // Before the boarding: send the party, or storm it
        want.craftName = "Pirate raider 3";
        want.state = "Disabled: reactor down";
        want.disabled = true;
        want.defenders = 3;
        b.refresh(d);
        page.refresh(b);
        assertEquals("ORDERS BOARD STORM", page.outline());
        // While the party is across, only the recall
        want.boarding = true;
        b.refresh(d);
        page.refresh(b);
        assertEquals("ORDERS RECALL", page.outline());
        // Taken, the game refuses a recall: no orders, the parts
        want.boarding = false;
        want.taken = true;
        want.salvage = true;
        want.defenders = 0;
        BoardingProvider.Part engine = want.addPart(), off = want.addPart();
        engine.name = "Ion Drive";
        engine.value = 900;
        engine.reach = "EVA only";
        engine.reachWhy = "can't be reached from inside: stand off alongside to strip it on EVA";
        off.name = "Laser Mk1";
        off.value = 2250;
        off.marked = off.off = true;
        off.toggleable = false;
        off.status = "carrying it home";
        // An escape pod is worth nothing: it is left out
        BoardingProvider.Part pod = want.addPart();
        pod.name = "Escape Pod";
        b.refresh(d);
        page.refresh(b);
        assertEquals("PARTS_TO_STRIP PART PART", page.outline());
        // Latched, the engine nobody can reach from inside says so, and can still be marked
        assertEquals("EVA only", BoardingPage.partState(engine));
        assertNull(BoardingPage.partRefusal(b.boarding, engine));
        // Selected, the soft row gives the game's whole reason
        assertEquals("CAN'T BE REACHED FROM INSIDE: STAND OFF ALONGSIDE TO STRIP IT ON EVA", BoardingPage.partNote(engine, 30));
        // Marked, its state is the game's, given whole on the soft row where the list cuts it short
        engine.marked = true;
        engine.status = "can't be reached from inside: stand off alongside to strip it on EVA";
        assertEquals(engine.status, BoardingPage.partState(engine));
        assertEquals("CAN'T BE REACHED FROM INSIDE: STAND OFF ALONGSIDE TO STRIP IT ON EVA", BoardingPage.partNote(engine, 30));
        engine.status = "stripping 40%";
        assertNull(BoardingPage.partNote(engine, 30));
        engine.status = "can't be reached from inside: stand off alongside to strip it on EVA";
        assertEquals("carrying it home", BoardingPage.partState(off));
        assertEquals("ALREADY OFF: IT IS BEING CARRIED HOME", BoardingPage.partRefusal(b.boarding, off));
        // Standing off, a part inside the hull can't be marked, and says why
        BoardingProvider.Part inside = new BoardingProvider.Part();
        inside.reach = "latch on";
        inside.reachWhy = inside.status = "inside the hull: latch on to strip it";
        inside.toggleable = false;
        assertEquals("latch on", BoardingPage.partState(inside));
        assertEquals("INSIDE THE HULL: LATCH ON TO STRIP IT", BoardingPage.partRefusal(b.boarding, inside));
        // A beaten craft nobody defends needs no party: straight to the lists
        want.taken = false;
        b.refresh(d);
        page.refresh(b);
        assertEquals("PARTS_TO_STRIP PART PART", page.outline());
    }

    @Test
    void aKeyThatCannotActSaysWhyAndEnterGoesToOneThatCan() {
        Rows r = new Rows(false);
        r.refusal = "The hold is full";
        r.refresh(null);
        r.key(Input.Keys.RIGHT);
        r.key(Input.Keys.ENTER);
        assertEquals(0, r.presses);
        assertEquals("The hold is full", r.said.peek());
        // With no key picked, Enter presses the first that can act
        r.shown();
        r.key(Input.Keys.ENTER);
        assertEquals(1, r.presses);
        assertEquals(1, r.pressed);
        // Left from none goes round to the last
        r.shown();
        r.key(Input.Keys.LEFT);
        assertEquals(1, r.focusKey);
    }

    @Test
    void aConsolesNumberKeysTurnItsOfferedTabsAndAreItsOwnPastTheLast() {
        Boards boards = new Boards();
        BigConsole c = new BigConsole(BigConsole.Livery.SHIP, "SHIP", boards);
        Rows a = new Rows(false), b = new Rows(false), e = new Rows(false);
        b.offer = false;
        c.add(a).add(b).add(e);
        c.show(a, new HudData());
        assertTrue(c.up());
        assertSame(a, c.current());
        assertFalse(c.offers(b), "a page the place doesn't offer has no tab");
        assertTrue(c.key(Input.Keys.NUM_2));
        assertSame(e, c.current(), "the second tab shown is the third page");
        assertTrue(c.key(Input.Keys.NUM_5), "past the last tab the key is still the console's");
        assertSame(e, c.current());
        c.flip(1);
        assertSame(a, c.current(), "round the row");
        // Closed and raised again, the page comes up afresh
        a.shows = 0;
        c.hide();
        assertFalse(c.up());
        c.show(a, new HudData());
        assertEquals(1, a.shows);
    }

    @Test
    void aControlsCardShowsItsOwnSectionsAndAnywhereTakesWhatNoCardNames() {
        HelpFrame help = new HelpFrame();
        ControlsCard[] cards = new ControlsCard[3];
        cards[0] = new ControlsCard("HELM", "AT THE HELM", help, cards, false, "At the helm");
        cards[1] = new ControlsCard("DOCKED", "DOCKED AT A STATION", help, cards, false, "Docked");
        cards[2] = new ControlsCard("ANYWHERE", "ANYWHERE", help, cards, true, "Anywhere");
        help.add("At the helm").add("W/S", "thrust", "THRUST");
        help.add("Docked");
        assertTrue(cards[0].offered());
        assertFalse(cards[1].offered(), "a section with no keys gives its card nothing to show");
        assertFalse(cards[2].offered(), "the helm's keys are on the helm's card alone");
        help.add("Salvage").add("G", "grab", "");
        assertTrue(cards[2].offered(), "a section no card names goes on Anywhere");
    }

    @Test
    void theTelexPrintsWhatHappenedOnShortRowsUnderItsTitle() {
        Telex t = new Telex();
        GameOver over = new GameOver();
        over.shown = true;
        over.title = "Career over";
        over.line = "You were killed, and none of your crew were left alive to bring the ship home to a station.";
        int[] ran = {0};
        over.add("F9", "QUICK_LOAD", "load the quick save", () -> ran[0]++);
        over.add("Esc", "", "menu", null);
        t.show(over, 4 * 86400 + 2 * 3600 + 11 * 60);
        assertTrue(t.visible);
        assertEquals("RINGS OF IRON TELEX   DAY 004  02:11", t.rows.get(0));
        assertEquals("CAREER OVER", t.rows.get(Telex.TITLE_ROW));
        assertEquals("NNNN", t.rows.peek());
        for (int i = 0; i < t.rows.size; i++) assertTrue(t.rows.get(i).length() <= Math.max(Telex.LINE, t.rows.get(0).length()));
        StringBuilder said = new StringBuilder();
        for (int i = Telex.TITLE_ROW + 2; i < t.rows.size - 2; i++) said.append(said.length() == 0 ? "" : " ").append(t.rows.get(i));
        assertEquals(over.line.toUpperCase(java.util.Locale.ROOT), said.toString(), "nothing lost in the wrapping");
        // A way on runs as its key would; one the game keeps for itself does nothing here
        t.hit(0);
        assertEquals(1, ran[0]);
        t.hit(1);
        t.hit(7);
        assertEquals(1, ran[0]);
    }

    @Test
    void theLogKeepsItsNoticesInOrderThoughTheFeedRefillsFromItsPool() {
        HudData d = new HudData();
        Boards b = new Boards();
        b.data = d;
        fill(d, "Third", "Second", "First");
        LogPage log = new LogPage();
        log.refresh(b);
        assertEquals("Third", log.line(0), "the newest first");
        // The game refills the feed each frame: its pooled entries now carry other notices
        fill(d, "Third", "Second", "First");
        assertEquals("Third", log.line(0));
        assertEquals("First", log.line(2));
        // A new notice is read in at the next refresh; notable and up shows only the caution
        fill(d, "Fourth", "Third", "Second", "First");
        d.messages.all().get(1).level = com.ringsofiron.uihud.data.AlertRegistry.Level.CAUTION;
        log.refresh(b);
        assertEquals(4, log.lines());
        assertEquals("Fourth", log.line(0));
        log.key(Input.Keys.RIGHT);
        assertEquals(1, log.lines());
        assertEquals("Third", log.line(0));
    }

    /** The feed refilled as the game does it: cleared, then each notice newest first. */
    private static void fill(HudData d, String... newestFirst) {
        d.messages.clear();
        for (int i = 0; i < newestFirst.length; i++) {
            d.messages.add(newestFirst[i], com.ringsofiron.uihud.data.AlertRegistry.Level.ADVISORY, 60.0 * (newestFirst.length - i));
        }
    }

    @Test
    void theTubesWriteNumbersAndTimesAsTheMockupsDo() {
        assertEquals("DAY 004  02:11", Fmt.stamp(4 * 86400 + 2 * 3600 + 11 * 60 + 30));
        assertEquals("004 02:11", Fmt.dayTime(4 * 86400 + 2 * 3600 + 11 * 60));
        assertEquals("2,350", Fmt.units(2350));
        assertEquals("12.5", Fmt.units(12.5));
        assertEquals("35.7", Fmt.price(35.66));
        assertEquals("1,850", Fmt.price(1850));
        assertEquals("1,240 cr", Fmt.credits(1240.4));
        assertEquals("The nose", Fmt.capital("the nose"));
        assertEquals("Highport", Ledger.cut("Highport", 8));
        assertEquals("Highp…", Ledger.cut("Highport", 6));
        assertEquals("…", Ledger.cut("Highport", 1));
        assertEquals("", Ledger.cut(null, 4));
    }

    /** A mission board that fills the board as {@code fill} says and takes no orders. */
    private static MissionProvider missions(Consumer<MissionBoard> fill) {
        return new MissionProvider() {
            @Override
            public void collect(MissionBoard out) {
                fill.accept(out);
            }

            @Override
            public String accept(long id) {
                return "";
            }

            @Override
            public String abandon(long id) {
                return "";
            }
        };
    }

    /** A boarding board that says what {@code from} says and takes no orders. */
    private static BoardingProvider boarding(BoardingProvider.BoardingBoard from) {
        return new BoardingProvider() {
            @Override
            public void collect(BoardingBoard out) {
                out.craftName = from.craftName;
                out.state = from.state;
                out.byEva = from.byEva;
                out.boarding = from.boarding;
                out.salvage = from.salvage;
                out.taken = from.taken;
                out.disabled = from.disabled;
                out.defenders = from.defenders;
                out.parts.addAll(from.parts);
            }

            @Override
            public String orderBoarding() {
                return "";
            }

            @Override
            public String recall() {
                return "";
            }

            @Override
            public String toggleStrip(int x, int y) {
                return "";
            }

            @Override
            public String takeCargo(String commodity) {
                return "";
            }
        };
    }

    /** A yard that fills the board as {@code fill} says and takes no orders. */
    private static YardProvider yard(Consumer<YardBoard> fill) {
        return new YardProvider() {
            @Override
            public void collect(YardBoard out) {
                fill.accept(out);
            }

            @Override
            public String orderRepairs() {
                return "";
            }

            @Override
            public String orderRefit(int hardpoint, String weaponId, boolean used) {
                return "";
            }
        };
    }

    /**
     * A list of rows, headings where {@code heads} says and second lines where {@code seconds} does,
     * with a key to act and one to go on.
     */
    private static final class Rows extends ListPage {
        boolean[] heads, seconds;
        boolean guarded, offer = true, side;
        String refusal, ask;
        int pressed = -1, presses, shows;
        final Array<String> said = new Array<>();

        Rows(boolean... heads) {
            super("ROWS", "ROWS", ClassicWindow.TRADE);
            this.heads = heads;
        }

        @Override
        boolean offered() {
            return offer;
        }

        @Override
        void refresh(Boards b) {
            keepSelection();
        }

        @Override
        void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        }

        @Override
        int count() {
            return heads.length;
        }

        @Override
        boolean selectable(int i) {
            return !heads[i] && !belongsAbove(i);
        }

        @Override
        boolean belongsAbove(int i) {
            return seconds != null && seconds[i];
        }

        @Override
        boolean fitsSide() {
            return side;
        }

        @Override
        void row(YardPaint p, int i, int r, boolean selected) {
        }

        @Override
        void keys() {
            if (guarded) guardedKey("SELL", refusal, ask);
            else softKey("BUY", refusal);
            softKey("NEXT", null);
        }

        @Override
        void press(int k) {
            pressed = k;
            presses++;
        }

        @Override
        void shown() {
            super.shown();
            shows++;
        }

        @Override
        void say(String text) {
            said.add(text);
        }
    }
}
