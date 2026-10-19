package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;
import com.ringsofiron.aicrew.CarryTask;
import com.ringsofiron.aicrew.YardTask;
import com.ringsofiron.coreengine.EntityRef;

import java.util.ArrayList;

/**
 * Parts a ship's crew are stripping off another craft ({@link Salvage}). Saved with the ship; the
 * tasks in hand are not, and are posted again.
 */
public class SalvageOrdersComponent implements Component {

    /** One part to strip and carry home. */
    public static class Strip {
        /** The craft it comes off. */
        public EntityRef craft;
        /** Its system's mount origin on that craft. */
        public int x, y;
        /** Commodity the crate is carried and stowed as: {@code part:<id>}. */
        public String part;
        /** Work put into stripping it. */
        public float work;
        /** Stripped: a crate waits by its mount to be carried home. */
        public boolean off;

        public transient YardTask task;
        public transient CarryTask trip;
        public transient boolean inHand;

        public Strip() {
        }

        public Strip(EntityRef craft, int x, int y, String part) {
            this.craft = craft;
            this.x = x;
            this.y = y;
            this.part = part;
        }
    }

    public ArrayList<Strip> strips = new ArrayList<>();

    /** The strip ordered for the system at (x, y) of {@code craft}, or null. */
    public Strip find(EntityRef craft, int x, int y) {
        for (Strip s : strips) {
            if (s.craft != null && s.craft.equals(craft) && s.x == x && s.y == y) return s;
        }
        return null;
    }
}
