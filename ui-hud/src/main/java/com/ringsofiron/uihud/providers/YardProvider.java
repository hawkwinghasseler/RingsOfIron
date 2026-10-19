package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.Array;

/**
 * A station's yard for the yard window: whether the player's ship is docked where it can be worked
 * on, what is damaged and what the repairs cost, the ship's hardpoints and the weapons on offer.
 * Implemented in the game wiring, so ui-hud does not depend on the station or weapon modules.
 * Called on the render thread.
 */
public interface YardProvider {

    /** Fills {@code out} (already cleared). Leave {@link YardBoard#stationName} empty when no yard can work on the ship. */
    void collect(YardBoard out);

    /** Orders every repair; returns what the yard says. */
    String orderRepairs();

    /** Orders {@code weaponId}, new or used, fitted to hardpoint {@code hardpoint}; returns what the yard says. */
    String orderRefit(int hardpoint, String weaponId, boolean used);

    final class Offer {
        public String weaponId = "";
        public String name = "";
        /** Credits new; a used one costs {@link YardBoard#usedPrice} of it. */
        public double price;
    }

    final class Hardpoint {
        public int index;
        /** Where it faces: "the nose", "port"... */
        public String name = "";
        /** The weapon on it now. */
        public String weapon = "";
        /** The refit under way there, or empty. */
        public String progress = "";
    }

    final class YardBoard {
        /** Station whose yard can work on the ship; empty when none. */
        public String stationName = "";
        public double credits;
        public int damaged;
        public double repairCost;
        /** Repairs ordered, and how they are going. */
        public String repairs = "";
        /** A used part's share of the new price. */
        public double usedPrice = 0.5;
        public final Array<Hardpoint> hardpoints = new Array<>();
        public final Array<Offer> offers = new Array<>();

        public Hardpoint addHardpoint() {
            Hardpoint h = new Hardpoint();
            hardpoints.add(h);
            return h;
        }

        public Offer addOffer() {
            Offer o = new Offer();
            offers.add(o);
            return o;
        }

        public void clear() {
            stationName = "";
            repairs = "";
            credits = repairCost = 0;
            damaged = 0;
            hardpoints.clear();
            offers.clear();
        }

        public boolean isOpen() {
            return !stationName.isEmpty();
        }
    }
}
