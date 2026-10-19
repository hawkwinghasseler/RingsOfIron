package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;

/**
 * Marks a ship that can no longer fight or fly under its own command, and why (GDD section 5:
 * ships are disabled or abandoned rather than destroyed). Set and cleared by
 * {@link ShipDisablement}; saved with the ship.
 *
 * <p>A ship disabled for want of power, crew or hull comes back if what it lacked returns (a
 * reactor switched back on, a crew member waking). A surrendered, abandoned or wrecked ship stays
 * that way: boarding and salvage treat those as theirs to take.
 */
public class DisabledComponent implements Component {

    public enum Reason {
        /** No reactor running. */
        NO_POWER("reactor down"),
        /** Crew aboard, none of them conscious. */
        NO_CREW("crew down"),
        /** Hull integrity below {@link ShipDisablement#DISABLED_HULL}. */
        HULL("hull broken"),
        /** An NPC ship that cut thrust and gave up the fight. */
        SURRENDERED("surrendered"),
        /** Its crew left in the escape pods. */
        ABANDONED("abandoned"),
        /** Broken past {@link ShipDisablement#WRECK_HULL}: a wreck to salvage, never to fly again. */
        WRECKED("wreck");

        /** A few words for the HUD. */
        public final String label;

        Reason(String label) {
            this.label = label;
        }

        /** True for the reasons a ship never recovers from on its own. */
        public boolean sticky() {
            return this == SURRENDERED || this == ABANDONED || this == WRECKED;
        }
    }

    public Reason reason = Reason.NO_POWER;
    /** Sim seconds when the ship was first disabled. */
    public double since;
    /** The ship's HUD label from before, to put back if it recovers; null if it had no marker. */
    public String markerLabel;

    public DisabledComponent() {}

    public DisabledComponent(Reason reason, double since) {
        this.reason = reason;
        this.since = since;
    }

    public boolean isWreck() {
        return reason == Reason.WRECKED;
    }
}
