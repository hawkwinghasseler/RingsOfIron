package com.hawkwing.RingsOfIron.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The scenarios the debug menu offers, in the order it lists them. Each is one line in {@link #BUILT_IN}:
 * <pre>
 *     new Entry("flight", "Flight", "The corvette adrift among asteroids, nothing to slow it", FlightScenario::new),
 * </pre>
 * The id is what {@code scenario("flight")} in the console and {@code --scenario flight} at launch take.
 */
public final class DebugScenarios {
    /** One scenario: how to name it, and how to make a new one for each load. */
    public static final class Entry {
        public final String id;
        public final String name;
        public final String description;
        private final Supplier<? extends DebugScenario> make;

        public Entry(String id, String name, String description, Supplier<? extends DebugScenario> make) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.make = make;
        }

        /** A new instance, to load. */
        public DebugScenario create() {
            return make.get();
        }

        @Override
        public String toString() {
            return id + ": " + description;
        }
    }

    private static final List<Entry> BUILT_IN = List.of(
            new Entry("free-flight", "Free flight", "The career's start without the career: undocked in orbit by"
                    + " Highport, no walkthrough", FreeFlightScenario::new),
            new Entry("crew", "Crew", "On deck as a fire, a reactor hit, a hull breach and boarders come one after"
                    + " another, to watch the crew answer", CrewScenario::new),
            new Entry("interior", "Interior", "The ship alone and pinned, every hull in turn, to hurt with the deck"
                    + " tools and an attacker and watch the wounds", InteriorScenario::new),
            new Entry("audio", "Audio", "Aboard the audio demo ship: hear walls, doors, distance and thin air change"
                    + " each sound; fire, vent, reactor and hull buttons", AudioScenario::new)
    );

    private static final List<Entry> ADDED = new ArrayList<>();

    private DebugScenarios() {
    }

    /** Every scenario: the built-in ones, then any {@link #register}ed at run time (a mod, a test). */
    public static synchronized List<Entry> all() {
        List<Entry> out = new ArrayList<>(BUILT_IN);
        out.addAll(ADDED);
        return Collections.unmodifiableList(out);
    }

    /** Adds a scenario, or replaces the one with the same id. */
    public static synchronized void register(Entry entry) {
        ADDED.removeIf(e -> e.id.equals(entry.id));
        ADDED.add(entry);
    }

    public static synchronized void unregister(String id) {
        ADDED.removeIf(e -> e.id.equals(id));
    }

    /** The scenario with this id, ignoring case, or null. */
    public static Entry find(String id) {
        if (id == null) return null;
        for (Entry e : all()) {
            if (e.id.equalsIgnoreCase(id.trim())) return e;
        }
        return null;
    }
}
