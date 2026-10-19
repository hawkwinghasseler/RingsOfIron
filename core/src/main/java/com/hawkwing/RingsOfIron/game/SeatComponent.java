package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;

/** On the player's avatar while it sits at a console of its ship: which one. Saved. See {@link Bridge}. */
public class SeatComponent implements Component {
    /** {@link SubsystemType#HELM} or {@link SubsystemType#WEAPONS}. */
    public SubsystemType console;

    public SeatComponent() {
    }

    public SeatComponent(SubsystemType console) {
        this.console = console;
    }
}
