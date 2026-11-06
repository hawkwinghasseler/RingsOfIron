package com.ringsofiron.uihud.variants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreensTest {
    /** A classic window: it opens only while available, as the market only opens docked. */
    static final class Window implements Screens.Classic {
        boolean available = true, open;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public boolean setOpen(boolean open) {
            this.open = open && available;
            return this.open;
        }
    }

    /** A variant that shows its own screens for {@code replaced}. */
    static final class Variant implements HudVariant {
        final Set<ClassicWindow> replaced;
        final EnumSet<ClassicWindow> showing = EnumSet.noneOf(ClassicWindow.class);
        int opens;

        Variant(ClassicWindow first, ClassicWindow... rest) {
            replaced = EnumSet.of(first, rest);
        }

        @Override
        public void attach(HudVariantHost host) {
        }

        @Override
        public void update(float delta) {
        }

        @Override
        public boolean replaces(ClassicWindow window) {
            return replaced.contains(window);
        }

        @Override
        public boolean open(ClassicWindow window) {
            opens++;
            showing.add(window);
            return true;
        }

        @Override
        public boolean showing(ClassicWindow window) {
            return showing.contains(window);
        }

        @Override
        public void close(ClassicWindow window) {
            showing.remove(window);
        }
    }

    private final Screens screens = new Screens();
    private final EnumMap<ClassicWindow, Window> classic = new EnumMap<>(ClassicWindow.class);

    @BeforeEach
    void registerEveryWindow() {
        for (ClassicWindow w : ClassicWindow.values()) {
            Window c = new Window();
            classic.put(w, c);
            screens.register(w, c);
        }
    }

    @Test
    void underTheClassicHudTheKeysOpenAndCloseTheClassicWindows() {
        assertTrue(screens.toggle(ClassicWindow.TRADE));
        assertTrue(classic.get(ClassicWindow.TRADE).open);
        assertTrue(screens.isOpen(ClassicWindow.TRADE));
        assertTrue(screens.anyOpen());
        assertTrue(screens.toggle(ClassicWindow.TRADE));
        assertFalse(classic.get(ClassicWindow.TRADE).open);
        // Undocked: the market's key does nothing
        classic.get(ClassicWindow.TRADE).available = false;
        assertFalse(screens.toggle(ClassicWindow.TRADE));
        assertFalse(screens.anyOpen());
    }

    @Test
    void escClosesTheTextPanelsFirstThenTheMapThenTheWindowsOneAPress() {
        screens.open(ClassicWindow.CONSOLE);
        screens.open(ClassicWindow.TRADE);
        screens.open(ClassicWindow.MAP);
        screens.open(ClassicWindow.LOG);
        assertTrue(screens.back());
        assertFalse(screens.isOpen(ClassicWindow.LOG));
        assertTrue(screens.isOpen(ClassicWindow.MAP));
        assertTrue(screens.back());
        assertFalse(screens.isOpen(ClassicWindow.MAP));
        assertTrue(screens.back());
        assertFalse(screens.isOpen(ClassicWindow.TRADE));
        assertTrue(screens.isOpen(ClassicWindow.CONSOLE));
        assertTrue(screens.back());
        assertFalse(screens.back());
    }

    @Test
    void theGameOverScreenIsNotAWindowEscCloses() {
        classic.get(ClassicWindow.GAME_OVER).open = true;
        assertFalse(screens.anyOpen());
        assertFalse(screens.back());
        assertTrue(screens.isOpen(ClassicWindow.GAME_OVER));
    }

    @Test
    void aVariantThatReplacesAWindowShowsItsScreenAndTheClassicOneStaysShut() {
        Variant v = new Variant(ClassicWindow.TRADE, ClassicWindow.YARD);
        screens.setVariant(v, null);
        assertTrue(screens.replaced(ClassicWindow.TRADE));
        assertFalse(screens.replaced(ClassicWindow.CREW));
        assertTrue(screens.toggle(ClassicWindow.TRADE));
        assertTrue(v.showing(ClassicWindow.TRADE));
        assertFalse(classic.get(ClassicWindow.TRADE).open);
        assertTrue(screens.isOpen(ClassicWindow.TRADE));
        assertTrue(screens.anyOpen());
        assertTrue(screens.variantShowing());
        // Its key again closes it, as Esc would
        screens.toggle(ClassicWindow.TRADE);
        assertFalse(v.showing(ClassicWindow.TRADE));
        // Windows it doesn't replace stay classic
        screens.open(ClassicWindow.CREW);
        assertTrue(classic.get(ClassicWindow.CREW).open);
        assertTrue(screens.back());
        assertFalse(classic.get(ClassicWindow.CREW).open);
    }

    @Test
    void theGameStillDecidesWhenAReplacedWindowCanOpenAndItsScreenClosesWhenItCanNoLonger() {
        Variant v = new Variant(ClassicWindow.BOARDING);
        screens.setVariant(v, null);
        classic.get(ClassicWindow.BOARDING).available = false;
        assertFalse(screens.open(ClassicWindow.BOARDING));
        assertEquals(0, v.opens);
        classic.get(ClassicWindow.BOARDING).available = true;
        assertTrue(screens.open(ClassicWindow.BOARDING));
        screens.update();
        assertTrue(v.showing(ClassicWindow.BOARDING));
        // The ships part
        classic.get(ClassicWindow.BOARDING).available = false;
        screens.update();
        assertFalse(v.showing(ClassicWindow.BOARDING));
    }

    @Test
    void theMapsCanvasStaysTheGamesAndAVariantShowsThePanelsRoundIt() {
        Variant v = new Variant(ClassicWindow.MAP);
        screens.setVariant(v, null);
        assertTrue(screens.toggle(ClassicWindow.MAP));
        assertTrue(classic.get(ClassicWindow.MAP).open);
        assertTrue(v.showing(ClassicWindow.MAP));
        assertTrue(screens.back());
        assertFalse(classic.get(ClassicWindow.MAP).open);
        assertFalse(v.showing(ClassicWindow.MAP));
        // The variant shuts its panels itself: the canvas goes with them on the next frame
        screens.open(ClassicWindow.MAP);
        v.close(ClassicWindow.MAP);
        screens.update();
        assertFalse(classic.get(ClassicWindow.MAP).open);
        // and the other way round
        screens.open(ClassicWindow.MAP);
        classic.get(ClassicWindow.MAP).open = false;
        screens.update();
        assertFalse(v.showing(ClassicWindow.MAP));
    }

    @Test
    void aVariantsGameOverScreenOpensByItselfAndGoesWhenASaveIsLoaded() {
        Variant v = new Variant(ClassicWindow.GAME_OVER);
        screens.setVariant(v, null);
        screens.update();
        // Available means the career is over; nothing to show before
        classic.get(ClassicWindow.GAME_OVER).available = false;
        screens.update();
        assertFalse(v.showing(ClassicWindow.GAME_OVER));
        classic.get(ClassicWindow.GAME_OVER).available = true;
        screens.update();
        assertTrue(v.showing(ClassicWindow.GAME_OVER));
        assertFalse(screens.anyOpen());
        assertFalse(screens.back());
        classic.get(ClassicWindow.GAME_OVER).available = false;
        screens.update();
        assertFalse(v.showing(ClassicWindow.GAME_OVER));
    }

    @Test
    void switchingVariantsKeepsWhatWasOpenShownByWhoeverShowsItNow() {
        screens.open(ClassicWindow.TRADE);
        screens.open(ClassicWindow.MAP);
        screens.open(ClassicWindow.CONSOLE);
        EnumSet<ClassicWindow> was = EnumSet.noneOf(ClassicWindow.class);
        screens.opened(was);
        Variant v = new Variant(ClassicWindow.TRADE, ClassicWindow.MAP, ClassicWindow.CONSOLE);
        screens.setVariant(v, was);
        assertFalse(classic.get(ClassicWindow.TRADE).open);
        assertTrue(v.showing(ClassicWindow.TRADE));
        // The canvas stays up, with the new variant's panels round it
        assertTrue(classic.get(ClassicWindow.MAP).open);
        assertTrue(v.showing(ClassicWindow.MAP));
        // A console opens again only from E at it
        assertFalse(v.showing(ClassicWindow.CONSOLE));
        // Back to the classic HUD: its market opens again
        was.clear();
        screens.opened(was);
        screens.setVariant(null, was);
        assertTrue(classic.get(ClassicWindow.TRADE).open);
        assertTrue(screens.isOpen(ClassicWindow.MAP));
    }

    @Test
    void aWindowNothingIsRegisteredForNeverOpens() {
        Screens bare = new Screens();
        assertFalse(bare.open(ClassicWindow.LOG));
        assertFalse(bare.isOpen(ClassicWindow.LOG));
        assertFalse(bare.available(ClassicWindow.LOG));
        assertFalse(bare.anyOpen());
    }

    @Test
    void aVariantReplacesNoWindowUnlessItSaysSo() {
        HudVariant v = new HudVariant() {
            @Override
            public void attach(HudVariantHost host) {
            }

            @Override
            public void update(float delta) {
            }
        };
        for (ClassicWindow w : ClassicWindow.values()) {
            assertFalse(v.replaces(w), w.name());
            assertFalse(v.open(w), w.name());
            assertFalse(v.showing(w), w.name());
        }
        assertNull(v.mapPainter(), "the classic map's look");
        assertNull(v.shellLook(), "the classic menus");
    }
}
