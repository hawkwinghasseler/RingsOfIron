package com.hawkwing.RingsOfIron.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The buttons a scenario puts in the debug menu, in the order it added them. */
public final class DebugMenuActions {
    public static final class Action {
        public final String name;
        public final String description;
        public final Runnable run;

        Action(String name, String description, Runnable run) {
            this.name = name;
            this.description = description;
            this.run = run;
        }

        @Override
        public String toString() {
            return name + ": " + description;
        }
    }

    private final List<Action> actions = new ArrayList<>();
    private int revision;

    /** Adds a button, or replaces the one with the same name. */
    public void add(String name, String description, Runnable run) {
        actions.removeIf(a -> a.name.equals(name));
        actions.add(new Action(name, description, run));
        revision++;
    }

    public List<Action> all() {
        return Collections.unmodifiableList(actions);
    }

    /** The action named so, ignoring case, or null. */
    public Action find(String name) {
        for (Action a : actions) {
            if (a.name.equalsIgnoreCase(name)) return a;
        }
        return null;
    }

    /** Goes up each time the list changes, so the menu knows to rebuild. */
    public int revision() {
        return revision;
    }
}
