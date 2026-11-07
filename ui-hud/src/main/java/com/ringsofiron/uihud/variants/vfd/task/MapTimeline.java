package com.ringsofiron.uihud.variants.vfd.task;

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
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Phosphor;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * The map's timeline (functional-task 7, MAP.5; as FILAMENT × Context's M1 has it): a strip of 64 cells from now to the last
 * event on a log scale, every event lit in its cell, under the plot between the panes. The events are
 * the plan's burns, the path's apsides, its changes of sphere of influence and an impact, the capture
 * the game plans at a crossing (an estimate until then: a hollow cell, its label dim), the arrival,
 * and the contracts' deadlines, which light the amber rail under the strip up to them. Each label is
 * right-aligned to its cell, pushed left to clear the one after, or right to clear NOW; the labels
 * that won't fit go, the apsides first. A click on an event centres the map on it; its » warps on to it.
 */
final class MapTimeline {
    static final float H = 90f;
    /** The strip's cells, and where the labels may start (clear of NOW). */
    static final int CELLS = 64;
    static final float LABELS_FROM = 40f, LABEL_GAP = 20f;
    /** How many events it holds, and how many deadlines among them. */
    static final int MAX = 24, MAX_DEADLINES = 3;
    static final String NOTE = "CLICK CENTRES THE MAP ON IT · » WARPS";

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
        /** Laid out: its cell, and its label's left and width (no label when not labelled). */
        int cell;
        boolean labelled;
        float lx, lw;
        String when = "";
    }

    float x, y, w;
    final Array<Event> events = new Array<>();
    private final Array<Event> pool = new Array<>();
    private final Array<Event> byPriority = new Array<>();
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
        e.labelled = false;
        e.when = "T-" + MapPlan.timeText(in);
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

    // ------------------------------------------------------------------ laying out

    /** The strip from {@code x} at {@code y} (the pane's top), {@code w} wide: each event's cell, and the labels that fit. */
    void place(float x, float y, float w) {
        this.x = x;
        this.y = y;
        this.w = w;
        float sx = stripX(), sw = stripW(), cw = sw / CELLS;
        float last = 0f;
        for (Event e : events) last = Math.max(last, e.in);
        double t0 = Math.max(30.0, last / 20.0);
        double span = Math.log(1 + last / t0);
        for (Event e : events) {
            e.cell = span <= 0 ? CELLS - 1 : (int) Math.min(CELLS - 1, Math.round((CELLS - 1) * Math.log(1 + e.in / t0) / span));
            e.labelled = false;
            e.lw = Glass.width(Math.max(e.name.length(), e.when.length() + 2));
        }
        // The labels, the most telling first, each kept only while every one kept still fits
        byPriority.clear();
        byPriority.addAll(events);
        byPriority.sort((a, b) -> a.kind != b.kind ? Integer.compare(a.kind.ordinal(), b.kind.ordinal()) : Float.compare(a.in, b.in));
        for (Event e : byPriority) {
            e.labelled = true;
            if (!fits(sx, sw, cw)) e.labelled = false;
        }
        fits(sx, sw, cw);
    }

    /**
     * Places the labelled events' labels from the latest back, each right-aligned to its cell's right
     * edge, or pushed left to clear the label after it, and one near now pushed right to clear NOW;
     * whether every one still clears the next.
     */
    private boolean fits(float sx, float sw, float cw) {
        float nextLeft = sx + sw + 10f;
        for (int i = events.size - 1; i >= 0; i--) {
            Event e = events.get(i);
            if (!e.labelled) continue;
            float right = Math.min(sx + (e.cell + 1) * cw, nextLeft - LABEL_GAP);
            e.lx = Math.max(sx + LABELS_FROM, right - e.lw);
            if (e.lx + e.lw > nextLeft - LABEL_GAP + 0.01f) return false;
            nextLeft = e.lx;
        }
        return true;
    }

    float stripX() {
        return x + 10f;
    }

    float stripW() {
        return w - 20f;
    }

    /** The strip's top, and its labels' first row. */
    float stripY() {
        return y + 28f;
    }

    float labelY() {
        return stripY() + 22f;
    }

    // ------------------------------------------------------------------ painting

    void paint(Filament f) {
        Glass.pane(f, x, y, w, H, "TIMELINE");
        Glass.dim(f, x + w - 10f - Glass.width(NOTE.length()), y + 9f, NOTE);
        float sx = stripX(), sw = stripW(), sy = stripY(), cw = sw / CELLS;
        for (int i = 0; i < CELLS; i++) {
            f.rectLit(sx + i * cw + 0.6f, sy, cw - 1.2f, 9f, f.o().ghostOnly().k(1.6f));
            f.rectLit(sx + i * cw + 0.6f, sy + 12f, cw - 1.2f, 2.4f, f.o().ghostOnly().color(Phosphor.AMBER).k(2.4f));
        }
        // Now
        f.rectLit(sx + 0.6f, sy, cw - 1.2f, 9f, f.o().norm());
        for (Event e : events) {
            float cx = sx + e.cell * cw;
            if (e.kind == Kind.DEADLINE) f.rectLit(sx, sy + 12f, cx - sx + cw - 1.2f, 2.4f, f.o().color(Phosphor.AMBER).norm());
            else if (e.estimate) f.frameLit(cx + 0.6f, sy, cw - 1.2f, 9f, 1f, f.o().norm());
            else f.rectLit(cx + 0.6f, sy, cw - 1.2f, 9f, f.o().full());
        }
        float ly = labelY();
        Glass.dim(f, sx, ly, "NOW");
        for (Event e : events) {
            if (!e.labelled) continue;
            boolean amber = e.kind == Kind.DEADLINE;
            float level = e.estimate ? Drive.DIM : Drive.NORM;
            if (amber) Glass.text(f, e.lx, ly, e.name, level, Phosphor.AMBER);
            else Glass.text(f, e.lx, ly, e.name, level);
            Glass.dim(f, e.lx, ly + Glass.RH, e.when + " »");
            float cx = sx + e.cell * cw;
            if (e.lx + e.lw < cx - 4f) f.dotLine(e.lx + e.lw + 4f, sy + 26f, cx + cw / 2f, sy + 17f, f.o().size(1f).step(3f).dim());
        }
    }

    // ------------------------------------------------------------------ clicks

    /** Each labelled event's label (centres the map on it) as {@code centre + i}, and its » (warps) as {@code warp + i}. */
    void boxes(FilamentActor a, int centre, int warp) {
        float ly = labelY(), sx = stripX(), cw = stripW() / CELLS, sy = stripY();
        for (int i = 0; i < events.size; i++) {
            Event e = events.get(i);
            // The cell too, for an event whose label had no room
            a.box(centre + i, sx + e.cell * cw - 2f, sy - 4f, cw + 4f, 18f);
            if (!e.labelled) continue;
            a.box(centre + i, e.lx - 2f, ly - 3f, e.lw + 4f, 2f * Glass.RH + 2f);
            float wx = e.lx + Glass.width(e.when.length() + 1);
            a.box(warp + i, wx - 4f, ly + Glass.RH - 4f, Glass.CW + 10f, Glass.RH + 4f);
        }
    }
}
