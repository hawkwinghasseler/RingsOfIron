package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.ObjectiveProvider.State;
import com.ringsofiron.uihud.providers.OrbitPathProvider.MapMarker;
import com.ringsofiron.uihud.providers.OrbitPathProvider.MarkKind;
import com.ringsofiron.uihud.providers.OrbitPathProvider.OrbitPath;
import com.ringsofiron.uihud.providers.OrbitPathProvider.PathMark;
import com.ringsofiron.uihud.providers.TrafficMapProvider.Contact;
import com.ringsofiron.uihud.providers.TrafficMapProvider.TrafficFrame;
import com.ringsofiron.uihud.variants.vfd.Fmt;

import java.util.Locale;

/**
 * What happens next on the Plot's map (functional-stations 10.2), read as FILAMENT × Context's map
 * timeline reads it: the plan's burns, the path's apsides, its changes of sphere of influence and an
 * impact, the capture the game plans at a crossing (an estimate until then), the arrival, and the
 * contracts' deadlines, soonest first, each with where it happens on the map when it has a place
 * there. The MAP page lists them: a click centres the map on one, its » warps on to it.
 */
final class PlotEvents {
    /** How many events it holds, and how many deadlines among them. */
    static final int MAX = 24, MAX_DEADLINES = 3;

    enum Kind {
        /** In the order the labels are kept when they crowd: a burn first, an apsis last. */
        BURN, CAPTURE, ARRIVAL, DEADLINE, SOI, IMPACT, APSIS
    }

    /** One event on the strip. */
    static final class Event {
        Kind kind = Kind.APSIS;
        String name = "";
        /** Seconds from now. */
        float in;
        /** An estimate (the capture before the crossing plans it). */
        boolean estimate;
        /** Where it happens, meters from the map's body, when it has a place there. */
        boolean placed;
        double x, y;
        /** The burn's index among the plan's, for a burn. */
        int burn = -1;
        String when = "";
    }

    final Array<Event> events = new Array<>();
    private final Array<Event> pool = new Array<>();
    /** The capture's place on the path: the onward body's periapsis. */
    private String onward = "";

    // ------------------------------------------------------------------ reading

    /** The events from this frame's path, the nav readout and the contracts held. */
    void read(HudData d, OrbitPath p, TrafficFrame t) {
        pool.addAll(events);
        events.clear();
        NavReadout n = d.navReadout;
        onward = n.onwardBody == null ? "" : n.onwardBody;
        if (p != null) {
            for (int i = 0; i < p.nodeIn.size && events.size < MAX; i++) {
                Event e = add(Kind.BURN, "BURN " + (i + 1), p.nodeIn.get(i));
                if (e == null) continue;
                e.burn = i;
                if (2 * i + 1 < p.nodes.size) place(e, p.originX + p.nodes.get(2 * i), p.originY + p.nodes.get(2 * i + 1));
            }
            for (PathMark m : p.marks) {
                if (events.size >= MAX) break;
                Event e = mark(m);
                if (e != null) place(e, m.x, m.y);
            }
        }
        if (!Float.isNaN(n.arrivalIn)) {
            Event e = add(Kind.ARRIVAL, "ARRIVAL", n.arrivalIn);
            if (e != null && p != null) {
                for (MapMarker m : p.markers) {
                    if (m.target) {
                        place(e, m.x, m.y);
                        break;
                    }
                }
            }
        }
        int deadlines = 0;
        for (Objective o : d.objectiveFrame.objectives) {
            if (deadlines >= MAX_DEADLINES || events.size >= MAX) break;
            if (o.state != State.ACTIVE && o.state != State.RETURN || Double.isNaN(o.timeLeft) || o.timeLeft <= 0) continue;
            Event e = add(Kind.DEADLINE, "DEADLINE", (float) o.timeLeft);
            if (e == null) continue;
            deadlines++;
            placeAt(e, o.next, p, t);
        }
        events.sort((a, b) -> Float.compare(a.in, b.in));
    }

    /** A mark on the path as an event: the capture at the onward body's periapsis, or null for none (a ghost, one past). */
    private Event mark(PathMark m) {
        if (Float.isNaN(m.in) || m.in <= 0f) return null;
        String body = m.body == null ? "" : m.body.toUpperCase(Locale.ROOT);
        switch (m.kind) {
            case PERIAPSIS:
            case APOAPSIS: {
                boolean pe = m.kind == MarkKind.PERIAPSIS;
                if (pe && !onward.isEmpty() && onward.equalsIgnoreCase(m.body)) {
                    Event e = add(Kind.CAPTURE, "CAPTURE", m.in);
                    if (e != null) e.estimate = true;
                    return e;
                }
                return add(Kind.APSIS, (body.isEmpty() ? "" : body + " ") + (pe ? "PE" : "AP"), m.in);
            }
            case SOI_ENTRY:
                return add(Kind.SOI, (body.isEmpty() ? "" : body + " ") + "SOI", m.in);
            case SOI_EXIT:
                return add(Kind.SOI, "LEAVES " + (body.isEmpty() ? "SOI" : body), m.in);
            case IMPACT:
                return add(Kind.IMPACT, "IMPACT", m.in);
            default:
                return null;
        }
    }

    private Event add(Kind kind, String name, float in) {
        if (Float.isNaN(in) || in <= 0f || events.size >= MAX) return null;
        Event e = pool.size > 0 ? pool.pop() : new Event();
        e.kind = kind;
        e.name = name;
        e.in = in;
        e.estimate = false;
        e.placed = false;
        e.burn = -1;
        e.when = "T-" + PlotEvents.timeText(in);
        events.add(e);
        return e;
    }

    private static void place(Event e, double x, double y) {
        e.placed = true;
        e.x = x;
        e.y = y;
    }

    /** A deadline's place: where its contract sends the ship next, when the map shows it. */
    private static void placeAt(Event e, String name, OrbitPath p, TrafficFrame t) {
        if (name == null || name.isEmpty()) return;
        if (p != null) {
            for (MapMarker m : p.markers) {
                if (name.equalsIgnoreCase(m.name)) {
                    place(e, m.x, m.y);
                    return;
                }
            }
        }
        if (t != null && p != null && t.frameName.equalsIgnoreCase(p.bodyName)) {
            for (Contact c : t.contacts) {
                if (name.equalsIgnoreCase(c.name)) {
                    place(e, c.x, c.y);
                    return;
                }
            }
        }
    }

    /** The first event after {@code in} seconds that the path itself has (a burn, a crossing, an apsis, an impact), or null. */
    Event after(float in) {
        for (Event e : events) {
            if (e.in <= in + 0.5f) continue;
            if (e.kind == Kind.ARRIVAL || e.kind == Kind.DEADLINE) continue;
            return e;
        }
        return null;
    }

    /** The capture's event, or null. */
    Event capture() {
        for (Event e : events) if (e.kind == Kind.CAPTURE) return e;
        return null;
    }

    /** Seconds as the map's times read: "42 S", "12:05", "3 H 05 M", "2.4 D". */
    static String timeText(double seconds) {
        if (Double.isNaN(seconds) || seconds < 0) return "";
        long s = (long) Math.ceil(seconds - 1e-6);
        if (s < 60) return s + " S";
        if (s < 3600) return Fmt.mmss(s);
        if (s < 86_400) return String.format(Locale.ROOT, "%d H %02d M", s / 3600, s % 3600 / 60);
        return Fmt.fit(s / 86_400.0, 3, 1) + " D";
    }
}
