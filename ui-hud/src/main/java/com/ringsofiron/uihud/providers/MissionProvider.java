package com.ringsofiron.uihud.providers;

import com.badlogic.gdx.utils.Array;

/**
 * A station's mission board for the mission window: the jobs on offer where the player's ship is
 * docked, and the contracts the player already holds. Implemented in the game wiring, so ui-hud
 * does not depend on the economy or the factions. Called on the render thread.
 */
public interface MissionProvider {

    /** Fills {@code out} (already cleared). Leave {@link MissionBoard#stationName} empty when not docked at a board. */
    void collect(MissionBoard out);

    /** Takes offer {@code id}; returns what the board says. */
    String accept(long id);

    /** Gives up the held contract {@code id}; returns what the board says. */
    String abandon(long id);

    final class Job {
        public long id;
        /** The job in a line: "Deliver 120 Water to Highport". */
        public String title = "";
        /** Who gives it. */
        public String giver = "";
        /** Pay, standing and time: "3,000 cr, +5 standing, 2.0 d". */
        public String terms = "";
        /** Why it can't be taken yet ("Needs rank Lieutenant"), or empty. */
        public String locked = "";
    }

    final class MissionBoard {
        /** Station whose board is open; empty when not docked at one. */
        public String stationName = "";
        /** The player's rank and standing with the board's faction, one line. */
        public String career = "";
        public final Array<Job> offers = new Array<>();
        /** Contracts held, with their terms showing time left and how far away. */
        public final Array<Job> held = new Array<>();

        public Job addOffer() {
            Job j = new Job();
            offers.add(j);
            return j;
        }

        public Job addHeld() {
            Job j = new Job();
            held.add(j);
            return j;
        }

        /** True when there is anything to show: a board at the docked station, or a contract held. */
        public boolean isOpen() {
            return !stationName.isEmpty() || held.size > 0;
        }

        public void clear() {
            stationName = "";
            career = "";
            offers.clear();
            held.clear();
        }
    }
}
