package com.ringsofiron.aicrew;

import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * A crew member's job. The role sets which board tasks they pick up and how much they
 * want each one (see {@link #affinity(CrewTask.Kind)}), and how well they fight.
 */
public enum CrewRole {
    ENGINEER,
    MARINE,
    MEDIC,
    CREWMAN;

    /**
     * How much this role wants a task of the given kind: 0 means never, 1 is neutral and
     * above 1 is a specialty. Multiplies the task's priority when choosing.
     */
    public float affinity(CrewTask.Kind kind) {
        switch (this) {
            case ENGINEER:
                switch (kind) {
                    case SEAL_BREACH: case REPAIR: return 1.5f;
                    case EXTINGUISH: return 1.2f;
                    case RESCUE: return 0.8f;
                    default: return 0f;
                }
            case MARINE:
                switch (kind) {
                    case RESCUE: return 1.2f;
                    case EXTINGUISH: return 0.8f;
                    default: return 0f;
                }
            case MEDIC:
                switch (kind) {
                    case RESCUE: case TREAT: return 1.5f;
                    case EXTINGUISH: return 0.6f;
                    default: return 0f;
                }
            default:
                switch (kind) {
                    case EXTINGUISH: case RESCUE: case MAN_CONSOLE: return 1f;
                    case REPAIR: case SEAL_BREACH: return 0.5f;
                    default: return 0f;
                }
        }
    }
}
