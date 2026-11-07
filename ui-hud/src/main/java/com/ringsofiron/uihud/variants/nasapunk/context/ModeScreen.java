package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.providers.TradeProvider.TradeBoard;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.variants.nasapunk.screens.Act;
import com.ringsofiron.uihud.variants.nasapunk.screens.BoardingPage;
import com.ringsofiron.uihud.variants.nasapunk.screens.Boards;
import com.ringsofiron.uihud.variants.nasapunk.screens.CrewPage;
import com.ringsofiron.uihud.variants.nasapunk.screens.Feed;
import com.ringsofiron.uihud.variants.nasapunk.screens.Fmt;
import com.ringsofiron.uihud.variants.nasapunk.screens.HeldPage;
import com.ringsofiron.uihud.variants.nasapunk.screens.HelpPage;
import com.ringsofiron.uihud.variants.nasapunk.screens.HirePage;
import com.ringsofiron.uihud.variants.nasapunk.screens.Hits;
import com.ringsofiron.uihud.variants.nasapunk.screens.LoadoutPage;
import com.ringsofiron.uihud.variants.nasapunk.screens.LogPage;
import com.ringsofiron.uihud.variants.nasapunk.screens.LostCard;
import com.ringsofiron.uihud.variants.nasapunk.screens.MarketPage;
import com.ringsofiron.uihud.variants.nasapunk.screens.OffersPage;
import com.ringsofiron.uihud.variants.nasapunk.screens.Page;
import com.ringsofiron.uihud.variants.nasapunk.screens.PageHost;
import com.ringsofiron.uihud.variants.nasapunk.screens.ScreenActor;
import com.ringsofiron.uihud.variants.nasapunk.screens.Sect;
import com.ringsofiron.uihud.variants.nasapunk.screens.ShipyardPage;
import com.ringsofiron.uihud.variants.nasapunk.screens.Sources;
import com.ringsofiron.uihud.variants.nasapunk.screens.Tables;
import com.ringsofiron.uihud.variants.nasapunk.screens.Ui;
import com.ringsofiron.uihud.variants.nasapunk.screens.YardPage;

import java.util.Locale;

/**
 * The context spec's modes in place of the game's windows (documentation/ui-overhaul/variations/
 * nasapunk-context, C6 to C10): the ship (M2: crew and the field refit, I), the station (M3: market,
 * yard, shipyard, missions and hire, T and Y), boarding (M4, K), the captain's record (M5: the
 * contracts and the log, J and L) and the controls (M6, F1), and the ship-lost card over everything.
 *
 * <p>Each mode has the frame C8 and C9 draw: a console body under P1, a title row of glass with the
 * mode's name and its tabs on their number caps, the page's display units, and a foot row of glass
 * with the one-line feed and the keys that close it. M3 is the one that doesn't take the whole
 * screen: a panel 1 120 wide and as tall as its tab needs over the docked view, with the rail every
 * tab shares (credits, standing, the hold, the tanks). One mode shows at a time; the number keys turn
 * its tabs, and the pages take the arrows, Enter and the page keys.
 */
final class ModeScreen implements PageHost, ScreenActor.Painter {
    /** The spec's modes that stand in for the game's windows, with their plates and title words. */
    enum Mode {
        SHIP("M2  SHIP", "SHIP"),
        STATION("M3  STATION", "STATION"),
        BOARDING("M4  BOARDING", "BOARDING"),
        RECORD("M5  RECORD", "CAPTAIN'S RECORD"),
        CONTROLS("M6  CONTROLS", "");

        final String plate, sub;

        Mode(String plate, String sub) {
            this.plate = plate;
            this.sub = sub;
        }
    }

    /** How often the boards are read again while a mode shows, s. */
    private static final float READ_EVERY = 0.25f;
    private static final float ROW_H = 24f, EDGE = 8f, GAP = 6f, DU_B = 8f;
    /** M3's panel and its rail (C7). */
    private static final float STATION_W = 1120f, RAIL_W = 276f;
    /** A page's own orders at its first unit's foot. */
    private static final float ACTS_H = 34f;
    private static final Object CLOSE = new Object();

    /** What the modes need of the game's HUD, so the tests can stand in for it. */
    interface Game {
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

    /** The game as the variant's host gives it. */
    static Game of(HudVariantHost h) {
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

    private final Game host;
    private final ScreenActor actor;
    private final Boards boards = new Boards();
    private final Feed feed = new Feed();
    private final LostCard lost = new LostCard();
    private final Tables.Style style = new Tables.Style();
    private final Tables.Style keys = new Tables.Style();

    private final MarketPage market = new MarketPage();
    private final YardPage yard = new YardPage();
    private final ShipyardPage shipyard = new ShipyardPage();
    private final OffersPage offers = new OffersPage();
    private final HirePage hire = new HirePage();
    private final CrewPage crew = new CrewPage();
    private final LoadoutPage loadout = new LoadoutPage();
    private final BoardingPage boarding = new BoardingPage();
    private final HeldPage held = new HeldPage();
    private final LogPage log = new LogPage();
    private final HelpPage help = new HelpPage();

    private Mode mode;
    private Page page;
    /** The open mode's pages that are offered now, in their order: its tabs. */
    private final Array<Page> tabs = new Array<>();
    private boolean lostShows;
    private float readIn;
    private long seenRevision = -1;
    private boolean mapWasOpen;
    /** The HUD's panels as it last laid them out, for the ship-lost card to strike through. */
    final Array<Rectangle> panels = new Array<>();
    /** The mode's frame as last drawn, local units, y down. */
    private final Rectangle frame = new Rectangle();

    /** One display unit of a page: its box, and its parts stacked or in columns. */
    private static final class Unit {
        float x, y, w, h;
        final IntArray sects = new IntArray();
        int columns = 1;
        boolean acts;
    }

    private final Array<Unit> units = new Array<>();
    private final Array<Unit> unitPool = new Array<>();
    private final float[] heights = new float[32];

    ModeScreen(Game host, Group layer, NpKit kit) {
        this.host = host;
        actor = new ScreenActor(kit, this);
        actor.setVisible(false);
        layer.addActor(actor);
        for (Page p : new Page[] {market, yard, shipyard, offers, hire, crew, loadout, boarding, held, log, help}) p.attach(this);
        // the glass cockpit's tables: rows tall enough for its lit push buttons (C7)
        style.rowH = 30f;
        style.actW = 72f;
        style.actH = 24f;
        style.gap = 10f;
        style.titleH = 34f;
        style.nameSize = 14f;
        // the controls list keys, not records: tight rows, no rules and no cursor (C10)
        keys.rowH = 21f;
        keys.titleH = 26f;
        keys.rules = false;
        keys.cursor = false;
        keys.gap = 8f;
    }

    // ---- what shows -------------------------------------------------------------------------------

    /** Whether a mode shows, so the HUD under it hides. */
    boolean covering() {
        return mode != null;
    }

    boolean showing(ClassicWindow w) {
        if (w == ClassicWindow.GAME_OVER) return lostShows;
        return mode != null && page != null && page.window == w;
    }

    /**
     * Shows the mode that stands for {@code w}, on its page: the market opens M3 on its Market tab,
     * the field refit M2 on its Loadout tab, the contracts M3's Missions tab when M3 shows and else
     * M5's Contracts tab.
     */
    boolean show(ClassicWindow w) {
        if (w == ClassicWindow.GAME_OVER) {
            lostShows = true;
            actor.setVisible(true);
            return true;
        }
        boards.refresh(host.data());
        Mode m;
        Page p;
        switch (w) {
            case TRADE:
                m = Mode.STATION;
                p = market;
                break;
            case YARD:
                if (yard.offered(boards)) {
                    m = Mode.STATION;
                    p = yard;
                } else {
                    m = Mode.SHIP;
                    p = loadout;
                }
                break;
            case SHIPYARD:
                m = Mode.STATION;
                p = shipyard;
                break;
            case MISSIONS:
                if (mode == Mode.STATION && offers.offered(boards)) {
                    m = Mode.STATION;
                    p = offers;
                } else {
                    m = Mode.RECORD;
                    p = held;
                }
                break;
            case BOARDING:
                m = Mode.BOARDING;
                p = boarding;
                break;
            case CREW:
                m = Mode.SHIP;
                p = crew;
                break;
            case LOG:
                m = Mode.RECORD;
                p = log;
                break;
            case HELP:
                m = Mode.CONTROLS;
                p = help;
                break;
            default:
                return false;
        }
        boolean fresh = mode != m;
        Mode was = mode;
        mode = m;
        tabs();
        if (!tabs.contains(p, true)) p = tabs.size > 0 ? tabs.first() : null;
        if (p == null) {
            mode = was;
            tabs();
            return false;
        }
        if (fresh) {
            feed.clear();
            seenRevision = host.data().messages.revision();
            // the modes take the screen one at a time, the map among them (M1)
            if (host.mapOpen()) host.closeMap();
        }
        mapWasOpen = false;
        turnTo(p);
        actor.setVisible(true);
        return true;
    }

    /** Closes the mode if it shows {@code w}'s page; the ship-lost card when the career goes on. */
    void close(ClassicWindow w) {
        if (w == ClassicWindow.GAME_OVER) {
            lostShows = false;
            actor.setVisible(mode != null);
            return;
        }
        if (showing(w)) closeMode();
    }

    void closeMode() {
        mode = null;
        page = null;
        tabs.clear();
        actor.release();
        actor.setVisible(lostShows);
    }

    private void turnTo(Page p) {
        page = p;
        p.refresh(boards);
        readIn = READ_EVERY;
    }

    /** The open mode's pages that are offered now. */
    private void tabs() {
        tabs.clear();
        if (mode == null) return;
        switch (mode) {
            case SHIP:
                tab(crew);
                tab(loadout);
                break;
            case STATION:
                tab(market);
                tab(yard);
                tab(shipyard);
                tab(offers);
                tab(hire);
                break;
            case BOARDING:
                tab(boarding);
                break;
            case RECORD:
                tab(held);
                tab(log);
                break;
            case CONTROLS:
                tab(help);
                break;
            default:
                break;
        }
    }

    private void tab(Page p) {
        if (p.offered(boards) && (p.window == null || host.available(p.window))) tabs.add(p);
    }

    // ---- each frame ------------------------------------------------------------------------------

    void update(float delta) {
        actor.setBounds(0f, 0f, host.width(), host.height());
        if (mode == null) {
            actor.setVisible(lostShows);
            return;
        }
        boolean mapOpen = host.mapOpen();
        // the map opened over the mode (M), or a console's window: the mode gives way
        if (mapOpen && !mapWasOpen || host.classicWindowOpen()) {
            closeMode();
            return;
        }
        mapWasOpen = mapOpen;
        feed.act(delta);
        page.act(delta);
        notices(host.data());
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

    // ---- PageHost --------------------------------------------------------------------------------

    @Override
    public HudData data() {
        return host.data();
    }

    @Override
    public void say(String source, String text) {
        feed.say(source, text);
        // the game's own notice for the same order says it again: the order's words stand
        seenRevision = host.data().messages.revision();
    }

    @Override
    public void reread() {
        readIn = READ_EVERY;
        if (mode == null) return;
        boards.refresh(host.data());
        tabs();
        // docked no longer: the station's panel goes, whatever tab it was on
        if (mode == Mode.STATION && !boards.docked() && !tabs.contains(shipyard, true) || tabs.size == 0) {
            closeMode();
            return;
        }
        if (!tabs.contains(page, true)) page = tabs.first();
        page.refresh(boards);
    }

    @Override
    public boolean available(ClassicWindow window) {
        return host.available(window);
    }

    @Override
    public boolean open(ClassicWindow window) {
        return host.open(window);
    }

    @Override
    public void closeScreen() {
        closeMode();
    }

    // ---- keys ------------------------------------------------------------------------------------

    /** A key while a mode shows: a number turns to its tab; the page takes the arrows, Enter and the page keys. */
    boolean keyDown(int keycode) {
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

    // ---- clicks and the wheel --------------------------------------------------------------------

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

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    public void paint(NpCanvas cv, Camera cam, float w, float h, Hits hits) {
        if (mode != null && page != null) {
            page.clearAreas();
            if (mode == Mode.STATION) stationPanel(cv, cam, w, h, hits);
            else fullScreen(cv, cam, w, h, hits);
        }
        if (lostShows) lost.paint(cv, w, h, host.data().gameOver, panels, hits);
    }

    /** M2, M4, M5 and M6: the console body under P1, its rows of glass and the page's units (C8 to C10). */
    private void fullScreen(NpCanvas cv, Camera cam, float w, float h, Hits hits) {
        float top = StatusStrip.H + 2f, fh = h - top;
        frame.set(0f, top, w, fh);
        hits.plate(0f, top, w, fh);
        body(cv, 0f, top, w, fh);
        cv.fill(18f, top + fh - 17f, 3f, 10f, NpColors.KAPTON);
        NpHardware.stencil(cv, 27f, top + fh - 8f, mode.plate, 9f, 1.6f, NpCanvas.START);
        titleRow(cv, EDGE, top + 6f, w - 2f * EDGE, hits);
        float footY = top + fh - 26f - ROW_H;
        footRow(cv, EDGE, footY, w - 2f * EDGE, hits);
        float y = top + 6f + ROW_H + GAP;
        plan(cv, EDGE, y, w - 2f * EDGE, footY - GAP - y);
        drawUnits(cv, cam, hits);
    }

    /** M3: a panel over the docked view, as tall as its tab, with the rail every tab shares (C7). */
    private void stationPanel(NpCanvas cv, Camera cam, float w, float h, Hits hits) {
        float pw = Math.min(STATION_W, w - 2f * EDGE), px = Math.round((w - pw) / 2f), py = StatusStrip.H + 8f;
        float mainW = pw - 2f * EDGE - RAIL_W - GAP;
        float most = h - py - EDGE - 74f;
        plan(cv, px + EDGE, py + 36f, mainW, most);
        float want = Math.max(railWant(), wantOf(cv, units.first()));
        float duH = Math.max(Math.min(240f, most), Math.min(most, want));
        plan(cv, px + EDGE, py + 36f, mainW, duH);
        float ph = duH + 74f;
        frame.set(px, py, pw, ph);
        hits.plate(px, py, pw, ph);
        body(cv, px, py, pw, ph);
        titleRow(cv, px + EDGE, py + 6f, pw - 2f * EDGE, hits);
        drawUnits(cv, cam, hits);
        float rx = px + EDGE + mainW + GAP;
        du(cv, rx, py + 36f, RAIL_W, duH);
        rail(cv, rx + DU_B, py + 36f + DU_B, RAIL_W - 2f * DU_B, duH - 2f * DU_B);
        footRow(cv, px + EDGE, py + 36f + duH + GAP, pw - 2f * EDGE, hits);
    }

    /** A console body: machined hardware with its fasteners at the corners. */
    private static void body(NpCanvas cv, float x, float y, float w, float h) {
        NpHardware.bezel(cv, x, y, w, h, 4f);
        NpHardware.fastener(cv, x + 7f, y + 7f);
        NpHardware.fastener(cv, x + w - 7f, y + 7f);
        NpHardware.fastener(cv, x + 7f, y + h - 7f);
        NpHardware.fastener(cv, x + w - 7f, y + h - 7f);
    }

    /** A display unit set in the body: its bezel, fasteners and glass. */
    private static void du(NpCanvas cv, float x, float y, float w, float h) {
        NpHardware.bezel(cv, x, y, w, h, 4f);
        NpHardware.fastener(cv, x + 6f, y + 6f);
        NpHardware.fastener(cv, x + w - 6f, y + 6f);
        NpHardware.fastener(cv, x + 6f, y + h - 6f);
        NpHardware.fastener(cv, x + w - 6f, y + h - 6f);
        NpHardware.glass(cv, x + DU_B, y + DU_B, w - 2f * DU_B, h - 2f * DU_B);
    }

    /** The title row: where, or whose, then the tabs the mode offers here, each on its number. */
    private void titleRow(NpCanvas cv, float x, float y, float w, Hits hits) {
        cv.fillRound(x, y, w, ROW_H, 1f, NpColors.GLASS, 1f);
        String name = title(), sub = mode == Mode.STATION && boards.station.isEmpty() ? "" : mode.sub;
        float nx = x + 10f, base = y + 17f;
        float nw = name.isEmpty() ? 0f : cv.text(nx, base, name, Ui.V, true, NpColors.WHITE, 0.8f, NpCanvas.START);
        float sx = nx + (nw > 0f ? nw + 12f : 0f);
        float sw = sub.isEmpty() ? 0f : cv.text(sx, base, sub, Ui.LB, true, NpColors.DIM, 0.8f, NpCanvas.START);
        if (mode == Mode.BOARDING) return;
        float tx = Math.max(x + (mode == Mode.STATION ? 212f : 282f), sx + sw + 40f);
        for (int i = 0; i < tabs.size; i++) {
            Page p = tabs.get(i);
            float kx = tx;
            tx += Ui.capKey(cv, tx, y + 4f, String.valueOf(i + 1), 16f) + 7f;
            boolean on = p == page;
            float lw = cv.text(tx, base, tabName(p), Ui.LB, true, on ? NpColors.WHITE : NpColors.DIM, 0.8f, NpCanvas.START);
            if (on) cv.fill(tx, base + 3.25f, lw, 1.5f, NpColors.WHITE);
            hits.add(kx - 4f, y, tx + lw - kx + 8f, ROW_H, p, 0);
            tx += lw + 26f;
        }
    }

    private String title() {
        switch (mode) {
            case STATION:
                return boards.station.isEmpty() ? "SHIPYARD" : boards.station;
            case SHIP:
                return Fmt.caps(boards.crew.shipName);
            case BOARDING:
                return Fmt.caps(boards.boarding.craftName);
            case RECORD: {
                String r = boards.rank;
                int comma = r.indexOf(',');
                return Fmt.caps(comma > 0 ? r.substring(0, comma) : r.isEmpty() ? "Captain" : r);
            }
            case CONTROLS:
                return "CONTROLS";
            default:
                return "";
        }
    }

    private String tabName(Page p) {
        return p == help ? "KEYBOARD" : p.name;
    }

    /** The foot row: the feed's line under its source, and the keys that close the mode. */
    private void footRow(NpCanvas cv, float x, float y, float w, Hits hits) {
        cv.fillRound(x, y, w, ROW_H, 1f, NpColors.GLASS, 1f);
        float base = y + 17f, right = x + w - 8f;
        float closeX = right - cv.width("CLOSE", Ui.LB, true, 0.6f);
        cv.text(right, base, "CLOSE", Ui.LB, true, NpColors.DIM, 0.6f, NpCanvas.END);
        float escX = closeX - 26f - Ui.capW(cv, "ESC");
        Ui.capKey(cv, escX, y + 4f, "ESC", 16f);
        float orW = cv.width("OR", Ui.LB, true, 0.4f), orX = escX - 6f - orW;
        cv.text(orX, base, "OR", Ui.LB, true, NpColors.DIM, 0.4f, NpCanvas.START);
        String key = closeKey();
        float kx = orX - 6f - Ui.capW(cv, key);
        Ui.capKey(cv, kx, y + 4f, key, 16f);
        hits.add(kx - 4f, y, right - kx + 8f, ROW_H, CLOSE, 0);
        if (!feed.showing()) return;
        float sx = x + 10f;
        float sw = cv.text(sx, base, Ui.fit(cv, feed.source, 140f, Ui.LB, true, 0.8f), Ui.LB, true, NpColors.DIM, 0.8f, NpCanvas.START);
        float tx = Math.max(x + 78f, sx + sw + 10f);
        cv.text(tx, base, Ui.fit(cv, feed.text, kx - 20f - tx, Ui.B, false, 0.2f), Ui.B, false, NpColors.WHITE, 0.2f, NpCanvas.START);
    }

    /** The key that opened the page and closes it again, as the game binds the windows' keys. */
    private String closeKey() {
        switch (page.window) {
            case TRADE: return "T";
            case YARD:
            case SHIPYARD: return "Y";
            case MISSIONS: return "J";
            case BOARDING: return "K";
            case CREW: return "I";
            case LOG: return key("LOG", "L");
            case HELP: return key("HELP", "F1");
            default: return "ESC";
        }
    }

    private String key(String action, String fallback) {
        String k = host.data().key(action);
        return k == null || k.isEmpty() ? fallback : k.toUpperCase(Locale.ROOT);
    }

    // ---- the page's units ------------------------------------------------------------------------

    /**
     * Lays the page out in units in the box: the log's filters over its list (C9), the crew's roster
     * beside its orders and prisoners, boarding's salvage beside the party's state and orders, the
     * controls in four columns (C10), and anything else in one unit.
     */
    private void plan(NpCanvas cv, float x, float y, float w, float h) {
        unitPool.addAll(units);
        units.clear();
        if (page == log) {
            int f = sectIndex("FILTERS");
            Unit filters = unit(x, y, w, 0f);
            if (f >= 0) filters.sects.add(f);
            filters.h = f < 0 ? 0f : Tables.want(cv, page, f, w - 2f * DU_B, style) + 2f * DU_B;
            float ly = f < 0 ? y : y + filters.h + GAP;
            if (f < 0) units.removeValue(filters, true);
            Unit list = unit(x, ly, w, y + h - ly);
            for (int i = 0; i < page.sects.size; i++) if (i != f) list.sects.add(i);
            list.acts = true;
            return;
        }
        if (page == crew || page == boarding) {
            float lw = Math.round((w - GAP) * (page == crew ? 0.62f : 0.56f));
            Unit left = unit(x, y, lw, h), right = unit(x + lw + GAP, y, w - lw - GAP, h);
            String main = page == crew ? "ABOARD" : "SALVAGE";
            for (int i = 0; i < page.sects.size; i++) (page.sects.get(i).title.equals(main) ? left : right).sects.add(i);
            left.acts = true;
            if (left.sects.size == 0 || right.sects.size == 0) {
                Unit only = left.sects.size == 0 ? right : left;
                units.removeValue(only == left ? right : left, true);
                only.x = x;
                only.w = w;
            }
            return;
        }
        Unit u = unit(x, y, w, h);
        for (int i = 0; i < page.sects.size; i++) u.sects.add(i);
        u.columns = page == help ? 4 : 1;
        u.acts = true;
    }

    private Unit unit(float x, float y, float w, float h) {
        Unit u = unitPool.size > 0 ? unitPool.pop() : new Unit();
        u.x = x;
        u.y = y;
        u.w = w;
        u.h = h;
        u.sects.clear();
        u.columns = 1;
        u.acts = false;
        units.add(u);
        return u;
    }

    private int sectIndex(String title) {
        for (int i = 0; i < page.sects.size; i++) if (page.sects.get(i).title.equals(title)) return i;
        return -1;
    }

    /** How tall unit {@code u} wants to be with every row of its parts showing. */
    private float wantOf(NpCanvas cv, Unit u) {
        if (u == null) return 0f;
        float gw = u.w - 2f * DU_B, h = 2f * DU_B + (u.acts && hasActs() ? ACTS_H : 0f);
        for (int i = 0; i < u.sects.size; i++) h += Tables.want(cv, page, u.sects.get(i), gw, style);
        return h;
    }

    private boolean hasActs() {
        return page.acts.size > 0 || !page.status.isEmpty();
    }

    private void drawUnits(NpCanvas cv, Camera cam, Hits hits) {
        for (int i = 0; i < units.size; i++) {
            Unit u = units.get(i);
            if (u.h < 2f * DU_B + 10f) continue;
            du(cv, u.x, u.y, u.w, u.h);
            float gx = u.x + DU_B, gy = u.y + DU_B, gw = u.w - 2f * DU_B, gh = u.h - 2f * DU_B;
            float actsH = u.acts && hasActs() ? ACTS_H : 0f;
            boolean clipped = cam != null && cv.clip(cam, gx, gy, gw, gh);
            if (u.columns > 1) columns(cv, cam, u, gx, gy, gw, gh - actsH, hits);
            else stack(cv, cam, u.sects, gx, gy, gw, gh - actsH, style, hits);
            if (actsH > 0f) pageActs(cv, cam, gx, gy + gh - actsH, gw, actsH, hits);
            if (clipped) cv.unclip();
        }
    }

    /**
     * The parts one under another, each as tall as it wants; when they want more than the unit has,
     * each keeps its share and the rows past it scroll.
     */
    private void stack(NpCanvas cv, Camera cam, IntArray sects, float x, float y, float w, float h, Tables.Style st, Hits hits) {
        int n = Math.min(sects.size, heights.length);
        float total = 0f;
        for (int i = 0; i < n; i++) total += heights[i] = Tables.want(cv, page, sects.get(i), w, st);
        if (total > h && total > 0f) {
            // the small parts keep what they want; the rest share what is left by what they want
            float small = Math.min(110f, h / Math.max(1, n)), keep = 0f, big = 0f;
            for (int i = 0; i < n; i++) {
                if (heights[i] <= small) keep += heights[i];
                else big += heights[i];
            }
            float k = big > 0f ? Math.max(0f, h - keep) / big : 1f;
            for (int i = 0; i < n; i++) if (heights[i] > small) heights[i] = Math.max(Math.min(heights[i], 90f), heights[i] * k);
        }
        float yy = y;
        for (int i = 0; i < n; i++) {
            float sh = Math.min(heights[i], y + h - yy);
            if (sh < 24f) break;
            Tables.sect(cv, cam, page, sects.get(i), x, yy, w, sh, st, hits);
            yy += sh;
        }
    }

    /** The controls' parts flowed down four columns, a part kept whole where it fits (C10). */
    private void columns(NpCanvas cv, Camera cam, Unit u, float x, float y, float w, float h, Hits hits) {
        int cols = u.columns;
        float cw = (w - (cols - 1) * 4f) / cols, cx = x, cy = y;
        int col = 0;
        for (int i = 0; i < u.sects.size; i++) {
            int s = u.sects.get(i);
            float want = Tables.want(cv, page, s, cw, keys);
            if (cy > y && cy + want > y + h && col < cols - 1) {
                col++;
                cx = x + col * (cw + 4f);
                cy = y;
            }
            float sh = Math.min(want, y + h - cy);
            if (sh < 30f) continue;
            Tables.sect(cv, cam, page, s, cx, cy, cw, sh, keys, hits);
            cy += sh;
        }
    }

    /** The page's own orders as lit buttons at the unit's foot, with its status line before them. */
    private void pageActs(NpCanvas cv, Camera cam, float x, float y, float w, float h, Hits hits) {
        Ui.rule(cv, x + 6f, y, w - 12f);
        float bw = 132f, bh = 22f, by = y + (h - bh) / 2f + 1f, right = x + w - 14f;
        float bx = right - page.acts.size * (bw + 6f) + 6f;
        for (int i = 0; i < page.acts.size; i++) {
            Act a = page.acts.get(i);
            float ax = bx + i * (bw + 6f);
            Ui.korry(cv, cam, ax, by, bw, bh, a.label, a.state, a.live, false);
            hits.add(ax - 2f, by - 2f, bw + 4f, bh + 4f, a, 0);
        }
        String line = page.status;
        if (line.isEmpty()) {
            for (int i = 0; i < page.acts.size && line.isEmpty(); i++) {
                Act a = page.acts.get(i);
                if (!a.live && !a.why.isEmpty()) line = a.label + ": " + a.why;
            }
        }
        if (!line.isEmpty()) Ui.lab(cv, x + 14f, y + h / 2f + 5f, Ui.fit(cv, line, bx - x - 30f, Ui.LB, true, 0.6f));
    }

    // ---- M3's rail ---------------------------------------------------------------------------------

    private float railWant() {
        float h = 2f * DU_B + 180f + Math.max(1, boards.tanks.size) * 19f;
        if (infirmaryLine()) h += 52f;
        return h;
    }

    private boolean infirmaryLine() {
        return boards.infirmary && boards.wounded > 0;
    }

    /** The rail: credits, standing with the station's owner, the hold with what is coming, the tanks (C7). */
    private void rail(NpCanvas cv, float gx, float gy, float gw, float gh) {
        TradeBoard t = boards.trade;
        float l = gx + 10f, r = gx + gw - 10f;
        Ui.lab(cv, l, gy + 24f, "CREDITS");
        Ui.valEnd(cv, r, gy + 24f, Fmt.credits(t.credits), "CR", Ui.V, NpColors.WHITE);
        sep(cv, gx, gy + 36f, gw);
        Ui.lab(cv, l, gy + 58f, "STANDING");
        cv.text(r, gy + 58f, boards.standing.isEmpty() ? "—" : boards.standing, Ui.V, true, NpColors.WHITE, 0f, NpCanvas.END);
        String who = !boards.owner.isEmpty() ? boards.owner : boards.standingWord;
        if (!who.isEmpty()) cv.text(l, gy + 76f, Ui.fit(cv, who, gw - 20f, Ui.LB, true, 0.4f), Ui.LB, true, NpColors.DIM, 0.4f, NpCanvas.START);
        sep(cv, gx, gy + 88f, gw);
        Ui.lab(cv, l, gy + 110f, "HOLD");
        if (t.hasHold && t.cargoCapacity > 0) {
            Ui.valEnd(cv, r, gy + 110f, Fmt.units(t.cargoUsed) + " OF " + Fmt.units(t.cargoCapacity), "M³", Ui.V, NpColors.WHITE);
            float bw = gw - 20f, share = (float) Math.min(1.0, t.cargoUsed / t.cargoCapacity);
            cv.fill(l, gy + 120f, bw, 8f, NpColors.TRACK);
            cv.fill(l, gy + 120f, bw * share, 8f, NpColors.WHITE);
            double coming = 0;
            for (int i = 0; i < t.lines.size; i++) coming += Math.max(0, t.lines.get(i).onOrder);
            if (coming >= 0.05) cv.text(l, gy + 146f, "+" + Fmt.units(coming) + " U ON ORDER", Ui.LB, true, NpColors.CYAN, 0.5f, NpCanvas.START);
            cv.text(r, gy + 146f, Fmt.units(Math.floor(t.freeCargo())) + " M³ FREE", Ui.LB, true, NpColors.DIM, 0.5f, NpCanvas.END);
        } else {
            cv.text(r, gy + 110f, t.hasHold || !t.isDocked() ? "—" : "NO HOLD", Ui.B, true, NpColors.DIM, 0.4f, NpCanvas.END);
        }
        sep(cv, gx, gy + 158f, gw);
        Ui.lab(cv, l, gy + 180f, "TANKS");
        float y = gy + 180f;
        if (boards.tanks.size == 0) cv.text(r, y, "—", Ui.B, true, NpColors.DIM, 0f, NpCanvas.END);
        for (int i = 0; i < boards.tanks.size && y < gy + gh - 8f; i++, y += 19f) {
            float f = boards.tankFull.get(i);
            cv.text(l + 82f, y, Ui.fit(cv, boards.tanks.get(i), r - l - 140f, Ui.B, false, 0.3f), Ui.B, false, NpColors.WHITE, 0.3f, NpCanvas.START);
            cv.text(r, y, Fmt.percent(f), Ui.B, true, f < 0.25f ? NpColors.AMBER : NpColors.WHITE, 0f, NpCanvas.END);
        }
        if (infirmaryLine() && y + 40f < gy + gh) {
            sep(cv, gx, y - 7f, gw);
            Ui.lab(cv, l, y + 15f, "INFIRMARY");
            String hurt = boards.wounded + (boards.wounded == 1 ? " HURT ABOARD" : " HURT ABOARD");
            cv.text(r, y + 15f, hurt, Ui.B, true, NpColors.AMBER, 0.3f, NpCanvas.END);
        }
    }

    private static void sep(NpCanvas cv, float gx, float y, float gw) {
        cv.fill(gx + 6f, y, gw - 12f, 1f, NpColors.GRATICULE);
    }

    // ---- for tests -------------------------------------------------------------------------------

    Mode mode() {
        return mode;
    }

    Page page() {
        return page;
    }

    Array<Page> tabsNow() {
        return tabs;
    }

    boolean lostShows() {
        return lostShows;
    }

    String feedLine() {
        return feed.showing() ? feed.source + " " + feed.text : "";
    }

    Sect sect(String title) {
        int i = page == null ? -1 : sectIndex(title);
        return i < 0 ? null : page.sects.get(i);
    }
}
