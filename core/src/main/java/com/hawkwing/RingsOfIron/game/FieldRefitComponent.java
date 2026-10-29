package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

import java.util.ArrayList;

/**
 * Salvaged weapons the crew are to mount on a ship's hardpoints from outside, away from a yard
 * ({@link FieldRefits}). Saved with the ship; the crew's tasks are not, and are posted again. A
 * crate in someone's hand at a save is back in the hold after the load.
 */
public class FieldRefitComponent implements Component {

    /** Where an order's part is. */
    public enum Stage {
        /** In its crate in the hold, for an engineer to fetch. */
        HOLD,
        /** In its crate in a crew member's hand, on its way out. */
        CARRIED,
        /** Clipped on outside at the hardpoint, where the work goes on. */
        AT_MOUNT,
        /** Mounted: the weapon it replaced, crated, is on its way in to the hold. */
        BRINGING_OLD
    }

    /** One salvaged weapon for one hardpoint. */
    public static class Order {
        /** Index into the ship's weapon mounts. */
        public int hardpoint;
        /** The salvaged weapon to mount. */
        public String weapon = "";
        public Stage stage = Stage.HOLD;
        /** Seconds of work put in at the hardpoint so far. */
        public float worked;
        /** The weapon taken off, while it is brought in ({@link Stage#BRINGING_OLD}). */
        public String old = "";
        /** Who has the crate in hand ({@link Stage#CARRIED}, {@link Stage#BRINGING_OLD}), or null. */
        public transient Entity carrier;

        public Order() {
        }

        public Order(int hardpoint, String weapon) {
            this.hardpoint = hardpoint;
            this.weapon = weapon;
        }
    }

    public ArrayList<Order> orders = new ArrayList<>();

    /** The order for {@code hardpoint}, or null. */
    public Order order(int hardpoint) {
        for (Order o : orders) {
            if (o.hardpoint == hardpoint) return o;
        }
        return null;
    }
}
