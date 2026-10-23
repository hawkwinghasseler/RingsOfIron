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

    /** Fills the ship's tanks, as far as its credits go; returns what the yard says. */
    default String orderRefill() {
        return "This yard doesn't refill tanks";
    }

    /**
     * Opens the ship editor on the ship's layout, for the yard to rebuild its decks to. Returns null
     * when the editor opened, or what the yard says instead.
     */
    default String openLayoutEditor() {
        return "This yard doesn't rebuild decks";
    }

    /** Orders {@code weaponId}, new or used, fitted to hardpoint {@code hardpoint}; returns what the yard says. */
    String orderRefit(int hardpoint, String weaponId, boolean used);

    /** Orders a salvaged {@code weaponId} out of the ship's own hold fitted to hardpoint {@code hardpoint}; returns what the yard says. */
    default String orderSalvagedRefit(int hardpoint, String weaponId) {
        return "This yard doesn't fit salvaged parts";
    }

    /**
     * Starts putting a new {@code furnishingId} on the ship's deck: the game shows it on the deck under
     * the pointer, to turn and click down where it should go. Returns null once placing has begun, or
     * what the yard says instead.
     */
    default String placeFurnishing(String furnishingId) {
        return "This yard doesn't sell furnishings";
    }

    /** Starts moving the furnishings aboard about, or handing them back to the yard. Null once begun, or what the yard says. */
    default String arrangeFurnishings() {
        return "This yard doesn't move furnishings";
    }

    /** A piece of furniture or decor on sale. */
    final class Furnishing {
        public String id = "";
        public String name = "";
        /** What the crew do with it: "lounge", "game", "decor"... */
        public String use = "";
        public double price;
    }

    final class Offer {
        public String weaponId = "";
        public String name = "";
        /** Salvaged ones in the ship's hold, fitted for nothing. */
        public int salvaged;
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
        /** Credits to fill every tank aboard; 0 when they are full. */
        public double refillCost;
        /** How full the ship's tanks are, e.g. "Propellant 40%, oxygen 100%"; empty with no tanks. */
        public String tanks = "";
        /** Repairs ordered, and how they are going. */
        public String repairs = "";
        /** A used part's share of the new price. */
        public double usedPrice = 0.5;
        public final Array<Hardpoint> hardpoints = new Array<>();
        public final Array<Offer> offers = new Array<>();
        /** Furniture and decor on sale. */
        public final Array<Furnishing> furnishings = new Array<>();
        /** Furnishings aboard and on their way, e.g. "4 aboard, 1 on its way"; empty when none. */
        public String furnished = "";

        public Furnishing addFurnishing() {
            Furnishing f = new Furnishing();
            furnishings.add(f);
            return f;
        }

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
            credits = repairCost = refillCost = 0;
            tanks = "";
            damaged = 0;
            hardpoints.clear();
            offers.clear();
            furnishings.clear();
            furnished = "";
        }

        public boolean isOpen() {
            return !stationName.isEmpty();
        }
    }
}
