package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.macrosim.hostile.HostileComponent;

/**
 * Round 10: the one word the playtest's {@code hostiles} command gives a hostile craft's fight. Round 9's
 * window found a fallen base's batteries read "fit" after the base had struck, and struck raiders read
 * by their enum name.
 */
public final class HostileLabel {
    /** A ship that cut thrust and surrendered. */
    public static final String STRUCK = "struck";
    /** One of a fallen base's gun platforms ({@link PirateBase#silenced}). */
    public static final String SILENCED = "silenced";
    /** Broke off and flying away, not beaten. */
    public static final String RUNNING = "running";
    /** Still in the fight. */
    public static final String FIT = "fit";

    private HostileLabel() {
    }

    /** {@code ship}'s label: silenced, struck, running or fit, or the reason it is disabled (NO_POWER, WRECKED...). */
    public static String of(GameWorld world, Entity ship) {
        HostileComponent h = ship.getComponent(HostileComponent.class);
        if (h != null && world.pirateBase.silenced(h.displayName)) return SILENCED;
        DisabledComponent d = ship.getComponent(DisabledComponent.class);
        if (d != null) return d.reason == DisabledComponent.Reason.SURRENDERED ? STRUCK : d.reason.name();
        return h != null && h.brokeOff ? RUNNING : FIT;
    }
}
