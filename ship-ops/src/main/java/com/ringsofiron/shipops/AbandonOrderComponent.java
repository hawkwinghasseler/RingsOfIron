package com.ringsofiron.shipops;

import com.badlogic.ashley.core.Component;

/**
 * On a ship whose crew have been ordered to the escape pods (see {@link AbandonShip}). Saved with
 * the ship; the seats are posted again on load.
 */
public class AbandonOrderComponent implements Component {
    /** Sim seconds when the order was given. */
    public double orderedAt;
    /** Pods that have launched under this order. */
    public int launched;

    public AbandonOrderComponent() {}

    public AbandonOrderComponent(double orderedAt) {
        this.orderedAt = orderedAt;
    }
}
