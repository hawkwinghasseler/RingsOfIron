package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.data.MessageFeed.Message;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.Slate;

import java.util.Locale;

/**
 * The slate's Log (functional-stations 10.4): every message the game keeps (the last
 * {@link MessageFeed#KEEP}, as the log window has them), newest first, each its severity's squares,
 * the station it belongs to (as the ticker tags it) and what happened; the one in focus in full under
 * the list, with its time. Its two filters head the list, the station and the severity: ← and → step
 * the one in focus.
 */
final class LogPage extends SectionPage {
    static final int STATION = ITEM, SEVERITY = ITEM + 1, MESSAGE = ITEM + 2;
    static final String[] STATIONS = {"ALL", "HELM", "NAV", "TAC", "ENG", "COMMS", "CMD"};
    static final String[] SEVERITIES = {"ALL", "CAUTION AND UP", "WARNING ONLY"};

    /** A message as read, copied: the feed's rows are the game's to reuse. */
    static final class Line {
        double time;
        String tag = "", text = "";
        Level level = Level.ADVISORY;
    }

    private final Array<Line> lines = new Array<>(), pool = new Array<>();
    /** The filters: the station (0 for all), the severity. */
    int station, severity;

    LogPage() {
        super("LOG", ClassicWindow.LOG, "L");
        keys.define(0, 0);
        hasDetail = true;
    }

    @Override
    void shown() {
        pages.focus(0);
    }

    @Override
    void build(Boards b, HudData d) {
        read(d.messages);
        int w = cells - 1;
        add(STATION, 0, filter("STATION", STATIONS[station], w));
        add(SEVERITY, 0, filter("SEVERITY", SEVERITIES[severity], w));
        int shownNow = 0;
        for (int i = 0; i < lines.size; i++) {
            Line l = lines.get(i);
            if (!passes(l)) continue;
            // Two cells for the severity's squares, painted over the row
            add(MESSAGE, i, cols.clear().gap(3).left(l.tag, 5).gap(1).left(l.text, w - 9).toString());
            shownNow++;
        }
        if (shownNow == 0) note(lines.size == 0 ? "NOTHING LOGGED YET" : "NOTHING THE FILTERS LET THROUGH");
    }

    /** A filter's row: "STATION   ← ALL →". */
    private String filter(String label, String value, int w) {
        return cols.clear().left(label, 10).left("← " + value + " →", w - 10).toString();
    }

    /** Copies the feed's messages, newest first. */
    void read(MessageFeed feed) {
        pool.addAll(lines);
        lines.clear();
        Array<Message> all = feed.all();
        for (int i = 0; i < all.size; i++) {
            Message m = all.get(i);
            if (m.text == null || m.text.isEmpty()) continue;
            Line l = pool.size > 0 ? pool.pop() : new Line();
            l.time = m.time;
            l.tag = Ticker.tag(m.text);
            l.text = m.label().toUpperCase(Locale.ROOT);
            l.level = m.level == null ? Level.ADVISORY : m.level;
            lines.add(l);
        }
    }

    boolean passes(Line l) {
        if (station > 0 && !STATIONS[station].equals(l.tag)) return false;
        if (severity == 1) return l.level == Level.CAUTION || l.level == Level.WARNING;
        if (severity == 2) return l.level == Level.WARNING;
        return true;
    }

    @Override
    boolean key(int keycode) {
        int k = kindAt(pages.focus());
        if ((k == STATION || k == SEVERITY) && (keycode == Keys.LEFT || keycode == Keys.RIGHT)) {
            step(k, keycode == Keys.RIGHT ? 1 : -1);
            return true;
        }
        return super.key(keycode);
    }

    /** Steps filter {@code k} by {@code dir}, round, and reads the list again through it. */
    void step(int k, int dir) {
        if (k == STATION) station = (station + dir + STATIONS.length) % STATIONS.length;
        else severity = (severity + dir + SEVERITIES.length) % SEVERITIES.length;
        panels.refresh();
    }

    @Override
    void clicked(int id) {
        // A click on a filter's row in focus steps it on
        if (id >= ROW_IDS && id < KEY_IDS) {
            int i = pages.first() + id - ROW_IDS, k = kindAt(i);
            if ((k == STATION || k == SEVERITY) && i == pages.focus()) {
                step(k, 1);
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
        if (k == MESSAGE) {
            Line l = lines.get(ref.get(focus));
            tell(Fmt.when(l.time) + "  " + l.tag);
            tell(l.text);
        } else if (k == STATION || k == SEVERITY) {
            tell("← AND → CHANGE IT");
        }
    }

    @Override
    void press(int k, int focus) {
    }

    @Override
    void paint(Filament f, float time) {
        super.paint(f, time);
        // Each message's severity, in the two cells its row leaves
        for (int i = pages.first(); i < pages.end(); i++) {
            if (kindAt(i) != MESSAGE || i == pages.focus()) continue;
            Level lv = lines.get(ref.get(i)).level;
            if (lv == Level.ADVISORY) continue;
            Slate.pips(f, listX + Glass.CW, rowY(i), lv == Level.WARNING ? Kit.Sev.WARNING : Kit.Sev.CAUTION);
        }
    }
}
