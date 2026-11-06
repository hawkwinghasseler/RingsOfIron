package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;

import java.util.Locale;

/**
 * The fight's record for the Log (Context P4 and 10.5, v16 and v17): each condition the game raises,
 * as it is raised and as it clears, in the catalogue's long form ("Fire: HOLD", "Fire: HOLD, cleared
 * after 2:10"), at its row's severity and with the source of what it is about. A room's air is one
 * course, as P2 shows it: a breach, then the vacuum once it is sealed, then the low pressure while it
 * refills, each step logged and the clearing timed from the first. Each missile fired at the ship
 * comes in as a warning, and goes out when the count drops. Watched every frame, whatever is on the
 * screen, so nothing is missed while the log is shut. A system's fault is logged once it has held
 * a moment, at the time it began: the power routing leaves a system just switched on dark for a tick
 * or two, which is no condition worth a line. A hazard is logged at once: the crew can put a fire
 * out inside two seconds, and it was still a fire.
 */
final class ConditionLog {
    /** Kept at most: the oldest go first. */
    static final int KEEP = 300;
    /** A condition gone for less than this and back again is one condition: no clearing logged. */
    static final double SETTLE = 2.0;
    /** A system's fault is logged once it has held this long, so a blip leaves no line. */
    static final double HOLD = 2.5;

    /** The sources the Log sorts by (P4's list), in its order. */
    static final String[] SOURCES = {"HELM", "GUNNERY", "SHIELDS", "ENGINEERING", "MEDICAL", "CREW", "BOARDING",
            "STATION", "SPONSOR", "COMMS", "LOG"};

    /** One line of the record. */
    static final class Entry {
        double time;
        String text = "";
        Level level = Level.ADVISORY;
        /** One of {@link #SOURCES}. */
        String source = "";
    }

    /**
     * A condition being watched: when it began, its last step, when it went (NaN while it holds), and
     * whether it has held long enough to be logged.
     */
    private static final class Open {
        String key = "", step = "", where = "", source = "";
        Level level = Level.ADVISORY;
        double since, goneAt = Double.NaN;
        boolean seen, logged;
    }

    final Array<Entry> entries = new Array<>();
    private final ObjectMap<String, Open> open = new ObjectMap<>();
    private final ObjectMap<String, Alert> frame = new ObjectMap<>();
    private final Array<String> gone = new Array<>();
    private final Array<Open> pool = new Array<>();
    private int missiles;
    /** Bumped each time a line is logged, so the Log can tell it has something new. */
    int revision;

    /** Watches {@code alerts} at sim time {@code now}: call each frame. */
    void update(AlertRegistry alerts, double now) {
        update(alerts, null, now);
    }

    /** As {@link #update(AlertRegistry, double)}, naming a gun that is down by its mount in {@code mounts}. */
    void update(AlertRegistry alerts, Array<Mount> mounts, double now) {
        if (alerts == null) return;
        // This frame's word on each condition: on a room's air, the first that holds of its breach,
        // its vacuum and its low pressure (P2's rule)
        frame.clear();
        Array<Alert> all = alerts.all();
        int inbound = 0;
        for (int i = 0; i < all.size; i++) {
            Alert a = all.get(i);
            if (a.id.equals("missile")) {
                inbound = Math.max(inbound, Math.max(1, a.count));
                continue;
            }
            String key = key(a);
            if (key == null) continue;
            Alert had = frame.get(key);
            if (had == null || rank(a) > rank(had)) frame.put(key, a);
        }
        for (ObjectMap.Entry<String, Open> e : open.entries()) e.value.seen = false;
        for (ObjectMap.Entry<String, Alert> e : frame.entries()) {
            Alert a = e.value;
            String step = longForm(a);
            Open o = open.get(e.key);
            if (o == null) {
                o = pool.size > 0 ? pool.pop() : new Open();
                o.key = e.key;
                o.since = a.since > 0 && a.since <= now ? a.since : now;
                o.step = step;
                o.where = where(a, mounts);
                o.source = source(a);
                o.logged = false;
                open.put(e.key, o);
            } else if (!step.equals(o.step)) {
                // The course moved on: the room's air sealed, then refilling
                o.step = step;
                if (o.logged) log(now, line(step, o.where, null), a.level, o.source);
            }
            o.level = a.level;
            o.goneAt = Double.NaN;
            o.seen = true;
            if (!o.logged && now - o.since >= hold(a)) {
                o.logged = true;
                log(o.since, line(o.step, o.where, null), o.level, o.source);
            }
        }
        // What has gone: cleared once it stays gone a moment, timed from the first
        gone.clear();
        for (ObjectMap.Entry<String, Open> e : open.entries()) {
            Open o = e.value;
            if (o.seen) continue;
            if (Double.isNaN(o.goneAt)) o.goneAt = now;
            if (now - o.goneAt < SETTLE) continue;
            if (o.logged) log(o.goneAt, line(o.step, o.where, ", cleared after " + duration(o.goneAt - o.since)), o.level, o.source);
            gone.add(e.key);
        }
        for (int i = 0; i < gone.size; i++) pool.add(open.remove(gone.get(i)));
        // Missiles: each one fired at the ship, and each gone
        if (inbound > missiles) {
            for (int k = missiles; k < inbound; k++) log(now, "Missile fired at the ship", Level.WARNING, "GUNNERY");
        } else if (inbound < missiles) {
            int n = missiles - inbound;
            log(now, n == 1 ? "Missile gone" : n + " missiles gone", Level.WARNING, "GUNNERY");
        }
        missiles = inbound;
    }

    /**
     * How long a condition must hold to be logged: {@link #HOLD} for a system's fault and the helm's,
     * which the power routing's once-a-second tick can raise for a moment after a switch; none for
     * the rest.
     */
    static double hold(Alert a) {
        return a.id.startsWith("sys:") || a.id.equals("helm") ? HOLD : 0;
    }

    /** Which of a room's air alerts speaks for it: the breach, then the vacuum, then the low pressure. */
    static int rank(Alert a) {
        if (a.id.startsWith("breach:")) return 3;
        if (a.id.startsWith("air:")) return "VACUUM".equals(a.code) ? 2 : 1;
        return a.level.ordinal();
    }

    /** "2:10", "11:04", "1:02:03". */
    static String duration(double seconds) {
        long s = Math.max(0L, Math.round(seconds));
        if (s < 3600) return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
        return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    private void log(double time, String text, Level level, String source) {
        Entry e = entries.size >= KEEP ? entries.removeIndex(0) : new Entry();
        e.time = time;
        e.text = text;
        e.level = level;
        e.source = source;
        // In time order: a condition raised before it was seen goes where it belongs
        int at = entries.size;
        while (at > 0 && entries.get(at - 1).time > time) at--;
        entries.insert(at, e);
        revision++;
    }

    /** "Fire: HOLD", "Ship disabled", "Fire: HOLD, cleared after 2:10". */
    static String line(String longForm, String where, String tail) {
        String s = where.isEmpty() ? longForm : longForm + ": " + where;
        return tail == null ? s : s + tail;
    }

    /**
     * What a condition is watched by: a room's breach, vacuum and low pressure share one key, so the
     * room's air is one course. Null for what is not a condition: a hostile on the scope, a launcher
     * empty, a missile (logged apart), and a system switched off, which is the crew's own choice.
     */
    static String key(Alert a) {
        String id = a.id;
        if (id.startsWith("breach:")) return "air:" + id.substring("breach:".length());
        if (id.equals("hostile") || id.startsWith("empty:")) return null;
        if (id.startsWith("sys:") && a.level == Level.ADVISORY) return null;
        return id;
    }

    /** Where it is: the room, a casualty's name, the system and its room. */
    static String where(Alert a) {
        return where(a, null);
    }

    /** As {@link #where(Alert)}, a gun by its mount in {@code mounts} (F2's rule: "FWD PORT PD"). */
    static String where(Alert a, Array<Mount> mounts) {
        String id = a.id;
        int colon = id.indexOf(':');
        if (id.startsWith("casualty:")) return id.substring(colon + 1);
        if (id.startsWith("sys:") && mounts != null && a.tileX >= 0) {
            for (int i = 0; i < mounts.size; i++) {
                Mount m = mounts.get(i);
                if (m.tileX == a.tileX && m.tileY == a.tileY && !m.name.isEmpty()) return m.name;
            }
        }
        if (id.startsWith("sys:")) {
            String[] parts = id.split(":");
            String system = parts.length > 1 ? parts[1] : "";
            return a.room.isEmpty() ? system : system + " (" + a.room + ")";
        }
        if (!a.room.isEmpty()) return a.room;
        if (id.startsWith("fire:") || id.startsWith("breach:") || id.startsWith("air:") || id.startsWith("smoke:")) return id.substring(colon + 1);
        return "";
    }

    /** The catalogue's long form for the game's alert, or its own words where the catalogue has none. */
    static String longForm(Alert a) {
        String id = a.id, code = a.code;
        if (id.startsWith("fire:")) return "Fire";
        if (id.startsWith("breach:")) return "Hull breach";
        if (id.startsWith("air:")) return "VACUUM".equals(code) ? "Vacuum" : "Low pressure";
        if (id.startsWith("smoke:")) return "Smoke";
        if (id.startsWith("sys:")) return "System down";
        if (id.startsWith("casualty:")) return "Crew critical";
        switch (id) {
            case "hull":
                return "Hull damaged";
            case "oxygen":
                return a.level == Level.WARNING ? "Emergency oxygen" : "Low oxygen";
            case "propellant":
                return "Propellant low";
            case "shield:down":
                return "Shields down";
            case "shield:tripped":
                return "Shield weak";
            case "captain:bleeding":
                return "Captain wounded";
            case "captain:suit":
                return "Suit air low";
            case "captain:air":
                return "No suit in thin air";
            case "disabled":
                return "Ship disabled";
            case "abandon":
                return "Abandon ship";
            case "eva":
                return "Crew outside";
            case "helm":
                // The game says why the helm can't fly the ship: "Helm: the helm has no power"
                return a.text.isEmpty() ? "Helm out" : a.text;
            default:
                if (!a.text.isEmpty()) return a.text;
                return code.isEmpty() ? id : Fmt.capital(code.toLowerCase(Locale.ROOT));
        }
    }

    /** The source of what a condition is about (v17): the hull, air, fire, power and systems are Engineering. */
    static String source(Alert a) {
        String id = a.id;
        if (id.startsWith("casualty:") || id.startsWith("captain:") || id.equals("eva")) return "CREW";
        if (id.startsWith("shield:")) return "SHIELDS";
        if (id.equals("helm") || id.equals("propellant")) return "HELM";
        if (a.category == AlertRegistry.Category.TACTICAL) return "GUNNERY";
        if (a.category == AlertRegistry.Category.FLIGHT || a.category == AlertRegistry.Category.NAV) return "HELM";
        if (a.category == AlertRegistry.Category.COMMS) return "COMMS";
        return "ENGINEERING";
    }
}
