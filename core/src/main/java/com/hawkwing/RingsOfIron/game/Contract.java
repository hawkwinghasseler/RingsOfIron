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
        PATROL
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
                return "Bounty: " + targetName;
            default:
                return progress == 0 ? "Patrol out to " + targetName + " and back"
                        : "Patrol: back to " + destinationName;
        }
    }

    /** Uuid of the craft to head for now: the target, or a patrol's way home once past the far end. */
    public String waypointUuid() {
        return type == Type.PATROL && progress > 0 ? destinationUuid : targetUuid;
    }

    public String waypointName() {
        return type == Type.PATROL && progress > 0 ? destinationName : targetName;
    }
}
