package com.ringsofiron.macrosim.fleet;

import com.badlogic.ashley.core.Component;
import com.ringsofiron.macrosim.economy.CommodityType;

/** An NPC hauler that takes trade orders. References are UUID strings, as the save format requires. */
public class FreighterComponent implements Component {
    public enum State {
        /** Docked and waiting for an order; rides its station's orbit. */
        IDLE,
        /** Flying empty to pick up an order's cargo. */
        TO_PICKUP,
        /** Flying loaded to deliver. */
        TO_DELIVERY,
        /** Flying back to a station without cargo, e.g. after being pulled into the bubble. */
        REPOSITIONING,
        /** Inside the player bubble: rails plan suspended, flown by the in-bubble pilot. */
        IN_BUBBLE
    }

    public String displayName;
    public State state = State.IDLE;
    /** State to resume after leaving the bubble. */
    public State resumeState = State.IDLE;
    public String dockedAtUuid;
    public String destinationUuid;
    /**
     * Cargo hold volume, m³, for one commodity at a time. The game sizes it from the bays of the
     * freighter's chassis; see {@link #capacityUnits}.
     */
    public double capacity = 400;
    public String cargoCommodity;
    public double cargoQuantity;
    public double credits;
    public long orderId;
    public double arrivalTime = Double.NaN;
    public transient long arrivalEventId;
    public int tripsCompleted;
    public double deltaVUsed;
    /** Distance between where the plan put the ship and the station at the last arrival, in meters. */
    public double lastArrivalMiss;

    public FreighterComponent() {
    }

    /** Units of a commodity the hold carries. */
    public double capacityUnits(CommodityType commodity) {
        return commodity.unitVolume > 0 ? capacity / commodity.unitVolume : capacity;
    }

    public FreighterComponent(String displayName, double capacity, double credits) {
        this.displayName = displayName;
        this.capacity = capacity;
        this.credits = credits;
    }
}
