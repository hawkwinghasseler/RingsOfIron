package com.ringsofiron.orbital.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;

/**
 * Scheduled impulsive burns for a body on rails, in time order. When the next node is due, the
 * orbital event system applies its delta-v and recomputes the orbit. A finite burn is approximated
 * as one impulse at its midpoint (split it into several nodes if it is long).
 *
 * <p>Inside the bubble the same nodes are goals for a flight controller with real thrust.
 */
public class ManeuverNodeComponent implements Component {
    public static class Node {
        /** Sim seconds. */
        public double time;
        /** m/s along the velocity. */
        public double prograde;
        /** m/s perpendicular to the velocity, positive away from the parent's side of the orbit. */
        public double radial;

        public Node() {
        }

        public Node(double time, double prograde, double radial) {
            this.time = time;
            this.prograde = prograde;
            this.radial = radial;
        }
    }

    public final Array<Node> nodes = new Array<>();

    /** Adds a node, keeping the list sorted by time. */
    public ManeuverNodeComponent add(double time, double prograde, double radial) {
        Node node = new Node(time, prograde, radial);
        int i = 0;
        while (i < nodes.size && nodes.get(i).time <= time) i++;
        nodes.insert(i, node);
        return this;
    }

    /** The earliest pending node, or null. */
    public Node next() {
        return nodes.size > 0 ? nodes.first() : null;
    }
}
