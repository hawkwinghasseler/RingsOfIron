package com.ringsofiron.macrosim.rails;

import java.util.Collections;
import java.util.List;

/** Immutable copy of the rails bodies at one moment, safe to hand to a background thread. */
public final class RailsSnapshot {

    public static final class Entry {
        public final String uuid;
        public final String parentUuid;
        public final Trajectory trajectory;

        public Entry(String uuid, String parentUuid, Trajectory trajectory) {
            this.uuid = uuid;
            this.parentUuid = parentUuid;
            this.trajectory = trajectory;
        }
    }

    public final double time;
    public final List<Entry> entries;

    public RailsSnapshot(double time, List<Entry> entries) {
        this.time = time;
        this.entries = Collections.unmodifiableList(entries);
    }
}
