package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.LostCard;

import java.util.Locale;

/**
 * The screens and panels of FILAMENT × Task (functional-task 7), in place of the game's windows: the
 * station terminal for the contracts, the market, the yard, the shipyard and the crew for hire while
 * docked (and the crew's field refit, or a prize's terms, away from one); the ship's book for the ship,
 * the crew, the contracts held, the career and the log; the map round the game's orbit canvas; the
 * boarding panel down the right; a console's panel beside the console; the controls card; and the
 * ship-lost card.
 *
 * <p>One shows at a time, and opening another replaces it. A screen stands for the window of its tab
 * showing, so the game's keys open and close it, Esc closes it and the game's own checks see it: T, J
 * and Y turn the terminal to their tabs and close it from there, I opens the book on Crew (or, while the
 * terminal shows at a station that hires, the terminal's Crew) and closes it, L turns the book to the
 * Log, K opens the boarding panel and M the map. Tab steps a screen's tabs; the arrows, Enter and the
 * page keys are the tab's. The controls reference opens over everything, a screen included, and closes
 * back to it, so Esc closes the card before the screen under it (functional-task 7, closing order).
 *
 * <p>It draws in its own actor in the host's screen layer, over the classic windows and the map, and
 * takes only the clicks on a screen: the world beside a panel keeps the pointer. The controls card has
 * an actor of its own over that one, since a screen's lit phosphor is drawn after everything's glass
 * and printing in a frame and would glow through a card drawn with it.
 */
final class Windows implements Filament.Painter, FilamentActor.Clicks {
    /** The plate under a screen, which takes the clicks that miss everything on it. */
    static final int PLATE = 99;
    /** Where a tab's own click ids start, and the lost card's ways on. */
    static final int TAB_IDS = 100, LOST_IDS = 80;
    /** How often the boards are read while a screen shows. */
    static final float REFRESH = 0.25f;
    /** How long an order's answer holds the response line before the screen lets it go. */
    static final float SAY_FOR = 8f;
    /** How far from its console the captain may stand, in tiles, before its panel closes (CON): the game's reach for E, and a little. */
    static final float CONSOLE_REACH = 2f;

    private final VfdTaskHud hud;
    private HudVariantHost host;
    private FilamentActor actor;
    /** The controls card's, over {@link #actor}. */
    private FilamentActor cardActor;
    final Boards boards = new Boards();
    /** The book's log, kept every frame whether the book is open or not. */
    final Logbook log = new Logbook();
    final StationTerminal terminal = new StationTerminal();
    final ShipsBook book = new ShipsBook();
    final MapScreen map = new MapScreen();
    final BoardingPanel boarding = new BoardingPanel();
    final ConsolePanel console = new ConsolePanel();
    final ControlsCard controls = new ControlsCard();
    private final Screen[] all = {terminal, book, map, boarding, console, controls};
    /** The game's orbit canvas's insets as last set, so they are set only when they change. */
    private final float[] insets = new float[4];
    /** Whether the canvas leaves its title and help to the map's panels, as last set. */
    private boolean framed;
    Screen current;
    /** The ship lost or the career over: the card shows over the dark HUD, and no screen opens. */
    boolean lost;
    final LostCard lostCard = new LostCard();
    private float sinceRefresh, sayLeft;
    float time;
    /** When the controls reference last opened: the game shuts the log as F1 opens the card, which must leave the book under it. */
    private float helpAt = -1f;
    private final Vector2 avatar = new Vector2();
    /** Where the pointer was at the last turn of the wheel, y down. */
    float pointerX, pointerY;

    Windows(VfdTaskHud hud) {
        this.hud = hud;
        for (Screen s : all) s.attach(this);
    }

    void attach(HudVariantHost host) {
        this.host = host;
        actor = new FilamentActor(this, this);
        actor.setVisible(false);
        wheel(actor);
        host.screenLayer().addActor(actor);
        cardActor = new FilamentActor(f -> {
            f.origin(0f, 0f);
            if (current == controls) controls.paint(f, time);
        }, this);
        cardActor.setVisible(false);
        wheel(cardActor);
        host.screenLayer().addActor(cardActor);
    }

    /**
     * The wheel turns the page of the list under the pointer: {@code a} takes the stage's scroll focus
     * while the pointer is over it, and gives it back as it leaves.
     */
    private void wheel(FilamentActor a) {
        a.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                Stage s = a.getStage();
                if (s != null && pointer == -1) s.setScrollFocus(a);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                Stage s = a.getStage();
                if (s != null && pointer == -1 && s.getScrollFocus() == a) s.setScrollFocus(null);
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                if (current == null || current.tab == null || amountY == 0f) return false;
                pointerX = x;
                pointerY = a.getHeight() - y;
                current.tab.scrolled(amountY > 0f ? 1 : -1);
                event.stop();
                return true;
            }
        });
    }

    void detach() {
        canvas(0f, 0f, 0f, 0f);
        framed(false);
        drop(actor);
        drop(cardActor);
        actor = cardActor = null;
        host = null;
        current = null;
    }

    private static void drop(FilamentActor a) {
        if (a == null) return;
        Stage s = a.getStage();
        if (s != null && s.getScrollFocus() == a) s.setScrollFocus(null);
        a.remove();
        a.dispose();
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
            case MAP:
            case CONSOLE:
            case HELP:
            case GAME_OVER:
                return true;
            default:
                return false;
        }
    }

    /** The screen that shows {@code w} here and now (the boards just read). */
    Screen screenFor(ClassicWindow w, HudData d) {
        switch (w) {
            case TRADE:
            case SHIPYARD:
            case YARD:
                return terminal;
            case MISSIONS:
                // The station's offers on the terminal; with no board here, the contracts held, in the book
                return boards.docked() && boards.contracts && !boards.missions.stationName.isEmpty() ? terminal : book;
            case CREW:
                // The terminal's Crew while it shows at a station that hires; else the book's
                return current == terminal && terminal.hires(boards, d) ? terminal : book;
            case LOG:
                return book;
            case BOARDING:
                return boarding;
            case MAP:
                return map;
            case CONSOLE:
                return console;
            case HELP:
                return controls;
            default:
                return null;
        }
    }

    boolean open(ClassicWindow w) {
        return open(w, data());
    }

    /** Opens the screen for {@code w} on {@code d}, or the lost card for the game-over screen. */
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
        return open(screenFor(w, d), w, d);
    }

    /** Opens screen {@code s} at its tab for {@code w}, the boards just read: the controls reference over what shows, anything else in its place. */
    boolean open(Screen s, ClassicWindow w, HudData d) {
        if (s == null || !s.opens(w, boards, d)) return false;
        Screen below = s != controls ? null : current == controls ? controls.under : current;
        if (!s.open(w, boards, d)) return false;
        become(s);
        controls.under = below;
        if (s == controls) helpAt = time;
        s.refresh(boards, d);
        return true;
    }

    /** {@code s} shows now, its response line clear when it wasn't up. */
    private void become(Screen s) {
        if (current == s) return;
        current = s;
        s.response = "";
    }

    /** The screen shown under the controls reference, or the one showing; null with none. */
    Screen base() {
        return current == controls ? controls.under : current;
    }

    boolean showing(ClassicWindow w) {
        if (w == ClassicWindow.GAME_OVER) return lost;
        if (current == null) return false;
        return current.showing(w) || current == controls && controls.under != null && controls.under.showing(w);
    }

    /**
     * Closes the screen standing for {@code w}: the controls reference back to the screen it opened
     * over; the screen under it on its own key, the card staying up.
     */
    void close(ClassicWindow w) {
        if (w == ClassicWindow.GAME_OVER) {
            lost = false;
            return;
        }
        if (current == controls && !controls.showing(w) && controls.under != null && controls.under.showing(w)) {
            // Not the log the game shuts as F1 opens the card over the book
            if (time != helpAt) controls.under = null;
            return;
        }
        if (current == controls && controls.showing(w)) {
            Screen back = controls.under;
            controls.under = null;
            if (back == null) closeAll();
            else current = back;
            return;
        }
        if (showing(w)) closeAll();
    }

    void closeAll() {
        current = null;
        controls.under = null;
        sayLeft = 0f;
        for (FilamentActor a : new FilamentActor[] {actor, cardActor}) {
            Stage s = a == null ? null : a.getStage();
            if (s != null && s.getScrollFocus() == a) s.setScrollFocus(null);
        }
    }

    /** Whether a screen shows now, or the lost card. */
    boolean up() {
        return current != null || lost;
    }

    /** How much of the HUD the screen showing covers. */
    Screen.Cover cover() {
        if (lost) return Screen.Cover.SCREEN;
        return current == null ? Screen.Cover.NONE : current.cover();
    }

    /** Turns screen {@code s} to {@code t} from another tab (the terminal's line to the book's Contracts). */
    boolean show(Screen s, Tab t) {
        HudData d = data();
        if (d == null || lost) return false;
        boards.refresh(d);
        sinceRefresh = 0f;
        if (!s.opens(t.window, boards, d)) return false;
        s.offer(boards, d);
        if (!s.isOffered(t)) return false;
        s.turn(t);
        become(s);
        controls.under = null;
        s.refresh(boards, d);
        return true;
    }

    /**
     * A key the HUD's own keys come after: true when a screen used it. The lost card's keys are the
     * game's. With no board here the game has no mission window to open without a contract held, so J
     * opens the book at its Contracts, from anywhere; the tab itself closes it on J.
     */
    boolean keyDown(int keycode) {
        if (lost) return false;
        if (keycode == Keys.J && !(current == book && book.tab == book.held) && !available(ClassicWindow.MISSIONS)) {
            return show(book, book.held);
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

    HudVariantHost host() {
        return host;
    }

    /** Whether the game would open {@code w} now. */
    boolean available(ClassicWindow w) {
        UiHud h = gameHud();
        return h != null && h.available(w);
    }

    // ------------------------------------------------------------------ each frame

    void update(float delta) {
        time += delta;
        if (actor == null) return;
        HudData d = data();
        log.update(d);
        if (current != null && d != null) {
            sinceRefresh += delta;
            if (sinceRefresh >= REFRESH) {
                boards.refresh(d);
                sinceRefresh = 0f;
                current.refresh(boards, d);
                if (base() != current && base() != null) base().refresh(boards, d);
            }
            if (!current.available(boards, d) || current == console && walkedAway(d)) closeAll();
            else if (current == controls && controls.under != null && (!controls.under.available(boards, d)
                    || controls.under == console && walkedAway(d))) controls.under = null;
        }
        if (sayLeft > 0f) {
            sayLeft -= delta;
            if (sayLeft <= 0f && current != null) current.response = "";
        }
        Screen base = base();
        boolean card = current == controls;
        actor.setVisible(lost || base != null);
        cardActor.setVisible(card && !lost);
        if (base != map) canvas(0f, 0f, 0f, 0f);
        framed(base == map);
        if (!up() || d == null) return;
        float w = host.width(), h = host.height();
        for (FilamentActor a : new FilamentActor[] {actor, cardActor}) {
            a.setBounds(0f, 0f, w, h);
            a.setUnitsPx(host.uiScale());
            a.clearBoxes();
        }
        if (current == null) {
            // The lost card: every click is its, or nothing's
            lostCard.place(w, h, d.gameOver);
            actor.box(PLATE, 0f, 0f, w, h);
            lostCard.boxes(actor, LOST_IDS, d.gameOver);
            return;
        }
        // The screen under the controls reference is laid out and drawn, and takes no clicks
        if (base != null && base != current) {
            base.update(boards, d, delta);
            base.layout(w, h);
        }
        current.update(boards, d, delta);
        current.layout(w, h);
        current.boxes(card ? cardActor : actor);
        // The game's orbit canvas keeps clear of the map's panels down the right and its timeline along the foot
        if (base == map) canvas(map.plotLeft(), map.plotTop(), w - map.plotRight(), h - map.plotBottom());
    }

    /** CON closes when the captain walks away from the console (functional-task 7). */
    private boolean walkedAway(HudData d) {
        if (!d.console.isOpen() || d.console.data == null || d.interactor == null) return false;
        if (!d.interactor.getAvatarTile(avatar)) return true;
        // The console's middle, as the prompt system measures its reach
        return avatar.dst2(d.console.data.tileX + 0.5f, d.console.data.tileY + 0.5f) > CONSOLE_REACH * CONSOLE_REACH;
    }

    /** Sets the game's orbit canvas's insets, UI units in from each edge, when they change. */
    private void canvas(float l, float t, float r, float b) {
        if (l == insets[0] && t == insets[1] && r == insets[2] && b == insets[3]) return;
        UiHud g = gameHud();
        if (g == null || g.getManeuverMap() == null) return;
        insets[0] = l;
        insets[1] = t;
        insets[2] = r;
        insets[3] = b;
        g.getManeuverMap().setCanvasInsets(l, t, r, b);
    }

    /** The canvas leaves its title and help line to the map's panels while they show, when it changes. */
    private void framed(boolean on) {
        if (on == framed) return;
        framed = on;
        hud.mapFramed(on);
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
        // The canvas covers the HUD's own layer: CHR and the message centre stand over it here (7)
        Screen base = base();
        if (base == map) hud.paintTop(f);
        // The controls card is its own actor's, over this one
        if (base != null) base.paint(f, time);
    }

    @Override
    public void clicked(int id) {
        if (id == PLATE) return;
        if (current != null) {
            current.clicked(id);
        } else if (lost && id >= LOST_IDS && id < TAB_IDS) {
            HudData d = data();
            if (d != null) lostCard.clicked(id - LOST_IDS, d.gameOver);
        }
    }
}
