package com.ringsofiron.shipops;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CarryTask;
import com.ringsofiron.aicrew.HullStripTask;
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
        /** Work across on EVA, standing off alongside the craft: the part off its hull and home. */
        public transient HullStripTask eva;
        public transient boolean inHand;
        /** Who has its crate in hand on the way home from an EVA, or null. */
        public transient Entity carrier;
        /** Latched on, no one can get to it from inside the craft (a part on its hull, say): it waits. */
        public transient boolean unreachable;
        /** Standing off alongside, it is inside the hull where no one on EVA can get at it: it waits. */
        public transient boolean inside;

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
