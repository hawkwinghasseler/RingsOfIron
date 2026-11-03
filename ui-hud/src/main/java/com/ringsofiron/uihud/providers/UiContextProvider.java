package com.ringsofiron.uihud.providers;

import com.badlogic.ashley.core.Entity;

/**
 * Where the player is and what is going on around them, for HUD variants that show different things
 * by seat and situation (the three functional specs in documentation/ui-overhaul). Filled once a frame
 * by the game wiring layer from the bridge, docking, warp, disablement and combat.
 */
public interface UiContextProvider {
    /** Fills {@code out}, already cleared. */
    void read(UiContext out);

    /** Where the captain is. */
    enum Presence {
        /** No ship and no avatar: the shell, or a career between ships. */
        NONE,
        /** Seated at the helm. */
        HELM,
        /** Seated at the weapons console (fire control, tactical). */
        WEAPONS,
        /** Seated at one gun. */
        GUN,
        /** On foot aboard their own ship. */
        DECK,
        /** On foot aboard another ship or a station: boarding, or docked and walked across. */
        ABOARD_OTHER,
        /** Outside in a suit. */
        EVA
    }

    /** The ship's alert state: green, yellow (hostiles about), red (under fire or in trouble). */
    enum Condition {
        GREEN, YELLOW, RED
    }

    final class UiContext {
        public Presence presence = Presence.NONE;
        /** The player's ship, or null. */
        public Entity ship;
        /** At a gun: the gun's subsystem entity; else null. */
        public Entity seatedGun;
        /** Whether the outside view is up (false: the deck). */
        public boolean exteriorView = true;

        public Condition condition = Condition.GREEN;
        /** Docked at a station, and its name ("" when not docked). */
        public boolean docked;
        public String stationName = "";
        /** Latched to another ship (boarding or towing). */
        public boolean latched;
        /** Time warp above 1x, and whether it is the rails warp. */
        public int warp = 1;
        public boolean railsWarp;
        /** A hostile is tracked and closing, or a missile is inbound. */
        public boolean combat;
        /** Missiles inbound at the player's ship. */
        public int missilesInbound;
        /** Disabled, abandoning, a fire, a breach or a room losing air: the ship needs saving. */
        public boolean emergency;
        /** Why the ship is disabled ("" when it is not). */
        public String disabledReason = "";
        /** Crew are being told to abandon ship. */
        public boolean abandoning;
        /** Crew outside on a spacewalk, so a burn could throw them off. */
        public boolean crewOutside;
        /** The captain's order to hold fire. */
        public boolean holdFire;

        /** Who flies the ship: "you", a crew member's name, "autopilot", or "" for nobody. */
        public String helmsman = "";
        /** Why the helm will not answer ("" when it will). */
        public String helmFault = "";
        /** The helm's standing order as the game words it ("Hold station on Kestrel"), or "". */
        public String helmOrder = "";
        /** A planned burn is being flown now. */
        public boolean burnFlown;
        /** Seconds until warp next drops out by itself (an encounter, a burn, arrival), NaN for none known. */
        public float warpNextStop = Float.NaN;
        /** Why warp is held or refused now ("" when it is free). */
        public String warpHold = "";

        /** Sim seconds since the career began. */
        public double simSeconds;

        public void clear() {
            presence = Presence.NONE;
            ship = null;
            seatedGun = null;
            exteriorView = true;
            condition = Condition.GREEN;
            docked = false;
            stationName = "";
            latched = false;
            warp = 1;
            railsWarp = false;
            combat = false;
            missilesInbound = 0;
            emergency = false;
            disabledReason = "";
            abandoning = false;
            crewOutside = false;
            holdFire = false;
            helmsman = "";
            helmFault = "";
            helmOrder = "";
            burnFlown = false;
            warpNextStop = Float.NaN;
            warpHold = "";
            simSeconds = 0;
        }

        /** Seated at the helm, the weapons console or a gun. */
        public boolean seated() {
            return presence == Presence.HELM || presence == Presence.WEAPONS || presence == Presence.GUN;
        }
    }
}
