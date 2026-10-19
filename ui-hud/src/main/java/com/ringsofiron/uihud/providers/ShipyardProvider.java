package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.Array;

/**
 * The shipyard window: ships on sale at the station the player's ship is docked at, with what each
 * costs after the trade-in of the ship they fly, and the prize in tow when a station's tugs can take
 * it in. Implemented in the game wiring, so ui-hud does not depend on the factions or the economy.
 * Called on the render thread.
 */
public interface ShipyardProvider {

    /** Fills {@code out} (already cleared). Leave it empty when there is nothing to show. */
    void collect(Yard out);

    /** Buys ship {@code id}; returns what the yard says. */
    String buy(String id);

    /** Sells the prize in tow; returns what the station says. */
    String sellPrize();

    /** Turns the prize in tow over to the sponsor for standing; returns what the station says. */
    String turnInPrize();

    final class Ship {
        public String id = "";
        /** "Gunship" */
        public String name = "";
        /** Who sells it. */
        public String seller = "";
        /** "360,000 cr, 207,000 cr after the trade-in" */
        public String terms = "";
        /** Why it can't be bought yet ("Needs rank Commander"), or empty. */
        public String locked = "";
    }

    final class Yard {
        /** Station whose yard is open; empty when not docked at one that sells ships. */
        public String stationName = "";
        /** The ship the player flies and its trade-in, one line. */
        public String current = "";
        public final Array<Ship> ships = new Array<>();
        /** The prize in tow, one line; empty when there is none to hand over. */
        public String prize = "";
        /** Why the prize can't be sold or turned in, or empty. */
        public String sellLocked = "", turnInLocked = "";
        /** What selling it pays and what turning it in gives: "+53,250 cr", "+6 standing". */
        public String sellTerms = "", turnInTerms = "";

        public boolean isOpen() {
            return !stationName.isEmpty() || !prize.isEmpty();
        }

        public void clear() {
            stationName = "";
            current = "";
            ships.clear();
            prize = "";
            sellLocked = turnInLocked = sellTerms = turnInTerms = "";
        }
    }
}
