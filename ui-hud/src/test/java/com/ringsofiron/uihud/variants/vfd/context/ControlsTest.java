package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.GameOverProvider.GameOver;
import com.ringsofiron.uihud.providers.KeyHintProvider;
import com.ringsofiron.uihud.providers.KeyHintProvider.HelpSection;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.LostCard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M6, the controls card, and the lost card: the card's keys and order, and what the lost card holds back. */
class ControlsTest {
    private final HudData d = new HudData();
    private final VfdContextHud hud = new VfdContextHud();
    private final Modes modes = hud.modes;
    private final ControlsMode controls = modes.controls;

    /** The game's panel at the helm and docked, in its own order: on deck first, the pad's last. */
    private void atTheHelmDocked() {
        d.hints = new KeyHintProvider() {
            @Override
            public void collect(KeyHintFrame out) {
            }

            @Override
            public void collectAll(HelpFrame out) {
                HelpSection deck = out.add("On deck");
                deck.add("W/A/S/D", "walk", "MOVE_UP");
                HelpSection any = out.add("Anywhere");
                any.add("I", "crew", "CREW");
                any.add("J", "missions held", "MISSIONS");
                any.add("F1", "controls", "HELP");
                HelpSection helm = out.add("At the helm");
                helm.here = true;
                helm.add("W/S", "thrust", "THRUST");
                HelpSection docked = out.add("Docked");
                docked.here = true;
                docked.add("T", "market", "TRADE");
                HelpSection pad = out.add("Gamepad");
                pad.gamepad = true;
                pad.add("Back+A/B/X/Y", "seats", "");
            }
        };
    }

    @Test
    void theKeyboardListsWhereYouAreFirstAndTheGamepadThePadAlone() {
        atTheHelmDocked();
        assertTrue(modes.open(ClassicWindow.HELP, d));
        ControlsTab k = controls.keyboard;
        assertSame(k, controls.tab, "F1 opens at the Keyboard");
        assertEquals("At the helm, docked", k.here.toString());
        assertEquals(4, k.order.size);
        assertEquals("At the helm", k.order.get(0).title);
        assertEquals("Docked", k.order.get(1).title);
        assertEquals("Anywhere", k.order.get(2).title, "what works anywhere works here too");
        assertEquals("On deck", k.order.get(3).title, "the rest in the panel's order");
        ControlsTab pad = controls.gamepad;
        pad.refresh(modes.boards, d);
        assertEquals(1, pad.order.size);
        assertEquals("Gamepad", pad.order.first().title);
        assertEquals("", pad.here.toString(), "the pad's buttons are the same wherever you are");
    }

    @Test
    void pgDnAndPgUpTurnTheCardsPagesWithinThoseItTakes() {
        ControlsTab k = controls.keyboard;
        assertTrue(k.key(Keys.PAGE_DOWN));
        assertEquals(0, k.page, "one page: nothing to turn to");
        k.pages = 2;
        assertTrue(k.key(Keys.PAGE_DOWN));
        assertEquals(1, k.page);
        k.scrolled(1);
        assertEquals(1, k.page);
        assertTrue(k.key(Keys.PAGE_UP));
        assertEquals(0, k.page);
        k.key(Keys.PAGE_DOWN);
        k.shown();
        assertEquals(0, k.page, "the card comes up at its first page");
    }

    @Test
    void theShipContractsAndLogKeysSayWhatTheyOpenHere() {
        KeyHint crew = new HelpSection().add("I", "crew", "CREW");
        assertEquals("the ship: systems, crew, cargo, loadout", ControlsTab.label(crew));
        assertEquals("contracts: the station's board, else the captain's record",
                ControlsTab.label(new HelpSection().add("J", "missions held", "MISSIONS")));
        assertEquals("the captain's record, at its log", ControlsTab.label(new HelpSection().add("L", "notice log", "LOG")));
        assertEquals("crew (patching a hull leak)", ControlsTab.label(new HelpSection().add("I", "crew (patching a hull leak)", "CREW")),
                "a hint that says why it shows keeps its words");
        assertEquals("quick save", ControlsTab.label(new HelpSection().add("F5", "quick save", "QUICK_SAVE")));
    }

    @Test
    void theDigitsTurnTheCardAndF1IsItsKey() {
        atTheHelmDocked();
        assertTrue(modes.open(ClassicWindow.HELP, d));
        assertSame(controls, modes.current);
        assertEquals("F1", controls.closeKey());
        assertTrue(controls.showing(ClassicWindow.HELP));
        assertFalse(controls.showing(ClassicWindow.LOG));
        assertTrue(controls.key(Keys.NUM_2));
        assertSame(controls.gamepad, controls.tab);
        assertTrue(controls.showing(ClassicWindow.HELP), "the Gamepad stands for the panel too");
        assertTrue(controls.key(Keys.NUM_1));
        assertSame(controls.keyboard, controls.tab);
        assertFalse(controls.key(Keys.NUM_3), "a digit past its tabs is the game's, as under the classic panel");
        assertSame(controls.keyboard, controls.tab);
    }

    @Test
    void whileTheShipIsLostTheCardShowsAndNoModeOpensOrTakesAKey() {
        atTheHelmDocked();
        assertFalse(modes.open(ClassicWindow.GAME_OVER, d), "not while the career goes on");
        assertTrue(modes.open(ClassicWindow.HELP, d));
        assertSame(controls, modes.current);
        lost(d.gameOver);
        assertTrue(modes.open(ClassicWindow.GAME_OVER, d));
        assertNull(modes.current, "the lost card closes what was open");
        assertTrue(modes.up());
        assertTrue(modes.showing(ClassicWindow.GAME_OVER));
        assertFalse(modes.showing(ClassicWindow.HELP));
        assertFalse(modes.open(ClassicWindow.HELP, d), "the panels wait, as the classic screen has them");
        assertFalse(modes.open(ClassicWindow.LOG, d));
        assertFalse(modes.keyDown(Keys.J), "its keys are the game's");
        assertFalse(modes.keyDown(Keys.ENTER));
        assertNull(modes.current);
        // A save loaded: the game closes it, and the modes open again
        modes.close(ClassicWindow.GAME_OVER);
        assertFalse(modes.up());
        assertTrue(modes.open(ClassicWindow.HELP, d));
    }

    @Test
    void aClickOnAWayRunsItAsItsKeyWouldAndEscIsTheKeyAlone() {
        GameOver g = new GameOver();
        int[] ran = new int[2];
        lost(g);
        g.choices.get(0).run = () -> ran[0]++;
        g.choices.get(1).run = () -> ran[1]++;
        LostCard card = new LostCard();
        card.place(1280f, 800f, g);
        card.clicked(1, g);
        card.clicked(2, g);
        card.clicked(7, g);
        assertEquals(0, ran[0]);
        assertEquals(1, ran[1]);
    }

    private static void lost(GameOver g) {
        g.clear();
        g.shown = true;
        g.shipLost = true;
        g.title = "SHIP LOST";
        g.line = "You crashed into Luna on day 12.";
        g.add("F9", "QUICK_LOAD", "load the quick save", () -> { });
        g.add("Enter", "", "load the latest autosave", () -> { });
        g.add("Esc", "", "quit", null);
    }
}
