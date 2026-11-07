package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

/**
 * A panel (functional-stations 10.1): its pages, which of them the place offers now, the one showing,
 * and what the last press said. A page opens on the game's key for it, through {@link #open}, and
 * the panel stands for the window of the page showing, so the game's keys toggle it, Esc closes it and
 * the game's own checks see it ({@link #showing}). Tab steps to the next page offered and Shift+Tab
 * back, for the pages with no key of their own; the rest of the keys go to the page.
 */
abstract class Panel {
    /** Every page it has, in the order of their keys. */
    Page[] pages = new Page[0];
    /** Which of them the place offers now. */
    boolean[] on = new boolean[0];
    Page page;
    Panels panels;
    /** Its housing on the stage, the plate's pad outside it. */
    float x, y, w, h;
    /** What the last press did, for the response line. */
    String response = "";

    abstract Page[] pages();

    void attach(Panels p) {
        panels = p;
        pages = pages();
        on = new boolean[pages.length];
        for (Page pg : pages) pg.attach(p, this);
    }

    /** Whether it can show at all now (the Station link only while docked). */
    boolean available(Boards b, HudData d) {
        return true;
    }

    /** Whether {@code w} can open it now; by default whenever it can show. */
    boolean opens(ClassicWindow w, Boards b, HudData d) {
        return available(b, d);
    }

    /** The page for {@code w}, or null when it has none. */
    Page pageFor(ClassicWindow w, Boards b, HudData d) {
        for (Page p : pages) if (p.window == w) return p;
        return null;
    }

    /** Whether it shows the screen for {@code w}: the page showing stands for it. */
    boolean showing(ClassicWindow w) {
        return page != null && page.standsFor(w);
    }

    /** Turns to the page for {@code w}, or the first offered; false when it has nothing to show. */
    boolean open(ClassicWindow w, Boards b, HudData d) {
        offer(b, d);
        Page p = pageFor(w, b, d);
        if (p == null || !isOffered(p)) p = first();
        if (p == null) return false;
        if (page == p) p.shown();
        else turn(p);
        return true;
    }

    /** Reads which pages the place offers; the page showing moves to the first when it is no longer offered. */
    void offer(Boards b, HudData d) {
        for (int i = 0; i < pages.length; i++) on[i] = pages[i].offered(b, d);
        if (page != null && !isOffered(page)) page = null;
        if (page == null) {
            Page p = first();
            if (p != null) turn(p);
        }
    }

    boolean isOffered(Page p) {
        for (int i = 0; i < pages.length; i++) if (pages[i] == p) return on[i];
        return false;
    }

    Page first() {
        for (int i = 0; i < pages.length; i++) if (on[i]) return pages[i];
        return null;
    }

    int index(Page p) {
        for (int i = 0; i < pages.length; i++) if (pages[i] == p) return i;
        return -1;
    }

    void turn(Page p) {
        if (page == p) return;
        page = p;
        response = "";
        p.shown();
    }

    /** Tab: the next page offered, round to the first; {@code dir} -1 for the one before. */
    void step(int dir) {
        int at = index(page), n = pages.length;
        for (int k = 1; k <= n; k++) {
            int i = ((at + dir * k) % n + n) % n;
            if (on[i]) {
                turn(pages[i]);
                return;
            }
        }
    }

    /** Reads the place again: the pages offered, the title, and the page showing. */
    void refresh(Boards b, HudData d) {
        offer(b, d);
        title(b, d);
        if (page != null) page.refresh(b, d);
    }

    void update(Boards b, HudData d, float delta) {
        if (page != null) page.update(b, d, delta);
    }

    /** Its title row, from the boards. */
    abstract void title(Boards b, HudData d);

    /** Lays it out on a {@code stageW} x {@code stageH} stage whose world shows above {@code floor} (y down). */
    abstract void layout(float stageW, float stageH, float floor);

    abstract void paint(Filament f, float time);

    /** The plate over the whole housing first, so a click that misses everything on it stays on it. */
    void boxes(FilamentActor a) {
        a.box(Panels.PLATE, x - Terminal.PAD, y - Terminal.PAD, w + 2f * Terminal.PAD, h + 2f * Terminal.PAD);
    }

    abstract void clicked(int id);

    /** A key while it shows: Tab steps the pages, the rest go to the page. */
    boolean key(int keycode) {
        if (keycode == Keys.TAB && pages.length > 1) {
            boolean shift = Gdx.input != null && (Gdx.input.isKeyPressed(Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Keys.SHIFT_RIGHT));
            step(shift ? -1 : 1);
            return true;
        }
        return page != null && page.key(keycode);
    }

    /** Whether it takes the screen (the Yard, the Shipyard, the Plot): the band and the world's marks go. */
    boolean fullScreen() {
        return false;
    }

    /** Whether it stands at the right with the world beside it: the top column recentres left of it. */
    boolean beside() {
        return !fullScreen();
    }
}
