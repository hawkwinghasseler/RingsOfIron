package com.ringsofiron.uihud.data;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.coreengine.Resettable;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;

import java.util.Locale;

/**
 * The record of the conditions the game raises, for a log to show among the notices: each condition
 * as it is raised and as it clears, in the catalogue's long form ("Fire: HOLD", "Fire: HOLD, cleared
 * after 2:10"), at its row's severity and with the source of what it is about. A room's air is one
 * course, as P2 shows it: a breach, then the vacuum once it is sealed, then the low pressure while it
 * refills, each step logged and the clearing timed from the first. Each missile fired at the ship
 * comes in as a warning, and goes out when the count drops. A system's fault is logged once it has
 * held a moment, at the time it began: the power routing leaves a system just switched on dark for a
 * tick or two, which is no condition worth a line. A hazard is logged at once: the crew can put a fire
 * out inside two seconds, and it was still a fire.
 *
 * <p>Kept two ways, so each HUD's log can follow its own spec. {@link #entries} has a line for each
 * step and each clearing (the Context spec's Log, v16 and v17). {@link #courses} has each condition
 * once, from its raising to its clearing: its latest words, the worst it reached and when it cleared
 * (the Task spec's book: "an alert is logged once ... when it clears, its entry gains the time ...
 * and one that worsens is revised in place"). A salvo is one course, its count growing as more are
 * fired, until none is left inbound.
 *
 * <p>Kept by {@link HudData} and read each frame there, whatever the HUD shows, so a change of HUD
 * keeps it; and saved with the game.
 */
public final class ConditionLog implements SaveParticipant, Resettable {
    /** Lines kept, and courses kept: the oldest go first. */
    public static final int KEEP = 500;
    /** A condition gone for less than this and back again is one condition: no clearing logged. */
    public static final double SETTLE = 2.0;
    /** A system's fault is logged once it has held this long, so a blip leaves no line. */
    public static final double HOLD = 2.5;
    /** The course of the missiles inbound. */
    public static final String MISSILES = "missile";

    /** The sources the Log sorts by (P4's list), in its order. */
    public static final String[] SOURCES = {"HELM", "GUNNERY", "SHIELDS", "ENGINEERING", "MEDICAL", "CREW", "BOARDING",
            "STATION", "SPONSOR", "COMMS", "LOG"};

    /** One line of the record. */
    public static final class Entry {
        public double time;
        public String text = "";
        public Level level = Level.ADVISORY;
        /** One of {@link #SOURCES}. */
        public String source = "";
    }

    /** One condition from its raising to its clearing. */
    public static final class Course {
        /** What it is watched by ({@link #key(Alert)}, or {@link #MISSILES}). */
        public String key = "";
        /** When it began. */
        public double time;
        /** Its latest words: "Low pressure: HOLD" once the breach is sealed and the room refills. */
        public String text = "";
        /** The worst it reached. */
        public Level level = Level.ADVISORY;
        /** One of {@link #SOURCES}. */
        public String source = "";
        /** When it cleared; NaN while it holds. */
        public double cleared = Double.NaN;

        public boolean isCleared() {
            return !Double.isNaN(cleared);
        }
    }

    /**
     * A condition being watched: when it began, its last step, when it went (NaN while it holds),
     * whether it has held long enough to be logged, and its course once it is.
     */
    private static final class Open {
        String key = "", step = "", where = "", source = "";
        Level level = Level.ADVISORY;
        double since, goneAt = Double.NaN;
        boolean seen, logged;
        Course course;
    }

    /** Oldest first. */
    public final Array<Entry> entries = new Array<>();
    /** Oldest first, by when each began. */
    public final Array<Course> courses = new Array<>();
    private final ObjectMap<String, Open> open = new ObjectMap<>();
    private final ObjectMap<String, Alert> frame = new ObjectMap<>();
    private final Array<String> gone = new Array<>();
    private final Array<Open> pool = new Array<>();
    /** Courses a loaded game left holding: each is taken up again by its condition, or cleared once the load has settled. */
    private final Array<Course> loaded = new Array<>();
    /** When a loaded game's courses that no condition took up are cleared; NaN until the first frame after a load. */
    private double loadedUntil = Double.NaN;
    private int missiles, fired;
    private Course salvo;
    /** Bumped each time a line is logged or a course changes, so a log can tell it has something new. */
    public int revision;

    /** Watches {@code alerts} at sim time {@code now}: call each frame. */
    public void update(AlertRegistry alerts, double now) {
        update(alerts, null, now);
    }

    /** As {@link #update(AlertRegistry, double)}, naming a gun that is down by its mount in {@code mounts}. */
    public void update(AlertRegistry alerts, Array<Mount> mounts, double now) {
        if (alerts == null) return;
        // This frame's word on each condition: on a room's air, the first that holds of its breach,
        // its vacuum and its low pressure (P2's rule)
        frame.clear();
        Array<Alert> all = alerts.all();
        int inbound = 0;
        for (int i = 0; i < all.size; i++) {
            Alert a = all.get(i);
            if (a.id.equals(MISSILES)) {
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
                o.course = null;
                // Still holding from before a load: its line and its course are in the record already
                Course was = takeLoaded(e.key);
                if (was != null) {
                    o.course = was;
                    o.since = was.time;
                    o.logged = true;
                    String words = line(step, o.where, null);
                    if (!was.text.equals(words)) {
                        was.text = words;
                        revision++;
                    }
                }
                open.put(e.key, o);
            } else if (!step.equals(o.step)) {
                // The course moved on: the room's air sealed, then refilling
                o.step = step;
                if (o.logged) {
                    log(now, line(step, o.where, null), a.level, o.source);
                    o.course.text = line(step, o.where, null);
                }
            }
            o.level = a.level;
            o.goneAt = Double.NaN;
            o.seen = true;
            if (!o.logged && now - o.since >= hold(a)) {
                o.logged = true;
                log(o.since, line(o.step, o.where, null), o.level, o.source);
                o.course = course(e.key, o.since, line(o.step, o.where, null), o.level, o.source);
            }
            // Worse than it was: its course is revised in place
            if (o.logged && o.level.ordinal() > o.course.level.ordinal()) {
                o.course.level = o.level;
                revision++;
            }
        }
        // What has gone: cleared once it stays gone a moment, timed from the first
        gone.clear();
        for (ObjectMap.Entry<String, Open> e : open.entries()) {
            Open o = e.value;
            if (o.seen) continue;
            if (Double.isNaN(o.goneAt)) o.goneAt = now;
            if (now - o.goneAt < SETTLE) continue;
            if (o.logged) {
                log(o.goneAt, line(o.step, o.where, ", cleared after " + duration(o.goneAt - o.since)), o.level, o.source);
                o.course.cleared = o.goneAt;
            }
            o.course = null;
            gone.add(e.key);
        }
        for (int i = 0; i < gone.size; i++) pool.add(open.remove(gone.get(i)));
        // Missiles: each one fired at the ship, and each gone; the salvo one course until none is left
        if (inbound > missiles) {
            for (int k = missiles; k < inbound; k++) log(now, "Missile fired at the ship", Level.WARNING, "GUNNERY");
            if (salvo == null) salvo = course(MISSILES, now, "", Level.WARNING, "GUNNERY");
            fired += inbound - missiles;
            salvo.text = fired == 1 ? "Missile fired at the ship" : fired + " missiles fired at the ship";
        } else if (inbound < missiles) {
            int n = missiles - inbound;
            log(now, n == 1 ? "Missile gone" : n + " missiles gone", Level.WARNING, "GUNNERY");
            if (inbound == 0 && salvo != null) {
                salvo.cleared = now;
                salvo = null;
                fired = 0;
            }
        }
        missiles = inbound;
        settleLoad(now);
    }

    /** The course a loaded game left holding for {@code key}, taken up by its condition again; null when there is none. */
    private Course takeLoaded(String key) {
        for (int i = 0; i < loaded.size; i++) {
            if (loaded.get(i).key.equals(key)) return loaded.removeIndex(i);
        }
        return null;
    }

    /**
     * A loaded game's courses that no condition took up once the load has settled ({@link #SETTLE}),
     * cleared at the load, when they were last known to hold: the game came back without them.
     */
    private void settleLoad(double now) {
        if (loaded.size == 0) return;
        if (Double.isNaN(loadedUntil)) loadedUntil = now + SETTLE;
        if (now < loadedUntil) return;
        double at = loadedUntil - SETTLE;
        for (int i = 0; i < loaded.size; i++) {
            Course c = loaded.get(i);
            log(at, c.text + ", cleared after " + duration(at - c.time), c.level, c.source);
            c.cleared = at;
        }
        loaded.clear();
    }

    /**
     * How long a condition must hold to be logged: {@link #HOLD} for a system's fault and the helm's,
     * which the power routing's once-a-second tick can raise for a moment after a switch; none for
     * the rest.
     */
    public static double hold(Alert a) {
        return a.id.startsWith("sys:") || a.id.equals("helm") ? HOLD : 0;
    }

    /** Which of a room's air alerts speaks for it: the breach, then the vacuum, then the low pressure. */
    static int rank(Alert a) {
        if (a.id.startsWith("breach:")) return 3;
        if (a.id.startsWith("air:")) return "VACUUM".equals(a.code) ? 2 : 1;
        return a.level.ordinal();
    }

    /** "2:10", "11:04", "1:02:03". */
    public static String duration(double seconds) {
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

    /**
     * A new course, in time order among the rest. The oldest goes past {@link #KEEP}, and is not
     * reused: a condition still holding may have it.
     */
    private Course course(String key, double time, String text, Level level, String source) {
        if (courses.size >= KEEP) courses.removeIndex(0);
        Course c = new Course();
        c.key = key;
        c.time = time;
        c.text = text;
        c.level = level;
        c.source = source;
        int at = courses.size;
        while (at > 0 && courses.get(at - 1).time > time) at--;
        courses.insert(at, c);
        revision++;
        return c;
    }

    /** "Fire: HOLD", "Ship disabled", "Fire: HOLD, cleared after 2:10". */
    public static String line(String longForm, String where, String tail) {
        String s = where.isEmpty() ? longForm : longForm + ": " + where;
        return tail == null ? s : s + tail;
    }

    /**
     * What a condition is watched by: a room's breach, vacuum and low pressure share one key, so the
     * room's air is one course. Null for what is not a condition: a hostile on the scope, a launcher
     * empty, a missile (logged apart), and a system switched off, which is the crew's own choice.
     */
    public static String key(Alert a) {
        String id = a.id;
        if (id.startsWith("breach:")) return "air:" + id.substring("breach:".length());
        if (id.equals("hostile") || id.startsWith("empty:")) return null;
        if (id.startsWith("sys:") && a.level == Level.ADVISORY) return null;
        return id;
    }

    /** Where it is: the room, a casualty's name, the system and its room. */
    public static String where(Alert a) {
        return where(a, null);
    }

    /** As {@link #where(Alert)}, a gun by its mount in {@code mounts} (F2's rule: "FWD PORT PD"). */
    public static String where(Alert a, Array<Mount> mounts) {
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
    public static String longForm(Alert a) {
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
                return code.isEmpty() ? id : capital(code.toLowerCase(Locale.ROOT));
        }
    }

    private static String capital(String s) {
        return s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** The source of what a condition is about (v17): the hull, air, fire, power and systems are Engineering. */
    public static String source(Alert a) {
        String id = a.id;
        if (id.startsWith("casualty:") || id.startsWith("captain:") || id.equals("eva")) return "CREW";
        if (id.startsWith("shield:")) return "SHIELDS";
        if (id.equals("helm") || id.equals("propellant")) return "HELM";
        if (a.category == AlertRegistry.Category.TACTICAL) return "GUNNERY";
        if (a.category == AlertRegistry.Category.FLIGHT || a.category == AlertRegistry.Category.NAV) return "HELM";
        if (a.category == AlertRegistry.Category.COMMS) return "COMMS";
        return "ENGINEERING";
    }

    // --- Saved with the game ------------------------------------------------------------

    @Override
    public String saveKey() {
        return "hud.record";
    }

    @Override
    public void write(Json json) {
        json.writeArrayStart("entries");
        for (int i = 0; i < entries.size; i++) {
            Entry e = entries.get(i);
            json.writeObjectStart();
            json.writeValue("t", e.time);
            json.writeValue("x", e.text);
            json.writeValue("l", e.level.name());
            json.writeValue("s", e.source);
            json.writeObjectEnd();
        }
        json.writeArrayEnd();
        json.writeArrayStart("courses");
        for (int i = 0; i < courses.size; i++) {
            Course c = courses.get(i);
            json.writeObjectStart();
            json.writeValue("k", c.key);
            json.writeValue("t", c.time);
            json.writeValue("x", c.text);
            json.writeValue("l", c.level.name());
            json.writeValue("s", c.source);
            if (c.isCleared()) json.writeValue("c", c.cleared);
            json.writeObjectEnd();
        }
        json.writeArrayEnd();
        json.writeValue("missiles", missiles);
        json.writeValue("fired", fired);
    }

    @Override
    public void read(Json json, JsonValue data) {
        reset();
        JsonValue lines = data.get("entries");
        for (JsonValue v = lines == null ? null : lines.child; v != null; v = v.next) {
            if (entries.size >= KEEP) entries.removeIndex(0);
            Entry e = new Entry();
            e.time = v.getDouble("t", 0);
            e.text = v.getString("x", "");
            e.level = level(v.getString("l", ""));
            e.source = v.getString("s", "");
            entries.add(e);
        }
        JsonValue held = data.get("courses");
        for (JsonValue v = held == null ? null : held.child; v != null; v = v.next) {
            if (courses.size >= KEEP) courses.removeIndex(0);
            Course c = new Course();
            c.key = v.getString("k", "");
            c.time = v.getDouble("t", 0);
            c.text = v.getString("x", "");
            c.level = level(v.getString("l", ""));
            c.source = v.getString("s", "");
            c.cleared = v.has("c") ? v.getDouble("c") : Double.NaN;
            courses.add(c);
        }
        missiles = data.getInt("missiles", 0);
        fired = data.getInt("fired", 0);
        // What was holding at the save: each condition takes its course up again as it is seen, the
        // salvo at once, since the count it was at came back with it
        for (int i = 0; i < courses.size; i++) {
            Course c = courses.get(i);
            if (c.isCleared()) continue;
            if (c.key.equals(MISSILES) && salvo == null && missiles > 0) salvo = c;
            else loaded.add(c);
        }
        if (salvo == null) fired = 0;
        revision++;
    }

    private static Level level(String name) {
        for (Level l : Level.values()) if (l.name().equals(name)) return l;
        return Level.ADVISORY;
    }

    /** Forgets everything, before a load is read over it. */
    @Override
    public void reset() {
        entries.clear();
        courses.clear();
        for (Open o : open.values()) pool.add(o);
        open.clear();
        loaded.clear();
        loadedUntil = Double.NaN;
        missiles = 0;
        fired = 0;
        salvo = null;
        revision++;
    }
}
