package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
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
    void aGuardedKeyGivesItsOrderOnlyOnTheSecondPressAndDropsWhenTheRowChanges() {
        Rows r = new Rows(false, false);
        r.guarded = true;
        r.refresh(null);
        r.key(Input.Keys.ENTER);
        assertEquals(0, r.presses, "the first press only lifts the guard");
        assertTrue(r.guardUp(0));
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
        // A new notice is read in at the next refresh; the cautions key shows only the caution
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

    /** A list of rows, headings where {@code heads} says, with a key to act and one to go on. */
    private static final class Rows extends ListPage {
        boolean[] heads;
        boolean guarded, offer = true;
        String refusal;
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
            return !heads[i];
        }

        @Override
        void row(YardPaint p, int i, int r, boolean selected) {
        }

        @Override
        void keys() {
            if (guarded) guardedKey("SELL", refusal);
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
