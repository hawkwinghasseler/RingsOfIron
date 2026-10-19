package com.hawkwing.RingsOfIron.game;

/**
 * One job off a station's mission board (GDD section 4), offered or taken. Plain fields, so the
 * {@link ContractBoard} saves it as it is; craft are referenced by uuid.
 *
 * <p>What the player has to do depends on the {@link Type}: see each one. Every contract has a
 * faction that gives it, credits and standing it pays on success, and a deadline in sim seconds.
 * Failing or abandoning one costs {@link ContractBoard#FAIL_SHARE} of the standing it would have earned.
 */
public class Contract {
    public enum Type {
        /** Carry {@link #quantity} of {@link #commodity} to {@link #targetUuid}, from one of the economy's trade orders. */
        DELIVERY,
        /** Stay with the freighter {@link #targetUuid} until it docks at {@link #destinationUuid}. */
        ESCORT,
        /** Disable or destroy the ship {@link #targetUuid}. */
        BOUNTY,
        /** Pass close by {@link #targetUuid}, then come back to {@link #destinationUuid}. */
        PATROL,
        /**
         * Pick up the escape pod {@link #targetUuid} (a {@link Derelicts} id) by a slow close pass,
         * then dock at {@link #destinationUuid} with its {@link #quantity} survivors aboard.
         */
        RESCUE,
        /**
         * Hold alongside the wreck {@link #targetUuid} (a {@link Derelicts} id) while the crew carry
         * its {@link #quantity} of {@link #commodity} across, then dock at {@link #destinationUuid} with it.
         */
        RECOVERY,
        /** Stay with each freighter of {@link #targets} until it docks at {@link #destinationUuid}; pay by how many arrive. */
        CONVOY,
        /** Beat the raiders of {@link #targets} by {@link #destinationUuid}; pay by how many are beaten. */
        DEFEND,
        /** Come within sensor range of the pirate {@link #targetUuid}, wherever it lurks. */
        RECON,
        /** An operation from the data ({@link #operationId}): taking it starts its first step. */
        OPERATION
    }

    public enum Status { OFFERED, ACTIVE, DONE, FAILED, ABANDONED, CANCELLED }

    public long id;
    public Type type;
    public Status status = Status.OFFERED;
    /** Id of the faction that gives the job. */
    public String giver;
    /** Uuid and name of the station whose board offered it. */
    public String stationUuid;
    public String stationName;
    /** 0 is open to anyone; higher needs that rank with the sponsor, or standing with another giver. */
    public int tier;
    /** Credits paid on success, on top of the sale for a delivery. */
    public double reward;
    /** Standing with the giver gained on success. */
    public float standing;
    /** Sim seconds it must be done by. */
    public double deadline;
    /** The craft the job is about: a delivery's or patrol's station, the escorted freighter, the bounty's ship. */
    public String targetUuid;
    public String targetName;
    /** Where an escort ends, or where a patrol comes back to. */
    public String destinationUuid;
    public String destinationName;
    /** Delivery: the goods, the units still to hand over, and the price the giver pays for them. */
    public String commodity;
    public String commodityName;
    public double quantity;
    public double delivered;
    public double unitPrice;
    /** Delivery: the economy's trade order the job took over. */
    public long orderId;
    /** Escort: trips the freighter had made when the job was taken. Patrol: 1 once the far end is passed. */
    public int progress;
    /** Escort: sim second the freighter docked, while waiting to see the player arrive; negative before. */
    public double arrivedAt = -1;
    /** Why it ended, for the screen. */
    public String outcome = "";
    /** Convoy and defend: the freighters or raiders the job is about, by uuid, and their names. */
    public String[] targets = new String[0];
    public String[] targetNames = new String[0];
    /**
     * Convoy and defend: what became of each of {@link #targets}, one of the {@code MARK_} values;
     * a convoy's freighter also keeps its trips at the start in {@link #trips}.
     */
    public int[] marks = new int[0];
    public int[] trips = new int[0];
    /** Convoy: sim second each freighter was first seen docked, negative before. */
    public double[] arrivals = new double[0];
    /** Operation offer: the {@code [operation.<id>]} it starts. A step of a running one: its def id too. */
    public String operationId;
    /** The operation's name, on its offer and on each of its steps. */
    public String operationName;
    /** A step of a running operation: its number, from 1, and how many the operation has; 0 for a plain contract. */
    public int step;
    public int steps;
    /** What the target has done that the job is about, for a bounty ("robbed 2 freighters"); empty if nothing. */
    public String record = "";
    /** Set by a world event: why the job is on the board ("Highport is short of Water"). */
    public String reason = "";

    /** Not settled yet. */
    public static final int MARK_OPEN = 0;
    /** Settled well: a convoy freighter that docked with the player close, a raider beaten. */
    public static final int MARK_GOOD = 1;
    /** Settled badly: a freighter lost or docked without the player, a raider gone unbeaten. */
    public static final int MARK_BAD = 2;

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    /** "Deliver 120 Water to Highport", the job in a line. */
    public String title() {
        switch (type) {
            case DELIVERY:
                return String.format(java.util.Locale.ROOT, "Deliver %.0f %s to %s", quantity - delivered,
                        commodityName, targetName);
            case ESCORT:
                return "Escort " + targetName + " to " + destinationName;
            case BOUNTY:
                return "Bounty: " + targetName + (record == null || record.isEmpty() ? "" : " (" + record + ")");
            case PATROL:
                return progress == 0 ? "Patrol out to " + targetName + " and back"
                        : "Patrol: back to " + destinationName;
            case RESCUE:
                return progress == 0 ? String.format(java.util.Locale.ROOT, "Rescue %.0f from %s", quantity, targetName)
                        : String.format(java.util.Locale.ROOT, "Bring %.0f survivors to %s", quantity, destinationName);
            case RECOVERY:
                return progress == 0 ? String.format(java.util.Locale.ROOT, "Recover %.0f %s from %s", quantity,
                        commodityName, targetName)
                        : String.format(java.util.Locale.ROOT, "Bring %.0f %s to %s", quantity, commodityName, destinationName);
            case CONVOY:
                return "Convoy of " + targets.length + " to " + destinationName;
            case DEFEND:
                return "Defend " + destinationName + " from " + targets.length + " raider" + (targets.length == 1 ? "" : "s");
            case RECON:
                return "Find " + targetName;
            default:
                return "Operation: " + operationName;
        }
    }

    /** The title with the operation step in front ("Clear the Lanes 2/3: Find ..."), as the HUD lists it. */
    public String heading() {
        return step > 0 ? String.format(java.util.Locale.ROOT, "%s %d/%d: %s", operationName, step, steps, title())
                : title();
    }

    /**
     * Uuid of the craft or derelict to head for now: the target, or once past it (a patrol's far end,
     * a pod or wreck picked up) the way home; a convoy's or defence's first unsettled target.
     */
    public String waypointUuid() {
        switch (type) {
            case PATROL:
            case RESCUE:
            case RECOVERY:
                return progress > 0 ? destinationUuid : targetUuid;
            case CONVOY:
            case DEFEND:
                for (int i = 0; i < targets.length; i++) if (marks[i] == MARK_OPEN) return targets[i];
                return destinationUuid;
            default:
                return targetUuid;
        }
    }

    public String waypointName() {
        switch (type) {
            case PATROL:
            case RESCUE:
            case RECOVERY:
                return progress > 0 ? destinationName : targetName;
            case CONVOY:
            case DEFEND:
                for (int i = 0; i < targets.length; i++) if (marks[i] == MARK_OPEN) return targetNames[i];
                return destinationName;
            default:
                return targetName;
        }
    }

    /** Convoy and defend: how many of {@link #targets} are marked {@code mark}. */
    public int count(int mark) {
        int n = 0;
        for (int m : marks) if (m == mark) n++;
        return n;
    }
}
