package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;
import com.ringsofiron.aicrew.CarryTask;
import com.ringsofiron.aicrew.YardTask;
import com.ringsofiron.coreengine.EntityRef;

import java.util.ArrayList;

/**
 * Yard work a ship has ordered at a station ({@link StationYard}): repairs, and refits of its
 * weapon hardpoints. Saved with the ship; the tasks in hand are not, and are posted again.
 */
public class YardOrdersComponent implements Component {

    /** A new weapon for one hardpoint: carried aboard, then fitted tile by tile. */
    public static class Refit {
        /** Index into the ship's weapon mounts. */
        public int hardpoint;
        public String weapon;
        public boolean used;
        /** Fitted from a salvaged part out of the ship's own hold: nothing to pay, and it goes back there if called off. */
        public boolean salvaged;
        /** Credits paid when the part came aboard. */
        public double paid;
        /** The part is aboard, by its mount. */
        public boolean aboard;
        /** Tiles of the mount fitted, and work put into the next one. */
        public int tilesDone;
        public float tileWork;

        public transient CarryTask trip;
        public transient YardTask task;

        public Refit() {
        }

        public Refit(int hardpoint, String weapon, boolean used) {
            this.hardpoint = hardpoint;
            this.weapon = weapon;
            this.used = used;
        }
    }

    /** The station doing the work. */
    public EntityRef station;
    /** Repairs ordered: the yard works through every damaged tile and system. */
    public boolean repairs;
    /** Credits the repairs have cost so far. */
    public double repairsPaid;
    public ArrayList<Refit> refits = new ArrayList<>();

    /** The refit ordered for {@code hardpoint}, or null. */
    public Refit refit(int hardpoint) {
        for (Refit r : refits) {
            if (r.hardpoint == hardpoint) return r;
        }
        return null;
    }

    public boolean isEmpty() {
        return !repairs && refits.isEmpty();
    }
}
