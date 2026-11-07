package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.uihud.providers.OrbitPathProvider.MarkKind;
import com.ringsofiron.uihud.providers.OrbitPathProvider.PathMark;
import com.ringsofiron.uihud.providers.TrafficMapProvider.Contact;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the map's tubes read out of the lines the game writes for the classic map panel (the planner's
 * orbit, burn and plan text, the path's burn labels and marks): times as the game writes them
 * ("0:12:30", "2.4 d"), the orbit now as rows, the selected burn's result, the helm's and the
 * autopilot's state, each burn's delta-v, the timeline's events, and the traffic in order.
 */
final class MapText {
    private MapText() {}

    /** A row of a tube: its words at the left, a value at the right; red for an impact. */
    static final class Row {
        String left = "", right = "";
        boolean red, dim;
        /** Seconds from now that the row is about (an apsis), or NaN. */
        double at = Double.NaN;

        Row set(String left, String right) {
            this.left = left;
            this.right = right;
            red = dim = false;
            at = Double.NaN;
            return this;
        }
    }

    /** A mark on the timeline: a burn, an apsis, a change of sphere, an impact, the closest approach. */
    static final class Event {
        String name = "", when = "";
        double t;
        /** The burn it is (0 the first), or -1. */
        int burn = -1;
        MarkKind kind;
        boolean red, closest;
    }

    // --- Times ------------------------------------------------------------------------------

    private static final Pattern CLOCK = Pattern.compile("(\\d+):(\\d\\d):(\\d\\d)");
    private static final Pattern DAYS = Pattern.compile("(\\d+(?:\\.\\d+)?) d");

    /** Seconds in a time as the game writes it ("1:02:03", or "2.4 d" past two days); NaN when it isn't one. */
    static double seconds(String t) {
        if (t == null) return Double.NaN;
        String s = t.trim();
        Matcher m = CLOCK.matcher(s);
        if (m.matches()) return Integer.parseInt(m.group(1)) * 3600.0 + Integer.parseInt(m.group(2)) * 60.0 + Integer.parseInt(m.group(3));
        m = DAYS.matcher(s);
        if (m.matches()) return Double.parseDouble(m.group(1)) * 86400.0;
        return Double.NaN;
    }

    /** Seconds written as the game writes a time: "1:02:03", or "2.4 d" from two days. */
    static String time(double seconds) {
        long s = Math.max(0L, (long) seconds);
        if (s >= 2 * 86400) return String.format(Locale.ROOT, "%.1f d", s / 86400.0);
        return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    /** A time as the tubes count down to it: "T-1:02:03", "T-2.4 D". */
    static String tMinus(String t) {
        return t == null || t.isEmpty() ? "" : "T-" + t.toUpperCase(Locale.ROOT);
    }

    /** The time a line ends with after " in ": "Enters Moon in 2.4 d" gives "2.4 d"; "" for none. */
    static String timeIn(String line) {
        int i = line == null ? -1 : line.lastIndexOf(" in ");
        if (i < 0) return "";
        String t = line.substring(i + 4).trim();
        return Double.isNaN(seconds(t)) ? "" : t;
    }

    /** A distance as the contacts list writes it: "850 M", "8.2 KM", "8,995 KM". */
    static String distance(double m) {
        if (Double.isNaN(m)) return "";
        if (m < 1000) return String.format(Locale.ROOT, "%,.0f M", m);
        if (m < 100_000) return String.format(Locale.ROOT, "%.1f KM", m / 1000);
        return String.format(Locale.ROOT, "%,.0f KM", m / 1000);
    }

    // --- The orbit now ----------------------------------------------------------------------

    private static final Pattern AROUND = Pattern.compile("Around (.+): altitude (.+), (.+ m/s)");
    private static final Pattern APSIS = Pattern.compile("(Pe|Ap) (.+?)(?: in (.+))?");
    private static final Pattern PERIOD = Pattern.compile("Period (.+), e (.+)");
    private static final Pattern ESCAPING = Pattern.compile("Escaping, e (.+)");

    /**
     * The planner's orbit lines as rows into {@code out} (from its pool), the body into {@code body[0]};
     * an apsis the ship reaches only after {@code firstBurn} seconds is left out, as the flight deck
     * leaves it out, the orbit changing at the burn. Returns the rows written.
     */
    static int orbit(String text, double firstBurn, Array<Row> out, String[] body) {
        body[0] = "";
        int n = 0;
        if (text == null || text.isEmpty()) return 0;
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            Row r = row(out, n);
            Matcher m;
            if ((m = AROUND.matcher(line)).matches()) {
                body[0] = m.group(1).toUpperCase(Locale.ROOT);
                r.set("ALT " + up(m.group(2)), up(m.group(3)));
            } else if ((m = APSIS.matcher(line)).matches()) {
                String t = m.group(3) == null ? "" : m.group(3);
                r.set(up(m.group(1)) + " " + up(m.group(2)), tMinus(t));
                r.at = seconds(t);
                if (!Double.isNaN(firstBurn) && r.at > firstBurn) continue;
            } else if ((m = PERIOD.matcher(line)).matches()) {
                r.set("PERIOD " + up(m.group(1)), "E " + m.group(2));
            } else if ((m = ESCAPING.matcher(line)).matches()) {
                r.set("ESCAPING", "E " + m.group(1));
            } else if (line.startsWith("ON COURSE TO HIT")) {
                r.set(line, "");
                r.red = true;
            } else {
                r.set(up(line), "");
                r.dim = line.startsWith("No orbit");
            }
            n++;
        }
        return n;
    }

    // --- The selected burn ------------------------------------------------------------------

    private static final Pattern BURN_OF = Pattern.compile("Burn (\\d+) of (\\d+) in (.+)");
    private static final Pattern BURN_DV = Pattern.compile("([\\d,]+(?:\\.\\d+)?) m/s(?:, about (.+) on the main engine)?");

    /**
     * The selected burn's lines as rows: what it costs and how long it fires, then the orbit it leads
     * to ("PE 400 KM", "AP 529,600 KM", "ESCAPING", an impact in red). {@code which[0]} gets the
     * burn's number, or 0 with none. Returns the rows written.
     */
    static int burn(String text, Array<Row> out, int[] which) {
        which[0] = 0;
        int n = 0;
        if (text == null || text.isEmpty()) return 0;
        for (String line : text.split("\n")) {
            line = line.trim();
            Matcher m;
            if ((m = BURN_OF.matcher(line)).matches()) {
                which[0] = Integer.parseInt(m.group(1));
            } else if ((m = BURN_DV.matcher(line)).matches()) {
                // Whole metres a second, so the firing time fits beside it; the tube writes DV, having no delta
                row(out, n++).set("DV " + String.format(Locale.ROOT, "%,d", Math.round(number(m.group(1)))) + " M/S",
                        m.group(2) == null ? "" : "FIRES " + up(m.group(2)));
            } else if (line.startsWith("Then ")) {
                for (String part : line.substring(5).split(", ")) {
                    Row r = row(out, n++).set(up(part.trim()), "");
                    r.red = part.contains("IMPACT");
                }
            }
        }
        return n;
    }

    // --- The plan's state -------------------------------------------------------------------

    /**
     * What the plan's lines say about the helm, the autopilot, the tanks and each burn's delta-v. The
     * helm flies the burns once it has the order, though the order may wait (the player has the helm,
     * or no one is at it).
     */
    static final class Plan {
        boolean helmFlying, helmWaits, autopilot, short_;
        /** The helm's and autopilot's own lines, as the game says them; "" for none. */
        String helm = "", auto = "";
        /** Each coming burn's delta-v, m/s, soonest first, as far as the plan lists them. */
        final FloatArray burnDv = new FloatArray();

        void clear() {
            helmFlying = helmWaits = autopilot = short_ = false;
            helm = auto = "";
            burnDv.clear();
        }
    }

    private static final Pattern NEXT_BURN = Pattern.compile("Next burn in [^:]+(?::\\d\\d:\\d\\d)?: ([\\d,]+) m/s.*");
    private static final Pattern LATER = Pattern.compile("\\(([\\d,]+) m/s\\)");

    /** Reads the planner's plan lines into {@code out}. */
    static void plan(String text, Plan out) {
        out.clear();
        if (text == null || text.isEmpty()) return;
        for (String line : text.split("\n")) {
            line = line.trim();
            Matcher m;
            if (line.startsWith("Helm") || line.startsWith("Flight computer")) {
                out.helm = line;
                out.helmFlying = line.contains("flying the planned burns") || line.contains("flying a burn");
                out.helmWaits = line.startsWith("Helm order (") && line.contains(") waits");
            } else if (line.startsWith("Autopilot to ")) {
                out.auto = line;
                out.autopilot = true;
            } else if ((m = NEXT_BURN.matcher(line)).matches()) {
                out.burnDv.add(number(m.group(1)));
            } else if (line.startsWith("Then burns in ")) {
                m = LATER.matcher(line);
                while (m.find()) out.burnDv.add(number(m.group(1)));
            }
            if (line.contains("NOT ENOUGH PROPELLANT")) out.short_ = true;
        }
    }

    // --- The timeline -----------------------------------------------------------------------

    /**
     * The timeline's events into {@code out} (from its pool), soonest first: each planned burn from its
     * label ("1: 0:12:30"); the apsides of the orbit now that come before the first burn; the path's
     * marks that say when (changes of sphere, an impact, the apsides of a leg past one); and the
     * closest approach to the navigation target, {@code closestIn} seconds from now (NaN for none).
     * Returns how many.
     */
    static int events(Array<String> nodeLabels, Array<Row> orbitRows, int orbitCount, Array<PathMark> marks,
                      double closestIn, String closestText, Array<Event> out) {
        int n = 0;
        double first = Double.NaN;
        for (int i = 0; i < nodeLabels.size; i++) {
            String l = nodeLabels.get(i);
            int c = l.indexOf(": ");
            String t = c < 0 ? "" : l.substring(c + 2);
            double s = seconds(t);
            if (Double.isNaN(s)) continue;
            if (Double.isNaN(first) || s < first) first = s;
            Event e = event(out, n++);
            e.name = "BURN " + (c < 0 ? String.valueOf(i + 1) : l.substring(0, c));
            e.when = tMinus(t);
            e.t = s;
            e.burn = i;
        }
        for (int i = 0; i < orbitCount; i++) {
            Row r = orbitRows.get(i);
            if (Double.isNaN(r.at) || !Double.isNaN(first) && r.at > first) continue;
            Event e = event(out, n++);
            e.name = r.left.substring(0, 2);
            e.when = r.right;
            e.t = r.at;
            e.kind = r.left.startsWith("AP") ? MarkKind.APOAPSIS : MarkKind.PERIAPSIS;
        }
        for (int i = 0; i < marks.size; i++) {
            PathMark m = marks.get(i);
            if (m.kind == MarkKind.GHOST) continue;
            String t = timeIn(m.text);
            if (t.isEmpty()) continue;
            Event e = event(out, n++);
            String what = m.text.substring(0, m.text.lastIndexOf(" in "));
            // "Moon Pe 1,234 km" as "MOON PE": the height is on the map beside the mark
            e.name = up(what.replaceAll(" [\\d,.]+ km$", "").replace("Impact: ", "Impact "));
            e.when = tMinus(t);
            e.t = seconds(t);
            e.kind = m.kind;
            e.red = m.kind == MarkKind.IMPACT;
        }
        if (!Double.isNaN(closestIn) && closestIn >= 0) {
            Event e = event(out, n++);
            e.name = closestText == null || closestText.isEmpty() ? "CLOSEST" : "CLOSEST " + closestText;
            e.when = tMinus(time(closestIn));
            e.t = closestIn;
            e.closest = true;
        }
        sort(out, n);
        return n;
    }

    /**
     * Where each label goes along a line from {@code min} to {@code max}: as near its mark ({@code want},
     * the label's left) as it can, never over the one before it ({@code gap} between); a label that
     * would run past {@code max} is left out (NaN). The marks come in order along the line.
     */
    static void spread(float[] want, float[] width, int n, float min, float max, float gap, float[] out) {
        float edge = min;
        for (int i = 0; i < n; i++) {
            float x = Math.max(want[i], edge);
            if (x + width[i] > max) {
                // Pulled back to fit at the end if the one before leaves room
                float back = max - width[i];
                if (back >= edge) x = back;
                else {
                    out[i] = Float.NaN;
                    continue;
                }
            }
            out[i] = x;
            edge = x + width[i] + gap;
        }
    }

    // --- The traffic ------------------------------------------------------------------------

    /** The contacts list's order: by distance from the ship, or by kind (stations first) and then distance. */
    static final ContactKind[] KIND_ORDER = {ContactKind.STATION, ContactKind.HAULER, ContactKind.HOSTILE, ContactKind.OTHER};

    /**
     * The contacts but the ship itself into {@code order} (indices into {@code contacts}), nearest the
     * ship first or by kind; their distances from it, m, into {@code dist} (by index).
     */
    static void order(Array<Contact> contacts, boolean byKind, IntArray order, FloatArray dist) {
        order.clear();
        dist.clear();
        double sx = 0, sy = 0;
        for (int i = 0; i < contacts.size; i++) {
            Contact c = contacts.get(i);
            if (c.kind == ContactKind.PLAYER) {
                sx = c.x;
                sy = c.y;
            }
        }
        for (int i = 0; i < contacts.size; i++) {
            Contact c = contacts.get(i);
            dist.add((float) Math.hypot(c.x - sx, c.y - sy));
            if (c.kind != ContactKind.PLAYER) order.add(i);
        }
        // Insertion sort: a few dozen contacts, kept in place between frames
        for (int i = 1; i < order.size; i++) {
            int v = order.get(i), j = i - 1;
            while (j >= 0 && after(contacts, dist, byKind, order.get(j), v)) {
                order.set(j + 1, order.get(j));
                j--;
            }
            order.set(j + 1, v);
        }
    }

    private static boolean after(Array<Contact> contacts, FloatArray dist, boolean byKind, int a, int b) {
        if (byKind) {
            int ka = rank(contacts.get(a).kind), kb = rank(contacts.get(b).kind);
            if (ka != kb) return ka > kb;
        }
        return dist.get(a) > dist.get(b);
    }

    private static int rank(ContactKind k) {
        for (int i = 0; i < KIND_ORDER.length; i++) if (KIND_ORDER[i] == k) return i;
        return KIND_ORDER.length;
    }

    // --- Helpers ----------------------------------------------------------------------------

    private static String up(String s) {
        return s.toUpperCase(Locale.ROOT);
    }

    private static float number(String s) {
        try {
            return Float.parseFloat(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    private static Row row(Array<Row> pool, int i) {
        while (pool.size <= i) pool.add(new Row());
        return pool.get(i);
    }

    private static Event event(Array<Event> pool, int i) {
        while (pool.size <= i) pool.add(new Event());
        Event e = pool.get(i);
        e.burn = -1;
        e.kind = null;
        e.red = e.closest = false;
        return e;
    }

    /** Sorts the first {@code n} events soonest first, keeping the order of those at one time. */
    private static void sort(Array<Event> a, int n) {
        for (int i = 1; i < n; i++) {
            Event v = a.get(i);
            int j = i - 1;
            while (j >= 0 && a.get(j).t > v.t) {
                a.set(j + 1, a.get(j));
                j--;
            }
            a.set(j + 1, v);
        }
    }
}
