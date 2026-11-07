package com.ringsofiron.uihud.providers;

import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;

/**
 * Where the player is going, as numbers: the nav target and how the ship closes on it, the next
 * planned burn, and the station the ship is docked at. Filled once a frame by the game wiring layer
 * from navigation, the burn plan, the contract board and the station's services.
 */
public interface NavigationProvider {
    /** Fills {@code out}, already cleared. */
    void read(NavReadout out);

    /** What the nav target is. */
    enum Kind {
        STATION, SHIP, WRECK, BODY, POINT
    }

    /** A station, as a destination or as the dock the ship is at. */
    final class StationInfo {
        public boolean present;
        public String name = "";
        /** The faction that runs it ("Free Traders"), or "". */
        public String owner = "";
        /** What kind of station it is ("Ice Mine"), or "". */
        public String type = "";
        /** Whether that faction sponsors the player's career. */
        public boolean sponsor;
        /** The player's standing with it, as the game rates it (−100 to 100), and its word ("Trusted"), or "". */
        public float standing = Float.NaN;
        public String standingName = "";
        /** It would refuse the player clearance; why ("" when it would not). */
        public String refusal = "";
        /** What it offers: a market, a yard (repairs and refits), ships for sale, contracts, crew for hire, an infirmary. */
        public boolean market, yard, shipyard, contracts, hiring, infirmary;

        public void clear() {
            present = false;
            name = "";
            owner = "";
            type = "";
            sponsor = false;
            standing = Float.NaN;
            standingName = "";
            refusal = "";
            market = yard = shipyard = contracts = hiring = infirmary = false;
        }
    }

    final class NavReadout {
        /** A nav target is set. */
        public boolean present;
        public String name = "";
        public Kind kind = Kind.POINT;
        public Relation relation = Relation.UNKNOWN;
        /** Distance, m, and closing speed, m/s (+ closing), at present velocities. NaN unknown. */
        public double distance = Double.NaN;
        public float closing = Float.NaN;
        /** In the bubble with the ship, so it can be flown to by eye; then its bearing from the nose, degrees, port +. */
        public boolean inBubble;
        public float bearingDeg = Float.NaN;
        /** Closest approach along the planned path (or the proposal): distance m, seconds from now, speed m/s. NaN when none is found. */
        public double cpaDistance = Double.NaN;
        public float cpaIn = Float.NaN, cpaSpeed = Float.NaN;
        /** The nav target when it is a station. */
        public final StationInfo station = new StationInfo();
        /** The contract whose waypoint it is ("Food to Shackleton Ice"), or "". */
        public String contract = "";
        /** The body it orbits when that isn't the one the ship orbits ("Moon"), else "". */
        public String around = "";

        /**
         * Burns still to come (the one being flown among them), and the burn's place in the plan: the
         * one being flown while {@link #burning}, else the next (1-based), of how many the plan has had.
         * Flown burns leave the game's plan, so the count of those flown is kept as this HUD saw them
         * go; a load starts it again. All 0 with no plan.
         */
        public int burnsLeft, burnNumber, burnsPlanned;
        /** The next burn still in the plan (after the one being flown, if any): seconds to its centre, and its delta-v, m/s. NaN with none. */
        public float burnIn = Float.NaN, burnDeltaV = Float.NaN;
        /**
         * Seconds until the main engine lights for that next burn (its centre less half its length at
         * full thrust), and until the helm turns onto it (that, less the turn lead). NaN with none.
         */
        public float burnStartIn = Float.NaN, turnIn = Float.NaN;
        /** A burn is being flown now: the engine is lit for it, or about to be with the ship on it. */
        public boolean burning;
        /** Delta-v still to go on the burn being flown, m/s; NaN when none is. */
        public float burnToGo = Float.NaN;
        /**
         * The burn being flown, else the next: seconds it takes (what is left of it) at full thrust;
         * its direction in the bubble's axes, degrees counter-clockwise from +x, 0 to 360; and the
         * turn still needed onto it from the nose, degrees, + counter-clockwise. NaN with none.
         */
        public float burnTime = Float.NaN, burnHeadingDeg = Float.NaN, pointingDeg = Float.NaN;
        /** Delta-v the plan or the proposal needs in all, m/s; 0 with none. */
        public double planDeltaV;
        /** A transfer proposal is up, not yet planned. */
        public boolean proposal;
        /**
         * An accepted trip that goes on past its last burn into another body's sphere of influence, where
         * the game plans the rest (the capture, then on to the target) at the crossing: that body, else "".
         */
        public String onwardBody = "";
        /** The rest's estimated delta-v, m/s; NaN unknown. */
        public float onwardDeltaV = Float.NaN;
        /** Seconds until the trip (or the proposal) meets its target, an estimate past a crossing; NaN unknown. */
        public float arrivalIn = Float.NaN;

        /** The station the ship is docked at. */
        public final StationInfo docked = new StationInfo();

        public void clear() {
            present = false;
            name = "";
            kind = Kind.POINT;
            relation = Relation.UNKNOWN;
            distance = Double.NaN;
            closing = Float.NaN;
            inBubble = false;
            bearingDeg = Float.NaN;
            cpaDistance = Double.NaN;
            cpaIn = cpaSpeed = Float.NaN;
            station.clear();
            contract = "";
            around = "";
            burnsLeft = burnNumber = burnsPlanned = 0;
            burnIn = burnDeltaV = Float.NaN;
            burnStartIn = turnIn = Float.NaN;
            burning = false;
            burnToGo = Float.NaN;
            burnTime = burnHeadingDeg = pointingDeg = Float.NaN;
            planDeltaV = 0;
            proposal = false;
            onwardBody = "";
            onwardDeltaV = arrivalIn = Float.NaN;
            docked.clear();
        }
    }
}
