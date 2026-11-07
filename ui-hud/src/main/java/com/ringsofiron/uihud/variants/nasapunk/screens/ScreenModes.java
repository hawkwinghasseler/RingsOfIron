package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;

import java.util.Locale;

/**
 * The screens a Nasa-punk combination shows in place of the game's windows: its modes (the context
 * spec's), panels (the task spec's) or slates (the stations spec's), each holding some of the pages
 * as its tabs, and the ship-lost card over everything. One mode shows at a time. The combination
 * says which mode and page each window opens ({@link #route}), which pages each mode has
 * ({@link #tabsOf}) and how a mode looks ({@link #paintMode}); this keeps the rest the same for all
 * three: the boards read four times a second while a mode shows and after each order, the tabs
 * offered now, the feed line, the keys (the number keys turn the tabs, the page takes the arrows,
 * Enter and the page keys), the clicks and the wheel, and a mode giving way to the map or a
 * console's window.
 *
 * @param <M> the combination's modes
 */
public abstract class ScreenModes<M extends Enum<M>> implements PageHost, ScreenActor.Painter {
    /** What the modes need of the game's HUD, so the tests can stand in for it. */
    public interface Game {
        HudData data();

        /** Whether the game would open {@code w} now. */
        boolean available(ClassicWindow w);

        /** Opens {@code w} through the game, so its rules hold. */
        boolean open(ClassicWindow w);

        boolean mapOpen();

        void closeMap();

        /** Whether a window the game draws itself (a console's) has the keys. */
        boolean classicWindowOpen();

        float width();

        float height();
    }

    /** The game as a variant's host gives it. */
    public static Game of(HudVariantHost h) {
        return new Game() {
            @Override
            public HudData data() {
                return h.data();
            }

            @Override
            public boolean available(ClassicWindow w) {
                return h.hud().available(w);
            }

            @Override
            public boolean open(ClassicWindow w) {
                return h.hud().open(w);
            }

            @Override
            public boolean mapOpen() {
                return h.hud().isMapOpen();
            }

            @Override
            public void closeMap() {
                h.hud().setMapVisible(false);
            }

            @Override
            public boolean classicWindowOpen() {
                return h.hud().openWindow() != null;
            }

            @Override
            public float width() {
                return h.width();
            }

            @Override
            public float height() {
                return h.height();
            }
        };
    }

    /** How often the boards are read again while a mode shows, s. */
    public static final float READ_EVERY = 0.25f;
    /** What a click on the keys that close the mode reaches. */
    protected static final Object CLOSE = new Object();

    protected final Game game;
    protected final ScreenActor actor;
    protected final Boards boards = new Boards();
    protected final Feed feed = new Feed();
    protected final LostCard lost = new LostCard();
    /** The HUD's panels as it last laid them out, UI units, y up, for the ship-lost card to strike through. */
    public final Array<Rectangle> panels = new Array<>();

    protected final MarketPage market = new MarketPage();
    protected final YardPage yard = new YardPage();
    protected final ShipyardPage shipyard = new ShipyardPage();
    protected final OffersPage offers = new OffersPage();
    protected final HirePage hire = new HirePage();
    protected final CrewPage crew = new CrewPage();
    protected final LoadoutPage loadout = new LoadoutPage();
    protected final BoardingPage boarding = new BoardingPage();
    protected final HeldPage held = new HeldPage();
    protected final LogPage log = new LogPage();
    protected final HelpPage help = new HelpPage();

    protected M mode;
    protected Page page;
    /** The open mode's pages that are offered now, in their order: its tabs. */
    protected final Array<Page> tabs = new Array<>();
    protected boolean lostShows;
    /** The mode's frame as last drawn, local units, y down: the wheel over it stays the screen's. */
    protected final Rectangle frame = new Rectangle();
    /** Where {@link #route} sends a window: the mode, and the page in it. */
    private M to;
    private Page toPage;
    private float readIn;
    private long seenRevision = -1;
    private boolean mapWasOpen;

    protected ScreenModes(Game game, Group layer, NpKit kit) {
        this.game = game;
        actor = new ScreenActor(kit, this);
        actor.setVisible(false);
        layer.addActor(actor);
        for (Page p : new Page[] {market, yard, shipyard, offers, hire, crew, loadout, boarding, held, log, help}) p.attach(this);
    }

    // ---- what the combination says --------------------------------------------------------------

    /** Which mode and page {@code w} opens now, with {@link #go}; false for a window it doesn't show. The boards are fresh. */
    protected abstract boolean route(ClassicWindow w);

    /** Adds mode {@code m}'s pages to {@link #tabs}, each with {@link #tab}, in their order. */
    protected abstract void tabsOf(M m);

    /** Draws the open mode on {@link #page}, laying down the boxes its clicks go to. */
    protected abstract void paintMode(NpCanvas cv, Camera cam, float w, float h, Hits hits);

    /** Whether mode {@code m} is the station's, which goes when the ship undocks (unless a prize is still to hand over). */
    protected boolean docksOnly(M m) {
        return false;
    }

    /** {@link #route}'s answer: mode {@code m} on page {@code p}. */
    protected final boolean go(M m, Page p) {
        to = m;
        toPage = p;
        return true;
    }

    /** Adds {@code p} to the tabs if it has anything to show and the game would open its window now. */
    protected final void tab(Page p) {
        if (p.offered(boards) && (p.window == null || game.available(p.window))) tabs.add(p);
    }

    // ---- what shows -------------------------------------------------------------------------------

    /** Whether a mode shows, so the HUD under it hides. */
    public boolean covering() {
        return mode != null;
    }

    public boolean lostShows() {
        return lostShows;
    }

    public M mode() {
        return mode;
    }

    public Page page() {
        return page;
    }

    public Array<Page> tabs() {
        return tabs;
    }

    /** Whether the screen for {@code w} shows: the ship-lost card, or the open mode on {@code w}'s page. */
    public boolean showing(ClassicWindow w) {
        if (w == ClassicWindow.GAME_OVER) return lostShows;
        return mode != null && page != null && page.window == w;
    }

    /** Shows the mode {@code w} opens, on its page, or turns the open mode to it; true when it shows. */
    public boolean show(ClassicWindow w) {
        if (w == ClassicWindow.GAME_OVER) {
            lostShows = true;
            actor.setVisible(true);
            return true;
        }
        boards.refresh(game.data());
        if (!route(w)) return false;
        M was = mode;
        Page p = toPage;
        mode = to;
        fillTabs();
        if (!tabs.contains(p, true)) p = tabs.size > 0 ? tabs.first() : null;
        if (p == null) {
            mode = was;
            fillTabs();
            return false;
        }
        if (was != mode) {
            feed.clear();
            seenRevision = game.data().messages.revision();
            // the modes take the screen one at a time, the map among them
            if (game.mapOpen()) game.closeMap();
        }
        mapWasOpen = false;
        turnTo(p);
        actor.setVisible(true);
        return true;
    }

    /** Closes the open mode if it shows {@code w}'s page; the ship-lost card when the career goes on. */
    public void close(ClassicWindow w) {
        if (w == ClassicWindow.GAME_OVER) {
            lostShows = false;
            actor.setVisible(mode != null);
            return;
        }
        if (showing(w)) closeMode();
    }

    /** Closes the open mode, whatever its page. */
    public void closeMode() {
        mode = null;
        page = null;
        tabs.clear();
        actor.release();
        actor.setVisible(lostShows);
    }

    /** Turns the open mode to its page {@code p}. */
    protected void turnTo(Page p) {
        page = p;
        p.refresh(boards);
        readIn = READ_EVERY;
    }

    private void fillTabs() {
        tabs.clear();
        if (mode != null) tabsOf(mode);
    }

    // ---- each frame ------------------------------------------------------------------------------

    public void update(float delta) {
        actor.setBounds(0f, 0f, game.width(), game.height());
        if (mode == null) {
            actor.setVisible(lostShows);
            return;
        }
        boolean mapOpen = game.mapOpen();
        // the map opened over the mode (M), or a console's window: the mode gives way
        if (mapOpen && !mapWasOpen || game.classicWindowOpen()) {
            closeMode();
            return;
        }
        mapWasOpen = mapOpen;
        feed.act(delta);
        page.act(delta);
        notices(game.data());
        readIn -= delta;
        if (readIn <= 0f) reread();
    }

    /** The game's newest notice goes on the feed line as it comes, for its 5 s. */
    private void notices(HudData d) {
        long rev = d.messages.revision();
        if (rev == seenRevision) return;
        seenRevision = rev;
        MessageFeed.Message m = d.messages.newest();
        if (m != null) feed.say(Sources.of(m.text), Sources.body(m.text));
    }

    /** The feed's line as it shows now, its source first, or "". */
    public String feedLine() {
        return feed.showing() ? feed.source + " " + feed.text : "";
    }

    // ---- PageHost --------------------------------------------------------------------------------

    @Override
    public HudData data() {
        return game.data();
    }

    @Override
    public void say(String source, String text) {
        feed.say(source, text);
        // the game's own notice of the same order says it again: the order's words stand
        seenRevision = game.data().messages.revision();
    }

    @Override
    public void reread() {
        readIn = READ_EVERY;
        if (mode == null) return;
        boards.refresh(game.data());
        fillTabs();
        // docked no longer: the station's mode goes, whatever tab it was on
        if (docksOnly(mode) && !boards.docked() && !tabs.contains(shipyard, true) || tabs.size == 0) {
            closeMode();
            return;
        }
        if (!tabs.contains(page, true)) page = tabs.first();
        page.refresh(boards);
    }

    @Override
    public boolean available(ClassicWindow window) {
        return game.available(window);
    }

    @Override
    public boolean open(ClassicWindow window) {
        return game.open(window);
    }

    @Override
    public void closeScreen() {
        closeMode();
    }

    // ---- keys ------------------------------------------------------------------------------------

    /** A key while a mode shows: a number turns to its tab, one past them is the game's; the page takes the arrows, Enter and the page keys. */
    public boolean keyDown(int keycode) {
        if (mode == null || page == null) return false;
        int n = digit(keycode);
        if (n > 0) {
            if (n > tabs.size) return false;
            turnTo(tabs.get(n - 1));
            return true;
        }
        return page.key(keycode);
    }

    private static int digit(int keycode) {
        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) return keycode - Input.Keys.NUM_1 + 1;
        if (keycode >= Input.Keys.NUMPAD_1 && keycode <= Input.Keys.NUMPAD_9) return keycode - Input.Keys.NUMPAD_1 + 1;
        return 0;
    }

    /**
     * What Enter gives on row {@code r}, or why it can't, for a frame that shows the cursor row's
     * orders away from the row: ENTER and the order, its second press while armed, or the order and
     * why it is barred; with ← → PICK when the row has more than one. "" for a row with no orders.
     */
    protected String enterLine(Row r) {
        if (r == null || r.acts.size == 0) return "";
        Act enter = page.picked(r);
        if (enter == null) return "NOTHING TO DO HERE NOW";
        String pick = r.acts.size > 1 ? "  ·  ← → PICK" : "";
        if (!enter.live) return enter.label + ": " + (enter.why.isEmpty() ? "NOT NOW" : enter.why) + pick;
        if (enter.state == Act.State.ARMED) return "ENTER AGAIN TO " + enter.label;
        return "ENTER " + enter.label + pick;
    }

    /** The key that opens the page's window and closes it again, as the game binds the windows' keys. */
    protected String closeKey() {
        if (page == null || page.window == null) return "ESC";
        switch (page.window) {
            case TRADE: return "T";
            case YARD:
            case SHIPYARD: return "Y";
            case MISSIONS: return "J";
            case BOARDING: return "K";
            case CREW: return "I";
            case LOG: return boundKey("LOG", "L");
            case HELP: return boundKey("HELP", "F1");
            default: return "ESC";
        }
    }

    private String boundKey(String action, String fallback) {
        String k = game.data().key(action);
        return k == null || k.isEmpty() ? fallback : k.toUpperCase(Locale.ROOT);
    }

    // ---- clicks, the wheel and drawing -----------------------------------------------------------

    @Override
    public void clicked(Object target, int arg) {
        if (lostShows) {
            LostCard.clicked(target);
            return;
        }
        if (mode == null || page == null) return;
        if (target == CLOSE) {
            closeMode();
            return;
        }
        if (target instanceof Page) {
            if (tabs.contains((Page) target, true)) turnTo((Page) target);
            return;
        }
        Tables.click(page, target, arg);
    }

    @Override
    public boolean scrolled(float x, float y, int amount) {
        if (lostShows || page == null) return false;
        int s = page.sectAt(x, y);
        if (s < 0) return frame.contains(x, y);
        page.scroll(s, amount * 3);
        return true;
    }

    @Override
    public void paint(NpCanvas cv, Camera cam, float w, float h, Hits hits) {
        if (mode != null && page != null) {
            page.clearAreas();
            paintMode(cv, cam, w, h, hits);
        }
        if (lostShows) lost.paint(cv, w, h, game.data().gameOver, panels, hits);
    }

    /** The index of the open page's part titled {@code title}, or −1. */
    protected int sectIndex(String title) {
        if (page == null) return -1;
        for (int i = 0; i < page.sects.size; i++) if (page.sects.get(i).title.equals(title)) return i;
        return -1;
    }
}
