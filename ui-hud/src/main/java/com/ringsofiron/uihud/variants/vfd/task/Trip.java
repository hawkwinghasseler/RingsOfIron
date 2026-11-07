package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed.Message;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.ObjectiveProvider.ObjectiveFrame;
import com.ringsofiron.uihud.providers.ObjectiveProvider.State;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;

import java.util.Locale;

/**
 * The trip computer, top right (functional-task 6, OBJ and NTF). OBJ: a slab of up to four rows, each
 * the contract (kind and a short title) with its time left in seven segments and LATE in its own amber
 * place; only the navigation target's row has a second line, its next step. NTF under it: the three
 * newest notices, a line each with repeats counted, for as long as the game keeps them up; in combat
 * the routine ones wait and come after the fight, the important ones show at once. Warnings are the
 * band's, never news.
 */
final class Trip {
    static final int ROWS = 4, LINES = 3, CELLS = 32;
    /** How long news held back by a fight shows once it ends, s. */
    static final float HELD_FOR = 5f;

    static final class Row {
        String title = "", next = "", left = "";
        boolean late, nav, done;
    }

    final Row[] rows = new Row[ROWS];
    int count;
    final String[] news = new String[LINES];
    int lines;
    boolean on;

    private final Array<String> held = new Array<>();
    private boolean wasCombat;
    private float heldShow;
    private double seenUpTo = Double.NEGATIVE_INFINITY;

    Trip() {
        for (int i = 0; i < ROWS; i++) rows[i] = new Row();
    }

    void update(HudData d, Posts p, float delta) {
        on = p.common();
        objectives(d);
        news(d, p.combat, delta);
        if (!on) {
            count = 0;
            lines = 0;
        }
    }

    private void objectives(HudData d) {
        count = 0;
        ObjectiveFrame frame = d.objectiveFrame;
        if (frame.objectives.size == 0 && !frame.operation.isEmpty()) {
            Row r = rows[count++];
            r.title = Fmt.cut(frame.operation, CELLS);
            r.next = frame.operationSteps > 0 ? "STEP " + frame.operationStep + " OF " + frame.operationSteps : "";
            r.left = "";
            r.late = r.done = false;
            r.nav = true;
        }
        for (int i = 0; i < frame.objectives.size && count < ROWS; i++) {
            Objective o = frame.objectives.get(i);
            if (o.state == State.FAILED) continue;
            Row r = rows[count++];
            String kind = o.kind.isEmpty() ? "" : o.kind.toUpperCase(Locale.ROOT) + ": ";
            r.title = kind + title(o).toUpperCase(Locale.ROOT);
            r.done = o.state == State.DONE;
            r.late = o.late;
            r.nav = o.nextIsNav;
            // A second line only for the navigation target's contract, and only with a next step
            String step = o.state == State.RETURN && !o.station.isEmpty() ? "RETURN LEG: " + o.station
                    : r.done ? "COLLECT AT " + (o.giver.isEmpty() ? o.station : o.giver) : o.next;
            r.next = r.nav ? Words.cut(step, CELLS - 2) : "";
            r.left = r.done ? "" : left(o.timeLeft);
        }
    }

    /** A contract's title without the kind the row already leads with: the game's bounty is "Bounty: Raider 'Vex'". */
    static String title(Objective o) {
        String t = o.title, lead = o.kind + ": ";
        return !o.kind.isEmpty() && t.regionMatches(true, 0, lead, 0, lead.length()) ? t.substring(lead.length()) : t;
    }

    /** Time left in five seven-segment cells: "2d21h", "21h05", "45:10". */
    static String left(double t) {
        if (Double.isNaN(t) || t < 0) return "";
        long s = (long) Math.ceil(t);
        if (s >= 86_400) return String.format(Locale.ROOT, "%dd%02dh", Math.min(9, s / 86_400), s % 86_400 / 3600);
        if (s >= 3600) return String.format(Locale.ROOT, "%dh%02d", s / 3600, s % 3600 / 60);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private void news(HudData d, boolean combat, float delta) {
        lines = 0;
        Array<Message> all = d.messages.all();
        // Routine news first seen in a fight waits for its end
        double newest = seenUpTo;
        for (int i = 0; i < all.size; i++) {
            Message m = all.get(i);
            if (m.time <= seenUpTo) continue;
            newest = Math.max(newest, m.time);
            if (combat && !m.important && m.level != Level.WARNING && held.size < LINES) held.add(line(m));
        }
        seenUpTo = newest;
        if (wasCombat && !combat && held.size > 0) heldShow = HELD_FOR;
        wasCombat = combat;
        if (heldShow > 0f) {
            heldShow -= delta;
            for (int i = held.size - 1; i >= 0 && lines < LINES; i--) news[lines++] = held.get(i);
            if (heldShow <= 0f) held.clear();
        } else if (!combat) {
            held.clear();
        }
        for (int i = 0; i < all.size && lines < LINES; i++) {
            Message m = all.get(i);
            if (m.secondsLeft <= 0f || m.text.isEmpty() || m.level == Level.WARNING) continue;
            if (combat && !m.important) continue;
            news[lines++] = line(m);
        }
    }

    /** A notice as one line, its repeats counted: "PAYDAY: 2,000 CR", "DECOY AWAY X3". */
    static String line(Message m) {
        String tail = m.count > 1 ? " X" + m.count : "";
        return Words.cut(m.text, CELLS - tail.length()) + tail;
    }

    float objHeight() {
        float h = 4f;
        for (int i = 0; i < count; i++) h += rowHeight(rows[i]);
        return count == 0 ? 0f : h;
    }

    /** Every row keeps LATE's place under its time, so a row is always two lines. */
    static float rowHeight(Row r) {
        return Geo.OBJ_ROW;
    }

    float newsY() {
        return Geo.OBJ_Y + (count > 0 ? objHeight() + 12f : 0f);
    }

    float newsHeight() {
        return lines == 0 ? 0f : 4f + lines * Geo.NTF_ROW;
    }

    // ------------------------------------------------------------------ painting, from the right group's origin

    void paint(Filament f) {
        if (!on) return;
        float x = Geo.RX, w = Geo.CW;
        if (count > 0) {
            float y = Geo.OBJ_Y;
            f.slab(x, y, w, objHeight(), 5f, 1, true);
            float tw = f.seg7Width(13f, 5, f.o()), tx = x + w - 6f - tw, lw = f.legendWidth("LATE", f.o());
            float ry = y + 4f;
            for (int i = 0; i < count; i++) {
                Row r = rows[i];
                f.seg7(tx, ry + 1f, 13f, r.left, f.o().cells(5).right().level(r.late ? Drive.FULL : Drive.NORM));
                f.dm(x + 6f, ry + 3f, Words.cut(r.title, Geo.fit(x + 6f, tx - 8f)), f.o().pitch(Geo.P).cells(Geo.fit(x + 6f, tx - 8f))
                        .level(r.done ? Drive.DIM : Drive.NORM));
                f.dm(x + 15f, ry + 17.5f, r.next, f.o().pitch(Geo.P).cells(Geo.fit(x + 15f, x + w - 6f - lw - 8f)).dim());
                f.legend(x + w - 6f - lw, ry + 28f, "LATE", f.o().amber().lit(r.late).full());
                ry += rowHeight(r);
            }
        }
        if (lines > 0) {
            float y = newsY();
            f.slab(x, y, w, newsHeight(), 5f, 1, true);
            for (int i = 0; i < lines; i++) {
                f.dm(x + 6f, y + 4f + i * Geo.NTF_ROW, news[i], f.o().pitch(Geo.P).cells(Geo.fit(x + 6f, x + w - 6f)).norm());
            }
        }
    }
}
