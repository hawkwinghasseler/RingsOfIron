package com.ringsofiron.orbital.events;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Every pending rails event, ordered by sim time. Rails code only does work when an event is due.
 *
 * <p>Not saved: maneuver nodes live in {@code ManeuverNodeComponent} and everything else is a
 * prediction recomputed from the elements on load.
 */
public class OrbitalEventQueue {
    private final PriorityQueue<OrbitalEvent> queue = new PriorityQueue<>();

    public void schedule(OrbitalEvent event) {
        if (!Double.isNaN(event.time)) queue.add(event);
    }

    public OrbitalEvent peek() {
        return queue.peek();
    }

    /** Removes and returns the earliest event if it is due at or before {@code t}, else null. */
    public OrbitalEvent pollDue(double t) {
        OrbitalEvent next = queue.peek();
        return next != null && next.time <= t ? queue.poll() : null;
    }

    /** Drops every event for this entity (it left the rails, or its predictions are being redone). */
    public void removeFor(String entityUuid) {
        queue.removeIf(e -> e.entityUuid.equals(entityUuid));
    }

    public void removeFor(String entityUuid, OrbitalEvent.Type type) {
        queue.removeIf(e -> e.type == type && e.entityUuid.equals(entityUuid));
    }

    /** Earliest pending event for this entity, ignoring REPREDICT markers, or null. */
    public OrbitalEvent nextFor(String entityUuid) {
        OrbitalEvent best = null;
        for (OrbitalEvent e : queue) {
            if (e.type == OrbitalEvent.Type.REPREDICT || !e.entityUuid.equals(entityUuid)) continue;
            if (best == null || e.time < best.time) best = e;
        }
        return best;
    }

    public List<OrbitalEvent> eventsFor(String entityUuid) {
        List<OrbitalEvent> out = new ArrayList<>();
        for (OrbitalEvent e : queue) if (e.entityUuid.equals(entityUuid)) out.add(e);
        out.sort(null);
        return out;
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }
}
