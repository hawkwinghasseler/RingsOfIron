package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;

/** On the player's avatar while it sits at a console of its ship: which one. Saved. See {@link Bridge}. */
public class SeatComponent implements Component {
    /** {@link SubsystemType#HELM}, {@link SubsystemType#WEAPONS} or {@link SubsystemType#TURRET_MOUNT} (a gun). */
    public SubsystemType console;
    /** At a gun: its turret mount's footprint origin, which tells it from the ship's other guns. */
    public int gunX = -1, gunY = -1;

    public SeatComponent() {
    }

    public SeatComponent(SubsystemType console) {
        this.console = console;
    }
}
