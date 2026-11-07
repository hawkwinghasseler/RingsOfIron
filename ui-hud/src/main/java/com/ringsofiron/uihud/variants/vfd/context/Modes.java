package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.LostCard;

import java.util.Locale;

/**
 * The full-screen modes of FILAMENT × Context (functional-context 4.3, 10), in place of the classic
 * windows: the station's terminal (M3) for the market, the yard, the shipyard and the mission board
 * while docked; the ship (M2) for the crew roster and the field refit; boarding (M4); the captain's
 * record (M5) for the log, and for the contracts away from a station; the controls (M6); the ship-lost
 * card; and the map's panels (M1) round the game's orbit canvas.
 *
 * <p>One mode shows at a time, and opening another replaces it. A mode tells the game which window it
 * stands for by the tab showing ({@link #showing}), so the windows' keys, Esc and the game's own
 * checks work as they do for the classic windows: T, Y and J turn the terminal to their tab and close
 * it from there, I opens the ship at its last tab, L the record's Log, and J away from a station's
 * board the record's Contracts. While a mode with tabs shows,
 * the digits are its tabs; the arrows, Enter and the page keys are its lists and soft keys.
 *
 * <p>It draws in its own actor in the host's screen layer, over the classic windows and the map, and
 * puts the threat alert and the emergency card (P3, X3) over itself, since they still interrupt.
 */
final class Modes implements Filament.Painter, FilamentActor.Clicks {
    /** The plate under a mode, which takes the clicks that miss everything on it; the top rack's chip over the map. */
    static final int PLATE = 99, CHIP = 98;
    /** Where a tab's own click ids start, and the lost card's ways on. */
    static final int TAB_IDS = 100, LOST_IDS = 80;
    /** How often the boards are read while a mode shows. */
    static final float REFRESH = 0.25f;
    /** How long an order's answer holds the foot's feed line. */
    static final float SAY_FOR = 6f;

    private final VfdContextHud hud;
    private HudVariantHost host;
    private FilamentActor actor;
    final Boards boards = new Boards();
    final StationMode station = new StationMode();
    final ShipMode ship = new ShipMode();
    final BoardingMode boarding = new BoardingMode();
    final RecordMode record = new RecordMode();
    final ControlsMode controls = new ControlsMode();
    final MapMode map = new MapMode();
    private final Mode[] all = {station, ship, boarding, record, controls, map};
    Mode current;
    /** The ship lost or the career over: the card shows over the dark HUD, and no mode opens. */
    boolean lost;
    final LostCard lostCard = new LostCard();
    private float sinceRefresh;
    float time;
    /** Where the pointer was at the last turn of the wheel, y down: the list under it turns. */
    float pointerX, pointerY;
    /** The canvas insets last given to the game's map, left, top, right, bottom. */
    private final float[] insets = {-1f, -1f, -1f, -1f};
    /** An order's answer for the foot, its source, how long it still holds, and the feed's revision when it was said. */
    private String said = "", saidSource = "";
    private float sayLeft;
    private long saidAt;

    Modes(VfdContextHud hud) {
        this.hud = hud;
        for (Mode m : all) m.attach(this);
    }

    void attach(HudVariantHost host) {
        this.host = host;
        actor = new FilamentActor(this, this);
        actor.setVisible(false);
        // The wheel turns the page of the list under the pointer: the actor takes the stage's scroll
        // focus while the pointer is over a mode, and gives it back as it leaves
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
                if (current == null || current.tab == null || amountY == 0f) return false;
                pointerX = x;
                pointerY = actor.getHeight() - y;
                current.tab.scrolled(amountY > 0f ? 1 : -1);
                // Over a pane round the map, the wheel is the pane's: the map doesn't zoom too
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
            case HELP:
            case GAME_OVER:
            case MAP:
                return true;
            default:
                return false;
        }
    }

    /** The mode that shows {@code w} here and now (the boards just read). */
    private Mode modeFor(ClassicWindow w) {
        switch (w) {
            case TRADE:
            case SHIPYARD:
                return station;
            case MISSIONS:
                // The station's board in its terminal; with no board here, the contracts held, in the record
                return boards.missions.stationName.isEmpty() ? record : station;
            case YARD:
                // A station's yard in its terminal; away from one, the crew's field refit in the Loadout
                return boards.yard.field ? ship : station;
            case CREW:
                return ship;
            case BOARDING:
                return boarding;
            case LOG:
                return record;
            case HELP:
                return controls;
            case MAP:
                return map;
            default:
                return null;
        }
    }

    boolean open(ClassicWindow w) {
        return open(w, data());
    }

    /** Opens the mode for {@code w} on {@code d}, or the lost card for the game-over screen. */
    boolean open(ClassicWindow w, HudData d) {
        if (d == null) return false;
        if (w == ClassicWindow.GAME_OVER) {
            // The game opens it while the career is over, and it holds the keys' screen to itself
            if (!d.gameOver.shown) return false;
            closeAll();
            lost = true;
            return true;
        }
        if (lost) return false;
        boards.refresh(d);
        sinceRefresh = 0f;
        return open(modeFor(w), w, d);
    }

    /** Opens mode {@code m} at its tab for {@code w}, the boards just read. */
    private boolean open(Mode m, ClassicWindow w, HudData d) {
        if (m == null || !m.available(boards, d)) return false;
        if (!m.open(w, boards, d)) return false;
        if (current != m) {
            current = m;
            said = "";
        }
        m.refresh(boards, d);
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
        said = "";
        Stage s = actor == null ? null : actor.getStage();
        if (s != null && s.getScrollFocus() == actor) s.setScrollFocus(null);
    }

    /** Whether a mode shows now, or the lost card. */
    boolean up() {
        return current != null || lost;
    }

    /**
     * A key the HUD didn't use: true when a mode used it. With no board here and no contract held the
     * game has no mission window to open, so J opens the captain's record at its Contracts, from
     * anywhere; the record itself closes them on J.
     */
    boolean keyDown(int keycode) {
        // The lost card's keys are the game's
        if (lost) return false;
        if (keycode == Keys.J && current != record && !available(ClassicWindow.MISSIONS)) {
            HudData d = data();
            if (d == null) return false;
            boards.refresh(d);
            sinceRefresh = 0f;
            return open(record, ClassicWindow.MISSIONS, d);
        }
        if (current == null) return false;
        return current.key(keycode);
    }

    // ------------------------------------------------------------------ orders' answers

    /** Shows an order's answer in the foot for a while, as if {@code source} said it. */
    void say(String source, String text) {
        if (text == null || text.isEmpty()) return;
        said = text.toUpperCase(Locale.ROOT);
        saidSource = source;
        sayLeft = SAY_FOR;
        HudData d = data();
        saidAt = d == null ? 0L : d.messages.revision();
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
        actor.setVisible(up());
        if (!up() || d == null) return;
        float w = host.width(), h = host.height();
        actor.setBounds(0f, 0f, w, h);
        actor.setUnitsPx(host.uiScale());
        if (current == null) {
            // The lost card: every click is its, or nothing's
            lostCard.place(w, h, d.gameOver);
            actor.clearBoxes();
            actor.box(PLATE, 0f, 0f, w, h);
            lostCard.boxes(actor, LOST_IDS, d.gameOver);
            return;
        }
        current.update(boards, d, delta);
        foot(d, delta);
        current.layout(w, h);
        actor.clearBoxes();
        current.boxes(actor);
        if (current == map) {
            canvas(w, h);
            // The rack's chip, which the map covers in the HUD's own layer
            if (hud.top.chip) actor.box(CHIP, hud.top.chipX, Geo.STRIP_Y, 66f, Geo.STRIP_H);
        }
    }

    /** Keeps the game's orbit canvas in the plot between M1's panes, on a {@code w} x {@code h} stage. */
    private void canvas(float w, float h) {
        UiHud g = gameHud();
        if (g == null) return;
        float[] o = map.frame.hole;
        float l = o[0], t = o[1], r = w - o[0] - o[2], b = h - o[1] - o[3];
        if (l == insets[0] && t == insets[1] && r == insets[2] && b == insets[3]) return;
        insets[0] = l;
        insets[1] = t;
        insets[2] = r;
        insets[3] = b;
        g.getManeuverMap().setCanvasInsets(l, t, r, b);
    }

    /** The foot's feed line: an order's answer while it holds, else the feed's newest line. */
    private void foot(HudData d, float delta) {
        ModeFrame fr = current.frame;
        if (sayLeft > 0f) sayLeft -= delta;
        MessageFeed.Message m = d.messages.newest();
        if (!said.isEmpty() && (sayLeft <= 0f || d.messages.revision() != saidAt && m != null)) said = "";
        if (!said.isEmpty()) {
            fr.feedSource = saidSource;
            fr.feedText = said;
        } else if (m != null) {
            fr.feedSource = Feed.source(m.text);
            fr.feedText = Feed.body(m.label());
        } else {
            fr.feedSource = "";
            fr.feedText = "";
        }
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
        // The map covers the HUD's own layer, rack and all: the rack again, over it
        if (current == map) hud.paintTopOver(f);
        current.paint(f, time);
        hud.paintInterruptsOver(f);
    }

    @Override
    public void clicked(int id) {
        if (id == PLATE) return;
        if (id == CHIP) {
            HudData d = data();
            if (d != null) hud.acknowledge(d);
            return;
        }
        if (current != null) {
            current.clicked(id);
        } else if (lost && id >= LOST_IDS && id < TAB_IDS) {
            HudData d = data();
            if (d != null) lostCard.clicked(id - LOST_IDS, d.gameOver);
        }
    }

    /** Whether {@code keycode} is a digit key 1 to 9. */
    static boolean digit(int keycode) {
        return keycode >= Keys.NUM_1 && keycode <= Keys.NUM_9;
    }
}
