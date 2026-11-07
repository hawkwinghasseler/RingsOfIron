package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.LostCard;

import java.util.Locale;

/**
 * The panels of FILAMENT × Stations (functional-stations 10), in place of the game's windows: the
 * Station link for the market, the yard, the shipyard, the contract board and the hires while docked
 * (and the crew's field refit away from a yard); the slate for the crew, boarding, the contracts held
 * away from a board and the log; the Plot round the game's orbit canvas; a console's panel; the
 * controls card; and the ship-lost card.
 *
 * <p>One panel shows at a time, and opening another replaces it (6.3 rule 6). The panel stands for the
 * window of the page showing, so the game's keys open and close it, Esc closes it and the game's own
 * checks see it: T, Y and J turn the Station link to their pages and close it from there, I opens the
 * slate and closes it from any page, K and L turn it to Boarding and the Log and close it from there.
 * Tab steps a panel's pages, for those with no key of their own; the arrows, Enter and the page keys are
 * the page's. A side panel keeps the world beside it, and the walking and flying keys stay the game's.
 *
 * <p>It draws in its own actor in the host's screen layer, over the classic windows and the map, and
 * takes only the clicks on a panel: the world beside one keeps the pointer.
 */
final class Panels implements Filament.Painter, FilamentActor.Clicks {
    /** The plate under a panel, which takes the clicks that miss everything on it. */
    static final int PLATE = 99;
    /** Where a page's own click ids start, and the lost card's ways on. */
    static final int PAGE_IDS = 100, LOST_IDS = 80;
    /** How often the boards are read while a panel shows. */
    static final float REFRESH = 0.25f;
    /** How long an order's answer holds the response line before the panel lets it go. */
    static final float SAY_FOR = 8f;

    private final VfdStationsHud hud;
    private HudVariantHost host;
    private FilamentActor actor;
    final Boards boards = new Boards();
    final StationLink link = new StationLink();
    final CaptainSlate slate = new CaptainSlate();
    private final Panel[] all = {link, slate};
    Panel current;
    /** The ship lost or the career over: the card shows over the dark HUD, and no panel opens. */
    boolean lost;
    final LostCard lostCard = new LostCard();
    private float sinceRefresh, sayLeft;
    float time;
    /** Where the pointer was at the last turn of the wheel, y down. */
    float pointerX, pointerY;

    Panels(VfdStationsHud hud) {
        this.hud = hud;
        for (Panel p : all) p.attach(this);
    }

    void attach(HudVariantHost host) {
        this.host = host;
        actor = new FilamentActor(this, this);
        actor.setVisible(false);
        // The wheel turns the page of the list under the pointer: the actor takes the stage's scroll
        // focus while the pointer is over a panel, and gives it back as it leaves
        actor.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                Stage s = actor.getStage();
                if (s != null && pointer == -1) s.setScrollFocus(actor);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                Stage s = actor.getStage();
                if (s != null && pointer == -1 && s.getScrollFocus() == actor) s.setScrollFocus(null);
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                if (current == null || current.page == null || amountY == 0f) return false;
                pointerX = x;
                pointerY = actor.getHeight() - y;
                current.page.scrolled(amountY > 0f ? 1 : -1);
                event.stop();
                return true;
            }
        });
        host.screenLayer().addActor(actor);
    }

    void detach() {
        if (actor != null) {
            Stage s = actor.getStage();
            if (s != null && s.getScrollFocus() == actor) s.setScrollFocus(null);
            actor.remove();
            actor.dispose();
        }
        actor = null;
        host = null;
        current = null;
    }

    // ------------------------------------------------------------------ the windows they stand for

    boolean replaces(ClassicWindow w) {
        switch (w) {
            case TRADE:
            case YARD:
            case SHIPYARD:
            case MISSIONS:
            case CREW:
            case BOARDING:
            case LOG:
            case GAME_OVER:
                return true;
            default:
                return false;
        }
    }

    /** The panel that shows {@code w} here and now (the boards just read). */
    Panel panelFor(ClassicWindow w) {
        switch (w) {
            case TRADE:
            case SHIPYARD:
            case YARD:
                return link;
            case MISSIONS:
                // The station's board on the link; with no board here, the contracts held, on the slate
                return boards.docked() && boards.contracts && !boards.missions.stationName.isEmpty() ? link : slate;
            case CREW:
            case BOARDING:
            case LOG:
                return slate;
            default:
                return null;
        }
    }

    boolean open(ClassicWindow w) {
        return open(w, data());
    }

    /** Opens the panel for {@code w} on {@code d}, or the lost card for the game-over screen. */
    boolean open(ClassicWindow w, HudData d) {
        if (d == null) return false;
        if (w == ClassicWindow.GAME_OVER) {
            // The game opens it while the career is over, and it holds the keys to itself
            if (!d.gameOver.shown) return false;
            closeAll();
            lost = true;
            return true;
        }
        if (lost) return false;
        boards.refresh(d);
        sinceRefresh = 0f;
        return open(panelFor(w), w, d);
    }

    /** Opens panel {@code p} at its page for {@code w}, the boards just read. */
    boolean open(Panel p, ClassicWindow w, HudData d) {
        if (p == null || !p.opens(w, boards, d)) return false;
        if (!p.open(w, boards, d)) return false;
        if (current != p) {
            current = p;
            p.response = "";
        }
        p.refresh(boards, d);
        return true;
    }

    boolean showing(ClassicWindow w) {
        if (w == ClassicWindow.GAME_OVER) return lost;
        return current != null && current.showing(w);
    }

    void close(ClassicWindow w) {
        if (w == ClassicWindow.GAME_OVER) lost = false;
        else if (showing(w)) closeAll();
    }

    void closeAll() {
        current = null;
        sayLeft = 0f;
        Stage s = actor == null ? null : actor.getStage();
        if (s != null && s.getScrollFocus() == actor) s.setScrollFocus(null);
    }

    /** Whether a panel shows now, or the lost card. */
    boolean up() {
        return current != null || lost;
    }

    /** Whether the panel showing stands at the right with the world beside it. */
    boolean beside() {
        return current != null && current.beside();
    }

    /** Whether the panel showing takes the screen: the band hides. */
    boolean fullScreen() {
        return current != null && current.fullScreen();
    }

    /** Turns panel {@code p} to {@code page} from another panel's page (the Board's line to the slate's Contracts). */
    boolean show(Panel p, Page page) {
        HudData d = data();
        if (d == null || lost) return false;
        boards.refresh(d);
        sinceRefresh = 0f;
        if (!p.opens(page.window, boards, d)) return false;
        p.offer(boards, d);
        if (!p.isOffered(page)) return false;
        p.turn(page);
        if (current != p) {
            current = p;
            p.response = "";
        }
        p.refresh(boards, d);
        return true;
    }

    /**
     * A key the HUD's own keys come after: true when a panel used it. The lost card's keys are the
     * game's. With no board here and no contract held the game has no mission window to open, so J
     * opens the slate at its Contracts, from anywhere; the page itself closes it on J.
     */
    boolean keyDown(int keycode) {
        if (lost) return false;
        if (keycode == Keys.J && !(current == slate && slate.page == slate.contracts) && !available(ClassicWindow.MISSIONS)) {
            return show(slate, slate.contracts);
        }
        if (current == null) return false;
        return current.key(keycode);
    }

    // ------------------------------------------------------------------ orders' answers

    /** Shows an order's answer on the response line until the next, or a while. */
    void say(String text) {
        if (current == null || text == null || text.isEmpty()) return;
        current.response = text.toUpperCase(Locale.ROOT);
        sayLeft = SAY_FOR;
    }

    /** Reads the boards again now, after an order. */
    void refresh() {
        HudData d = data();
        if (d == null || current == null) return;
        boards.refresh(d);
        sinceRefresh = 0f;
        current.refresh(boards, d);
    }

    HudData data() {
        return host == null ? null : host.data();
    }

    UiHud gameHud() {
        return host == null ? null : host.hud();
    }

    /** Whether the game would open {@code w} now. */
    boolean available(ClassicWindow w) {
        UiHud h = gameHud();
        return h != null && h.available(w);
    }

    // ------------------------------------------------------------------ each frame

    /** Where the world beside a panel ends, y down: over the band seated, over the gun's plate at a gun, else the stage's foot. */
    static float floor(HudData d, float stageH) {
        Presence seat = d.ctx.presence;
        if (seat == Presence.HELM || seat == Presence.WEAPONS) return Geo.bandY(stageH) + Geo.BAND_Y - 10f - Geo.PANEL_GAP;
        if (seat == Presence.GUN) return stageH - Geo.PANEL_MARGIN - 56f - 6f - Geo.PANEL_GAP;
        return stageH - Geo.PANEL_MARGIN;
    }

    void update(float delta) {
        time += delta;
        if (actor == null) return;
        HudData d = data();
        if (current != null && d != null) {
            sinceRefresh += delta;
            if (sinceRefresh >= REFRESH) {
                boards.refresh(d);
                sinceRefresh = 0f;
                current.refresh(boards, d);
            }
            if (!current.available(boards, d)) closeAll();
        }
        if (sayLeft > 0f) {
            sayLeft -= delta;
            if (sayLeft <= 0f && current != null) current.response = "";
        }
        actor.setVisible(up());
        if (!up() || d == null) return;
        float w = host.width(), h = host.height();
        actor.setBounds(0f, 0f, w, h);
        actor.setUnitsPx(host.uiScale());
        actor.clearBoxes();
        if (current == null) {
            // The lost card: every click is its, or nothing's
            lostCard.place(w, h, d.gameOver);
            actor.box(PLATE, 0f, 0f, w, h);
            lostCard.boxes(actor, LOST_IDS, d.gameOver);
            return;
        }
        current.update(boards, d, delta);
        current.layout(w, h, floor(d, h));
        current.boxes(actor);
    }

    @Override
    public void paint(Filament f) {
        if (!up()) return;
        f.origin(0f, 0f);
        if (current == null) {
            HudData d = data();
            if (d != null) lostCard.paint(f, d.gameOver);
            return;
        }
        current.paint(f, time);
    }

    @Override
    public void clicked(int id) {
        if (id == PLATE) return;
        if (current != null) {
            current.clicked(id);
        } else if (lost && id >= LOST_IDS && id < PAGE_IDS) {
            HudData d = data();
            if (d != null) lostCard.clicked(id - LOST_IDS, d.gameOver);
        }
    }

    /** Whether {@code keycode} is the Tab key, which steps a panel's pages. */
    static boolean tab(int keycode) {
        return keycode == Keys.TAB;
    }
}
