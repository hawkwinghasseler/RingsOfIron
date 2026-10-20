package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.Array;

/**
 * Boarding and salvage for the boarding window: the ship the player's is latched to, how its
 * boarding stands, and once it is taken, what can be stripped off it and the cargo aboard.
 * Implemented in the game wiring, so ui-hud does not depend on the crew or docking modules.
 * Called on the render thread.
 */
public interface BoardingProvider {

    /** Fills {@code out} (already cleared). Leave {@link BoardingBoard#craftName} empty when not latched to another ship. */
    void collect(BoardingBoard out);

    /** Sends the boarding party across; returns what was ordered. */
    String orderBoarding();

    /** Calls the boarding party back; returns what was done. */
    String recall();

    /** Marks the system whose mount starts at (x, y) to be stripped, or unmarks it; returns what was ordered. */
    String toggleStrip(int x, int y);

    /** Has every unit of {@code commodity} aboard the other ship carried over; returns what was ordered. */
    String takeCargo(String commodity);

    final class Part {
        /** Its mount's origin on the other ship. */
        public int x, y;
        public String name = "";
        /** Credits a station pays for it once stripped. */
        public double value;
        /** "", "marked", "stripping 40%", "carrying it home"... */
        public String status = "";
        public boolean marked;
    }

    final class Cargo {
        public String commodity = "";
        public double units;
        /** Units still being carried over. */
        public double onOrder;
    }

    final class BoardingBoard {
        /** The ship latched to; empty when none. */
        public String craftName = "";
        /** How it stands: disabled and why, being boarded, taken. */
        public String state = "";
        /** Why no boarding party can go now, or empty if one can. */
        public String boardRefusal = "";
        /** A boarding party is on its way or aboard. */
        public boolean boarding;
        /** Taken: its parts and cargo are the player's to strip. */
        public boolean salvage;
        /** Why it can't be stripped yet, while it can't. */
        public String salvageRefusal = "";
        public final Array<Part> parts = new Array<>();
        public final Array<Cargo> cargo = new Array<>();

        public Part addPart() {
            Part p = new Part();
            parts.add(p);
            return p;
        }

        public Cargo addCargo() {
            Cargo c = new Cargo();
            cargo.add(c);
            return c;
        }

        public void clear() {
            craftName = state = boardRefusal = salvageRefusal = "";
            boarding = salvage = false;
            parts.clear();
            cargo.clear();
        }

        public boolean isOpen() {
            return !craftName.isEmpty();
        }
    }
}
