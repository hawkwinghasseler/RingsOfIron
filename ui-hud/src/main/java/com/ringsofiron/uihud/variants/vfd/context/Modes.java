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
 * it from there, I opens the ship at its last tab, L the record's Log. While a mode with tabs shows,
 * the digits are its tabs; the arrows, Enter and the page keys are its lists and soft keys.
 *
 * <p>It draws in its own actor in the host's screen layer, over the classic windows and the map, and
 * puts the threat alert and the emergency card (P3, X3) over itself, since they still interrupt.
 */
final class Modes implements Filament.Painter, FilamentActor.Clicks {
    /** The plate under a mode, which takes the clicks that miss everything on it. */
    static final int PLATE = 99;
    /** Where a tab's own click ids start. */
    static final int TAB_IDS = 100;
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
    private final Mode[] all = {station, ship, boarding};
    Mode current;
    private float sinceRefresh;
    float time;
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
                current.tab.scrolled(amountY > 0f ? 1 : -1);
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
            case MISSIONS:
                return station;
            case YARD:
                // A station's yard in its terminal; away from one, the crew's field refit in the Loadout
                return boards.yard.field ? ship : station;
            case CREW:
                return ship;
            case BOARDING:
                return boarding;
            default:
                return null;
        }
    }

    boolean open(ClassicWindow w) {
        HudData d = data();
        if (d == null) return false;
        boards.refresh(d);
        sinceRefresh = 0f;
        Mode m = modeFor(w);
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
        return current != null && current.showing(w);
    }

    void close(ClassicWindow w) {
        if (showing(w)) closeAll();
    }

    void closeAll() {
        current = null;
        said = "";
        Stage s = actor == null ? null : actor.getStage();
        if (s != null && s.getScrollFocus() == actor) s.setScrollFocus(null);
    }

    /** Whether a mode shows now. */
    boolean up() {
        return current != null;
    }

    /** A key the HUD didn't use, while a mode shows: true when the mode used it. */
    boolean keyDown(int keycode) {
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
        actor.setVisible(current != null);
        if (current == null) return;
        float w = host.width(), h = host.height();
        actor.setBounds(0f, 0f, w, h);
        actor.setUnitsPx(host.uiScale());
        current.update(boards, d, delta);
        foot(d, delta);
        current.layout(w, h);
        actor.clearBoxes();
        current.boxes(actor);
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
        if (current == null) return;
        f.origin(0f, 0f);
        current.paint(f, time);
        hud.paintInterruptsOver(f);
    }

    @Override
    public void clicked(int id) {
        if (current != null && id != PLATE) current.clicked(id);
    }

    /** Whether {@code keycode} is a digit key 1 to 9. */
    static boolean digit(int keycode) {
        return keycode >= Keys.NUM_1 && keycode <= Keys.NUM_9;
    }
}
