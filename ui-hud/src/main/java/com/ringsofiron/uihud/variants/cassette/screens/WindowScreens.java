package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.providers.KeyHintProvider.HelpFrame;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.cassette.YardCanvas;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

/**
 * The game's windows in the Yard Standard (style guide, section 9), shared by the three Cassette HUDs:
 * each classic window is a tab of a big console, laid out as the Context spec arranges its modes
 * (section 10). The station's console has the market, yard, shipyard, missions and hire; the ship's
 * its systems, the crew, the hold, its loadout and a prize in tow (I opens the last of the first
 * three used); boarding has its own, across the screen or down its right side ({@link #sideBoarding});
 * the record has the contracts held and the log; the controls are cards. The ship lost or the career
 * over is a telex. The map's panels are the console's bays round the game's map ({@link MapMode}),
 * which its look's painter draws. The console E opens at a deck console stays the classic one.
 *
 * <p>A HUD attaches it with its own, and hands it the screen hooks ({@code replaces}, {@code open},
 * {@code showing}, {@code close}, {@code keyDown}), {@code update} and {@code detach}.
 */
public final class WindowScreens {
    /** How wide the station's console is at most: it stands over the docked view (Context 10.3). */
    static final float STATION_W = 1280f;

    /** How far in from the right edge a side panel hangs, and where it starts (Stations, 6.2). */
    public static final float SIDE_MARGIN = 16f, SIDE_TOP = 16f;
    /**
     * How wide the boarding console hangs beside the view ({@link #sideBoarding}): the Task spec's
     * BRD, 360 in its wireframe, by the quarter an artist may add.
     */
    public static final float BOARDING_SIDE_W = 450f;

    private final Boards boards = new Boards();
    private final Array<BigConsole> consoles = new Array<>();
    private BigConsole station, ship, boarding, record;
    private final BigConsole controls;
    /** The Stations spec's side panels: the Station link and the slate; null under the other arrangement. */
    private BigConsole link, slate;
    /** Whether the windows are the Stations spec's panels ({@link #panels}). */
    private boolean panels;
    /** Whether the boarding console hangs beside the view under the modes ({@link #sideBoarding}). */
    private boolean sideBoarding;
    /** Where a side panel starts, from the top in design units. */
    private float panelTop = SIDE_TOP;
    /** Where a side panel stops, from the top in design units: above the band when seated, else 16 from the foot; 0 for the foot. */
    private float panelBottom;
    private final MarketPage market = new MarketPage();
    private final YardPage yard = new YardPage(false), loadout = new YardPage(true);
    private final ShipyardPage shipyard = new ShipyardPage(false), prize = new ShipyardPage(true);
    private final MissionsPage missions = new MissionsPage(false), contracts = new MissionsPage(true);
    private final CrewPage crew = new CrewPage(false), hire = new CrewPage(true);
    /** Each fire's tiles against 10 s before, for the systems page, watched every frame. */
    private final FireTrend fires = new FireTrend();
    private final SystemsPage systems = new SystemsPage(fires);
    private final CargoPage cargo = new CargoPage();
    private final BoardingPage boardingPage = new BoardingPage();
    /** The fight's record for the log: each condition as it is raised and clears, watched every frame. */
    private final LogPage log = new LogPage();
    private final HelpFrame help = new HelpFrame();
    private final ControlsCard[] cards = new ControlsCard[8];
    private final Telex telex = new Telex();
    private final MapMode map = new MapMode();
    private HudVariantHost host;
    private YardCanvas canvas;
    private float top;
    private boolean mapWasOpen;
    /**
     * The ship's console's tab I opens: the last of systems, crew and cargo used (the crew the first
     * time); with the panels, the slate's last tab.
     */
    private Page shipTab;
    /**
     * The controls card rises over the screen under it (the map, or a console on its tab), and Esc or
     * F1 closes it first, giving that screen back (Task, round 11). Else it replaces the screen, one
     * mode at a time (Context, Stations).
     */
    private boolean controlsOver;
    /** The console the controls card rose over, which comes back as it closes; null for none. */
    private BigConsole under;

    public WindowScreens() {
        arrange();
        // The controls cards, a card for each place keys apply; what no card names goes on Anywhere
        cards[0] = new ControlsCard("HELM", "AT THE HELM", help, cards, false, "At the helm");
        cards[1] = new ControlsCard("GUNS", "AT THE WEAPONS CONSOLE", help, cards, false, "At the weapons console");
        cards[2] = new ControlsCard("DECK", "ON DECK", help, cards, false, "On deck", "Ship disabled");
        cards[3] = new ControlsCard("MAP", "THE ORBIT MAP AND BURN PLANNER", help, cards, false, "Orbit map and burn planner");
        cards[4] = new ControlsCard("DOCKED", "DOCKED AT A STATION", help, cards, false, "Docked");
        cards[5] = new ControlsCard("LATCHED", "LATCHED TO A SHIP", help, cards, false, "Latched to a ship");
        cards[6] = new ControlsCard("ANYWHERE", "ANYWHERE", help, cards, true, "Anywhere");
        cards[7] = new ControlsCard("PAD", "THE GAMEPAD", help, cards, false, "Gamepad");
        BigConsole c = new BigConsole(BigConsole.Livery.SHIP, "CONTROLS", boards);
        for (ControlsCard card : cards) c.add(card);
        controls = console(c);
    }

    /**
     * The consoles each window is a tab of: the Context spec's modes (the station's console, the
     * ship's, boarding and the record), or the Stations spec's panels ({@link #panels}).
     */
    private void arrange() {
        BigConsole was = controls;
        consoles.clear();
        if (panels) {
            // The Station link: everything the berth offers, docked only (Stations, 10.3)
            market.label = "MARKET";
            missions.label = "BOARD";
            hire.label = "HIRING";
            link = console(new BigConsole(BigConsole.Livery.STATION, "STATION", boards).side()
                    .add(market).add(yard).add(shipyard).add(missions).add(hire));
            // The slate: the captain's own, anywhere aboard (10.4); the field refit and a prize off a station
            slate = console(new BigConsole(BigConsole.Livery.SHIP, "SLATE", boards).side()
                    .add(crew).add(cargo).add(boardingPage).add(contracts).add(log).add(loadout).add(prize));
            station = link;
            ship = slate;
            boarding = slate;
            record = slate;
        } else {
            market.label = "MARKET";
            missions.label = "MISSIONS";
            hire.label = "HIRE";
            link = slate = null;
            station = console(new BigConsole(BigConsole.Livery.STATION, "STATION", boards)
                    .add(market).add(yard).add(shipyard).add(missions).add(hire));
            ship = console(new BigConsole(BigConsole.Livery.SHIP, "SHIP", boards).shipName()
                    .add(systems).add(crew).add(cargo).add(loadout).add(prize));
            boarding = new BigConsole(BigConsole.Livery.SHIP, "BOARDING", boards);
            if (sideBoarding) boarding.side(BOARDING_SIDE_W);
            boarding = console(boarding.add(boardingPage));
            record = console(new BigConsole(BigConsole.Livery.SHIP, "RECORD", boards).add(contracts).add(log));
        }
        if (was != null) consoles.add(was);
    }

    /**
     * Has the windows be the Stations spec's panels (section 10): the Station link while docked, with
     * the market, the yard, the shipyard, the contract board and the hiring hall, and the slate anywhere
     * aboard, with the crew, the hold, boarding, the contracts held and the log, the field refit and a
     * prize. Both hang as side panels 480 wide at the right, so the world and the band stay in view;
     * the yard's tabs, which need the room, draw across the screen. Else the Context spec's modes.
     * Set before {@link #attach}, which puts the consoles on the canvas.
     */
    public WindowScreens panels(boolean on) {
        if (panels == on) return this;
        if (canvas != null) throw new IllegalStateException("The windows are arranged before they are attached");
        panels = on;
        arrange();
        return this;
    }

    /**
     * Has the boarding console hang down the right side, so the deck or the outside view stays in sight
     * while the boarders work (the Task spec's BRD), the other consoles across the screen as the modes
     * have them. Set before {@link #attach}; the Stations spec's panels ({@link #panels}) have boarding
     * on the slate at the side already.
     */
    public WindowScreens sideBoarding(boolean on) {
        if (sideBoarding == on) return this;
        if (canvas != null) throw new IllegalStateException("The windows are arranged before they are attached");
        sideBoarding = on;
        arrange();
        return this;
    }

    /** Where a side panel stops, from the top in design units: above the seated band, or 0 for 16 from the foot. */
    public void panelBottom(float y) {
        panelBottom = y;
    }

    /** Where a side panel starts, from the top in design units: 16 unless a HUD keeps more above it. */
    public void panelTop(float y) {
        panelTop = y;
    }

    /** Whether a side panel hangs at the right now, the band and the world left in view beside it. */
    public boolean sideUp() {
        BigConsole c = up();
        return c != null && c.hangsAtSide();
    }

    /** The left edge of the side panel up now, in design units; the screen's width when none is. */
    public float sideLeft() {
        BigConsole c = up();
        if (c == null || !c.hangsAtSide() || host == null) return host == null ? 0f : host.width();
        return c.x;
    }

    /** Has the controls card rise over the screen under it, Esc closing it first; else it replaces the screen. */
    public WindowScreens controlsOver(boolean over) {
        controlsOver = over;
        return this;
    }

    /**
     * Has the Log keep each condition on one line, revised as it worsens and stamped when it clears,
     * and filter by alerts, important news and the rest (the Task spec's book); else each step and
     * each clearing is a line of its own, filtered by severity and source (the Context spec's Log).
     */
    public WindowScreens onceLog(boolean once) {
        log.once(once);
        return this;
    }

    private BigConsole console(BigConsole c) {
        consoles.add(c);
        return c;
    }

    /**
     * Puts the screens' canvas in the host's screen layer, painting with {@code paint} (the HUD's,
     * which it keeps). {@code top} is what the HUD keeps above a console: its status strip, or none.
     */
    public void attach(HudVariantHost host, YardPaint paint, float top) {
        this.host = host;
        this.top = top;
        canvas = new YardCanvas(paint);
        canvas.add(map);
        for (int i = 0; i < consoles.size; i++) canvas.add(consoles.get(i));
        canvas.add(telex);
        canvas.addListener(new InputListener() {
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                BigConsole c = up();
                // Over the map's contacts the wheel scrolls them; elsewhere on the map it zooms, as the game has it
                if (c == null && !map.scroll(x, canvas.getHeight() - y, amountY)) return false;
                if (c != null) c.scroll(amountY);
                event.stop();
                return true;
            }
        });
        host.screenLayer().addActor(canvas);
        mapWasOpen = host.hud().isOpen(ClassicWindow.MAP);
    }

    public void detach() {
        // The map's layers as the game has them by default
        if (host != null) host.hud().getManeuverMap().getTraffic().showAll();
        Stage stage = host == null ? null : host.stage();
        if (stage != null && canvas != null && stage.getScrollFocus() == canvas) stage.setScrollFocus(null);
        if (canvas != null) canvas.remove();
        canvas = null;
        host = null;
    }

    /** The windows these screens show in place of the classic ones: all but a deck console's. */
    public boolean replaces(ClassicWindow w) {
        return w != ClassicWindow.CONSOLE;
    }

    /** The console and tab for {@code w} as things stand: the yard's at a yard, the crew's own away from one. */
    private Page pageFor(ClassicWindow w) {
        switch (w) {
            case TRADE:
                return market;
            case YARD:
                return boards.yard.field ? loadout : yard;
            case SHIPYARD:
                return boards.shipyard.stationName.isEmpty() ? prize : shipyard;
            case MISSIONS:
                return boards.missions.stationName.isEmpty() ? contracts : missions;
            case BOARDING:
                return boardingPage;
            case CREW:
                return shipTab != null && shipTab.offered() ? shipTab : crew;
            case LOG:
                return log;
            case HELP:
                return card();
            default:
                return null;
        }
    }

    /** The console {@code w} opens on as things stand (not the controls, which need the host), for tests. */
    BigConsole consoleFor(ClassicWindow w) {
        Page p = w == ClassicWindow.HELP ? null : pageFor(w);
        return p == null ? null : p.console;
    }

    /** The station's contract board's tab legend under the arrangement now, for tests. */
    String boardLabel() {
        return missions.label;
    }

    /** The controls card for where the player is: the helm's at the helm, the map's with the map up. */
    private Page card() {
        if (host.data().hints != null) {
            help.clear();
            host.data().hints.collectAll(help);
        }
        com.ringsofiron.uihud.providers.UiContextProvider.UiContext ctx = host.data().ctx;
        int want;
        if (host.hud().isOpen(ClassicWindow.MAP)) want = 3;
        else if (ctx.docked) want = 4;
        else if (ctx.latched) want = 5;
        else if (ctx.presence == com.ringsofiron.uihud.providers.UiContextProvider.Presence.HELM) want = 0;
        else if (ctx.presence == com.ringsofiron.uihud.providers.UiContextProvider.Presence.WEAPONS
                || ctx.presence == com.ringsofiron.uihud.providers.UiContextProvider.Presence.GUN) want = 1;
        else want = 2;
        if (cards[want].offered()) return cards[want];
        for (ControlsCard c : cards) if (c.offered()) return c;
        return cards[6];
    }

    public boolean open(ClassicWindow w) {
        if (host == null) return false;
        if (w == ClassicWindow.MAP) {
            // The game has put its map up: the console's bays go round it
            for (int i = 0; i < consoles.size; i++) consoles.get(i).hide();
            under = null;
            map.show(host);
            mapWasOpen = true;
            layout();
            map.refreshNow();
            return true;
        }
        if (w == ClassicWindow.GAME_OVER) {
            for (int i = 0; i < consoles.size; i++) consoles.get(i).hide();
            under = null;
            telex.show(host.data().gameOver, host.data().ctx.simSeconds);
            layout();
            return true;
        }
        boards.refresh(host.data());
        Page page = pageFor(w);
        if (page == null) return false;
        page.refresh(boards);
        if (!page.offered()) return false;
        BigConsole c = page.console;
        if (controlsOver && c == controls) {
            // Over what is up: the map stays open under it, a console waits on its tab
            BigConsole was = up();
            if (was != null && was != controls) under = was;
            if (was != null && was != controls) was.hide();
        } else {
            // One mode at a time: the map, or another console, gives way
            for (int i = 0; i < consoles.size; i++) if (consoles.get(i) != c) consoles.get(i).hide();
            under = null;
            if (host.hud().isOpen(ClassicWindow.MAP)) host.hud().close(ClassicWindow.MAP);
            mapWasOpen = false;
        }
        c.closeKeys(keyOf(w));
        c.show(page, host.data());
        layout();
        return true;
    }

    /** The key the game opens {@code w} with, for the console's foot. */
    static String keyOf(ClassicWindow w) {
        switch (w) {
            case TRADE:
                return "T";
            case YARD:
            case SHIPYARD:
                return "Y";
            case MISSIONS:
                return "J";
            case BOARDING:
                return "K";
            case CREW:
                return "I";
            case LOG:
                return "L";
            case HELP:
                return "F1";
            default:
                return null;
        }
    }

    public boolean showing(ClassicWindow w) {
        if (w == ClassicWindow.MAP) return map.visible;
        if (w == ClassicWindow.GAME_OVER) return telex.visible;
        BigConsole c = up();
        return c != null && c.current() != null && c.current().window == w;
    }

    public void close(ClassicWindow w) {
        if (w == ClassicWindow.MAP) map.hide();
        else if (w == ClassicWindow.GAME_OVER) telex.hide();
        else if (showing(w)) {
            BigConsole c = up();
            c.hide();
            if (c == controls) uncover();
        }
    }

    /** The controls card has gone: the console it rose over comes back on its tab, if its place still offers it. */
    private void uncover() {
        BigConsole u = under;
        under = null;
        if (u == null || host == null) return;
        Page p = u.current();
        if (p == null) return;
        boards.refresh(host.data());
        p.refresh(boards);
        if (p.offered()) u.resume(host.data());
        layout();
    }

    /** The console up now, or null. */
    private BigConsole up() {
        for (int i = 0; i < consoles.size; i++) if (consoles.get(i).up()) return consoles.get(i);
        return null;
    }

    /** A key while a console shows. The telex leaves the game its keys. */
    public boolean keyDown(int keycode) {
        if (telex.visible) return false;
        BigConsole c = up();
        return c != null && c.key(keycode);
    }

    public void update(float delta) {
        if (host == null) return;
        fires.update(host.data().alerts, host.data().ctx.simSeconds);
        if (telex.visible) {
            if (host.data().gameOver.shown) telex.show(host.data().gameOver, host.data().ctx.simSeconds);
            telex.update(delta);
        }
        BigConsole c = up();
        boolean mapOpen = host.hud().isOpen(ClassicWindow.MAP);
        if (map.visible) {
            if (!mapOpen) map.hide();
            else map.update(delta);
        }
        if (c != null) {
            // The map opened over the console: it takes the screen
            if (mapOpen && !mapWasOpen) {
                c.hide();
                c = null;
                under = null;
            } else {
                c.update(delta);
                Page p = c.current();
                // I opens the last of these used; on the slate, the last of its tabs (Stations, 16.1)
                if (c == ship && (panels || p == systems || p == crew || p == cargo)) shipTab = p;
                if (p == null || !p.offered() || c.closing()) {
                    c.hide();
                    if (c == controls) uncover();
                    c = up();
                } else {
                    c.closeKeys(keyOf(p.window));
                }
            }
        }
        mapWasOpen = mapOpen;
        layout();
        Stage stage = host.stage();
        if (stage != null) {
            boolean wheel = c != null || map.visible;
            if (wheel && stage.getScrollFocus() != canvas) stage.setScrollFocus(canvas);
            else if (!wheel && stage.getScrollFocus() == canvas) stage.setScrollFocus(null);
        }
    }

    private void layout() {
        if (canvas == null || host == null) return;
        float sw = host.width(), sh = host.height();
        canvas.setBounds(0f, 0f, sw, sh);
        for (int i = 0; i < consoles.size; i++) {
            BigConsole c = consoles.get(i);
            if (c.hangsAtSide()) {
                // A side panel: 16 in from the right, from its top to above the band or the foot
                float bottom = panelBottom > 0f ? panelBottom : sh - SIDE_MARGIN, sw2 = c.sideW();
                c.place(sw - SIDE_MARGIN - sw2 + c.slideOffset(), panelTop, sw2, Math.max(200f, bottom - panelTop));
                continue;
            }
            float w = c == station ? Math.min(sw, STATION_W) : sw;
            c.place((sw - w) / 2f, top + c.riseOffset(), w, sh - top);
        }
        telex.place(0f, 0f, sw, sh);
        map.layout(sw, sh, top);
        if (map.visible) map.fitMap();
    }
}
