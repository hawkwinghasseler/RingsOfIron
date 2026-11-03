package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.variants.vfd.Fmt;

/**
 * The annunciator's row (functional-stations 7.1, 13.4): one tile per kind of alert, a tag and a value
 * of eight characters each, warnings first and then cautions, newest first within each. Every tile is
 * dual-filtered, so warnings fill inward from the WARN lamp's end and cautions from the CAUT lamp's;
 * past six kinds, the tile where they meet counts the rest ("+n" / "MORE") in the role of the worst it
 * hides. Advisories never take a tile, and the ship's disabled and abandon states are the status
 * strip's, so they take none either: their cause has its own tile.
 */
final class Annunciator {
    static final int TILES = 6;
    /** Tag and value width, characters. */
    static final int CHARS = 8;

    /** One tile: its two lines, its role, and the alert it shows (null for the "+n MORE" tile). */
    static final class Tile {
        String tag = "", value = "";
        boolean warning;
        Alert alert;
        /** Instances of its kind, and when its newest was raised. */
        int count;
        double since;
        Level level = Level.CAUTION;
    }

    /** The six tiles, left to right; null where a tile is dark. */
    final Tile[] slots = new Tile[TILES];
    /** Kinds that hold, by role. */
    int warnings, cautions;
    /** An unacknowledged warning or caution holds: its master lamp flashes. */
    boolean warnUnacked, cautUnacked;

    private final Array<Tile> kinds = new Array<>();
    private final Array<Tile> pool = new Array<>();
    private final Tile more = new Tile();

    /**
     * Fills the row from the alerts that hold. {@code power} names each system fault's kind (a short
     * counts in the POWER tile, a stun in STUN); {@code blackout} is no reactor running, which makes
     * the POWER tile a warning reading NONE.
     */
    void fill(Array<Alert> alerts, PowerFrame power, boolean blackout) {
        pool.addAll(kinds);
        kinds.clear();
        warnUnacked = cautUnacked = false;
        for (int i = 0; i < alerts.size; i++) {
            Alert a = alerts.get(i);
            if (a.level == Level.ADVISORY || "DISABLED".equals(a.code) || "ABANDON".equals(a.code)) continue;
            if (!a.acknowledged) {
                if (a.level == Level.WARNING) warnUnacked = true;
                else cautUnacked = true;
            }
            String tag = tag(a, power);
            Tile t = kind(tag);
            boolean worse = t.alert == null || a.level.ordinal() > t.level.ordinal()
                    || (a.level == t.level && a.since > t.alert.since);
            t.count++;
            t.since = Math.max(t.since, a.since);
            if (worse) {
                t.alert = a;
                t.level = a.level;
            }
        }
        if (blackout) {
            Tile t = kind("POWER");
            t.level = Level.WARNING;
            if (t.count == 0) t.since = Double.MAX_VALUE;
            t.count = Math.max(t.count, 1);
        }
        for (int i = 0; i < kinds.size; i++) {
            Tile t = kinds.get(i);
            t.warning = t.level == Level.WARNING;
            t.value = value(t, power, blackout);
        }
        // Warnings first, newest first within each role
        kinds.sort((x, y) -> x.warning != y.warning ? (x.warning ? -1 : 1) : Double.compare(y.since, x.since));
        warnings = cautions = 0;
        for (int i = 0; i < kinds.size; i++) {
            if (kinds.get(i).warning) warnings++;
            else cautions++;
        }
        place();
    }

    private Tile kind(String tag) {
        for (int i = 0; i < kinds.size; i++) if (kinds.get(i).tag.equals(tag)) return kinds.get(i);
        Tile t = pool.size > 0 ? pool.pop() : new Tile();
        t.tag = tag;
        t.value = "";
        t.alert = null;
        t.count = 0;
        t.since = Double.NEGATIVE_INFINITY;
        t.level = Level.CAUTION;
        t.warning = false;
        kinds.add(t);
        return t;
    }

    /** Warnings from the WARN end, cautions from the CAUT end, "+n MORE" where they meet. */
    private void place() {
        for (int i = 0; i < TILES; i++) slots[i] = null;
        int w = warnings, c = cautions;
        if (w + c <= TILES) {
            for (int i = 0; i < w; i++) slots[i] = kinds.get(i);
            for (int i = 0; i < c; i++) slots[TILES - 1 - i] = kinds.get(w + i);
            return;
        }
        if (w >= TILES) {
            for (int i = 0; i < TILES - 1; i++) slots[i] = kinds.get(i);
            more(w - (TILES - 1) + c, true);
            slots[TILES - 1] = more;
            return;
        }
        for (int i = 0; i < w; i++) slots[i] = kinds.get(i);
        int shown = TILES - 1 - w;
        for (int i = 0; i < shown; i++) slots[TILES - 1 - i] = kinds.get(w + i);
        more(c - shown, false);
        slots[w] = more;
    }

    private void more(int n, boolean warning) {
        more.tag = "+" + n;
        more.value = "MORE";
        more.warning = warning;
        more.alert = null;
        more.count = n;
        more.level = warning ? Level.WARNING : Level.CAUTION;
    }

    /** Whether any tile is up. */
    boolean any() {
        return warnings + cautions > 0;
    }

    // ------------------------------------------------------------------ the words (13.4)

    /** The tile's tag for {@code a}: its kind. */
    static String tag(Alert a, PowerFrame power) {
        SystemRow row = systemRow(a, power);
        if (row != null) {
            switch (row.fault) {
                case SHORTED:
                case NO_POWER:
                    return "POWER";
                case STUNNED:
                    return "STUN";
                default:
                    return Fmt.cut(row.system, CHARS);
            }
        }
        switch (a.code) {
            case "FUEL":
                return "DELTA-V";
            case "O2":
                return a.text.contains("dry") ? "O2 TANKS" : "O2";
            default:
                return Fmt.cut(a.code, CHARS);
        }
    }

    /** The tile's value: the worst instance's, with a count of the rest where its value is a place. */
    static String value(Tile t, PowerFrame power, boolean blackout) {
        Alert a = t.alert;
        if ("POWER".equals(t.tag)) {
            if (blackout) return "NONE";
            return t.count + " SYS";
        }
        if (a == null) return "";
        SystemRow row = systemRow(a, power);
        if (row != null) {
            if (row.fault == Fault.STUNNED) {
                String secs = Fmt.secs(Float.isNaN(a.timer) ? row.timer : a.timer);
                return Fmt.cut(secs.isEmpty() ? shortName(row.system) : shortName(row.system) + " " + secs, CHARS);
            }
            if (row.fault == Fault.DESTROYED) return "WRECKED";
            if (row.fault == Fault.DISABLED) return "DISABLED";
            return Fmt.cut(row.room, CHARS);
        }
        switch (a.code) {
            case "MISSILE":
            case "CASUALTY": {
                String time = Fmt.mmss(a.timer);
                String n = Integer.toString(Math.max(1, a.count));
                return Fmt.cut(time.isEmpty() ? n : n + (n.length() + time.length() <= CHARS - 2 ? "  " : " ") + time, CHARS);
            }
            case "O2":
                // The tanks dry (13.4: O2 TANKS / DRY); otherwise how much is left
                return a.text.contains("dry") ? "DRY" : percent(a.text);
            case "HULL":
                return percent(a.text);
            case "FUEL":
                return "LOW";
            case "HELM":
                // Bridge.helmFault's words
                if (a.text.contains("wrecked")) return "WRECKED";
                if (a.text.contains("switched off")) return "OFF";
                if (a.text.contains("power")) return "NO POWER";
                return "DOWN";
            case "HOSTILE":
                return "LOCK ON";
            case "SHIELD":
                return a.text.contains("tripped") ? "TRIP" : "DOWN";
            default:
                break;
        }
        String time = Fmt.mmss(a.timer);
        if (!time.isEmpty()) return Fmt.cut(time, CHARS);
        if (!a.room.isEmpty()) return place(a.room, t.count - 1);
        if (a.count > 1) return "X" + a.count;
        return "";
    }

    /** A room's name, with "+n" for the other rooms of its kind, cut to fit beside it. */
    static String place(String room, int others) {
        String plus = others > 0 ? "+" + others : "";
        return Fmt.cut(room, CHARS - plus.length()) + plus;
    }

    /** The last percentage in {@code text} ("Hull at 52%" gives "52%"), or "". */
    static String percent(String text) {
        int p = text.lastIndexOf('%');
        if (p < 0) return "";
        int s = p;
        while (s > 0 && (Character.isDigit(text.charAt(s - 1)) || text.charAt(s - 1) == '.')) s--;
        return text.substring(s, p + 1);
    }

    /** The power fault an alert stands for ("sys:REACTOR:3,4"), or null when it is not a system's. */
    static SystemRow systemRow(Alert a, PowerFrame power) {
        if (power == null || !a.id.startsWith("sys:")) return null;
        for (int i = 0; i < power.faults.size; i++) {
            SystemRow r = power.faults.get(i);
            if (a.id.equals("sys:" + r.system + ":" + r.tileX + "," + r.tileY)) return r;
        }
        return null;
    }

    /** A system's name in four letters, for a value that also holds a count ("RCTR 22"). */
    static String shortName(String system) {
        switch (system) {
            case "REACTOR": return "RCTR";
            case "SHIELDS": return "SHLD";
            case "TURRET": return "GUN";
            case "LIFE SUP": return "LIFE";
            case "WEAPONS": return "WPNS";
            case "AIRLOCK": return "LOCK";
            case "ENGINE": return "ENG";
            case "DOCK PORT": return "PORT";
            default: return Fmt.cut(system, 4);
        }
    }
}
