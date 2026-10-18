package com.ringsofiron.macrosim;

import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Sim-time priority queue for macro events (freighter arrivals, economy passes). Rails events
 * (burns, SOI changes) live in the rails model's own queue; {@link MacroSimulation} interleaves the two.
 */
public class MacroEventQueue {

    private static final class Event implements Comparable<Event> {
        final double time;
        final long id;
        final Runnable action;

        Event(double time, long id, Runnable action) {
            this.time = time;
            this.id = id;
            this.action = action;
        }

        @Override
        public int compareTo(Event o) {
            int c = Double.compare(time, o.time);
            return c != 0 ? c : Long.compare(id, o.id);
        }
    }

    private final PriorityQueue<Event> queue = new PriorityQueue<>();
    private final Set<Long> cancelled = new HashSet<>();
    private long nextId = 1;

    /** Queues {@code action} at sim second {@code time} and returns a handle for {@link #cancel}. */
    public long schedule(double time, Runnable action) {
        long id = nextId++;
        queue.add(new Event(time, id, action));
        return id;
    }

    public void cancel(long id) {
        if (id > 0) cancelled.add(id);
    }

    /** Time of the next live event, or {@code +inf}. */
    public double nextTime() {
        skipCancelled();
        return queue.isEmpty() ? Double.POSITIVE_INFINITY : queue.peek().time;
    }

    /** Runs the next event if it is due at or before {@code t}. Returns false if none was. */
    public boolean runNext(double t) {
        skipCancelled();
        if (queue.isEmpty() || queue.peek().time > t) return false;
        queue.poll().action.run();
        return true;
    }

    public int size() {
        return queue.size() - cancelled.size();
    }

    private void skipCancelled() {
        while (!queue.isEmpty() && cancelled.remove(queue.peek().id)) queue.poll();
    }
}
