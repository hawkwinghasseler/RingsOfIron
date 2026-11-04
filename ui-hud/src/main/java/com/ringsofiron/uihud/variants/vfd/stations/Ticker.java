package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.data.MessageFeed.Message;

import java.util.Locale;

/**
 * The callout ticker (functional-stations 7.2): three fixed lines, newest at the bottom, each the
 * game's own words with the station they belong to. A line holds while the game keeps it on its
 * notice stack, so the holds are the game's. A line longer than its field marquees: it holds at its
 * start, steps along a cell at a time until its end shows, holds, and starts again (style-vfd 9.3).
 */
final class Ticker {
    static final int LINES = 3;
    /** Seconds a long line holds at each end, and per cell while it steps. */
    static final float HOLD = 1.6f, STEP = 0.14f;

    static final class Line {
        String tag = "", text = "";
        Level level = Level.ADVISORY;
        /** Seconds this line has been on the ticker (real time). */
        float shown;
        /** What it was posted as, to tell a new line from the same one read again. */
        String key = "";
    }

    /** Top to bottom; null where a line is empty. */
    final Line[] lines = new Line[LINES];
    private final Line[] next = new Line[LINES];
    private final Array<Message> picked = new Array<>();

    /** Reads the newest lines still up from {@code feed}; {@code delta} real seconds since the last read. */
    void fill(MessageFeed feed, float delta) {
        picked.clear();
        Array<Message> all = feed.all();
        for (int i = 0; i < all.size && picked.size < LINES; i++) {
            Message m = all.get(i);
            if (m.secondsLeft > 0f && !m.text.isEmpty()) picked.add(m);
        }
        for (int i = 0; i < LINES; i++) next[i] = null;
        // Newest at the bottom: the picked lines are newest first
        for (int i = 0; i < picked.size; i++) {
            Message m = picked.get(i);
            String key = m.text + '@' + m.time;
            Line line = null;
            for (Line l : lines) if (l != null && l.key.equals(key)) line = l;
            // A new line is rare (one a notice), so it is simply made
            if (line == null) {
                line = new Line();
                line.key = key;
            }
            line.text = upper(m.label());
            line.tag = tag(m.text);
            line.level = m.level;
            next[LINES - 1 - i] = line;
        }
        for (int i = 0; i < LINES; i++) {
            lines[i] = next[i];
            if (lines[i] != null) lines[i].shown += delta;
        }
    }

    private static String upper(String s) {
        return s.toUpperCase(Locale.ROOT);
    }

    /**
     * The first cell to show of a {@code length} character line in a {@code cells} wide field, after
     * {@code shown} seconds: 0 for a line that fits; else held at 0, stepped to its end, held, again.
     */
    static int scroll(int length, int cells, float shown) {
        int over = length - cells;
        if (over <= 0) return 0;
        float cycle = HOLD + over * STEP + HOLD;
        float t = shown % cycle;
        if (t < HOLD) return 0;
        return Math.min(over, (int) ((t - HOLD) / STEP));
    }

    /**
     * The station a message belongs to, from its words: the game's notices carry no station, so the
     * tag is read off what they are about. Command takes whatever no station owns.
     */
    static String tag(String text) {
        String s = text.toLowerCase(Locale.ROOT);
        if (s.startsWith("helm") || s.contains("autopilot") || s.contains("helmsman")) return "HELM";
        if (has(s, "abandon", "wound", "bleed", "casualt", "crew", "hire", "hired", "pay", "promot", "career", "captain",
                "medic", "spacewalk", "eva ", "adrift", "pod")) return "CMD";
        if (has(s, "missile", "target", "decoy", "shield", "hostile", "pirate", "raider", "point defense", "surrender",
                "struck", "lock", "gun", "harpoon", "salvo", "torpedo")) return "TAC";
        if (has(s, "fire", "breach", "reactor", "power", "short", "repair", "hull", "vent", "smoke", "pressure", "oxygen",
                "patch", "stun", "conduit", "tank", "air ", "vacuum")) return "ENG";
        if (has(s, "dock", "clearance", "undock", "tow", "beacon", "station", "market", "contract", "berth", "latch",
                "port ", "trade", "sold", "bought", "yard")) return "COMMS";
        if (has(s, "burn", "navigation", "transfer", "warp", "orbit", "plan", "course", "arriv", "soi")) return "NAV";
        return "CMD";
    }

    private static boolean has(String s, String... words) {
        for (String w : words) if (s.contains(w)) return true;
        return false;
    }
}
