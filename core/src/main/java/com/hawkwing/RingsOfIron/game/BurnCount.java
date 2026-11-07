package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.orbital.components.ManeuverNodeComponent;

import java.util.function.ToDoubleFunction;

/**
 * Counts the burns flown out of the plan, so "burn 2 of 3" still reads right once burn 1 has left
 * the game's list (the helm takes a burn off it as it lights the engine, the rails as they apply it,
 * and a missed one goes too). A burn that leaves the list when it was due, within its lead, counts
 * as flown; one taken off long before its time was deleted from the plan. The count starts again
 * once the plan is empty with nothing being flown. Seen once a frame; a load starts it at nothing.
 */
final class BurnCount {
    /** Slack past a burn's lead within which leaving the list still counts as flown, s. */
    static final double DUE_SLACK = 1;

    private final Array<ManeuverNodeComponent.Node> seen = new Array<>(false, 8);
    /** Each seen burn's time as it was then (a burn moved later or earlier keeps its node). */
    private double[] seenTimes = new double[8];
    private int flown;

    /**
     * Takes note of the plan {@code m} (null for none) at {@code now}, with a burn being flown or
     * not; {@code lead} is how long before a burn's time the helm starts on it.
     */
    void update(ManeuverNodeComponent m, boolean burning, double now, ToDoubleFunction<ManeuverNodeComponent.Node> lead) {
        for (int i = 0; i < seen.size; i++) {
            ManeuverNodeComponent.Node n = seen.get(i);
            if (m != null && m.nodes.contains(n, true)) continue;
            if (seenTimes[i] - now <= lead.applyAsDouble(n) + DUE_SLACK) flown++;
        }
        seen.clear();
        int pending = m == null ? 0 : m.nodes.size;
        if (seenTimes.length < pending) seenTimes = new double[pending * 2];
        for (int i = 0; i < pending; i++) {
            seen.add(m.nodes.get(i));
            seenTimes[i] = m.nodes.get(i).time;
        }
        if (pending == 0 && !burning) flown = 0;
        // Flying one that left the list before this was watching: it was in the plan
        if (burning && flown == 0) flown = 1;
    }

    /** Burns flown out of this plan, the one being flown among them. */
    int flown() {
        return flown;
    }
}
