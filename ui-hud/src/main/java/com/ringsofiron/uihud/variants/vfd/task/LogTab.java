package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.Slate;

import java.util.Locale;

/**
 * The book's Log (functional-task 7, BOOK): every notice and every alert in the {@link Logbook}, newest
 * first, each stamped with its time and marked with its level, an alert that has cleared with the time
 * it cleared. The filter's row at the top steps with ← and →: everything, the alerts, the important
 * notices, the rest. Beside the list, the entry in focus in full, with its day.
 */
final class LogTab extends SectionTab {
    static final int FILTER = ITEM, ENTRY = ITEM + 1;
    static final String[] FILTERS = {"ALL", "ALERTS", "IMPORTANT", "INFO"};

    /** The filter: everything, the alerts, the important notices, the other notices. */
    int filter;
    private Logbook log;

    LogTab() {
        super("LOG", ClassicWindow.LOG, "L");
        keys.define(0, 0);
    }

    @Override
    void shown() {
        pages.focus(0);
    }

    @Override
    void build(Boards b, HudData d) {
        log = windows.log;
        int w = cells - 1;
        add(FILTER, 0, cols.clear().left("SHOWING", 10).left("← " + FILTERS[filter] + " →", w - 10).toString());
        int shownNow = 0;
        for (int i = log.entries.size - 1; i >= 0; i--) {
            Logbook.Entry e = log.entries.get(i);
            if (!passes(e)) continue;
            String cleared = Double.isNaN(e.cleared) ? "" : "CLEARED " + hhmm(e.cleared);
            // Two cells for the level's squares, painted over the row
            add(ENTRY, i, cols.clear().gap(3).left(hhmm(e.time), 5).gap(1).left(e.text.toUpperCase(Locale.ROOT), w - 9 - (cleared.isEmpty() ? 0 : 15))
                    .right(cleared, cleared.isEmpty() ? 0 : 15).toString());
            shownNow++;
        }
        if (shownNow == 0) note(log.entries.size == 0 ? "NOTHING LOGGED YET" : "NOTHING THE FILTER LETS THROUGH");
    }

    /** The time of day, hh:mm. */
    static String hhmm(double t) {
        return Fmt.clock(t).substring(0, 5);
    }

    boolean passes(Logbook.Entry e) {
        switch (filter) {
            case 1:
                return e.alert;
            case 2:
                return !e.alert && (e.important || e.level != Level.ADVISORY);
            case 3:
                return !e.alert && !e.important && e.level == Level.ADVISORY;
            default:
                return true;
        }
    }

    @Override
    boolean key(int keycode) {
        if (kindAt(pages.focus()) == FILTER && (keycode == Keys.LEFT || keycode == Keys.RIGHT)) {
            step(keycode == Keys.RIGHT ? 1 : -1);
            return true;
        }
        return super.key(keycode);
    }

    /** Steps the filter by {@code dir}, round, and reads the list again through it. */
    void step(int dir) {
        filter = (filter + dir + FILTERS.length) % FILTERS.length;
        windows.refresh();
    }

    @Override
    void clicked(int id) {
        // A click on the filter's row in focus steps it on
        if (id >= ROW_IDS && id < KEY_IDS) {
            int i = pages.first() + id - ROW_IDS;
            if (kindAt(i) == FILTER && i == pages.focus()) {
                step(1);
                return;
            }
        }
        super.clicked(id);
    }

    @Override
    void keysFor(int focus) {
    }

    @Override
    void detailFor(int focus) {
        int k = kindAt(focus);
        if (k == ENTRY && log != null) {
            Logbook.Entry e = log.entries.get(ref.get(focus));
            tell(Fmt.when(e.time) + (e.alert ? ", " + e.level.name() : e.important ? ", IMPORTANT" : ""));
            tell(e.text);
            if (!Double.isNaN(e.cleared)) tell("Cleared " + Fmt.when(e.cleared));
            else if (e.alert) tell("Still live");
        } else if (k == FILTER) {
            tell("← and → change it");
            if (log != null) tell(log.entries.size + " logged, the last " + Logbook.KEEP + " kept");
        }
    }

    @Override
    void press(int k, int focus) {
    }

    @Override
    void paint(Filament f, float time) {
        super.paint(f, time);
        // Each entry's level, in the two cells its row leaves
        if (log == null) return;
        for (int i = pages.first(); i < pages.end(); i++) {
            if (kindAt(i) != ENTRY || i == pages.focus()) continue;
            Level lv = log.entries.get(ref.get(i)).level;
            if (lv == Level.ADVISORY) continue;
            Slate.pips(f, listX + Glass.CW, rowY(i), lv == Level.WARNING ? Kit.Sev.WARNING : Kit.Sev.CAUTION);
        }
    }
}
