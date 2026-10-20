package com.ringsofiron.aicrew;

import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * A crew member's job. The role sets which board tasks they pick up and how much they
 * want each one (see {@link #affinity(CrewTask.Kind)}), and how well they fight.
 */
public enum CrewRole {
    /** Repairs aboard; strips salvage off another craft (yard work on it) and lends a hand carrying. */
    ENGINEER,
    /** Fights, mans the guns, forces doors and boards other craft; lends a hand carrying. */
    MARINE,
    MEDIC,
    CREWMAN,
    /** Station hand: carries cargo between the station's bays and docked craft. */
    LOADER,
    /** Station yard hand: repairs docked craft and installs refit parts, and keeps the station running. */
    TECHNICIAN;

    /**
     * How much this role wants {@code task}: {@link #affinity(CrewTask.Kind)}, except that which
     * console a crew member mans depends on the console: marines take the weapons console,
     * crewmen the helm first and the weapons console after.
     */
    public float affinity(CrewTask task) {
        if (!(task instanceof ManConsoleTask)) return affinity(task.kind);
        SubsystemComponent.SubsystemType type = ((ManConsoleTask) task).type();
        boolean weapons = type == SubsystemComponent.SubsystemType.WEAPONS;
        switch (this) {
            case MARINE: return weapons ? 1.5f : 0f;
            case CREWMAN: return weapons ? 1f : 1.2f;
            default: return affinity(task.kind);
        }
    }

    /**
     * How much this role wants a task of the given kind: 0 means never, 1 is neutral and
     * above 1 is a specialty. Multiplies the task's priority when choosing.
     */
    public float affinity(CrewTask.Kind kind) {
        // Everyone abandons ship
        if (kind == CrewTask.Kind.EVACUATE) return 1f;
        switch (this) {
            case ENGINEER:
                switch (kind) {
                    case SEAL_BREACH: case REPAIR: return 1.5f;
                    case EXTINGUISH: case FORCE_DOOR: case YARD: return 1.2f;
                    case RESCUE: return 0.8f;
                    case CARRY: return 0.5f;
                    default: return 0f;
                }
            case MARINE:
                switch (kind) {
                    case RESCUE: return 1.2f;
                    case EXTINGUISH: return 0.8f;
                    case MAN_CONSOLE: case FORCE_DOOR: return 1.5f;
                    case BOARD: return 2f;
                    case CARRY: return 0.5f;
                    default: return 0f;
                }
            case MEDIC:
                switch (kind) {
                    case RESCUE: case TREAT: return 1.5f;
                    case EXTINGUISH: return 0.6f;
                    default: return 0f;
                }
            case LOADER:
                switch (kind) {
                    case CARRY: return 1.5f;
                    case EXTINGUISH: case RESCUE: return 0.8f;
                    default: return 0f;
                }
            case TECHNICIAN:
                switch (kind) {
                    case YARD: return 1.5f;
                    case REPAIR: case SEAL_BREACH: return 1.2f;
                    case EXTINGUISH: case RESCUE: return 0.8f;
                    default: return 0f;
                }
            default:
                switch (kind) {
                    case EXTINGUISH: case RESCUE: case MAN_CONSOLE: case FORCE_DOOR: return 1f;
                    case CARRY: case BOARD: return 0.5f;
                    case REPAIR: case SEAL_BREACH: return 0.5f;
                    default: return 0f;
                }
        }
    }
}
