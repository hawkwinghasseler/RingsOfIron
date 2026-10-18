package com.ringsofiron.macrosim.economy;

import com.badlogic.ashley.core.Component;

/** Marks a station entity as part of the economy. */
public class EconomyNodeComponent implements Component {
    public EconomyNode node;

    public EconomyNodeComponent() {
    }

    public EconomyNodeComponent(EconomyNode node) {
        this.node = node;
    }
}
