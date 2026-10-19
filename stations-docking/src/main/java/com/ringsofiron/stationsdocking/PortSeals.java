package com.ringsofiron.stationsdocking;

import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;

import java.util.function.Predicate;

/**
 * Pressure doors on a latched pair of ports. A port door its side opened ({@link DockLinks#join})
 * shuts by itself when the air beyond it is too thin ({@link #THIN}) and thinner than on its own
 * side, so a ship latched onto a holed one keeps its air. It opens for whoever walks through
 * ({@link EdgeWall#opensForCrew}) and shuts behind them, a puff of air lost each time, and opens
 * for good once the two sides' air is alike again ({@link #ALIKE}).
 *
 * <p>Each side's door looks after its own side: the thin side's door has nothing to hold and
 * stays as it was. A door its side never opened (clearance refused) or one blown away is left
 * alone. Which doors are shut for pressure is read from the doors themselves, so it needs no
 * saving: a door its side opened that is found shut is a pressure door.
 */
final class PortSeals {
    /** Air beyond the door at or below this share of standard pressure is too thin to share. */
    static final float THIN = 0.75f;
    /** Pressures within this share of standard of each other count as alike. */
    static final float ALIKE = 0.05f;
    /** Seconds a pressure door stays open after the last walker is through. */
    static final float HOLD_OPEN = 1f;

    private PortSeals() {
    }

    /**
     * Sets both doors of a welded link: open where its side agreed, unless holding a pressure
     * difference and nobody is crossing. {@code crossing} says whether someone is walking through
     * a pair of linked openings.
     */
    static void update(DockingSystem.Link link, ShipGridComponent ga, ShipGridComponent gb,
                       Predicate<GridLink> crossing, float dt) {
        if (link.gridLinks.size == 0) return;
        boolean busy = false;
        for (GridLink l : link.gridLinks) busy |= crossing.test(l);
        link.sealHold = busy ? HOLD_OPEN : Math.max(0f, link.sealHold - dt);
        boolean through = link.sealHold > 0f;
        side(ga, link.pa, link.gridLinks.first(), through);
        side(gb, link.pb, link.gridLinks.first().mate, through);
    }

    /** One side's door, judged from its first linked opening {@code l} (on {@code grid}). */
    private static void side(ShipGridComponent grid, DockingPort port, GridLink l, boolean through) {
        if (!(port.consented || port.forced)) return;
        MountFootprint m = DockLinks.mount(grid, port);
        if (m == null) return;
        float here = grid.pressureRatioAt(l.x, l.y), there = l.other.pressureRatioAt(l.ox, l.oy);
        boolean held = shut(grid, m);
        boolean hold = held ? !(there > THIN && Math.abs(here - there) < ALIKE) : there <= THIN && here > there;
        for (int[] t : DockLinks.doorTiles(m)) {
            EdgeWall door = DockingPortLayout.door(grid, m, t[0], t[1]);
            if (door == null || door.isDestroyed()) continue;
            door.opensForCrew = hold;
            door.bulkheadSealed = hold && !through;
        }
    }

    /** True if the port's doors are shut (or held for pressure); false if any stands open. */
    private static boolean shut(ShipGridComponent grid, MountFootprint m) {
        boolean any = false;
        for (int[] t : DockLinks.doorTiles(m)) {
            EdgeWall door = DockingPortLayout.door(grid, m, t[0], t[1]);
            if (door == null || door.isDestroyed()) continue;
            if (door.opensForCrew) return true;
            if (!door.bulkheadSealed) return false;
            any = true;
        }
        return any;
    }
}
