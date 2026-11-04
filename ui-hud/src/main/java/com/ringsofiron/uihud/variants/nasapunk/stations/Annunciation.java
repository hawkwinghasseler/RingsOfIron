package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The annunciator's tiles (functional-stations.md sections 7.1 and 13.4), from the game's alerts:
 * one tile per kind, its tag and value at most 8 capitals each ("FIRE" / "BRIDGE+1"), lit by the
 * worst of its kind. Warnings come first, then cautions, newest first; advisories take no tile. The
 * row holds six; past six the last reads "+n MORE", lit as the worst of what it hides.
 *
 * <p>A system the power doesn't reach, or that a short cut off, counts on POWER / n SYS rather than
 * a tile of its own; with no reactor working it reads POWER / NONE, a warning. A stun is STUN with
 * the system and its seconds, and a system wrecked or disabled is its own tile.
 *
 * <p>Each tile has its station and home page, which the board's lights and the MFDs' paging follow.
 * The ship disabled and abandon ship take no tile, since the status strip says them, but light
 * Command's. The master lamps are lit while a warning, or a caution, is not yet acknowledged.
 *
 * <p>Section 13.1's escalation sets the level of what the crew work: a fire or a breach is a
 * caution while crew are on it, or for its first 10 s, and a warning once nobody has been on it for
 * 10 s, or for a fire, once it burns in a second room; a casualty is a caution while someone tends
 * them, and a warning while nobody does. A rise lights its lamp again, acknowledged or not (13.3).
 */
final class Annunciation {
    /** Tiles in the row. */
    static final int ROW = 6;
    /** Capitals on a tile's line. */
    static final int CHARS = 8;
    /** Nobody on a fire or a breach this long makes it a warning, s (13.1). */
    static final double UNATTENDED = 10.0;
    private static final Pattern PERCENT = Pattern.compile("(\\d+)%");

    static final class Tile {
        /** Its kind: "FIRE", "POWER", or "OTHER:" and the code of a kind this HUD doesn't know. */
        String key = "";
        String tag = "", value = "";
        Level level = Level.CAUTION;
        Station station = Station.CMD;
        Page home = Page.CREW;
        /** When its oldest instance was raised, sim s. */
        double since;
        /** The "+n MORE" tile: how many tiles it stands for; 0 for any other. */
        int hides;
    }

    /** Every tile lit, in the row's order. */
    final List<Tile> all = new ArrayList<>();
    /** What the row shows: six, or five and "+n MORE". */
    final List<Tile> row = new ArrayList<>();
    /** Each station's worst level among its tiles, by ordinal; null with none. */
    final Level[] stations = new Level[Station.values().length];
    /** A warning, or a caution, not yet acknowledged: what lights each master lamp. */
    boolean warnUnacked, cautUnacked;

    private final Map<String, List<Alert>> groups = new LinkedHashMap<>();
    private final List<List<Alert>> spare = new ArrayList<>();
    private final Tile more = new Tile();
    private final List<Tile> pool = new ArrayList<>();
    /** Each alert's level after escalation this frame, and last frame's, by id. */
    private Map<String, Level> levels = new HashMap<>(), lastLevels = new HashMap<>();
    /** When crew were last on each fire and breach, sim s; its raising while nobody has been. */
    private final Map<String, double[]> lastOn = new HashMap<>();
    /** What escalation has made worse since the player last acknowledged: lit again. */
    private final Set<String> rose = new HashSet<>();
    private boolean wasBlackout;
    private double blackoutSince;

    /**
     * This frame's tiles from {@code alerts}. {@code blackout}: no reactor makes power, which reads
     * POWER / NONE whatever the alerts say; {@code now} is the sim time.
     */
    void build(AlertRegistry alerts, boolean blackout, double now) {
        pool.addAll(all);
        all.clear();
        row.clear();
        Arrays.fill(stations, null);
        warnUnacked = cautUnacked = false;
        for (List<Alert> g : groups.values()) {
            g.clear();
            spare.add(g);
        }
        groups.clear();
        if (blackout && !wasBlackout) blackoutSince = now;
        wasBlackout = blackout;

        Array<Alert> a = alerts.all();
        escalate(a, now);
        for (int i = 0; i < a.size; i++) {
            Alert x = a.get(i);
            Level l = level(x);
            boolean acked = x.acknowledged && !rose.contains(x.id);
            if (!acked && l == Level.WARNING) warnUnacked = true;
            if (!acked && l == Level.CAUTION) cautUnacked = true;
            if (x.id.equals("disabled") || x.id.equals("abandon")) {
                light(Station.CMD, l);
                continue;
            }
            if (l == Level.ADVISORY) continue;
            String key = keyOf(x);
            if (key != null) group(key).add(x);
        }
        if (blackout) group("POWER");
        for (Map.Entry<String, List<Alert>> e : groups.entrySet()) {
            Tile t = pool.isEmpty() ? new Tile() : pool.remove(pool.size() - 1);
            if (tile(t, e.getKey(), e.getValue(), blackout)) all.add(t);
            else pool.add(t);
        }
        all.sort((p, q) -> p.level != q.level ? q.level.ordinal() - p.level.ordinal() : Double.compare(q.since, p.since));
        for (int i = 0; i < all.size(); i++) light(all.get(i).station, all.get(i).level);
        if (all.size() <= ROW) {
            row.addAll(all);
            return;
        }
        row.addAll(all.subList(0, ROW - 1));
        Tile first = all.get(ROW - 1);
        more.key = "MORE";
        more.hides = all.size() - (ROW - 1);
        more.tag = "+" + more.hides;
        more.value = "MORE";
        // warnings sort first, so the worst it hides is the first of them
        more.level = first.level;
        more.station = first.station;
        more.home = first.home;
        more.since = first.since;
        row.add(more);
    }

    /** {@code x}'s level after escalation (13.1): a warning the crew have in hand is a caution. */
    Level level(Alert x) {
        Level l = levels.get(x.id);
        return l == null ? x.level : l;
    }

    /** The player acknowledges every alert as it stands: only something new or worse lights a lamp again. */
    void acknowledge(AlertRegistry alerts) {
        alerts.acknowledgeAll();
        rose.clear();
    }

    /**
     * This frame's levels: a fire, breach or casualty the game raises as a warning stays one only
     * while the crew don't have it in hand. Alerts the game can't say who works ({@code handling}
     * -1) keep its level.
     */
    private void escalate(Array<Alert> a, double now) {
        Map<String, Level> t = lastLevels;
        lastLevels = levels;
        levels = t;
        levels.clear();
        int fires = 0;
        for (int i = 0; i < a.size; i++) if (a.get(i).id.startsWith("fire:")) fires++;
        for (int i = 0; i < a.size; i++) {
            Alert x = a.get(i);
            Level l = x.level;
            boolean fire = x.id.startsWith("fire:"), breach = x.id.startsWith("breach:");
            if (l == Level.WARNING && x.handling >= 0) {
                if (fire || breach) {
                    double[] on = lastOn.get(x.id);
                    if (on == null) {
                        on = new double[] {x.since};
                        lastOn.put(x.id, on);
                    }
                    if (x.handling > 0) on[0] = now;
                    boolean spread = fire && fires > 1;
                    if (!spread && now - on[0] < UNATTENDED) l = Level.CAUTION;
                } else if ((x.id.startsWith("casualty:") || x.id.equals("captain:bleeding")) && x.handling > 0) {
                    l = Level.CAUTION;
                }
            }
            levels.put(x.id, l);
            Level was = lastLevels.get(x.id);
            if (was != null && l.ordinal() > was.ordinal()) rose.add(x.id);
        }
        // what has cleared is forgotten
        lastOn.keySet().retainAll(levels.keySet());
        rose.retainAll(levels.keySet());
    }

    /** The tile of {@code key}, or null while it isn't lit. */
    Tile get(String key) {
        for (int i = 0; i < all.size(); i++) if (all.get(i).key.equals(key)) return all.get(i);
        return null;
    }

    /** The worst level lit, or null. */
    Level worst() {
        return all.isEmpty() ? null : all.get(0).level;
    }

    private void light(Station s, Level l) {
        Level was = stations[s.ordinal()];
        if (l != Level.ADVISORY && (was == null || l.ordinal() > was.ordinal())) stations[s.ordinal()] = l;
    }

    private List<Alert> group(String key) {
        List<Alert> g = groups.get(key);
        if (g == null) {
            g = spare.isEmpty() ? new ArrayList<>() : spare.remove(spare.size() - 1);
            groups.put(key, g);
        }
        return g;
    }

    /** Which tile an alert goes on, by the game's ids (GameAlerts); null for none. */
    static String keyOf(Alert x) {
        String id = x.id;
        if (id.equals("missile")) return "MISSILE";
        if (id.equals("hostile")) return "HOSTILE";
        if (id.startsWith("fire:")) return "FIRE";
        if (id.startsWith("breach:")) return "BREACH";
        if (id.startsWith("smoke:")) return "SMOKE";
        if (id.startsWith("air:")) return "AIR";
        if (id.equals("hull")) return "HULL";
        if (id.equals("oxygen")) return "O2";
        if (id.equals("propellant")) return "DELTA-V";
        if (id.equals("helm")) return "HELM";
        if (id.startsWith("shield:")) return "SHIELD";
        if (id.equals("captain:suit")) return "SUIT AIR";
        if (id.equals("captain:air")) return "NO SUIT";
        if (id.equals("captain:bleeding")) return "BLEEDING";
        if (id.startsWith("casualty:")) return "CASUALTY";
        if (id.startsWith("sys:")) {
            switch (what(x)) {
                case "stunned": return "STUN";
                case "shorted":
                case "no power": return "POWER";
                case "destroyed":
                case "disabled": return reactor(x) ? "POWER" : "SYSTEM";
                default: return null;
            }
        }
        return "OTHER:" + x.code;
    }

    /** A system alert's state, as the game ends its text: "destroyed", "shorted", "no power". */
    static String what(Alert x) {
        int c = x.text.lastIndexOf(": ");
        return c < 0 ? "" : x.text.substring(c + 2).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean reactor(Alert x) {
        return "REACTOR".equals(x.code);
    }

    /** Fills {@code t} for {@code key} from its instances, worst then oldest first; false when it takes no tile. */
    private boolean tile(Tile t, String key, List<Alert> g, boolean blackout) {
        t.key = key;
        t.hides = 0;
        t.level = Level.CAUTION;
        t.since = Double.NaN;
        for (int i = 0; i < g.size(); i++) {
            Alert x = g.get(i);
            Level l = level(x);
            if (l.ordinal() > t.level.ordinal()) t.level = l;
            if (Double.isNaN(t.since) || x.since < t.since) t.since = x.since;
        }
        Alert first = g.isEmpty() ? null : g.get(0);
        switch (key) {
            case "MISSILE": {
                int n = Math.max(1, first.count);
                t.tag = "MISSILE";
                t.value = Float.isNaN(first.timer) ? Integer.toString(n) : n + "  " + NpFormat.clock(first.timer);
                home(t, Station.TAC, Page.DEFENSE);
                return true;
            }
            case "HOSTILE":
                t.tag = "HOSTILE";
                t.value = "FIRING";
                home(t, Station.TAC, Page.SENSORS);
                return true;
            case "FIRE":
            case "BREACH":
            case "SMOKE":
                t.tag = key;
                t.value = rooms(g);
                home(t, Station.ENG, Page.DAMAGE);
                return true;
            case "AIR":
                t.tag = t.level == Level.WARNING ? "VACUUM" : "PRESSURE";
                t.value = rooms(g);
                home(t, Station.ENG, Page.ATMOS);
                return true;
            case "HULL": {
                Matcher m = PERCENT.matcher(first.text);
                t.tag = "HULL";
                t.value = m.find() ? m.group(1) + "%" : "DAMAGED";
                home(t, Station.ENG, Page.DAMAGE);
                return true;
            }
            case "O2":
                t.tag = "O2 TANKS";
                t.value = t.level == Level.WARNING ? "DRY" : "LOW";
                home(t, Station.ENG, Page.ATMOS);
                return true;
            case "DELTA-V":
                t.tag = "DELTA-V";
                t.value = "LOW";
                home(t, Station.NAV, Page.PLAN);
                return true;
            case "HELM":
                t.tag = "HELM";
                t.value = helmWord(first.text);
                home(t, Station.HELM, Page.HELM);
                return true;
            case "SHIELD": {
                boolean tripped = false;
                for (int i = 0; i < g.size(); i++) if (g.get(i).id.equals("shield:tripped")) tripped = true;
                t.tag = "SHIELD";
                t.value = tripped ? "TRIP" : "DOWN";
                home(t, Station.TAC, Page.DEFENSE);
                return true;
            }
            case "SUIT AIR":
                t.tag = "SUIT AIR";
                t.value = Float.isNaN(first.timer) ? "LOW" : NpFormat.clock(first.timer);
                home(t, Station.CMD, Page.CREW);
                return true;
            case "NO SUIT":
                t.tag = "NO SUIT";
                t.value = first.room.isEmpty() ? "THIN AIR" : cut(upper(first.room), CHARS);
                home(t, Station.CMD, Page.CREW);
                return true;
            case "BLEEDING":
                t.tag = "BLEEDING";
                t.value = Float.isNaN(first.timer) ? "" : NpFormat.clock(first.timer);
                home(t, Station.CMD, Page.CREW);
                return true;
            case "CASUALTY": {
                float soonest = Float.NaN;
                for (int i = 0; i < g.size(); i++) {
                    float s = g.get(i).timer;
                    if (!Float.isNaN(s) && (Float.isNaN(soonest) || s < soonest)) soonest = s;
                }
                t.tag = "CASUALTY";
                t.value = Float.isNaN(soonest) ? Integer.toString(g.size()) : g.size() + " " + NpFormat.clock(soonest);
                home(t, Station.CMD, Page.CREW);
                return true;
            }
            case "STUN": {
                Alert soon = first;
                for (int i = 0; i < g.size(); i++) {
                    Alert x = g.get(i);
                    if (!Float.isNaN(x.timer) && (Float.isNaN(soon.timer) || x.timer < soon.timer)) soon = x;
                }
                String abbr = abbreviation(soon.code);
                t.tag = "STUN";
                t.value = Float.isNaN(soon.timer) ? abbr : abbr + " " + (int) Math.ceil(soon.timer);
                home(t, Station.ENG, Page.POWER);
                return true;
            }
            case "POWER": {
                int systems = 0;
                boolean none = blackout;
                for (int i = 0; i < g.size(); i++) {
                    if (reactor(g.get(i))) none = true;
                    else systems++;
                }
                if (Double.isNaN(t.since)) t.since = blackoutSince;
                t.tag = "POWER";
                if (none) {
                    t.value = "NONE";
                    t.level = Level.WARNING;
                } else {
                    t.value = systems + " SYS";
                }
                home(t, Station.ENG, Page.POWER);
                return true;
            }
            case "SYSTEM": {
                if (g.size() == 1) {
                    t.tag = cut(upper(first.code), CHARS);
                    t.value = what(first).equals("destroyed") ? "WRECKED" : "DISABLED";
                } else {
                    t.tag = "SYSTEMS";
                    t.value = g.size() + " DOWN";
                }
                home(t, Station.ENG, Page.POWER);
                return true;
            }
            default: {
                if (first == null) return false;
                t.tag = cut(upper(first.code.isEmpty() ? key.substring(key.indexOf(':') + 1) : first.code), CHARS);
                t.value = cut(upper(first.room), CHARS);
                Station s = stationOf(first.category);
                home(t, s, s.pages()[0]);
                return true;
            }
        }
    }

    private static void home(Tile t, Station s, Page p) {
        t.station = s;
        t.home = p;
    }

    /** The worst instance's room, and how many more rooms have it: "BRIDGE+1", within 8. */
    private static String rooms(List<Alert> g) {
        String room = upper(g.get(0).room);
        if (room.isEmpty()) room = "ABOARD";
        String plus = g.size() > 1 ? "+" + (g.size() - 1) : "";
        if (room.length() + plus.length() > CHARS) room = room.substring(0, CHARS - plus.length()).trim();
        return room + plus;
    }

    /** What the helm's fault comes to (Bridge.helmFault), in a word. */
    static String helmWord(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("wrecked")) return "WRECKED";
        if (t.contains("switched off")) return "OFF";
        if (t.contains("no power")) return "NO POWER";
        if (t.contains("no helm")) return "NONE";
        return "DOWN";
    }

    /** A system in 4 capitals for a STUN tile: RCTR, LIFE, ENG. */
    static String abbreviation(String system) {
        String s = upper(system);
        switch (s) {
            case "REACTOR": return "RCTR";
            case "LIFE SUP":
            case "LIFE SUPPORT": return "LIFE";
            case "ENGINE":
            case "ENGINES": return "ENG";
            case "SHIELD":
            case "SHIELDS": return "SHLD";
            case "WEAPONS": return "WPNS";
            case "TURRET": return "GUN";
            default: {
                String t = s.replace(" ", "");
                return t.length() <= 4 ? t : t.substring(0, 4);
            }
        }
    }

    static Station stationOf(AlertRegistry.Category c) {
        switch (c) {
            case FLIGHT: return Station.HELM;
            case NAV: return Station.NAV;
            case TACTICAL: return Station.TAC;
            case ENGINEERING:
            case LIFE_SUPPORT: return Station.ENG;
            case COMMS: return Station.COMMS;
            default: return Station.CMD;
        }
    }

    static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    static String cut(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n).trim();
    }
}
