package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed.Message;

/**
 * The ship's book's log (functional-task 7, BOOK.LOG): every notice and every alert, stamped with game
 * time, kept as they come whether the book is open or not. An alert is logged once, as it is raised,
 * in full and marked with its level; when it clears its entry gains the time it cleared rather than a
 * second entry, and one that worsens or is reworded is revised in place. A notice the game posts again
 * while it shows is revised in place too, with its count. It keeps the last {@link #KEEP}, where the
 * game's own feed keeps 50; like the feed, it is not saved with the game.
 */
final class Logbook {
    static final int KEEP = 500;

    /** One entry: a notice or an alert. */
    static final class Entry {
        double time, cleared = Double.NaN;
        String text = "";
        Level level = Level.ADVISORY;
        boolean alert, important;
        /** The alert's id, or the notice's key. */
        String key = "";
    }

    /** Oldest first. */
    final Array<Entry> entries = new Array<>();
    private final ObjectMap<String, Entry> live = new ObjectMap<>(), notices = new ObjectMap<>();
    private final ObjectSet<String> seen = new ObjectSet<>();
    private final Array<String> gone = new Array<>();
    /** The feed's newest notice as last read: anything after it is new. */
    private double lastTime = Double.NEGATIVE_INFINITY;
    private final ObjectSet<String> atLast = new ObjectSet<>();
    private long revision = Long.MIN_VALUE;
    /** Bumped on every change, for the tab to read it again. */
    long changes;

    void update(HudData d) {
        if (d == null) return;
        notices(d);
        alerts(d);
    }

    /** The feed's notices since the last read, oldest first; a notice posted again revised with its count. */
    private void notices(HudData d) {
        if (d.messages == null || d.messages.revision() == revision) return;
        revision = d.messages.revision();
        Array<Message> all = d.messages.all();
        double newest = lastTime;
        for (int i = all.size - 1; i >= 0; i--) {
            Message m = all.get(i);
            if (m.text == null || m.text.isEmpty()) continue;
            String key = m.time + "|" + m.text;
            Entry e = notices.get(key);
            if (e != null) {
                String label = m.label();
                if (!label.equals(e.text)) {
                    e.text = label;
                    changes++;
                }
                continue;
            }
            if (m.time < lastTime || m.time == lastTime && atLast.contains(m.text)) continue;
            e = add(m.time, m.label(), m.level == null ? Level.ADVISORY : m.level, false, key);
            e.important = m.important;
            notices.put(key, e);
            newest = Math.max(newest, m.time);
        }
        if (newest > lastTime) {
            lastTime = newest;
            atLast.clear();
        }
        for (int i = 0; i < all.size; i++) if (all.get(i).time == lastTime) atLast.add(all.get(i).text);
    }

    /** Each alert raised logged once; revised as it worsens or is reworded; its clearing stamped on it. */
    private void alerts(HudData d) {
        seen.clear();
        Array<Alert> all = d.alerts.all();
        double now = d.ctx.simSeconds;
        for (int i = 0; i < all.size; i++) {
            Alert a = all.get(i);
            if (a.id == null || a.text == null || a.text.isEmpty()) continue;
            seen.add(a.id);
            Entry e = live.get(a.id);
            if (e == null) {
                e = add(a.since > 0 ? a.since : now, a.text, a.level, true, a.id);
                live.put(a.id, e);
            } else if (!e.text.equals(a.text) || a.level.ordinal() > e.level.ordinal()) {
                e.text = a.text;
                if (a.level.ordinal() > e.level.ordinal()) e.level = a.level;
                changes++;
            }
        }
        gone.clear();
        for (ObjectMap.Entry<String, Entry> l : live) if (!seen.contains(l.key)) gone.add(l.key);
        for (int i = 0; i < gone.size; i++) {
            Entry e = live.remove(gone.get(i));
            if (e != null) {
                e.cleared = now;
                changes++;
            }
        }
    }

    private Entry add(double time, String text, Level level, boolean alert, String key) {
        Entry e = new Entry();
        e.time = time;
        e.text = text;
        e.level = level;
        e.alert = alert;
        e.key = key;
        entries.add(e);
        while (entries.size > KEEP) {
            Entry old = entries.removeIndex(0);
            if (old.alert) {
                if (live.get(old.key) == old) live.remove(old.key);
            } else {
                notices.remove(old.key);
            }
        }
        changes++;
        return e;
    }
}
