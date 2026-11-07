package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.data.MessageFeed.Message;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Kit;

import java.util.Locale;

/**
 * P4, the message feed (functional-context 6, P4): a slab of at most three dot-matrix lines at the top
 * of the right column, newest on top, each its source at L1 and what happened. In combat the feed
 * summarises: the gunnery lines of the last few seconds are one line with a count, and routine news
 * from elsewhere waits in the Log until the fight ends, then shows as one line; notable messages and
 * the replies to the captain's own orders still show at once.
 */
final class Feed {
    static final int LINES = 3, CELLS = 33;
    /** Gunnery events this close together merge into one line, s. */
    static final float MERGE = 5f;
    /** How long the "in the log" line holds after a fight, s. */
    static final float HELD_FOR = 5f;
    static final String[] SOURCES = {"Helm", "Gunnery", "Shields", "Engineering", "Medical", "Crew", "Boarding", "Station",
            "Sponsor", "Comms", "Log"};

    final String[] source = new String[LINES], text = new String[LINES];
    int count;
    private boolean wasCombat;
    private int held;
    private float heldShow;
    private double seenUpTo = Double.NEGATIVE_INFINITY;

    void update(MessageFeed feed, boolean combat, double now, float delta) {
        count = 0;
        Array<Message> all = feed.all();
        // Messages first seen in a fight that were held back, to say how many wait in the Log after it
        double newest = seenUpTo;
        for (int i = 0; i < all.size; i++) {
            Message m = all.get(i);
            if (m.time <= seenUpTo) continue;
            newest = Math.max(newest, m.time);
            if (combat && held(m)) held++;
        }
        seenUpTo = newest;
        if (wasCombat && !combat && held > 0) heldShow = HELD_FOR;
        if (!combat && heldShow <= 0f) held = 0;
        wasCombat = combat;

        // In a fight the newest gunnery line stands for every gunnery event of the last few seconds
        int gunnery = 0;
        if (combat) {
            for (int i = 0; i < all.size; i++) {
                Message m = all.get(i);
                if (m.secondsLeft > 0f && !m.important && now - m.time <= MERGE && source(m.text).equals("GUNNERY")) gunnery += m.count;
            }
        }
        boolean gunShown = false;
        for (int i = 0; i < all.size && count < LINES; i++) {
            Message m = all.get(i);
            if (m.secondsLeft <= 0f || m.text.isEmpty()) continue;
            String src = source(m.text);
            int repeats = m.count;
            if (combat) {
                if (held(m)) continue;
                if (src.equals("GUNNERY") && !m.important) {
                    if (gunShown) continue;
                    gunShown = true;
                    repeats = Math.max(repeats, gunnery);
                }
            }
            put(src, body(m.text), repeats);
        }
        if (heldShow > 0f) {
            heldShow -= delta;
            if (count == LINES) count--;
            for (int i = count; i > 0; i--) {
                source[i] = source[i - 1];
                text[i] = text[i - 1];
            }
            source[0] = "LOG";
            text[0] = held + (held == 1 ? " MESSAGE IN THE LOG" : " MESSAGES IN THE LOG");
            count++;
            if (heldShow <= 0f) held = 0;
        }
    }

    private void put(String src, String body, int repeats) {
        source[count] = src;
        text[count] = fit(body, src, repeats > 1 ? " (×" + repeats + ")" : "");
        count++;
    }

    /** {@code body} cut so the source, its colon and the line fit the column's 33 cells. */
    private static String fit(String body, String src, String tail) {
        int room = CELLS - src.length() - 2 - tail.length();
        if (room <= 1) return "";
        String b = body.length() <= room ? body : body.substring(0, room - 1) + "…";
        return b + tail;
    }

    /** Routine news from a quiet source, which waits in the Log while the ship fights. */
    static boolean held(Message m) {
        if (m.important) return false;
        String src = source(m.text);
        // Gunnery is summarised, not held; the helm and the shields answer the captain's own orders
        return !(src.equals("GUNNERY") || src.equals("HELM") || src.equals("SHIELDS"));
    }

    /** The message's source: its own "Source: " prefix when it has one, else read off its words. */
    static String source(String text) {
        int colon = text.indexOf(": ");
        if (colon > 0 && colon <= 12) {
            String head = text.substring(0, colon);
            for (String s : SOURCES) if (s.equalsIgnoreCase(head)) return s.toUpperCase(Locale.ROOT);
        }
        String s = text.toLowerCase(Locale.ROOT);
        // A contract names what it is about ("Contract done: Bounty: Raider"): it is the board's news, not a fight's
        if (s.startsWith("contract")) return "STATION";
        if (s.startsWith("budget") || s.startsWith("promoted")) return "SPONSOR";
        if (s.startsWith("helm") || has(s, "autopilot", "helmsman", "burn", "course", "arriv", "orbit", "navigation", "nav target")) return "HELM";
        if (has(s, "shield")) return "SHIELDS";
        // Salvage names the craft and the part ("the harpoon launcher is off Raider"): a boarding's, not a fight's
        if (has(s, "strip", "salvag", "carried home", "sells for")) return "BOARDING";
        if (has(s, "missile", "target", "decoy", "hostile", "raider", "pirate", "point defense", "hit", "struck", "gun",
                "harpoon", "salvo", "disabled", "destroyed", "surrender")) return "GUNNERY";
        // The job board is the station's; boarding a ship, the prisoners and the salvage are a boarding's
        if (has(s, "the board", "its board", "mission board", "job board")) return "STATION";
        if (has(s, "board", "prisoner", "salvag", "cast off", "latch")) return "BOARDING";
        if (has(s, "wound", "bleed", "casualt", "medic", "treat", "infirmary")) return "MEDICAL";
        if (has(s, "fire", "breach", "reactor", "power", "short", "repair", "hull", "vent", "smoke", "pressure", "oxygen",
                "patch", "conduit", "tank")) return "ENGINEERING";
        if (has(s, "crew", "hire", "hired", "spacewalk", "eva", "back inside", "pod")) return "CREW";
        if (has(s, "sponsor", "payday", "promot", "career", "standing")) return "SPONSOR";
        if (has(s, "dock", "clearance", "undock", "station", "market", "contract", "berth", "port", "trade", "sold",
                "bought", "yard")) return "STATION";
        if (has(s, "tow", "beacon", "hail", "signal")) return "COMMS";
        return "LOG";
    }

    /** The line without its source prefix, in capitals. */
    static String body(String text) {
        int colon = text.indexOf(": ");
        if (colon > 0 && colon <= 12) {
            String head = text.substring(0, colon);
            for (String s : SOURCES) {
                if (s.equalsIgnoreCase(head)) return text.substring(colon + 2).trim().toUpperCase(Locale.ROOT);
            }
        }
        return text.trim().toUpperCase(Locale.ROOT);
    }

    /** Whether {@code s} has a word starting with one of {@code words}: "aboard" has no "board", "report" no "port". */
    private static boolean has(String s, String... words) {
        for (String w : words) {
            for (int i = s.indexOf(w); i >= 0; i = s.indexOf(w, i + 1)) {
                if (i == 0 || !Character.isLetter(s.charAt(i - 1))) return true;
            }
        }
        return false;
    }

    float height() {
        return count == 0 ? 0f : count * Geo.LIST_ROW + 4f;
    }

    // ------------------------------------------------------------------ painting, on the grid

    void paint(Filament f) {
        if (count == 0) return;
        float x = Geo.RX, y = Geo.TOP_Y, rh = Geo.LIST_ROW;
        f.slab(x, y, Geo.CW, height(), 5f, 1, true);
        for (int i = 0; i < count; i++) {
            float ty = y + 2f + i * rh + (rh - 7f * Geo.P) / 2f;
            f.dm(x + 6f, ty, "", f.o().pitch(Geo.P).cells(CELLS));
            f.dm(x + 6f, ty, source[i] + ":", f.o().pitch(Geo.P).dim().noGhost());
            float tx = x + 6f + (source[i].length() + 2) * Geo.CELL;
            f.dm(tx, ty, text[i], f.o().pitch(Geo.P).norm().noGhost());
            Kit.fits(tx + text[i].length() * Geo.CELL, x + Geo.CW - 4f, "P4 line");
        }
    }
}
