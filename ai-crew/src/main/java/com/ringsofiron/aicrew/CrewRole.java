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
    /** Rescues and treats the wounded; others treat slower ({@link CrewMemberComponent#careRate}). */
    MEDIC,
    CREWMAN,
    /** Station hand: carries cargo between the station's bays and docked craft. */
    LOADER,
    /** Station yard hand: repairs docked craft and installs refit parts, and keeps the station running. */
    TECHNICIAN;

    /**
     * How much this role wants {@code task}: {@link #affinity(CrewTask.Kind)}, except that which
     * console a crew member mans depends on the console: marines take the weapons console, then
     * the guns, crewmen the helm first, the weapons console after and a gun last. Nobody else
     * mans a gun.
     */
    public float affinity(CrewTask task) {
        if (!(task instanceof ManConsoleTask)) return affinity(task.kind);
        SubsystemComponent.SubsystemType type = ((ManConsoleTask) task).type();
        boolean weapons = type == SubsystemComponent.SubsystemType.WEAPONS;
        boolean gun = type == SubsystemComponent.SubsystemType.TURRET_MOUNT;
        switch (this) {
            case MARINE: return weapons ? 1.5f : gun ? 1.3f : 0f;
            case CREWMAN: return weapons ? 1f : gun ? 0.9f : 1.2f;
            default: return gun ? 0f : affinity(task.kind);
        }
    }

    /**
     * How much this role wants a task of the given kind: 0 means never, 1 is neutral and
     * above 1 is a specialty. Multiplies the task's priority when choosing.
     */
    public float affinity(CrewTask.Kind kind) {
        // Everyone abandons ship
        if (kind == CrewTask.Kind.EVACUATE) return 1f;
        // Anyone stops a bleed: as keen as they are to put out a fire, a medic keener
        if (kind == CrewTask.Kind.STABILISE) return this == MEDIC ? 1.5f : Math.max(1f, affinity(CrewTask.Kind.EXTINGUISH));
        // Anyone fetches a shipmate adrift as they would carry one out of a fire, an engineer, who works outside, keenest
        if (kind == CrewTask.Kind.RETRIEVE) return this == ENGINEER ? 1.5f : affinity(CrewTask.Kind.RESCUE);
        switch (this) {
            case ENGINEER:
                switch (kind) {
                    case SEAL_BREACH: case PATCH_LEAK: case REPAIR: case REPAIR_RCS: case RESET_SHORT: case ISOLATE_TANK: return 1.5f;
                    case PATCH_HULL: case FREE_COLLAR: case MOUNT_PART: return 1.5f;
                    case EXTINGUISH: case FORCE_DOOR: case YARD: return 1.2f;
                    case RESCUE: return 0.8f;
                    case CARRY: case TREAT: return 0.5f;
                    default: return 0f;
                }
            case MARINE:
                switch (kind) {
                    case RESCUE: return 1.2f;
                    case EXTINGUISH: return 0.8f;
                    case MAN_CONSOLE: case FORCE_DOOR: return 1.5f;
                    case BOARD: return 2f;
                    case CARRY: case TREAT: return 0.5f;
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
                    case TREAT: return 0.5f;
                    default: return 0f;
                }
            case TECHNICIAN:
                switch (kind) {
                    case YARD: return 1.5f;
                    case REPAIR: case REPAIR_RCS: case SEAL_BREACH: case PATCH_LEAK: case RESET_SHORT: case ISOLATE_TANK: return 1.2f;
                    case PATCH_HULL: case FREE_COLLAR: case MOUNT_PART: return 1.2f;
                    case EXTINGUISH: case RESCUE: return 0.8f;
                    case TREAT: return 0.5f;
                    default: return 0f;
                }
            default:
                switch (kind) {
                    case EXTINGUISH: case RESCUE: case MAN_CONSOLE: case FORCE_DOOR: return 1f;
                    case CARRY: case BOARD: case TREAT: return 0.5f;
                    case REPAIR: case SEAL_BREACH: case PATCH_LEAK: case RESET_SHORT: case ISOLATE_TANK: return 0.5f;
                    // EVA is engineers' work: a crewman stays at the helm
                    case REPAIR_RCS: case PATCH_HULL: case FREE_COLLAR: case MOUNT_PART: return 0f;
                    default: return 0f;
                }
        }
    }
}
