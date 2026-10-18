package com.ringsofiron.orbital.events;

/** Something that happens to a rails body at a known sim time. */
public class OrbitalEvent implements Comparable<OrbitalEvent> {
    public enum Type {
        /** Next node of the body's {@code ManeuverNodeComponent} is due. */
        MANEUVER,
        /** The body leaves its parent's sphere of influence and is re-parented to the grandparent. */
        SOI_EXIT,
        /** The body enters the SOI of {@link #targetUuid}, a child of its current parent. */
        SOI_ENTRY,
        /** The body reaches its parent's surface. */
        IMPACT,
        /** End of the prediction window: look further ahead. */
        REPREDICT
    }

    public final Type type;
    /** Sim seconds. */
    public final double time;
    public final String entityUuid;
    /** For SOI_ENTRY: the body being entered. */
    public final String targetUuid;
    /** {@code OrbitComponent.version} the prediction was made for; -1 for maneuvers (never stale). */
    public final int orbitVersion;

    public OrbitalEvent(Type type, double time, String entityUuid, String targetUuid, int orbitVersion) {
        this.type = type;
        this.time = time;
        this.entityUuid = entityUuid;
        this.targetUuid = targetUuid;
        this.orbitVersion = orbitVersion;
    }

    /** True for events that only happen if the orbit is unchanged since they were predicted. */
    public boolean isPrediction() {
        return type != Type.MANEUVER;
    }

    @Override
    public int compareTo(OrbitalEvent o) {
        return Double.compare(time, o.time);
    }

    @Override
    public String toString() {
        return type + "@" + String.format("%.3f", time) + " " + entityUuid + (targetUuid != null ? " -> " + targetUuid : "");
    }
}
