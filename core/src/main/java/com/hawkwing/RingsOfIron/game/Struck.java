package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.macrosim.hostile.HostileComponent;

/**
 * Round 10: which ships have struck, for the player's guns firing at will and for Tab. A ship has
 * struck when it has {@link DisabledComponent.Reason#SURRENDERED surrendered}, or when it is one of
 * a fallen base's gun platforms ({@link PirateBase#silenced}): those never surrender, they fall
 * silent with the base. A ship disabled for other reasons (a short, a broken hull) has not struck.
 */
final class Struck {
    private Struck() {
    }

    /** True if {@code ship} has struck its colours: surrendered, or a fallen base's silenced battery. */
    static boolean struck(GameWorld world, Entity ship) {
        if (ship == null) return false;
        if (ShipDisablement.reason(ship) == DisabledComponent.Reason.SURRENDERED) return true;
        HostileComponent h = ship.getComponent(HostileComponent.class);
        return h != null && world.pirateBase != null && world.pirateBase.silenced(h.displayName);
    }
}
